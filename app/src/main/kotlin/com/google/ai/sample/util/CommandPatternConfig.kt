package com.google.ai.sample.util

import android.util.Log
import org.json.JSONArray

/**
 * Allows the set of recognized command *syntaxes* to be extended at runtime from a remotely
 * fetched JSON config (e.g. shipped alongside the WebView's index.html on the
 * feature/webview-test branch), without requiring a new app release.
 *
 * Example payload (a JSON array, e.g. "command-patterns.json" next to index.html):
 * ```json
 * [
 *   { "id": "clickBtnCapitalized", "commandType": "CLICK_BUTTON", "regex": "(?i)\\bClick\\([\"']([^\"']+)[\"']" }
 * ]
 * ```
 *
 * IMPORTANT (safety boundary): an override can only attach a *new regular expression* to an
 * *already existing* [CommandParser.CommandType]. It can never introduce a new kind of action,
 * and it can never run arbitrary code - the actual [Command] that gets built (and therefore
 * everything that is allowed to happen on the device) is still produced by the same,
 * compiled-in builder function that ships with the app for that command type. This means a new
 * model that simply phrases an existing action differently (e.g. "Click('...')" instead of
 * "click(\"...\")") can be supported purely by editing a JSON file in the repo - while what each
 * action is actually allowed to do stays fixed in native code and unrelated to this mechanism.
 */
internal object CommandPatternConfig {
    private const val TAG = "CommandPatternConfig"

    data class ParsedOverride(
        val id: String,
        val commandType: CommandParser.CommandType,
        val regex: Regex
    )

    /**
     * Parses a JSON array of pattern overrides. Any entry that is malformed, references an
     * unknown command type, or contains an invalid regex is skipped (and logged) instead of
     * throwing, so a bad remote config degrades to "no extra patterns" rather than crashing
     * the app or blocking recognition of built-in patterns.
     */
    fun parse(json: String): List<ParsedOverride> {
        val result = mutableListOf<ParsedOverride>()
        if (json.isBlank()) return result

        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i)
                if (entry == null) {
                    Log.w(TAG, "Skipping override at index $i: not a JSON object")
                    continue
                }

                val id = entry.optString("id", "remote_$i")
                val typeName = entry.optString("commandType", "")
                val pattern = entry.optString("regex", "")

                if (pattern.isBlank()) {
                    Log.w(TAG, "Skipping override '$id': empty/missing regex")
                    continue
                }

                val commandType = try {
                    CommandParser.CommandType.valueOf(typeName)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Skipping override '$id': unknown commandType '$typeName'")
                    continue
                }

                val regex = try {
                    Regex(pattern)
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping override '$id': invalid regex '$pattern' (${e.message})")
                    continue
                }

                result.add(ParsedOverride(id, commandType, regex))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse remote command pattern overrides: ${e.message}", e)
        }
        return result
    }
}
