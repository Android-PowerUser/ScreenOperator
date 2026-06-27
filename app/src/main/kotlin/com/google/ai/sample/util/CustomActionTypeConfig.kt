package com.google.ai.sample.util

import android.util.Log
import org.json.JSONArray

/**
 * Allows entirely new action types to be defined at runtime from a remotely fetched JSON
 * config (e.g. shipped alongside the WebView's index.html), without requiring a new app
 * release.
 *
 * When the native command parser matches one of these entries it emits a
 * [Command.WebViewCustomAction] and the accessibility service calls back into JavaScript
 * via `window.onCustomAction(id, groups[])`. The JS handler can then invoke any existing
 * `Android.*` bridge method to carry out the actual work.
 *
 * Example payload (`custom-action-types.json` next to index.html):
 * ```json
 * [
 *   {
 *     "id": "PINCH_ZOOM",
 *     "regex": "(?i)\\bpinchZoom\\(\\s*([\\d.%]+)\\s*,\\s*([\\d.%]+)\\s*,\\s*([\\d.]+)\\s*\\)"
 *   }
 * ]
 * ```
 *
 * - `id`    — unique name passed to `window.onCustomAction` as the first argument.
 * - `regex` — Kotlin/Java regular expression; capture groups are forwarded to JS as an
 *             array (index 0 = first capture group).
 */
internal object CustomActionTypeConfig {
    private const val TAG = "CustomActionTypeConfig"

    data class ParsedEntry(
        val id: String,
        val regex: Regex
    )

    /**
     * Parses a JSON array of custom action type definitions. Malformed entries are skipped
     * (and logged) rather than throwing, so a bad remote config degrades gracefully to "no
     * extra action types" rather than breaking the app or the built-in command set.
     */
    fun parse(json: String): List<ParsedEntry> {
        val result = mutableListOf<ParsedEntry>()
        if (json.isBlank()) return result

        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i)
                if (entry == null) {
                    Log.w(TAG, "Skipping entry at index $i: not a JSON object")
                    continue
                }

                val id = entry.optString("id", "").trim()
                if (id.isEmpty()) {
                    Log.w(TAG, "Skipping entry at index $i: missing or empty 'id'")
                    continue
                }

                val pattern = entry.optString("regex", "")
                if (pattern.isBlank()) {
                    Log.w(TAG, "Skipping entry '$id': empty/missing regex")
                    continue
                }

                val regex = try {
                    Regex(pattern)
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping entry '$id': invalid regex '$pattern' (${e.message})")
                    continue
                }

                result.add(ParsedEntry(id, regex))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse custom action type definitions: ${e.message}", e)
        }
        return result
    }
}
