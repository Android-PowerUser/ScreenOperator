package com.google.ai.sample

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Manager class for handling API key storage and retrieval
 */
class ApiKeyManager(context: Context) {
    private val TAG = "ApiKeyManager"
    private val PREFS_NAME = "api_key_prefs"
    private fun getKeysName(provider: ApiProvider) = "api_keys_${provider.name}"
    private fun getCurrentKeyIndexName(provider: ApiProvider) = "current_key_index_${provider.name}"
    private fun getFailedKeysName(provider: ApiProvider) = "failed_keys_${provider.name}"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Get the current API key for a specific provider
     * @param provider The API provider (e.g., GOOGLE, CEREBRAS)
     * @return The current API key or null if none exists for that provider
     */
    fun getCurrentApiKey(provider: ApiProvider): String? {
        val keys = getApiKeys(provider)
        if (keys.isEmpty()) {
            Log.d(TAG, "No API keys found for provider $provider")
            return null
        }

        val currentIndex = prefs.getInt(getCurrentKeyIndexName(provider), 0)
        val safeIndex = if (currentIndex >= keys.size) 0 else currentIndex

        Log.d(TAG, "Getting current API key for $provider at index $safeIndex")
        return keys[safeIndex]
    }

    /**
     * Get all stored API keys for a specific provider
     * @param provider The API provider
     * @return List of API keys for that provider
     */
    fun getApiKeys(provider: ApiProvider): List<String> {
        val keysString = prefs.getString(getKeysName(provider), "") ?: ""
        return if (keysString.isEmpty()) {
            emptyList()
        } else {
            keysString.split(",")
        }
    }

    /**
     * Add a new API key for a specific provider
     * @param apiKey The API key to add
     * @param provider The API provider
     * @return True if the key was added, false if it already exists
     */
    fun addApiKey(apiKey: String, provider: ApiProvider): Boolean {
        if (apiKey.isBlank()) {
            Log.d(TAG, "Attempted to add blank API key for provider $provider")
            return false
        }

        val keys = getApiKeys(provider).toMutableList()

        if (keys.contains(apiKey)) {
            Log.d(TAG, "API key for $provider already exists")
            return false
        }

        keys.add(apiKey)
        saveApiKeys(keys, provider)

        if (keys.size == 1) {
            setCurrentKeyIndex(0, provider)
        }

        removeFailedKey(apiKey, provider)
        Log.d(TAG, "Added new API key for $provider, total keys: ${keys.size}")
        return true
    }

    /**
     * Remove an API key for a specific provider
     * @param apiKey The API key to remove
     * @param provider The API provider
     * @return True if the key was removed, false if it doesn't exist
     */
    fun removeApiKey(apiKey: String, provider: ApiProvider): Boolean {
        val keys = getApiKeys(provider).toMutableList()
        val removed = keys.remove(apiKey)

        if (removed) {
            saveApiKeys(keys, provider)
            val currentIndex = prefs.getInt(getCurrentKeyIndexName(provider), 0)
            if (currentIndex >= keys.size && keys.isNotEmpty()) {
                setCurrentKeyIndex(0, provider)
            }
            removeFailedKey(apiKey, provider)
            Log.d(TAG, "Removed API key for $provider, remaining keys: ${keys.size}")
        } else {
            Log.d(TAG, "API key for $provider not found for removal")
        }
        return removed
    }

    /**
     * Set the current API key index for a specific provider
     * @param index The index to set
     * @param provider The API provider
     * @return True if successful, false if index is invalid
     */
    fun setCurrentKeyIndex(index: Int, provider: ApiProvider): Boolean {
        val keys = getApiKeys(provider)
        if (index < 0 || index >= keys.size) {
            Log.d(TAG, "Invalid API key index for $provider: $index, max: ${keys.size - 1}")
            return false
        }

        prefs.edit().putInt(getCurrentKeyIndexName(provider), index).apply()
        Log.d(TAG, "Set current API key index for $provider to $index")
        return true
    }

    /**
     * Get the current API key index for a specific provider
     * @param provider The API provider
     * @return The current index
     */
    fun getCurrentKeyIndex(provider: ApiProvider): Int {
        return prefs.getInt(getCurrentKeyIndexName(provider), 0)
    }

    /**
     * Mark an API key as failed for a specific provider
     * @param apiKey The API key to mark
     * @param provider The API provider
     */
    fun markKeyAsFailed(apiKey: String, provider: ApiProvider) {
        val failedKeys = getFailedKeys(provider).toMutableList()
        if (!failedKeys.contains(apiKey)) {
            failedKeys.add(apiKey)
            saveFailedKeys(failedKeys, provider)
            Log.d(TAG, "Marked API key for $provider as failed: ${apiKey.take(5)}...")
        }
    }

