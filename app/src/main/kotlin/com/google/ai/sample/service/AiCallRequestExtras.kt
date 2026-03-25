package com.google.ai.sample.service

import android.content.Intent
import com.google.ai.sample.ApiProvider
import com.google.ai.sample.ScreenCaptureService

data class AiCallRequestExtras(
    val inputContentJson: String?,
    val chatHistoryJson: String?,
    val modelName: String?,
    val apiKey: String?,
    val apiProvider: ApiProvider,
    val tempFilePaths: ArrayList<String>
) {
    companion object {
        fun fromIntent(intent: Intent): AiCallRequestExtras {
            val apiProviderString = intent.getStringExtra(ScreenCaptureService.EXTRA_AI_API_PROVIDER)
            return AiCallRequestExtras(
                inputContentJson = intent.getStringExtra(ScreenCaptureService.EXTRA_AI_INPUT_CONTENT_JSON),
                chatHistoryJson = intent.getStringExtra(ScreenCaptureService.EXTRA_AI_CHAT_HISTORY_JSON),
                modelName = intent.getStringExtra(ScreenCaptureService.EXTRA_AI_MODEL_NAME),
                apiKey = intent.getStringExtra(ScreenCaptureService.EXTRA_AI_API_KEY),
                apiProvider = ApiProvider.valueOf(apiProviderString ?: ApiProvider.GOOGLE.name),
                tempFilePaths = intent.getStringArrayListExtra(ScreenCaptureService.EXTRA_TEMP_FILE_PATHS) ?: ArrayList()
            )
        }
    }
}
