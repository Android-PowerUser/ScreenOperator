package com.google.ai.sample.util

import android.content.Context
import android.util.Log

/**
 * Persists TopK, TopP, and Temperature settings per model.
 */
object GenerationSettingsPreferences {
    private const val TAG = "GenSettingsPrefs"
    private const val PREFS_NAME = "generation_settings"
    private const val KEY_TEMPERATURE_SUFFIX = "_temperature"
    private const val KEY_TOP_P_SUFFIX = "_topP"
    private const val KEY_TOP_K_SUFFIX = "_topK"

    data class GenerationSettings(
        val temperature: Float = 0.0f,
        val topP: Float = 0.0f,
        val topK: Int = 0
    )

    private fun key(modelName: String, suffix: String) = "$modelName$suffix"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSettings(context: Context, modelName: String, settings: GenerationSettings) {
        prefs(context).edit()
            .putFloat(key(modelName, KEY_TEMPERATURE_SUFFIX), settings.temperature)
            .putFloat(key(modelName, KEY_TOP_P_SUFFIX), settings.topP)
            .putInt(key(modelName, KEY_TOP_K_SUFFIX), settings.topK)
            .apply()
        Log.d(TAG, "Saved settings for $modelName: temp=${settings.temperature}, topP=${settings.topP}, topK=${settings.topK}")
    }

    fun loadSettings(context: Context, modelName: String): GenerationSettings {
        val prefs = prefs(context)
        return GenerationSettings(
            temperature = prefs.getFloat(key(modelName, KEY_TEMPERATURE_SUFFIX), 0.0f),
            topP = prefs.getFloat(key(modelName, KEY_TOP_P_SUFFIX), 0.0f),
            topK = prefs.getInt(key(modelName, KEY_TOP_K_SUFFIX), 0)
        )
    }
}
