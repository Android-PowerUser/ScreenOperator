package com.google.ai.sample.util

import android.util.Log

/**
 * Holds the currently known [CustomModelDefinition]s and which one (if any) is selected.
 *
 * This is intentionally kept completely separate from [com.google.ai.sample.ModelOption] /
 * [com.google.ai.sample.GenerativeAiViewModelFactory]: it does not touch the existing,
 * compiled-in model enum or its dispatch logic at all. A custom model is either active (and
 * then [PhotoReasoningViewModel]'s `reason()` delegates the actual API call to JavaScript) or
 * it isn't, in which case the app behaves exactly as before this feature existed.
 */
object CustomModelRegistry {
    private const val TAG = "CustomModelRegistry"

    @Volatile
    private var models: List<CustomModelDefinition> = emptyList()

    @Volatile
    private var activeModelId: String? = null

    /** Replaces the known custom models from a remote JSON config. Returns how many were installed. */
    @Synchronized
    fun setModels(json: String): Int {
        val parsed = CustomModelConfig.parse(json)
        models = parsed
        // If the previously active model no longer exists in the new config, deactivate it
        // rather than silently keep routing to a stale definition.
        if (activeModelId != null && parsed.none { it.id == activeModelId }) {
            Log.w(TAG, "Previously active custom model '$activeModelId' is no longer in config; deactivating")
            activeModelId = null
        }
        Log.d(TAG, "Installed ${models.size} custom model definition(s)")
        return models.size
    }

    fun getModels(): List<CustomModelDefinition> = models

    fun findById(id: String): CustomModelDefinition? = models.find { it.id == id }

    /** @return true if [id] matches a known custom model and was activated. */
    @Synchronized
    fun setActiveModelId(id: String): Boolean {
        val found = findById(id)
        return if (found != null) {
            activeModelId = id
            true
        } else {
            false
        }
    }

    @Synchronized
    fun clearActiveModel() {
        activeModelId = null
    }

    fun getActiveModel(): CustomModelDefinition? = activeModelId?.let { findById(it) }

    fun getActiveModelId(): String? = activeModelId
}
