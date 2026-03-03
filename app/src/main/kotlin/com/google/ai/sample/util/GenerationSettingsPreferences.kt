package com.google.ai.sample.util

import android.content.Context
import android.util.Log

/**
 * Persists TopK, TopP, and Temperature settings per model.
 */
object GenerationSettingsPreferences {
    private const val TAG = "GenSettingsPrefs"
    private const val PREFS_NAME = "generation_settings"

    data class GenerationSettings(
        val temperature: Float = 0.0f,
        val topP: Float = 0.0f,
        val topK: Int = 0
    )

    fun saveSettings(context: Context, modelName: String, settings: GenerationSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("${modelName}_temperature", settings.temperature)
            .putFloat("${modelName}_topP", settings.topP)
            .putInt("${modelName}_topK", settings.topK)
            .apply()
        Log.d(TAG, "Saved settings for $modelName: temp=${settings.temperature}, topP=${settings.topP}, topK=${settings.topK}")
    }

    fun loadSettings(context: Context, modelName: String): GenerationSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return GenerationSettings(
            temperature = prefs.getFloat("${modelName}_temperature", 0.0f),
            topP = prefs.getFloat("${modelName}_topP", 0.0f),
            topK = prefs.getInt("${modelName}_topK", 0)
        )
    }
}
