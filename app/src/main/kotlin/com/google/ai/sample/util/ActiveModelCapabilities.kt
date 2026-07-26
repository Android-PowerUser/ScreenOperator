package com.google.ai.sample.util

import android.content.Context
import com.google.ai.sample.GenerativeAiViewModelFactory

/**
 * Resolves whether the currently active model (whichever of the three model categories -
 * custom JSON-defined model, JS-only online model, or native built-in model/offline model -
 * is active) actually supports screenshots/vision input.
 *
 * This is the single source of truth used everywhere a screenshot-related decision must
 * reflect the model's *real* capability rather than the native [GenerativeAiViewModelFactory]
 * model enum, which always reports `supportsScreenshot = true` for `ONLINE_MODEL` (the
 * placeholder used for every JS-only model selected from the WebView dropdown). Used for:
 * the actual capture/MediaProjection-request decision in
 * `ScreenOperatorAccessibilityService.executeTakeScreenshotCommand`, its related toasts, and
 * the pre-send MediaProjection permission check in `MainActivity.sendMessageFromWebView`.
 */
object ActiveModelCapabilities {

    fun currentModelSupportsScreenshot(context: Context): Boolean {
        CustomModelRegistry.getActiveModel()?.let { customModel ->
            return customModel.supportsScreenshot
        }

        val currentModel = GenerativeAiViewModelFactory.getCurrentModel()
        val jsModelPrefs = context.applicationContext
            .getSharedPreferences("js_model_prefs", Context.MODE_PRIVATE)
        val jsOnlyModelId = jsModelPrefs.getString("js_only_model_id", null)

        // JS-only online models (the normal WebView model dropdown) are a third, separate
        // case: they are not in CustomModelRegistry, and ModelOption.ONLINE_MODEL always
        // reports supportsScreenshot=true. Their real capability is persisted by the WebView
        // on model selection (dispatch("setJsOnlyModelSupportsScreenshot", ...)) - the same
        // flag PhotoReasoningViewModel already reads to decide whether to attach the image to
        // the outgoing payload. jsOnlyModelId being non-null is itself the reliable signal
        // that a JS-only model is active (it is cleared whenever a custom or native built-in
        // model is selected instead - see WebViewBridge.setSelectedModel) - it is checked on
        // its own, without also requiring currentModel == ONLINE_MODEL, because currentModel
        // is only updated by native built-in model selections and can otherwise still hold a
        // stale value (e.g. a previously selected offline model) while a JS-only model is the
        // one actually active.
        if (jsOnlyModelId != null) {
            return jsModelPrefs.getBoolean("js_only_supports_screenshot", true)
        }

        return currentModel.supportsScreenshot
    }
}
