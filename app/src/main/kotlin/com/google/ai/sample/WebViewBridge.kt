package com.google.ai.sample

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.ai.sample.feature.multimodal.PhotoReasoningUiState
import com.google.ai.sample.util.GenerationSettingsPreferences
import com.google.ai.sample.util.SystemMessageEntry
import com.google.ai.sample.util.SystemMessageEntryPreferences
import com.google.ai.sample.util.SystemMessagePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class WebViewBridge(private val mainActivity: MainActivity) {

    private val TAG = "WebViewBridge"
    private val context: Context get() = mainActivity.applicationContext

    // ── System Message ────────────────────────────────────────────────────────

    @JavascriptInterface
    fun getSystemMessage(): String {
        val viewModel = mainActivity.getPhotoReasoningViewModel()
        val currentMessage = viewModel?.systemMessage?.value ?: ""
        
        // If system message is empty and ViewModel is not initialized, load from preferences
        if (currentMessage.isEmpty() && (viewModel?.isInitialized?.value == false)) {
            val savedMessage = SystemMessagePreferences.loadSystemMessage(context)
            Log.d(TAG, "getSystemMessage: Loading from preferences because ViewModel not initialized. Length: ${savedMessage.length}")
            return savedMessage
        }
        
        return currentMessage
    }

    @JavascriptInterface
    fun setSystemMessage(message: String) {
        mainActivity.getPhotoReasoningViewModel()?.updateSystemMessage(message, context)
    }

    @JavascriptInterface
    fun restoreSystemMessage() {
        mainActivity.runOnUiThread {
            mainActivity.getPhotoReasoningViewModel()?.restoreSystemMessage(context)
        }
    }

    // ── Model Selection ───────────────────────────────────────────────────────

    @JavascriptInterface
    fun getSelectedModelId(): String {
        com.google.ai.sample.util.CustomModelRegistry.getActiveModelId()?.let { return it }
        return GenerativeAiViewModelFactory.getCurrentModel().name
    }

    @JavascriptInterface
    fun setSelectedModel(id: String) {
        try {
            val model = ModelOption.valueOf(id)
            com.google.ai.sample.util.CustomModelRegistry.clearActiveModel()
            com.google.ai.sample.util.CustomModelPreferences.saveActiveModelId(context, null)
            GenerativeAiViewModelFactory.setModel(model, context)
            mainActivity.runOnUiThread {
                mainActivity.onModelChangedFromWebView()
            }
        } catch (e: IllegalArgumentException) {
            // Not a built-in ModelOption - check whether it's a custom, JSON-defined model
            // (see CustomModelRegistry). This is what lets a brand-new model/provider be
            // selected without it ever having existed as a compiled-in enum constant.
            val activated = com.google.ai.sample.util.CustomModelRegistry.setActiveModelId(id)
            if (activated) {
                com.google.ai.sample.util.CustomModelPreferences.saveActiveModelId(context, id)
                mainActivity.runOnUiThread {
                    mainActivity.getPhotoReasoningViewModel()?.closeOfflineModel()
                }
            } else {
                Log.w(TAG, "setSelectedModel: unknown model id '$id' (not a ModelOption nor a known custom model)")
            }
        }
    }

    // ── API Keys ──────────────────────────────────────────────────────────────

    @JavascriptInterface
    fun getAllApiKeys(providerName: String): String {
        return try {
            val provider = ApiProvider.valueOf(providerName)
            val keys = mainActivity.apiKeyManager.getApiKeys(provider)
            JSONArray(keys).toString()
        } catch (e: Exception) {
            Log.w(TAG, "getAllApiKeys error: ${e.message}")
            "[]"
        }
    }

    @JavascriptInterface
    fun addApiKey(key: String, providerName: String) {
        try {
            val provider = ApiProvider.valueOf(providerName)
            mainActivity.apiKeyManager.addApiKey(key, provider)
        } catch (e: Exception) {
            Log.e(TAG, "addApiKey error: ${e.message}")
        }
    }

    @JavascriptInterface
    fun removeApiKey(key: String, providerName: String) {
        try {
            val provider = ApiProvider.valueOf(providerName)
            mainActivity.apiKeyManager.removeApiKey(key, provider)
        } catch (e: Exception) {
            Log.e(TAG, "removeApiKey error: ${e.message}")
        }
    }

    @JavascriptInterface
    fun getCurrentKeyIndex(providerName: String): Int {
        return try {
            val provider = ApiProvider.valueOf(providerName)
            mainActivity.apiKeyManager.getCurrentKeyIndex(provider)
        } catch (e: Exception) {
            0
        }
    }

    @JavascriptInterface
    fun setCurrentKeyIndex(index: Int, providerName: String) {
        try {
            val provider = ApiProvider.valueOf(providerName)
            mainActivity.apiKeyManager.setCurrentKeyIndex(index, provider)
        } catch (e: Exception) {
            Log.e(TAG, "setCurrentKeyIndex error: ${e.message}")
        }
    }

    // ── Database Entries ──────────────────────────────────────────────────────

    @JavascriptInterface
    fun getDatabaseEntries(): String {
        val entries = SystemMessageEntryPreferences.loadEntries(context)
        val arr = JSONArray()
        entries.forEach { 
            arr.put(JSONObject().put("title", it.title).put("guide", it.guide))
        }
        return arr.toString()
    }

    @JavascriptInterface
    fun addDatabaseEntry(title: String, guide: String) {
        SystemMessageEntryPreferences.addEntry(context, SystemMessageEntry(title, guide))
    }

    @JavascriptInterface
    fun updateDatabaseEntry(oldTitle: String, newTitle: String, guide: String) {
        val oldEntry = SystemMessageEntryPreferences.loadEntries(context).find { it.title == oldTitle }
        if (oldEntry != null) {
            SystemMessageEntryPreferences.updateEntry(context, oldEntry, SystemMessageEntry(newTitle, guide))
        }
    }

    @JavascriptInterface
    fun deleteDatabaseEntry(title: String) {
        val entry = SystemMessageEntryPreferences.loadEntries(context).find { it.title == title }
        if (entry != null) {
            SystemMessageEntryPreferences.deleteEntry(context, entry)
        }
    }

    // ── Generation Settings ───────────────────────────────────────────────────

    @JavascriptInterface
    fun getGenerationSettings(modelId: String): String {
        return try {
            val model = ModelOption.valueOf(modelId)
            val s = GenerationSettingsPreferences.loadSettings(context, model.modelName)
            JSONObject()
                .put("temperature", s.temperature)
                .put("topP", s.topP)
                .put("topK", s.topK)
                .toString()
        } catch (e: Exception) {
            Log.w(TAG, "getGenerationSettings error for '$modelId': ${e.message}")
            "{}"
        }
    }

    @JavascriptInterface
    fun saveGenerationSettings(modelId: String, temperature: Float, topP: Float, topK: Int) {
        try {
            val model = ModelOption.valueOf(modelId)
            GenerationSettingsPreferences.saveSettings(
                context,
                model.modelName,
                GenerationSettingsPreferences.GenerationSettings(temperature, topP, topK)
            )
        } catch (e: Exception) {
            Log.e(TAG, "saveGenerationSettings error: ${e.message}")
        }
    }

    // ── Custom Models (entirely JSON-defined, JS-driven - see CustomModelRegistry) ──────────
    // A "custom model" never existed as a compiled ModelOption. Its API call is made by JS
    // itself (fetch()), not by native networking code, so adding one - even for a brand-new
    // provider - needs only a custom-models.json commit, no app release.

    @JavascriptInterface
    fun setCustomModelOverrides(json: String): Int {
        return try {
            val installed = com.google.ai.sample.util.CustomModelRegistry.setModels(json)
            com.google.ai.sample.util.CustomModelPreferences.saveModelsJson(context, json)
            installed
        } catch (e: Exception) {
            Log.e(TAG, "setCustomModelOverrides error: ${e.message}")
            0
        }
    }

    @JavascriptInterface
    fun getCustomModelOverrides(): String {
        return com.google.ai.sample.util.CustomModelPreferences.loadModelsJson(context) ?: "[]"
    }

    @JavascriptInterface
    fun setCustomModelApiKey(modelId: String, key: String) {
        try {
            com.google.ai.sample.util.CustomModelPreferences.saveApiKey(context, modelId, key)
        } catch (e: Exception) {
            Log.e(TAG, "setCustomModelApiKey error: ${e.message}")
        }
    }

    @JavascriptInterface
    fun getCustomModelApiKey(modelId: String): String {
        return com.google.ai.sample.util.CustomModelPreferences.loadApiKey(context, modelId) ?: ""
    }

    // ── Chat Operations ───────────────────────────────────────────────────────

    @JavascriptInterface
    fun sendMessage(text: String) {
        mainActivity.runOnUiThread {
            mainActivity.sendMessageFromWebView(text, emptyList())
        }
    }

    @JavascriptInterface
    fun sendMessageWithImages(text: String, urisCsv: String) {
        val uris = urisCsv.split(",").filter { it.isNotBlank() }.map { android.net.Uri.parse(it) }
        mainActivity.runOnUiThread {
            mainActivity.sendMessageFromWebView(text, uris)
        }
    }

    @JavascriptInterface
    fun pickImage() {
        mainActivity.runOnUiThread {
            mainActivity.openImagePicker()
        }
    }

    @JavascriptInterface
    fun clearChatHistory() {
        mainActivity.runOnUiThread {
            mainActivity.getPhotoReasoningViewModel()?.clearChatHistory(context)
        }
    }

    @JavascriptInterface
    fun stopGeneration() {
        mainActivity.runOnUiThread {
            mainActivity.getPhotoReasoningViewModel()?.onStopClicked()
        }
    }

    // ── Custom Model Responses ───────────────────────────────────────────────
    // Called by JS after it performed the actual fetch() to a custom model's endpoint. The
    // text is fed into the EXISTING, unmodified command-parsing/execution/persistence
    // pipeline (PhotoReasoningCommandProcessing, AccessibilityCommandQueue, chat history) -
    // only the network transport differs from a built-in ModelOption.

    @JavascriptInterface
    fun onCustomModelPartialResponse(text: String) {
        mainActivity.runOnUiThread {
            mainActivity.getPhotoReasoningViewModel()?.onCustomModelPartialResponse(text)
        }
    }

    @JavascriptInterface
    fun onCustomModelFinalResponse(text: String) {
        mainActivity.runOnUiThread {
            mainActivity.getPhotoReasoningViewModel()?.onCustomModelFinalResponse(text)
        }
    }

    @JavascriptInterface
    fun onCustomModelError(message: String) {
        mainActivity.runOnUiThread {
            mainActivity.getPhotoReasoningViewModel()?.onCustomModelError(message)
        }
    }

    @JavascriptInterface
    fun isGenerationRunning(): Boolean {
        return mainActivity.getPhotoReasoningViewModel()?.isGenerationRunningFlow?.value ?: false
    }

    @JavascriptInterface
    fun isOfflineModelLoaded(): Boolean {
        return mainActivity.getPhotoReasoningViewModel()?.isOfflineGpuModelLoadedFlow?.value ?: false
    }

    // ── Backend Preference ────────────────────────────────────────────────────

    @JavascriptInterface
    fun getBackendPreference(): String {
        return GenerativeAiViewModelFactory.getBackend().name
    }

    @JavascriptInterface
    fun setBackendPreference(backend: String) {
        try {
            val b = InferenceBackend.valueOf(backend)
            GenerativeAiViewModelFactory.setBackend(b, context)
        } catch (e: Exception) {
            Log.e(TAG, "setBackendPreference error: ${e.message}")
        }
    }

    // ── Billing / Donation ────────────────────────────────────────────────────

    @JavascriptInterface
    fun initiateDonation() {
        mainActivity.runOnUiThread {
            mainActivity.initiateDonationFromWebView()
        }
    }

    @JavascriptInterface
    fun isPurchased(): Boolean {
        return TrialManager.isPurchased(context)
    }

    // ── Termux ────────────────────────────────────────────────────────────────

    @JavascriptInterface
    fun setTermuxBackground(background: Boolean) {
        mainActivity.runOnUiThread {
            mainActivity.setTermuxBackgroundFromWebView(background)
        }
    }

    @JavascriptInterface
    fun getTermuxBackground(): Boolean {
        return com.google.ai.sample.util.TermuxExecutionModePreferences.executeInBackground(context)
    }

    // ── Command Pattern Overrides (remote-updatable command syntax) ────────────
    // Lets the WebView bundle teach the native command parser new/alternate ways to spell
    // an *existing* action (see CommandPatternConfig for the safety boundary). This is what
    // makes "a new model emits slightly different command syntax" fixable via a repo commit
    // instead of an app release.

    @JavascriptInterface
    fun setCommandPatternOverrides(json: String): Int {
        return try {
            val applied = com.google.ai.sample.util.CommandParser.setRemotePatternOverrides(json)
            com.google.ai.sample.util.CommandPatternOverridesPreferences.save(context, json)
            applied
        } catch (e: Exception) {
            Log.e(TAG, "setCommandPatternOverrides error: ${e.message}")
            0
        }
    }

    @JavascriptInterface
    fun getCommandPatternOverrides(): String {
        return com.google.ai.sample.util.CommandPatternOverridesPreferences.load(context) ?: "[]"
    }

    // ── Custom Models (fully JSON-defined, JS-driven models - no native code needed) ──────────
    // Lets a genuinely new model/provider be added purely via custom-models.json: the actual
    // network call happens in window.onCustomModelRequest() in the WebView (fetch()), not in
    // native code. See CustomModelRegistry for the in-memory state and reasonWithCustomJsModel
    // in PhotoReasoningViewModel for how a turn is delegated to JS.

    @JavascriptInterface
    fun setCustomModelOverrides(json: String): Int {
        return try {
            val installed = com.google.ai.sample.util.CustomModelRegistry.setModels(json)
            com.google.ai.sample.util.CustomModelPreferences.saveModelsJson(context, json)
            installed
        } catch (e: Exception) {
            Log.e(TAG, "setCustomModelOverrides error: ${e.message}")
            0
        }
    }

    @JavascriptInterface
    fun getCustomModelOverrides(): String {
        return com.google.ai.sample.util.CustomModelPreferences.loadModelsJson(context) ?: "[]"
    }

    @JavascriptInterface
    fun setCustomModelApiKey(modelId: String, key: String) {
        try {
            com.google.ai.sample.util.CustomModelPreferences.saveApiKey(context, modelId, key)
        } catch (e: Exception) {
            Log.e(TAG, "setCustomModelApiKey error: ${e.message}")
        }
    }

    @JavascriptInterface
    fun getCustomModelApiKey(modelId: String): String {
        return com.google.ai.sample.util.CustomModelPreferences.loadApiKey(context, modelId) ?: ""
    }

    @JavascriptInterface
    fun onCustomModelPartialResponse(text: String) {
        mainActivity.runOnUiThread {
            mainActivity.customModelPartialResponseFromWebView(text)
        }
    }

    @JavascriptInterface
    fun onCustomModelFinalResponse(text: String) {
        mainActivity.runOnUiThread {
            mainActivity.customModelFinalResponseFromWebView(text)
        }
    }

    @JavascriptInterface
    fun onCustomModelError(message: String) {
        mainActivity.runOnUiThread {
            mainActivity.customModelErrorFromWebView(message)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    companion object {
        fun jsEscape(s: String): String =
            s.replace("\\", "\\\\")
             .replace("'", "\\'")
             .replace("\n", "\\n")
             .replace("\r", "\\r")
             .replace("<", "\\u003C")
    }
}
