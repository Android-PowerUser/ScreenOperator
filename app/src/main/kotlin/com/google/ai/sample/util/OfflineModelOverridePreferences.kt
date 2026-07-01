package com.google.ai.sample.util

import android.content.Context
import android.util.Log
import androidx.core.content.edit

/**
 * Persists the most recently received remote offline-model override JSON (see
 * [OfflineModelOverrides]) so corrected download metadata keeps working across app restarts -
 * including before the WebView bundle has re-fetched and re-applied it for the current
 * session.
 */
object OfflineModelOverridePreferences {
    private const val TAG = "OfflineModelOverridesPrefs"
    private const val PREFS_NAME = "offline_model_overrides_prefs"
    private const val KEY_JSON = "overrides_json"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Saves the raw override JSON as last received from the WebView/remote bundle. */
    fun save(context: Context, json: String) {
        try {
            prefs(context).edit { putString(KEY_JSON, json) }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving offline model overrides: ${e.message}", e)
        }
    }

    /** Loads the last saved override JSON, or null if none has been received yet. */
    fun load(context: Context): String? {
        return try {
            prefs(context).getString(KEY_JSON, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading offline model overrides: ${e.message}", e)
            null
        }
    }
}
