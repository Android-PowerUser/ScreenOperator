package com.google.ai.sample.util

import android.content.Context
import android.util.Log
import androidx.core.content.edit

/**
 * Persists the most recently received macro/extension-handler JSON (see
 * [WebViewBridge.setMacros] and [WebViewBridge.setExtensionHandlers]) across app
 * restarts, mirroring [CommandPatternOverridesPreferences].
 *
 * Macros are JSON-defined sequences of [WebViewBridge.dispatch] calls that let a
 * remotely fetched web bundle add new composite behaviour — and wire pre-provisioned
 * extension slots (extA..extJ) — without an app release.
 */
object MacroPreferences {
    private const val TAG = "MacroPreferences"
    private const val PREFS_NAME = "macro_prefs"
    private const val KEY_JSON = "macros_json"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, json: String) {
        try {
            prefs(context).edit { putString(KEY_JSON, json) }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving macros: ${e.message}", e)
        }
    }

    fun load(context: Context): String? {
        return try {
            prefs(context).getString(KEY_JSON, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading macros: ${e.message}", e)
            null
        }
    }
}
