package com.google.ai.sample.util

import android.util.Log
import org.json.JSONObject

/**
 * Remote-updatable error classification: the substrings used to detect a quota/rate-limit
 * error (triggers API key switching + retry) versus a high-demand/overloaded error (does not
 * switch keys, just informs the user) are matched against the AI provider's raw error message.
 *
 * That wording is controlled by the AI provider (Google, OpenAI, ...), not by this app, and has
 * changed before. Previously, fixing a stale match required a native code change and a new
 * release; `error-classification-overrides.json` (fetched by the WebView relative to
 * `index.html`, same pattern as `command-patterns.json`) lets you add/replace the substrings
 * without one - see `docs/error-classification-overrides.md`.
 *
 * Example payload:
 * ```json
 * {
 *   "quotaExceededSubstrings": ["exceeded your current quota", "code 429", "too many requests", "rate_limit"],
 *   "highDemandSubstrings": ["service unavailable (503)", "unavailable", "high demand", "overloaded"]
 * }
 * ```
 *
 * All matching is case-insensitive. An empty/missing payload falls back to the built-in
 * defaults below, which match the substrings this app has always checked for.
 */
internal object ErrorClassificationConfig {
    private const val TAG = "ErrorClassificationConfig"

    val DEFAULT_QUOTA_EXCEEDED_SUBSTRINGS = listOf(
        "exceeded your current quota",
        "code 429",
        "too many requests",
        "rate_limit"
    )

    val DEFAULT_HIGH_DEMAND_SUBSTRINGS = listOf(
        "service unavailable (503)",
        "unavailable",
        "high demand",
        "overloaded"
    )

    data class Policy(
        val quotaExceededSubstrings: List<String> = DEFAULT_QUOTA_EXCEEDED_SUBSTRINGS,
        val highDemandSubstrings: List<String> = DEFAULT_HIGH_DEMAND_SUBSTRINGS
    ) {
        fun isQuotaExceededError(message: String): Boolean =
            quotaExceededSubstrings.any { message.contains(it, ignoreCase = true) }

        fun isHighDemandError(message: String): Boolean =
            highDemandSubstrings.any { message.contains(it, ignoreCase = true) }
    }

    @Volatile
    private var currentPolicy: Policy = Policy()

    fun current(): Policy = currentPolicy

    /**
     * Parses and installs remotely supplied substring lists. Malformed JSON falls back to the
     * previous policy (or defaults); a present-but-empty array is honored as-is (so you can
     * intentionally disable a category), while a missing field keeps the built-in default for
     * that category.
     *
     * @return 1 if the payload was successfully parsed and installed, 0 if it was blank/invalid.
     */
    @Synchronized
    fun setRemoteOverride(json: String): Int {
        if (json.isBlank()) {
            currentPolicy = Policy()
            return 0
        }
        return try {
            val obj = JSONObject(json)
            val quota = readStringArray(obj, "quotaExceededSubstrings") ?: DEFAULT_QUOTA_EXCEEDED_SUBSTRINGS
            val highDemand = readStringArray(obj, "highDemandSubstrings") ?: DEFAULT_HIGH_DEMAND_SUBSTRINGS
            currentPolicy = Policy(quotaExceededSubstrings = quota, highDemandSubstrings = highDemand)
            Log.d(
                TAG,
                "Installed error classification override: ${quota.size} quota substring(s), " +
                    "${highDemand.size} high-demand substring(s)"
            )
            1
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse error classification overrides: ${e.message}", e)
            0
        }
    }

    private fun readStringArray(obj: JSONObject, key: String): List<String>? {
        if (!obj.has(key)) return null
        val array = obj.optJSONArray(key) ?: return null
        return (0 until array.length()).mapNotNull { idx -> array.optString(idx)?.takeIf { it.isNotBlank() } }
    }

    /** Reverts to the built-in default substring lists. */
    @Synchronized
    fun clearRemoteOverride() {
        currentPolicy = Policy()
    }
}
