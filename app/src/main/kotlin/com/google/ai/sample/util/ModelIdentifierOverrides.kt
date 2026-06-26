package com.google.ai.sample.util

import android.util.Log
import com.google.ai.sample.ModelOption
import org.json.JSONArray

/**
 * Lets the WebView bundle remotely correct the wire-level model identifier string
 * ("modelName") that an existing, built-in [ModelOption] sends to its provider, without
 * requiring a new app release.
 *
 * This exists because provider-side model identifiers occasionally change or get retired out
 * from under the app - most commonly Gemini preview models (see README: "Preview models will
 * eventually be removed by Google ... If this happens, please change the API in the code.").
 * Instead of that always requiring a code change + release, a JSON file shipped next to
 * index.html (e.g. "model-identifier-overrides.json") can supply a replacement identifier for
 * an *existing* built-in model.
 *
 * Example payload:
 * ```json
 * [
 *   { "id": "GEMINI_FLASH_LITE_PREVIEW", "modelName": "gemini-2.5-flash-lite" }
 * ]
 * ```
 *
 * SAFETY BOUNDARY (same spirit as [CommandPatternConfig]): this can only replace the
 * identifier string sent for an *already existing* [ModelOption] entry, and only at the exact
 * call sites that were already deliberately wired up to consult it (see usages of [resolve]).
 * It cannot add a new provider, change which endpoint/SDK/code path handles the request,
 * change billing/API-key handling, or introduce any new capability - it only swaps which
 * model name is requested from the same, already-reviewed call site. To add a genuinely new
 * model/provider, use [CustomModelConfig] instead.
 */
object ModelIdentifierOverrides {
    private const val TAG = "ModelIdOverrides"

    @Volatile
    private var overrides: Map<String, String> = emptyMap()

    /**
     * Parses and applies a remote override JSON, replacing any previously applied overrides.
     * Entries referencing an unknown [ModelOption] id, or missing a field, are skipped (and
     * logged) instead of throwing, so a bad remote config degrades to "no overrides" rather
     * than crashing the app. Returns the number of overrides applied.
     */
    fun setRemoteOverrides(json: String): Int {
        val parsed = mutableMapOf<String, String>()
        if (json.isNotBlank()) {
            try {
                val knownIds = ModelOption.values().map { it.name }.toSet()
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val entry = array.optJSONObject(i)
                    if (entry == null) {
                        Log.w(TAG, "Skipping override at index $i: not a JSON object")
                        continue
                    }
                    val id = entry.optString("id", "")
                    val modelName = entry.optString("modelName", "")
                    if (id.isBlank() || modelName.isBlank()) {
                        Log.w(TAG, "Skipping override at index $i: missing id/modelName")
                        continue
                    }
                    if (id !in knownIds) {
                        Log.w(TAG, "Skipping override '$id': not a known built-in ModelOption. Use custom models to add new ones, not this mechanism.")
                        continue
                    }
                    parsed[id] = modelName
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse remote model identifier overrides: ${e.message}", e)
            }
        }
        overrides = parsed
        return parsed.size
    }

    /**
     * Returns the effective wire-level model name for [option]: the remote override if one
     * exists and is non-blank, otherwise the compiled-in default ([ModelOption.modelName]).
     *
     * Only call this where the resolved string is placed directly into an outgoing
     * request/SDK call. Do NOT use it for any reverse lookup (e.g.
     * `ModelOption.values().find { it.modelName == x }`) or as a persistence/settings key -
     * those must keep using the original, stable [ModelOption.modelName].
     */
    fun resolve(option: ModelOption): String = overrides[option.name] ?: option.modelName
}
