package com.google.ai.sample.util

import android.content.Context
import android.util.Log
import androidx.core.content.edit

/**
 * Persists the most recently received operational-tuning override JSON (see
 * [OperationalTuningConfig]) across app restarts, mirroring [CommandPatternOverridesPreferences].
 */
object OperationalTuningOverridesPreferences {
    private const val TAG = "OperationalTuningPrefs"
    private const val PREFS_NAME = "operational_tuning_overrides_prefs"
    private const val KEY_JSON = "overrides_json"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, json: String) {
        try {
            prefs(context).edit { putString(KEY_JSON, json) }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving operational tuning overrides: ${e.message}", e)
        }
    }

    fun load(context: Context): String? {
        return try {
            prefs(context).getString(KEY_JSON, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading operational tuning overrides: ${e.message}", e)
            null
        }
    }
}
