package com.google.ai.sample.util

import android.util.Log
import org.json.JSONObject

/**
 * Remote-updatable overrides for native (Jetpack Compose) UI strings that are not already
 * covered by Android's own `strings.xml` resource system or by one of the more specific
 * configs ([TrialUiConfig] for trial/donation dialogs, [ErrorClassificationConfig] for AI-error
 * matching, etc).
 *
 * This is the native-side counterpart to `index.html`'s own UI strings (which already live in,
 * and can already be edited directly in, the WebView bundle since the WebView renders its own
 * UI from that file). Compose UI runs in a different render engine that `index.html` cannot
 * reach directly - so for *native* screens, the default English text is defined once as plain
 * Kotlin string literals at each call site (just like before), and this config provides an
 * optional remote override keyed by a stable string ID, fetched from `ui-strings-overrides.json`
 * (same fetch/bridge/preferences pattern as every other override in this app).
 *
 * Usage at a call site:
 * ```kotlin
 * Toast.makeText(context, UiStringsConfig.get("toast_model_already_downloaded", "Model already downloaded."), Toast.LENGTH_SHORT).show()
 * ```
 *
 * The second argument is always the original, unmodified built-in text - so even if this
 * config were never wired up to a remote file at all, behavior is byte-for-byte identical to
 * before. See `docs/ui-strings-overrides.md` for the full list of recognized IDs.
 */
internal object UiStringsConfig {
    private const val TAG = "UiStringsConfig"

    @Volatile
    private var overrides: Map<String, String> = emptyMap()

    /** Returns the remote override for [id] if one is set and non-blank, otherwise [default]. */
    fun get(id: String, default: String): String {
        return overrides[id]?.takeIf { it.isNotBlank() } ?: default
    }

    /**
     * Like [get], but substitutes positional placeholders ({0}, {1}, ...) with [args] (in
     * order) after resolving the override-vs-default text. Use for strings that need to embed
     * dynamic content (e.g. an error message or a file name) - both the built-in default and
     * any override may use {0}, {1}, etc. anywhere in their text.
     */
    fun get(id: String, default: String, vararg args: Any?): String {
        var text = get(id, default)
        args.forEachIndexed { index, arg -> text = text.replace("{$index}", arg.toString()) }
        return text
    }

    /**
     * Parses and installs a remotely supplied `{"id": "text", ...}` map. Non-string values are
     * skipped individually rather than failing the whole payload. Malformed JSON leaves the
     * previous overrides untouched.
     *
     * @return the number of string overrides installed.
     */
    @Synchronized
    fun setRemoteOverride(json: String): Int {
        if (json.isBlank()) {
            overrides = emptyMap()
            return 0
        }
        return try {
            val obj = JSONObject(json)
            val parsed = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.optString(key, "")
                if (value.isNotBlank()) {
                    parsed[key] = value
                }
            }
            overrides = parsed
            Log.d(TAG, "Installed ${parsed.size} UI string override(s)")
            parsed.size
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse UI string overrides: ${e.message}", e)
            0
        }
    }

    /** Reverts to no overrides (every [get] call returns its built-in default). */
    @Synchronized
    fun clearRemoteOverride() {
        overrides = emptyMap()
    }
}
