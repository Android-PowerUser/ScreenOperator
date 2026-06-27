package com.google.ai.sample.util

import android.content.Context
import android.util.Log
import androidx.core.content.edit

/**
 * Persists the most recently received custom action type JSON (see [CustomActionTypeConfig] /
 * [CommandParser.setCustomActionTypes]) so that remotely defined action types keep working
 * across app restarts — including before the WebView bundle has re-fetched and re-applied
 * its config for the current session.
 */
object CustomActionTypePreferences {
    private const val TAG = "CustomActionTypePrefs"
    private const val PREFS_NAME = "custom_action_type_prefs"
    private const val KEY_JSON = "action_types_json"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Saves the raw JSON as last received from the WebView/remote bundle. */
    fun save(context: Context, json: String) {
        try {
            prefs(context).edit { putString(KEY_JSON, json) }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving custom action types: ${e.message}", e)
        }
    }

    /** Loads the last saved JSON, or null if none has been received yet. */
    fun load(context: Context): String? {
        return try {
            prefs(context).getString(KEY_JSON, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading custom action types: ${e.message}", e)
            null
        }
    }
}
