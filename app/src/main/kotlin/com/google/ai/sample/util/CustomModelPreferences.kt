package com.google.ai.sample.util

import android.content.Context
import android.util.Log
import androidx.core.content.edit

/**
 * Persists everything [CustomModelRegistry] needs across app restarts:
 * - the last received `custom-models.json` (so definitions survive before the WebView re-fetches it)
 * - which custom model (if any) was last selected
 * - a per-model API key, since custom models aren't tied to the existing [com.google.ai.sample.ApiProvider]
 *   enum/[com.google.ai.sample.ApiKeyManager] storage
 */
object CustomModelPreferences {
    private const val TAG = "CustomModelPreferences"
    private const val PREFS_NAME = "custom_model_prefs"
    private const val KEY_MODELS_JSON = "models_json"
    private const val KEY_ACTIVE_MODEL_ID = "active_model_id"
    private const val KEY_API_KEY_PREFIX = "api_key_"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveModelsJson(context: Context, json: String) {
        try {
            prefs(context).edit { putString(KEY_MODELS_JSON, json) }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving custom models json: ${e.message}", e)
        }
    }

    fun loadModelsJson(context: Context): String? {
        return try {
            prefs(context).getString(KEY_MODELS_JSON, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading custom models json: ${e.message}", e)
            null
        }
    }

    fun saveActiveModelId(context: Context, id: String?) {
        try {
            prefs(context).edit {
                if (id == null) remove(KEY_ACTIVE_MODEL_ID) else putString(KEY_ACTIVE_MODEL_ID, id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving active custom model id: ${e.message}", e)
        }
    }

    fun loadActiveModelId(context: Context): String? {
        return try {
            prefs(context).getString(KEY_ACTIVE_MODEL_ID, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading active custom model id: ${e.message}", e)
            null
        }
    }

    fun saveApiKey(context: Context, modelId: String, key: String) {
        try {
            prefs(context).edit { putString(KEY_API_KEY_PREFIX + modelId, key) }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving api key for custom model '$modelId': ${e.message}", e)
        }
    }

    fun loadApiKey(context: Context, modelId: String): String? {
        return try {
            prefs(context).getString(KEY_API_KEY_PREFIX + modelId, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading api key for custom model '$modelId': ${e.message}", e)
            null
        }
    }
}
