package com.google.ai.sample

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.ai.sample.feature.multimodal.PhotoReasoningUiState
import com.google.ai.sample.util.GenerationSettingsPreferences
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
        val vm = mainActivity.getPhotoReasoningViewModel()
        if (vm != null) return vm.systemMessage.value
        return context.getSharedPreferences(PREFS_WEBVIEW, Context.MODE_PRIVATE)
            .getString(KEY_SYS_MSG, "") ?: ""
    }

    @JavascriptInterface
    fun setSystemMessage(message: String) {
        context.getSharedPreferences(PREFS_WEBVIEW, Context.MODE_PRIVATE)
            .edit().putString(KEY_SYS_MSG, message).apply()
        mainActivity.getPhotoReasoningViewModel()?.updateSystemMessage(message, context)
    }

    // ── Model Selection ───────────────────────────────────────────────────────

    @JavascriptInterface
    fun getSelectedModelId(): String {
        return GenerativeAiViewModelFactory.getCurrentModel().name
    }

    @JavascriptInterface
    fun setSelectedModel(id: String) {
        try {
            val model = ModelOption.valueOf(id)
            GenerativeAiViewModelFactory.setModel(model, context)
            mainActivity.runOnUiThread {
                mainActivity.onModelChangedFromWebView()
            }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "setSelectedModel: unknown model id '$id'")
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
        val prefs = context.getSharedPreferences(PREFS_WEBVIEW_DB, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DB_ENTRIES, "[]") ?: "[]"
    }

    @JavascriptInterface
    fun addDatabaseEntry(title: String, guide: String) {
        val prefs = context.getSharedPreferences(PREFS_WEBVIEW_DB, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_DB_ENTRIES, "[]") ?: "[]")
        arr.put(JSONObject().put("title", title).put("guide", guide))
        prefs.edit().putString(KEY_DB_ENTRIES, arr.toString()).apply()
    }

    @JavascriptInterface
    fun updateDatabaseEntry(oldTitle: String, newTitle: String, guide: String) {
        val prefs = context.getSharedPreferences(PREFS_WEBVIEW_DB, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_DB_ENTRIES, "[]") ?: "[]")
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("title") == oldTitle) {
                newArr.put(JSONObject().put("title", newTitle).put("guide", guide))
            } else {
                newArr.put(obj)
            }
        }
        prefs.edit().putString(KEY_DB_ENTRIES, newArr.toString()).apply()
    }

    @JavascriptInterface
    fun deleteDatabaseEntry(title: String) {
        val prefs = context.getSharedPreferences(PREFS_WEBVIEW_DB, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_DB_ENTRIES, "[]") ?: "[]")
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("title") != title) newArr.put(obj)
        }
        prefs.edit().putString(KEY_DB_ENTRIES, newArr.toString()).apply()
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

    // ── Chat Operations ───────────────────────────────────────────────────────

    @JavascriptInterface
    fun sendMessage(text: String) {
        mainActivity.runOnUiThread {
            mainActivity.sendMessageFromWebView(text)
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

    @JavascriptInterface
    fun isGenerationRunning(): Boolean {
        return mainActivity.getPhotoReasoningViewModel()?.isGenerationRunningFlow?.value ?: false
    }

    @JavascriptInterface
    fun isOfflineModelLoaded(): Boolean {
        return mainActivity.getPhotoReasoningViewModel()?.isOfflineGpuModelLoadedFlow?.value ?: false
    }

    // ── Custom Models (no-op – section removed from HTML) ─────────────────────

    @JavascriptInterface
    fun addCustomModel(json: String) {
        Log.d(TAG, "addCustomModel called (no-op, section removed from UI): $json")
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    companion object {
        private const val PREFS_WEBVIEW = "webview_prefs"
        private const val PREFS_WEBVIEW_DB = "webview_db"
        private const val KEY_SYS_MSG = "sysMsg"
        private const val KEY_DB_ENTRIES = "entries"

        fun jsEscape(s: String): String =
            s.replace("\\", "\\\\")
             .replace("'", "\\'")
             .replace("\n", "\\n")
             .replace("\r", "\\r")
             .replace("<", "\\u003C")
    }
}