    /**
     * Remove an API key from the failed list for a specific provider
     * @param apiKey The API key to remove
     * @param provider The API provider
     */
    fun removeFailedKey(apiKey: String, provider: ApiProvider) {
        val failedKeys = getFailedKeys(provider).toMutableList()
        if (failedKeys.remove(apiKey)) {
            saveFailedKeys(failedKeys, provider)
            Log.d(TAG, "Removed API key for $provider from failed keys: ${apiKey.take(5)}...")
        }
    }

    /**
     * Get all failed API keys for a specific provider
     * @param provider The API provider
     * @return List of failed keys
     */
    fun getFailedKeys(provider: ApiProvider): List<String> {
        val keysString = prefs.getString(getFailedKeysName(provider), "") ?: ""
        return if (keysString.isEmpty()) emptyList() else keysString.split(",")
    }

    /**
     * Check if an API key is marked as failed for a specific provider
     * @param apiKey The API key to check
     * @param provider The API provider
     * @return True if failed, false otherwise
     */
    fun isKeyFailed(apiKey: String, provider: ApiProvider): Boolean {
        return getFailedKeys(provider).contains(apiKey)
    }

    /**
     * Reset all failed keys for a specific provider
     * @param provider The API provider
     */
    fun resetFailedKeys(provider: ApiProvider) {
        prefs.edit().remove(getFailedKeysName(provider)).apply()
        Log.d(TAG, "Reset all failed keys for $provider")
    }

    /**
     * Check if all keys for a specific provider are marked as failed
     * @param provider The API provider
     * @return True if all keys are failed, false otherwise
     */
    fun areAllKeysFailed(provider: ApiProvider): Boolean {
        val keys = getApiKeys(provider)
        val failedKeys = getFailedKeys(provider)
        return keys.isNotEmpty() && failedKeys.size >= keys.size
    }

    /**
     * Get the count of available keys for a specific provider
     * @param provider The API provider
     * @return The number of keys
     */
    fun getKeyCount(provider: ApiProvider): Int {
        return getApiKeys(provider).size
    }

    /**
     * Switch to the next available API key for a specific provider
     * @param provider The API provider
     * @return The new API key or null if no valid keys are available
     */
    fun switchToNextAvailableKey(provider: ApiProvider): String? {
        val keys = getApiKeys(provider)
        if (keys.isEmpty()) {
            Log.d(TAG, "No API keys available for $provider to switch to")
            return null
        }

        val failedKeys = getFailedKeys(provider)
        val currentIndex = getCurrentKeyIndex(provider)

        if (failedKeys.size >= keys.size) {
            Log.d(TAG, "All keys for $provider are marked as failed, resetting")
            resetFailedKeys(provider)
            setCurrentKeyIndex(0, provider)
            return keys[0]
        }

        var nextIndex = (currentIndex + 1) % keys.size
        var attempts = 0

        while (attempts < keys.size) {
            if (!failedKeys.contains(keys[nextIndex])) {
                setCurrentKeyIndex(nextIndex, provider)
                Log.d(TAG, "Switched to next available key for $provider at index $nextIndex")
                return keys[nextIndex]
            }
            nextIndex = (nextIndex + 1) % keys.size
            attempts++
        }

        Log.d(TAG, "Could not find a non-failed key for $provider, resetting")
        resetFailedKeys(provider)
        setCurrentKeyIndex(0, provider)
        return keys[0]
    }

    /**
     * Save the list of API keys for a specific provider
     * @param keys The list to save
     * @param provider The API provider
     */
    private fun saveApiKeys(keys: List<String>, provider: ApiProvider) {
        val keysString = keys.joinToString(",")
        prefs.edit().putString(getKeysName(provider), keysString).apply()
    }

    /**
     * Save the list of failed API keys for a specific provider
     * @param keys The list to save
     * @param provider The API provider
     */
    private fun saveFailedKeys(keys: List<String>, provider: ApiProvider) {
        val keysString = keys.joinToString(",")
        prefs.edit().putString(getFailedKeysName(provider), keysString).apply()
    }

    /**
     * Clear all stored API keys for a specific provider
     * @param provider The API provider
     */
    fun clearAllKeys(provider: ApiProvider) {
        prefs.edit()
            .remove(getKeysName(provider))
            .remove(getCurrentKeyIndexName(provider))
            .remove(getFailedKeysName(provider))
            .apply()
        Log.d(TAG, "Cleared all API keys for $provider")
    }
    
    companion object {
        @Volatile
        private var INSTANCE: ApiKeyManager? = null
        
        fun getInstance(context: Context): ApiKeyManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiKeyManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
