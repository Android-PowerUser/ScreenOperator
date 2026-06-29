package com.google.ai.sample.util

import android.content.Context
import android.util.Log
import androidx.core.content.edit

/**
 * Persists the most recently received generation-defaults override JSON (see
 * [GenerationDefaultsConfig]) across app restarts, mirroring [CommandPatternOverridesPreferences].
 */
object GenerationDefaultsOverridesPreferences {
    private const val TAG = "GenDefaultsOverridesPrefs"
    private const val PREFS_NAME = "generation_defaults_overrides_prefs"
    private const val KEY_JSON = "overrides_json"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, json: String) {
        try {
            prefs(context).edit { putString(KEY_JSON, json) }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving generation defaults overrides: ${e.message}", e)
        }
    }

    fun load(context: Context): String? {
        return try {
            prefs(context).getString(KEY_JSON, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading generation defaults overrides: ${e.message}", e)
            null
        }
    }
}
