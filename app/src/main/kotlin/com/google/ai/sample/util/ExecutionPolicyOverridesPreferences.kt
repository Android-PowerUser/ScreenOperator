package com.google.ai.sample.util

import android.content.Context
import android.util.Log
import androidx.core.content.edit

/**
 * Persists the most recently received remote execution-policy override JSON (see
 * [ExecutionPolicyConfig]) so the per-message command limit and feedback template keep working
 * across app restarts - including before the WebView bundle has re-fetched and re-applied it
 * for the current session.
 */
object ExecutionPolicyOverridesPreferences {
    private const val TAG = "ExecPolicyOverridesPrefs"
    private const val PREFS_NAME = "execution_policy_overrides_prefs"
    private const val KEY_JSON = "overrides_json"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Saves the raw override JSON as last received from the WebView/remote bundle. */
    fun save(context: Context, json: String) {
        try {
            prefs(context).edit { putString(KEY_JSON, json) }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving execution policy overrides: ${e.message}", e)
        }
    }

    /** Loads the last saved override JSON, or null if none has been received yet. */
    fun load(context: Context): String? {
        return try {
            prefs(context).getString(KEY_JSON, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading execution policy overrides: ${e.message}", e)
            null
        }
    }
}
