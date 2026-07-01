package com.google.ai.sample.feature.multimodal

import android.content.Context
import android.widget.Toast

internal object PhotoReasoningScreenshotUiNotifier {
    fun showProcessing(context: Context, onStatus: (String) -> Unit) {
        onStatus("Processing screenshot...")
        Toast.makeText(context, com.google.ai.sample.util.UiStringsConfig.get("toast_processing_screenshot", "Processing screenshot..."), Toast.LENGTH_SHORT).show()
    }

    fun showSendingToAi(context: Context, onStatus: (String) -> Unit) {
        onStatus("Screenshot added, sending to AI...")
        Toast.makeText(context, com.google.ai.sample.util.UiStringsConfig.get("toast_screenshot_sending", "Screenshot added, sending to AI..."), Toast.LENGTH_SHORT).show()
    }

    fun showAddedToConversation(context: Context) {
        Toast.makeText(context, com.google.ai.sample.util.UiStringsConfig.get("toast_screenshot_added", "Screenshot added to conversation"), Toast.LENGTH_SHORT).show()
    }
}
