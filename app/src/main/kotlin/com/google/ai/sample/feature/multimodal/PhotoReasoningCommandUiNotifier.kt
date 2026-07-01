package com.google.ai.sample.feature.multimodal

import android.content.Context
import android.widget.Toast

internal object PhotoReasoningCommandUiNotifier {
    fun showStoppedByAi(context: Context) {
        Toast.makeText(context, com.google.ai.sample.util.UiStringsConfig.get("toast_ai_stopped_app", "The AI stopped Screen Operator"), Toast.LENGTH_SHORT).show()
    }
}
