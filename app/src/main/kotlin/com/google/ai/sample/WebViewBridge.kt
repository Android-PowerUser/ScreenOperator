package com.google.ai.sample

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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

    // ââ System Message ââââââââââââââââââââââââââââââââââââââââââââââââââââââââ

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

    // ââ Model Selection âââââââââââââââââââââââââââââââââââââââââââââââââââââââ

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

    // ââ API Keys ââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââ

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

    // ââ Database Entries ââââââââââââââââââââââââââââââââââââââââââââââââââââââ

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

    // ââ Generation Settings âââââââââââââââââââââââââââââââââââââââââââââââââââ

    @JavascriptInterface
    fun getGenerationSettings(modelId: String): String {
        return try {
            // Resolve to the persistence key the same way regardless of whether this is a
            // built-in ModelOption or a custom (JSON-defined) model: GenerationSettingsPreferences
            // itself is already keyed by an arbitrary string, not by the ModelOption enum, so no
            // new storage mechanism is needed here - only this id-resolution step.
            val settingsKey = try {
                ModelOption.valueOf(modelId).modelName
            } catch (e: IllegalArgumentException) {
                com.google.ai.sample.util.CustomModelRegistry.findById(modelId)?.id
                    ?: throw e
            }
            val s = GenerationSettingsPreferences.loadSettings(context, settingsKey)
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
            val settingsKey = try {
                ModelOption.valueOf(modelId).modelName
            } catch (e: IllegalArgumentException) {
                com.google.ai.sample.util.CustomModelRegistry.findById(modelId)?.id
                    ?: throw e
            }
            GenerationSettingsPreferences.saveSettings(
                context,
                settingsKey,
                GenerationSettingsPreferences.GenerationSettings(temperature, topP, topK)
            )
        } catch (e: Exception) {
            Log.e(TAG, "saveGenerationSettings error: ${e.message}")
        }
    }

    // ââ Custom Models (entirely JSON-defined, JS-driven - see CustomModelRegistry) ââââââââââ
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

    // ââ Chat Operations âââââââââââââââââââââââââââââââââââââââââââââââââââââââ

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

    // ââ Custom Model Responses âââââââââââââââââââââââââââââââââââââââââââââââ
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

    // ââ Backend Preference ââââââââââââââââââââââââââââââââââââââââââââââââââââ

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

    // ââ Billing / Donation ââââââââââââââââââââââââââââââââââââââââââââââââââââ

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

    // ââ Termux ââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââ

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

    // ââ Command Pattern Overrides (remote-updatable command syntax) ââââââââââââ
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

    // ââ Model Identifier Overrides (remote-updatable wire-level model names) âââ
    // Lets the WebView bundle correct the API-side model identifier string for an *existing*
    // built-in ModelOption (see ModelIdentifierOverrides for the safety boundary). This is
    // what makes "a Gemini preview model got renamed/retired" fixable via a repo commit
    // instead of an app release.

    @JavascriptInterface
    fun setModelIdentifierOverrides(json: String): Int {
        return try {
            val applied = com.google.ai.sample.util.ModelIdentifierOverrides.setRemoteOverrides(json)
            com.google.ai.sample.util.ModelIdentifierOverridePreferences.save(context, json)
            applied
        } catch (e: Exception) {
            Log.e(TAG, "setModelIdentifierOverrides error: ${e.message}")
            0
        }
    }

    @JavascriptInterface
    fun getModelIdentifierOverrides(): String {
        return com.google.ai.sample.util.ModelIdentifierOverridePreferences.load(context) ?: "[]"
    }

    // ââ Offline Model Overrides (remote-updatable download URL/size/extra files) â
    // Lets the WebView bundle correct the download metadata for an *existing* built-in
    // offline ModelOption (see OfflineModelOverrides for the safety boundary). This is what
    // makes "a Hugging Face download link moved" fixable via a repo commit instead of an app
    // release.

    @JavascriptInterface
    fun setOfflineModelOverrides(json: String): Int {
        return try {
            val applied = com.google.ai.sample.util.OfflineModelOverrides.setRemoteOverrides(json)
            com.google.ai.sample.util.OfflineModelOverridePreferences.save(context, json)
            applied
        } catch (e: Exception) {
            Log.e(TAG, "setOfflineModelOverrides error: ${e.message}")
            0
        }
    }

    @JavascriptInterface
    fun getOfflineModelOverrides(): String {
        return com.google.ai.sample.util.OfflineModelOverridePreferences.load(context) ?: "[]"
    }

    // ââ Custom Action Types (remote-updatable, entirely new action kinds) ââââââââââââââââââ
    // Lets the WebView bundle define completely new action types (regex + id) without a native
    // app release. When the command parser matches one of these, it emits a
    // Command.WebViewCustomAction and the native side calls window.onCustomAction(id, groups[])
    // so the JS handler can invoke any existing Android.* bridge method to carry out the action.

    @JavascriptInterface
    fun setCustomActionTypes(json: String): Int {
        return try {
            val installed = com.google.ai.sample.util.CommandParser.setCustomActionTypes(json)
            com.google.ai.sample.util.CustomActionTypePreferences.save(context, json)
            installed
        } catch (e: Exception) {
            Log.e(TAG, "setCustomActionTypes error: ${e.message}")
            0
        }
    }

    @JavascriptInterface
    fun getCustomActionTypes(): String {
        return com.google.ai.sample.util.CustomActionTypePreferences.load(context) ?: "[]"
    }

    // ââ Execution Policy Overrides (remote-updatable per-message command limit) âââââââââââ
    // Lets the WebView bundle cap how many commands from a single AI response are executed
    // (and customize the feedback text sent back together with the next screenshot/screen-
    // elements message when commands were dropped because too many were sent at once)
    // without a native app release. See ExecutionPolicyConfig for the safety boundary
    // (missing/invalid config => unlimited, i.e. unchanged behavior).

    @JavascriptInterface
    fun setExecutionPolicyOverrides(json: String): Int {
        return try {
            val applied = com.google.ai.sample.util.ExecutionPolicyConfig.setRemoteOverride(json)
            com.google.ai.sample.util.ExecutionPolicyOverridesPreferences.save(context, json)
            applied
        } catch (e: Exception) {
            Log.e(TAG, "setExecutionPolicyOverrides error: ${e.message}")
            0
        }
    }

    @JavascriptInterface
    fun getExecutionPolicyOverrides(): String {
        return com.google.ai.sample.util.ExecutionPolicyOverridesPreferences.load(context) ?: "{}"
    }

    // ââ App Mapping Overrides (remote-updatable openApp() name/package resolution) ââââââââ
    // Lets the WebView bundle teach openApp("...") about new apps, aliases, or a retuned
    // fuzzy-match threshold without a native app release. See AppMappingOverridesConfig.

    @JavascriptInterface
    fun setAppMappingOverrides(json: String): Int {
        return try {
            val applied = com.google.ai.sample.util.AppMappingOverridesConfig.setRemoteOverride(json)
            com.google.ai.sample.util.AppMappingOverridesPreferences.save(context, json)
            applied
        } catch (e: Exception) {
            Log.e(TAG, "setAppMappingOverrides error: ${e.message}")
            0
        }
    }

    @JavascriptInterface
    fun getAppMappingOverrides(): String {
        return com.google.ai.sample.util.AppMappingOverridesPreferences.load(context) ?: "{}"
    }

    // ââ Error Classification Overrides (remote-updatable AI-provider error matching) ââââââ
    // Lets the WebView bundle update the substrings used to detect a quota/rate-limit error
    // (triggers API key switching + retry) vs. a high-demand/overloaded error (does not switch
    // keys) - without a native app release, in case the AI provider changes its error wording.

    @JavascriptInterface
    fun setErrorClassificationOverrides(json: String): Int {
        return try {
            val applied = com.google.ai.sample.util.ErrorClassificationConfig.setRemoteOverride(json)
            com.google.ai.sample.util.ErrorClassificationOverridesPreferences.save(context, json)
            applied
        } catch (e: Exception) {
            Log.e(TAG, "setErrorClassificationOverrides error: ${e.message}")
            0
        }
    }

    @JavascriptInterface
    fun getErrorClassificationOverrides(): String {
        return com.google.ai.sample.util.ErrorClassificationOverridesPreferences.load(context) ?: "{}"
    }

    // ââ Trial/Donation UI Overrides (remote-updatable dialog text, not the gating logic) ââ
    // Lets the WebView bundle change the wording of the first-launch info dialog, the trial-
    // expired dialog, and the payment-method dialog, without a native app release. Does NOT
    // touch TrialManager's trial-length/entitlement logic - see TrialUiConfig's doc comment.

    @JavascriptInterface
    fun setTrialUiOverrides(json: String): Int {
        return try {
            val applied = com.google.ai.sample.util.TrialUiConfig.setRemoteOverride(json)
            com.google.ai.sample.util.TrialUiOverridesPreferences.save(context, json)
            applied
        } catch (e: Exception) {
            Log.e(TAG, "setTrialUiOverrides error: ${e.message}")
            0
        }
    }

    @JavascriptInterface
    fun getTrialUiOverrides(): String {
        return com.google.ai.sample.util.TrialUiOverridesPreferences.load(context) ?: "{}"
    }

    // ââ Operational Tuning Overrides (remote-updatable retry/cooldown timing) ââââââââââââââ
    // Lets the WebView bundle retune Mistral request cooldowns, model-download retry timing,
    // and the Termux "process completed" marker without a native app release.

    @JavascriptInterface
    fun setOperationalTuningOverrides(json: String): Int {
        return try {
            val applied = com.google.ai.sample.util.OperationalTuningConfig.setRemoteOverride(json)
            com.google.ai.sample.util.OperationalTuningOverridesPreferences.save(context, json)
            applied
        } catch (e: Exception) {
            Log.e(TAG, "setOperationalTuningOverrides error: ${e.message}")
            0
        }
    }

    @JavascriptInterface
    fun getOperationalTuningOverrides(): String {
        return com.google.ai.sample.util.OperationalTuningOverridesPreferences.load(context) ?: "{}"
    }

    // ââ Trial Duration Override (remote-updatable trial length only) ââââââââââââââââââââââ
    // See TrialDurationOverrideConfig's doc comment and docs/trial-duration-overrides.md for
    // exactly what this does and does not affect, and the explicit confirmation this required.

    @JavascriptInterface
    fun setTrialDurationOverride(json: String): Int {
        return try {
            val applied = com.google.ai.sample.util.TrialDurationOverrideConfig.setRemoteOverride(json)
            com.google.ai.sample.util.TrialDurationOverridePreferences.save(context, json)
            applied
        } catch (e: Exception) {
            Log.e(TAG, "setTrialDurationOverride error: ${e.message}")
            0
        }
    }

    @JavascriptInterface
    fun getTrialDurationOverride(): String {
        return com.google.ai.sample.util.TrialDurationOverridePreferences.load(context) ?: "{}"
    }

    // ââ Generation Defaults Overrides (remote-updatable factory defaults, not user settings) â
    // Lets the WebView bundle ship a better out-of-the-box temperature/topP/topK default for
    // models the user hasn't customized yet, without a native app release. A user's own saved
    // per-model settings (via saveGenerationSettings) always take precedence over this.

    @JavascriptInterface
    fun setGenerationDefaultsOverrides(json: String): Int {
        return try {
            val applied = com.google.ai.sample.util.GenerationDefaultsConfig.setRemoteOverride(json)
            com.google.ai.sample.util.GenerationDefaultsOverridesPreferences.save(context, json)
            applied
        } catch (e: Exception) {
            Log.e(TAG, "setGenerationDefaultsOverrides error: ${e.message}")
            0
        }
    }

    @JavascriptInterface
    fun getGenerationDefaultsOverrides(): String {
        return com.google.ai.sample.util.GenerationDefaultsOverridesPreferences.load(context) ?: "{}"
    }

    // ââ UI String Overrides (remote-updatable native Compose-screen text) ââââââââââââââââââ
    // Lets the WebView bundle override individual native (non-WebView) UI strings - toasts,
    // dialog labels, button text - by stable ID, without a native app release. Defaults always
    // live in the Kotlin call sites themselves (UiStringsConfig.get(id, default)); this can
    // only replace, never remove, that fallback.

    @JavascriptInterface
    fun setUiStringsOverrides(json: String): Int {
        return try {
            val applied = com.google.ai.sample.util.UiStringsConfig.setRemoteOverride(json)
            com.google.ai.sample.util.UiStringsOverridesPreferences.save(context, json)
            applied
        } catch (e: Exception) {
            Log.e(TAG, "setUiStringsOverrides error: ${e.message}")
            0
        }
    }

    @JavascriptInterface
    fun getUiStringsOverrides(): String {
        return com.google.ai.sample.util.UiStringsOverridesPreferences.load(context) ?: "{}"
    }

    // ââ Toast ââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââ
    // Generic bridge method to show an Android Toast from JavaScript. Exists so a
    // custom-action-types.json entry (e.g. an AI-emitted toast("message") command) can show
    // the user a message without any native code change - see docs/ai-toast-command.md for a
    // ready-to-use example wiring this up as an AI command.

    @JavascriptInterface
    fun showToast(message: String, isLong: Boolean) {
        // Defensive: never let a long/empty/malformed message from a remote JSON-driven action
        // type crash the UI thread or spam an unreadable wall of text.
        val safeMessage = message.take(500).ifBlank { return }
        mainActivity.runOnUiThread {
            android.widget.Toast.makeText(
                context,
                safeMessage,
                if (isLong) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ââ Device Control (every native gesture/navigation capability, exposed to JS) âââââââââ
    // Previously, a window.onCustomAction handler (custom-action-types.json) could only
    // *display* something via showToast - it had no way to actually trigger a click, scroll,
    // app launch, or any other accessibility-service action, even though those capabilities
    // already existed natively for AI-emitted commands. Each method below constructs the same
    // com.google.ai.sample.util.Command the AI's own command text would produce and hands it to
    // ScreenOperatorAccessibilityService.executeCommand(), so it goes through the exact same
    // execution path (queueing, geometry resolution, safety checks, async handling) as a
    // command the AI wrote itself - no logic is duplicated or reimplemented here.

    @JavascriptInterface
    fun tapByText(buttonText: String) {
        ScreenOperatorAccessibilityService.executeCommand(
            com.google.ai.sample.util.Command.ClickButton(buttonText)
        )
    }

    @JavascriptInterface
    fun longTapByText(buttonText: String) {
        ScreenOperatorAccessibilityService.executeCommand(
            com.google.ai.sample.util.Command.LongClickButton(buttonText)
        )
    }

    @JavascriptInterface
    fun tapAtCoordinates(x: String, y: String) {
        ScreenOperatorAccessibilityService.executeCommand(
            com.google.ai.sample.util.Command.TapCoordinates(x, y)
        )
    }

    @JavascriptInterface
    fun pressHome() {
        ScreenOperatorAccessibilityService.executeCommand(com.google.ai.sample.util.Command.PressHomeButton)
    }

    @JavascriptInterface
    fun pressBack() {
        ScreenOperatorAccessibilityService.executeCommand(com.google.ai.sample.util.Command.PressBackButton)
    }

    @JavascriptInterface
    fun showRecentApps() {
        ScreenOperatorAccessibilityService.executeCommand(com.google.ai.sample.util.Command.ShowRecentApps)
    }

    @JavascriptInterface
    fun pressEnterKey() {
        ScreenOperatorAccessibilityService.executeCommand(com.google.ai.sample.util.Command.PressEnterKey)
    }

    @JavascriptInterface
    fun writeText(text: String) {
        ScreenOperatorAccessibilityService.executeCommand(
            com.google.ai.sample.util.Command.WriteText(text)
        )
    }

    @JavascriptInterface
    fun scrollDown() {
        ScreenOperatorAccessibilityService.executeCommand(com.google.ai.sample.util.Command.ScrollDown)
    }

    @JavascriptInterface
    fun scrollUp() {
        ScreenOperatorAccessibilityService.executeCommand(com.google.ai.sample.util.Command.ScrollUp)
    }

    @JavascriptInterface
    fun scrollLeft() {
        ScreenOperatorAccessibilityService.executeCommand(com.google.ai.sample.util.Command.ScrollLeft)
    }

    @JavascriptInterface
    fun scrollRight() {
        ScreenOperatorAccessibilityService.executeCommand(com.google.ai.sample.util.Command.ScrollRight)
    }

    @JavascriptInterface
    fun scrollDownFromCoordinates(x: String, y: String, distance: String, durationMs: Long) {
        ScreenOperatorAccessibilityService.executeCommand(
            com.google.ai.sample.util.Command.ScrollDownFromCoordinates(x, y, distance, durationMs)
        )
    }

    @JavascriptInterface
    fun scrollUpFromCoordinates(x: String, y: String, distance: String, durationMs: Long) {
        ScreenOperatorAccessibilityService.executeCommand(
            com.google.ai.sample.util.Command.ScrollUpFromCoordinates(x, y, distance, durationMs)
        )
    }

    @JavascriptInterface
    fun scrollLeftFromCoordinates(x: String, y: String, distance: String, durationMs: Long) {
        ScreenOperatorAccessibilityService.executeCommand(
            com.google.ai.sample.util.Command.ScrollLeftFromCoordinates(x, y, distance, durationMs)
        )
    }

    @JavascriptInterface
    fun scrollRightFromCoordinates(x: String, y: String, distance: String, durationMs: Long) {
        ScreenOperatorAccessibilityService.executeCommand(
            com.google.ai.sample.util.Command.ScrollRightFromCoordinates(x, y, distance, durationMs)
        )
    }

    @JavascriptInterface
    fun openAppByNameOrPackage(appNameOrPackage: String) {
        ScreenOperatorAccessibilityService.executeCommand(
            com.google.ai.sample.util.Command.OpenApp(appNameOrPackage)
        )
    }

    @JavascriptInterface
    fun runTermuxCommand(command: String) {
        ScreenOperatorAccessibilityService.executeCommand(
            com.google.ai.sample.util.Command.TermuxCommand(command)
        )
    }

    @JavascriptInterface
    fun waitSeconds(seconds: Long) {
        ScreenOperatorAccessibilityService.executeCommand(
            com.google.ai.sample.util.Command.Wait(seconds)
        )
    }

    @JavascriptInterface
    fun requestScreenshot() {
        ScreenOperatorAccessibilityService.executeCommand(com.google.ai.sample.util.Command.TakeScreenshot)
    }

    @JavascriptInterface
    fun markCompleted() {
        ScreenOperatorAccessibilityService.executeCommand(com.google.ai.sample.util.Command.Completed)
    }

    @JavascriptInterface
    fun pinchGesture(centerX: String, centerY: String, startDistance: String, endDistance: String, durationMs: Long) {
        ScreenOperatorAccessibilityService.executeCommand(
            com.google.ai.sample.util.Command.PinchGesture(centerX, centerY, startDistance, endDistance, durationMs)
        )
    }

    // ââ Clipboard (no extra Android permission required) âââââââââââââââââââââââ
    // Clipboard read/write is granted to every app by default, so - like the gesture/navigation
    // methods above - the write path is routed through the same Command/executeCommand pipeline
    // (so an AI-emitted copyToClipboard("...") text command and a custom-action-types.json
    // entry both go through identical logic). The read path returns a value synchronously, so
    // it talks to ClipboardManager directly rather than via the queued accessibility-command
    // pipeline, which has no return channel back to JS.

    @JavascriptInterface
    fun copyToClipboard(text: String) {
        ScreenOperatorAccessibilityService.executeCommand(
            com.google.ai.sample.util.Command.CopyToClipboard(text)
        )
    }

    @JavascriptInterface
    fun getClipboardText(): String {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = clipboard.primaryClip
            clip?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "getClipboardText error: ${e.message}")
            ""
        }
    }

    // ââ Accessibility Service Status ââââââââââââââââââââââââââââââââââââââââââ

    @JavascriptInterface
    fun isAccessibilityServiceEnabled(): Boolean {
        return AccessibilityServiceStatusResolver.isServiceEnabled(context.contentResolver, context.packageName)
    }

    @JavascriptInterface
    fun openAccessibilitySettings() {
        val intent = (context as? MainActivity)?.getAccessibilitySettingsIntent() ?: return
        (context as? MainActivity)?.runOnUiThread {
            context.startActivity(intent)
        }
    }

    /**
     * Generic intent launcher — callable from WebView JS via
     *   Android.launchIntent(action, extrasJson, data)
     * or via dispatch("launchIntent", {action:..., extrasJson:..., data:...}).
     *
     * Lets the WebView control which Android screen to open without a new build.
     * Always runs on the UI thread.
     *
     * @param action      Android intent action, e.g. "android.settings.ACCESSIBILITY_SETTINGS"
     * @param extrasJson  JSON object of String key→String value extras. Pass "" or "{}" for none.
     * @param data        Optional URI string, e.g. "https://example.com". Pass "" to omit.
     * @return "ok" on success, JSON error string on failure.
     */
    @JavascriptInterface
    fun launchIntent(action: String, extrasJson: String, data: String): String {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (data.isNotBlank()) setData(Uri.parse(data))
                if (extrasJson.isNotBlank() && extrasJson != "{}") {
                    val extras = JSONObject(extrasJson)
                    extras.keys().forEach { key -> putExtra(key, extras.getString(key)) }
                }
            }
            (context as? MainActivity)?.runOnUiThread {
                context.startActivity(intent)
            } ?: return """{"error":"no activity context"}"""
            "ok"
        } catch (e: Exception) {
            Log.w(TAG, "launchIntent('$action') error: \${e.message}")
            """{"error":\${JSONObject.quote(e.message ?: "unknown")}}"""
        }
    }

    // ââ Generic Meta-Dispatcher âââââââââââââââââââââââââââââââââââââââââââââââ
    // A single @JavascriptInterface entry-point that routes to every existing
    // bridge method by name. Why this matters:
    //
    //  1. FORWARD COMPAT â a newly shipped web bundle can call
    //       Android.dispatch("brandNewMethod", "{\"x\":1}")
    //     on an old APK. If a macro named "brandNewMethod" has been installed via
    //     setMacros(), it executes immediately. Otherwise dispatch returns a safe
    //     {"error":"..."} JSON string instead of crashing.
    //
    //  2. BACKWARD COMPAT â all direct Android.tapByText("OK") etc. calls in
    //     existing JS continue to work completely unchanged.
    //
    //  3. MACRO FALLBACK â unknown method names fall through to runMacro(), which
    //     checks the JSON-persisted macro registry before giving up.
    //
    // Return value is always a String: "" for void methods, the value serialised
    // to a String for Boolean/Int return types, or the raw String for String-
    // returning methods. Error JSON: {"error":"<message>"}.

    @JavascriptInterface
    fun dispatch(method: String, argsJson: String): String {
        val a = try { JSONObject(argsJson) } catch (_: Exception) { JSONObject() }
        return try {
            when (method) {
                // ââ System Message ââââââââââââââââââââââââââââââââââââââââââââ
                "getSystemMessage"              -> getSystemMessage()
                "setSystemMessage"              -> { setSystemMessage(a.getString("message")); "" }
                "restoreSystemMessage"          -> { restoreSystemMessage(); "" }
                // ââ Model Selection âââââââââââââââââââââââââââââââââââââââââââ
                "getSelectedModelId"            -> getSelectedModelId()
                "setSelectedModel"              -> { setSelectedModel(a.getString("id")); "" }
                // ââ API Keys ââââââââââââââââââââââââââââââââââââââââââââââââââ
                "getAllApiKeys"                 -> getAllApiKeys(a.getString("providerName"))
                "addApiKey"                     -> { addApiKey(a.getString("key"), a.getString("providerName")); "" }
                "removeApiKey"                  -> { removeApiKey(a.getString("key"), a.getString("providerName")); "" }
                "getCurrentKeyIndex"            -> getCurrentKeyIndex(a.getString("providerName")).toString()
                "setCurrentKeyIndex"            -> { setCurrentKeyIndex(a.getInt("index"), a.getString("providerName")); "" }
                // ââ Database Entries ââââââââââââââââââââââââââââââââââââââââââ
                "getDatabaseEntries"            -> getDatabaseEntries()
                "addDatabaseEntry"              -> { addDatabaseEntry(a.getString("title"), a.getString("guide")); "" }
                "updateDatabaseEntry"           -> { updateDatabaseEntry(a.getString("oldTitle"), a.getString("newTitle"), a.getString("guide")); "" }
                "deleteDatabaseEntry"           -> { deleteDatabaseEntry(a.getString("title")); "" }
                // ââ Generation Settings âââââââââââââââââââââââââââââââââââââââ
                "getGenerationSettings"         -> getGenerationSettings(a.getString("modelId"))
                "saveGenerationSettings"        -> {
                    saveGenerationSettings(
                        a.getString("modelId"),
                        a.getDouble("temperature").toFloat(),
                        a.getDouble("topP").toFloat(),
                        a.getInt("topK")
                    ); ""
                }
                // ââ Custom Models âââââââââââââââââââââââââââââââââââââââââââââ
                "setCustomModelOverrides"       -> setCustomModelOverrides(a.getString("json")).toString()
                "getCustomModelOverrides"       -> getCustomModelOverrides()
                "setCustomModelApiKey"          -> { setCustomModelApiKey(a.getString("modelId"), a.getString("key")); "" }
                "getCustomModelApiKey"          -> getCustomModelApiKey(a.getString("modelId"))
                // ââ Chat Operations âââââââââââââââââââââââââââââââââââââââââââ
                "sendMessage"                   -> { sendMessage(a.getString("text")); "" }
                "sendMessageWithImages"         -> { sendMessageWithImages(a.getString("text"), a.getString("urisCsv")); "" }
                "pickImage"                     -> { pickImage(); "" }
                "clearChatHistory"              -> { clearChatHistory(); "" }
                "stopGeneration"                -> { stopGeneration(); "" }
                // ââ Custom Model Responses ââââââââââââââââââââââââââââââââââââ
                "onCustomModelPartialResponse"  -> { onCustomModelPartialResponse(a.getString("text")); "" }
                "onCustomModelFinalResponse"    -> { onCustomModelFinalResponse(a.getString("text")); "" }
                "onCustomModelError"            -> { onCustomModelError(a.getString("message")); "" }
                "isGenerationRunning"           -> isGenerationRunning().toString()
                "isOfflineModelLoaded"          -> isOfflineModelLoaded().toString()
                // ââ Backend Preference ââââââââââââââââââââââââââââââââââââââââ
                "getBackendPreference"          -> getBackendPreference()
                "setBackendPreference"          -> { setBackendPreference(a.getString("backend")); "" }
                // ââ Billing / Donation ââââââââââââââââââââââââââââââââââââââââ
                "initiateDonation"              -> { initiateDonation(); "" }
                "isPurchased"                   -> isPurchased().toString()
                // ââ Termux ââââââââââââââââââââââââââââââââââââââââââââââââââââ
                "setTermuxBackground"           -> { setTermuxBackground(a.getBoolean("background")); "" }
                "getTermuxBackground"           -> getTermuxBackground().toString()
                // ââ Override Setters / Getters ââââââââââââââââââââââââââââââââ
                "setCommandPatternOverrides"        -> setCommandPatternOverrides(a.getString("json")).toString()
                "getCommandPatternOverrides"        -> getCommandPatternOverrides()
                "setModelIdentifierOverrides"       -> setModelIdentifierOverrides(a.getString("json")).toString()
                "getModelIdentifierOverrides"       -> getModelIdentifierOverrides()
                "setOfflineModelOverrides"          -> setOfflineModelOverrides(a.getString("json")).toString()
                "getOfflineModelOverrides"          -> getOfflineModelOverrides()
                "setCustomActionTypes"              -> setCustomActionTypes(a.getString("json")).toString()
                "getCustomActionTypes"              -> getCustomActionTypes()
                "setExecutionPolicyOverrides"       -> setExecutionPolicyOverrides(a.getString("json")).toString()
                "getExecutionPolicyOverrides"       -> getExecutionPolicyOverrides()
                "setAppMappingOverrides"            -> setAppMappingOverrides(a.getString("json")).toString()
                "getAppMappingOverrides"            -> getAppMappingOverrides()
                "setErrorClassificationOverrides"   -> setErrorClassificationOverrides(a.getString("json")).toString()
                "getErrorClassificationOverrides"   -> getErrorClassificationOverrides()
                "setTrialUiOverrides"               -> setTrialUiOverrides(a.getString("json")).toString()
                "getTrialUiOverrides"               -> getTrialUiOverrides()
                "setOperationalTuningOverrides"     -> setOperationalTuningOverrides(a.getString("json")).toString()
                "getOperationalTuningOverrides"     -> getOperationalTuningOverrides()
                "setTrialDurationOverride"          -> setTrialDurationOverride(a.getString("json")).toString()
                "getTrialDurationOverride"          -> getTrialDurationOverride()
                "setGenerationDefaultsOverrides"    -> setGenerationDefaultsOverrides(a.getString("json")).toString()
                "getGenerationDefaultsOverrides"    -> getGenerationDefaultsOverrides()
                "setUiStringsOverrides"             -> setUiStringsOverrides(a.getString("json")).toString()
                "getUiStringsOverrides"             -> getUiStringsOverrides()
                // ââ Toast âââââââââââââââââââââââââââââââââââââââââââââââââââââ
                "showToast"                     -> { showToast(a.getString("message"), a.optBoolean("isLong", false)); "" }
                // ââ Device Control ââââââââââââââââââââââââââââââââââââââââââââ
                "tapByText"                     -> { tapByText(a.getString("buttonText")); "" }
                "longTapByText"                 -> { longTapByText(a.getString("buttonText")); "" }
                "tapAtCoordinates"              -> { tapAtCoordinates(a.getString("x"), a.getString("y")); "" }
                "pressHome"                     -> { pressHome(); "" }
                "pressBack"                     -> { pressBack(); "" }
                "showRecentApps"                -> { showRecentApps(); "" }
                "pressEnterKey"                 -> { pressEnterKey(); "" }
                "writeText"                     -> { writeText(a.getString("text")); "" }
                "scrollDown"                    -> { scrollDown(); "" }
                "scrollUp"                      -> { scrollUp(); "" }
                "scrollLeft"                    -> { scrollLeft(); "" }
                "scrollRight"                   -> { scrollRight(); "" }
                "scrollDownFromCoordinates"     -> {
                    scrollDownFromCoordinates(a.getString("x"), a.getString("y"), a.getString("distance"), a.getLong("durationMs")); ""
                }
                "scrollUpFromCoordinates"       -> {
                    scrollUpFromCoordinates(a.getString("x"), a.getString("y"), a.getString("distance"), a.getLong("durationMs")); ""
                }
                "scrollLeftFromCoordinates"     -> {
                    scrollLeftFromCoordinates(a.getString("x"), a.getString("y"), a.getString("distance"), a.getLong("durationMs")); ""
                }
                "scrollRightFromCoordinates"    -> {
                    scrollRightFromCoordinates(a.getString("x"), a.getString("y"), a.getString("distance"), a.getLong("durationMs")); ""
                }
                "openAppByNameOrPackage"        -> { openAppByNameOrPackage(a.getString("appNameOrPackage")); "" }
                "runTermuxCommand"              -> { runTermuxCommand(a.getString("command")); "" }
                "waitSeconds"                   -> { waitSeconds(a.getLong("seconds")); "" }
                "requestScreenshot"             -> { requestScreenshot(); "" }
                "markCompleted"                 -> { markCompleted(); "" }
                "pinchGesture"                  -> {
                    pinchGesture(
                        a.getString("centerX"), a.getString("centerY"),
                        a.getString("startDistance"), a.getString("endDistance"),
                        a.getLong("durationMs")
                    ); ""
                }
                // ââ Clipboard âââââââââââââââââââââââââââââââââââââââââââââââââ
                "copyToClipboard"               -> { copyToClipboard(a.getString("text")); "" }
                "getClipboardText"              -> getClipboardText()
                // ââ Accessibility Service Status ââââââââââââââââââââââââââââââ
                "isAccessibilityServiceEnabled" -> isAccessibilityServiceEnabled().toString()
                "openAccessibilitySettings"     -> { openAccessibilitySettings(); "" }
                "launchIntent"                  -> launchIntent(a.getString("action"), a.optString("extrasJson", "{}"), a.optString("data", ""))
                // ââ Macros & Extension Slots (self-referential but safe) ââââââ
                "setMacros"                     -> setMacros(a.getString("json")).toString()
                "getMacros"                     -> getMacros()
                "setExtensionHandlers"          -> setExtensionHandlers(a.getString("json")).toString()
                "getExtensionHandlers"          -> getExtensionHandlers()
                // ââ Unknown: fall through to macro registry âââââââââââââââââââ
                else                            -> runMacro(method, a)
            }
        } catch (e: Exception) {
            Log.w(TAG, "dispatch('$method') error: ${e.message}")
            """{"error":${JSONObject.quote(e.message ?: "unknown")}}"""
        }
    }

    // ââ JS-defined Macro Scripts ââââââââââââââââââââââââââââââââââââââââââââââ
    // Macros are JSON-defined sequences of dispatch() calls. They are registered
    // from the web bundle (e.g. fetched alongside index.html) and survive app
    // restarts via MacroPreferences.
    //
    // Any new behaviour that is composable from existing bridge methods can be
    // deployed entirely via a repo commit â no app release required.
    //
    // The same registry also powers the extA..extJ pre-provisioned slots below:
    // a macro named "extA" gives Android.extA() real behaviour without touching
    // native code.
    //
    // JSON format:
    // [
    //   {
    //     "name": "tapOkThenScreenshot",
    //     "steps": [
    //       {"method": "tapByText",         "args": {"buttonText": "OK"}},
    //       {"method": "waitSeconds",        "args": {"seconds": 1}},
    //       {"method": "requestScreenshot",  "args": {}}
    //     ]
    //   }
    // ]
    //
    // Step args may reference the caller's argsJson via "$outer.<key>":
    //   {"method": "tapByText", "args": {"buttonText": "$outer.label"}}
    // â Android.dispatch("myMacro", '{"label":"Save"}') taps the "Save" button.

    @Volatile private var macroRegistry: Map<String, String> = emptyMap()

    init {
        // Pre-warm the registry from persisted JSON so dispatch("macroName", ...)
        // works correctly before the web bundle calls setMacros() again.
        val saved = com.google.ai.sample.util.MacroPreferences.load(context)
        if (saved != null) {
            try {
                val arr = JSONArray(saved)
                val map = mutableMapOf<String, String>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "").trim()
                    if (name.isEmpty()) continue
                    val steps = obj.optJSONArray("steps") ?: continue
                    map[name] = steps.toString()
                }
                macroRegistry = map
                Log.d(TAG, "init: restored ${map.size} macro(s) from preferences")
            } catch (e: Exception) {
                Log.w(TAG, "init: could not restore macros: ${e.message}")
            }
        }
    }

    @JavascriptInterface
    fun setMacros(json: String): Int {
        return try {
            val arr = JSONArray(json)
            val map = mutableMapOf<String, String>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                if (name.isEmpty()) continue
                val steps = obj.optJSONArray("steps") ?: continue
                map[name] = steps.toString()
            }
            macroRegistry = map   // atomic reference swap (@Volatile)
            com.google.ai.sample.util.MacroPreferences.save(context, json)
            Log.d(TAG, "setMacros: installed ${map.size} macro(s)")
            map.size
        } catch (e: Exception) {
            Log.e(TAG, "setMacros error: ${e.message}")
            0
        }
    }

    @JavascriptInterface
    fun getMacros(): String =
        com.google.ai.sample.util.MacroPreferences.load(context) ?: "[]"

    private fun runMacro(name: String, outerArgs: JSONObject): String {
        val stepsJson = macroRegistry[name]
            ?: return """{"error":${JSONObject.quote("unknown method or macro: ${name.take(80)}")}}"""
        return try {
            val steps = JSONArray(stepsJson)
            var lastResult = ""
            for (i in 0 until steps.length()) {
                val step = steps.optJSONObject(i) ?: continue
                val method = step.optString("method", ""); if (method.isEmpty()) continue
                val args = step.optJSONObject("args") ?: JSONObject()
                lastResult = dispatch(method, resolveArgs(args, outerArgs).toString())
            }
            lastResult
        } catch (e: Exception) {
            Log.w(TAG, "runMacro('$name') error: ${e.message}")
            """{"error":${JSONObject.quote(e.message ?: "unknown")}}"""
        }
    }

    /**
     * Resolves `"$outer.<key>"` placeholders in a macro step's args object against
     * the outerArgs that were passed to the enclosing [dispatch] / [runMacro] call.
     * Non-placeholder values are forwarded unchanged.
     */
    private fun resolveArgs(args: JSONObject, outer: JSONObject): JSONObject {
        val resolved = JSONObject()
        val keys = args.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = args.opt(k)
            if (v is String && v.startsWith("\$outer.")) {
                val outerKey = v.removePrefix("\$outer.")
                resolved.put(k, outer.opt(outerKey) ?: v)
            } else {
                resolved.put(k, v)
            }
        }
        return resolved
    }

    // ââ Pre-provisioned Extension Slots (extA .. extJ) ââââââââââââââââââââââââ
    // Ten @JavascriptInterface methods that are compiled into the APK ahead of
    // time ("auf Vorrat"). Because they are registered with the WebView host at
    // compile time, a JS bundle can always call Android.extA("{...}") safely â
    // even if the slot isn't wired yet (returns a stub JSON) and even if a
    // future app release replaces the stub body with a real native
    // implementation without breaking existing callers.
    //
    // HOW TO WIRE A SLOT WITHOUT A RELEASE:
    //   Register a macro whose name matches the slot name via setExtensionHandlers
    //   (or equivalently setMacros). The slot method delegates to runMacro() which
    //   checks the registry:
    //
    //   Android.setExtensionHandlers(JSON.stringify([{
    //     "name": "extA",
    //     "steps": [
    //       {"method": "tapByText", "args": {"buttonText": "$outer.label"}},
    //       {"method": "requestScreenshot", "args": {}}
    //     ]
    //   }]));
    //   // Now Android.extA('{"label":"OK"}') taps "OK" and takes a screenshot.
    //
    // HOW TO WIRE A SLOT WITH A FUTURE RELEASE:
    //   Replace the runExtSlot() call body with native Kotlin code. The JS side
    //   never needs to change because the method name and signature are stable.
    //
    // setExtensionHandlers / getExtensionHandlers delegate to the shared macro
    // registry (setMacros / getMacros) so there is a single unified lookup path.

    @JavascriptInterface
    fun setExtensionHandlers(json: String): Int = setMacros(json)

    @JavascriptInterface
    fun getExtensionHandlers(): String = getMacros()

    @JavascriptInterface fun extA(argsJson: String): String = runExtSlot("extA", argsJson)
    @JavascriptInterface fun extB(argsJson: String): String = runExtSlot("extB", argsJson)
    @JavascriptInterface fun extC(argsJson: String): String = runExtSlot("extC", argsJson)
    @JavascriptInterface fun extD(argsJson: String): String = runExtSlot("extD", argsJson)
    @JavascriptInterface fun extE(argsJson: String): String = runExtSlot("extE", argsJson)
    @JavascriptInterface fun extF(argsJson: String): String = runExtSlot("extF", argsJson)
    @JavascriptInterface fun extG(argsJson: String): String = runExtSlot("extG", argsJson)
    @JavascriptInterface fun extH(argsJson: String): String = runExtSlot("extH", argsJson)
    @JavascriptInterface fun extI(argsJson: String): String = runExtSlot("extI", argsJson)
    @JavascriptInterface fun extJ(argsJson: String): String = runExtSlot("extJ", argsJson)

    private fun runExtSlot(slot: String, argsJson: String): String {
        val args = try { JSONObject(argsJson) } catch (_: Exception) { JSONObject() }
        val result = runMacro(slot, args)
        // Replace the generic "unknown method or macro" message with a clear stub
        // signal so JS callers can distinguish "not wired yet" from "real error".
        return if (result.contains("unknown method or macro"))
            """{"status":"stub","slot":"$slot"}"""
        else
            result
    }

    // ââ Helpers âââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââ

    companion object {
        fun jsEscape(s: String): String =
            s.replace("\\", "\\\\")
             .replace("'", "\\'")
             .replace("\n", "\\n")
             .replace("\r", "\\r")
             .replace("<", "\\u003C")
    }
}




