package com.google.ai.sample.feature.multimodal

import android.content.Context
import android.widget.Toast

internal object PhotoReasoningScreenshotUiNotifier {
    fun showProcessing(context: Context, onStatus: (String) -> Unit) {
        onStatus("Processing screenshot...")
        Toast.makeText(context, com.google.ai.sample.util.UiStringsConfig.get("toast_processing_screenshot", "Processing screenshot..."), Toast.LENGTH_SHORT).show()
    }

    fun showSendingToAi(context: Context, onStatus: (String) -> Unit) {
        // No-op Toast: the screenshot now appears as a thumbnail directly inside the user
        // bubble in the WebView (see index.html addUserBubble), so this separate native
        // notification is no longer needed. onStatus() is kept for any other status-area
        // consumers, but intentionally left blank here.
    }

    fun showAddedToConversation(context: Context) {
        // No-op: superseded by the in-bubble screenshot thumbnail in the WebView.
    }
}
