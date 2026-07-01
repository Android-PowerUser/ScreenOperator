package com.google.ai.sample.util

import android.content.Context
import android.util.Log
import androidx.core.content.edit

/**
 * Persists the most recently received app-mapping override JSON (see
 * [AppMappingOverridesConfig]) across app restarts, mirroring
 * [CommandPatternOverridesPreferences].
 */
object AppMappingOverridesPreferences {
    private const val TAG = "AppMappingOverridesPrefs"
    private const val PREFS_NAME = "app_mapping_overrides_prefs"
    private const val KEY_JSON = "overrides_json"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, json: String) {
        try {
            prefs(context).edit { putString(KEY_JSON, json) }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving app-mapping overrides: ${e.message}", e)
        }
    }

    fun load(context: Context): String? {
        return try {
            prefs(context).getString(KEY_JSON, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading app-mapping overrides: ${e.message}", e)
            null
        }
    }
}
