package com.google.ai.sample.util

import android.util.Log
import org.json.JSONObject

/**
 * Remote-updatable execution policy: caps how many commands from a single AI response are
 * actually executed, and lets the WebView bundle customize the feedback text that is appended
 * to the next screenshot/screen-elements message when commands had to be dropped because the
 * model sent more than the configured limit in one go.
 *
 * `execution-policy-overrides.json` (fetched by the WebView relative to `index.html`, just like
 * `command-patterns.json` and the other `*-overrides.json` files) lets you tune this without a
 * native app release - see `docs/execution-policy-overrides.md`.
 *
 * Example payload:
 * ```json
 * {
 *   "maxCommandsPerMessage": 2,
 *   "truncationFeedbackTemplate": "Note: this response contained {total} commands, but only the first {executed} were executed because more than {limit} commands were sent in a single message. Wait for this screenshot before sending more.",
 *   "maxRelevantScreenElementMessages": 3
 * }
 * ```
 *
 * `maxCommandsPerMessage` <= 0 (or the field missing/the whole file missing) means "unlimited",
 * i.e. the original, unrestricted behavior - the feature is fully opt-in via remote config and
 * never changes existing behavior unless an override is explicitly installed.
 *
 * `maxRelevantScreenElementMessages` controls how many of the most recent screenshot messages
 * keep their "Screen elements:" section intact in the chat history sent to the model; older
 * ones get collapsed to "no longer relevant" (see [PhotoReasoningScreenElementHistoryPolicy]).
 * Missing/invalid falls back to the built-in default of 3.
 */
internal object ExecutionPolicyConfig {
    private const val TAG = "ExecutionPolicyConfig"
    private const val DEFAULT_MAX_RELEVANT_SCREEN_ELEMENT_MESSAGES = 3

    const val DEFAULT_TEMPLATE =
        "Note: this response contained {total} commands, but only the first {executed} were " +
            "executed because more than {limit} commands were sent in a single message " +
            "without an intermediate screenshot. Please send at most {limit} commands per " +
            "message, then wait for the next screenshot before continuing."

    data class Policy(
        val maxCommandsPerMessage: Int = 0,
        val truncationFeedbackTemplate: String = DEFAULT_TEMPLATE,
        val maxRelevantScreenElementMessages: Int = DEFAULT_MAX_RELEVANT_SCREEN_ELEMENT_MESSAGES
    ) {
        /** Fills {total}/{executed}/{limit} placeholders into [truncationFeedbackTemplate]. */
        fun formatTruncationFeedback(total: Int, executed: Int): String {
            return truncationFeedbackTemplate
                .replace("{total}", total.toString())
                .replace("{executed}", executed.toString())
                .replace("{limit}", maxCommandsPerMessage.toString())
        }
    }

    @Volatile
    private var currentPolicy: Policy = Policy()

    fun current(): Policy = currentPolicy

    /**
     * Parses and installs a remotely supplied JSON object describing the execution policy.
     * Malformed JSON or a missing field falls back to defaults (unlimited commands / built-in
     * feedback template / 3 retained screen-element messages) instead of throwing, so a bad
     * remote config degrades gracefully rather than crashing the app or blocking command
     * execution.
     *
     * @return 1 if a policy object was successfully parsed and installed, 0 if the payload was
     *   blank/invalid and the previous policy (or defaults) remains in effect.
     */
    @Synchronized
    fun setRemoteOverride(json: String): Int {
        if (json.isBlank()) return 0
        return try {
            val obj = JSONObject(json)
            val max = obj.optInt("maxCommandsPerMessage", 0)
            val template = obj.optString("truncationFeedbackTemplate", DEFAULT_TEMPLATE)
                .ifBlank { DEFAULT_TEMPLATE }
            val maxRelevantScreenElementMessages = obj.optInt(
                "maxRelevantScreenElementMessages",
                DEFAULT_MAX_RELEVANT_SCREEN_ELEMENT_MESSAGES
            ).let { if (it < 0) DEFAULT_MAX_RELEVANT_SCREEN_ELEMENT_MESSAGES else it }
            currentPolicy = Policy(
                maxCommandsPerMessage = max,
                truncationFeedbackTemplate = template,
                maxRelevantScreenElementMessages = maxRelevantScreenElementMessages
            )
            Log.d(
                TAG,
                "Installed execution policy override: maxCommandsPerMessage=$max, " +
                    "maxRelevantScreenElementMessages=$maxRelevantScreenElementMessages"
            )
            1
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse execution policy override: ${e.message}", e)
            0
        }
    }

    /** Reverts to the built-in default policy (unlimited commands, 3 retained screenshots). */
    @Synchronized
    fun clearRemoteOverride() {
        currentPolicy = Policy()
    }
}
