package com.google.ai.sample.feature.multimodal

import android.content.Context
import android.widget.Toast

internal object PhotoReasoningScreenshotUiNotifier {
    fun showProcessing(context: Context, onStatus: (String) -> Unit) {
        onStatus("Processing screenshot...")
        Toast.makeText(context, "Processing screenshot...", Toast.LENGTH_SHORT).show()
    }

    fun showSendingToAi(context: Context, onStatus: (String) -> Unit) {
        onStatus("Screenshot added, sending to AI...")
        Toast.makeText(context, "Screenshot added, sending to AI...", Toast.LENGTH_SHORT).show()
    }

    fun showAddedToConversation(context: Context) {
        Toast.makeText(context, "Screenshot added to conversation", Toast.LENGTH_SHORT).show()
    }
}
