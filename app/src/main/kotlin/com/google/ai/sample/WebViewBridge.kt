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

    // ── Model Identifier Overrides (remote-updatable wire-level model names) ───
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

    // ── Offline Model Overrides (remote-updatable download URL/size/extra files) ─
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

    // ── Custom Action Types (remote-updatable, entirely new action kinds) ──────────────────
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

    // ── Execution Policy Overrides (remote-updatable per-message command limit) ───────────
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

    // ── App Mapping Overrides (remote-updatable openApp() name/package resolution) ────────
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

    // ── Error Classification Overrides (remote-updatable AI-provider error matching) ──────
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

    // ── Trial/Donation UI Overrides (remote-updatable dialog text, not the gating logic) ──
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

    // ── Operational Tuning Overrides (remote-updatable retry/cooldown timing) ──────────────
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

    // ── Trial Duration Override (remote-updatable trial length only) ──────────────────────
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

    // ── Generation Defaults Overrides (remote-updatable factory defaults, not user settings) ─
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

    // ── UI String Overrides (remote-updatable native Compose-screen text) ──────────────────
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

    // ── Toast ────────────────────────────────────────────────────────────────
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

    // ── Device Control (every native gesture/navigation capability, exposed to JS) ─────────
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

    // ── Clipboard (no extra Android permission required) ───────────────────────
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

    // ── Accessibility Service Status ──────────────────────────────────────────

    @JavascriptInterface
    fun isAccessibilityServiceEnabled(): Boolean {
        return AccessibilityServiceStatusResolver.isServiceEnabled(context)
    }

    @JavascriptInterface
    fun openAccessibilitySettings() {
        val intent = (context as? MainActivity)?.getAccessibilitySettingsIntent() ?: return
        context.startActivity(intent)
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

