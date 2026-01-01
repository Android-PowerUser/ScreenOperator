package com.google.ai.sample

import android.content.Context
import com.google.ai.sample.MainActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.ai.sample.feature.live.LiveApiManager
import com.google.ai.sample.feature.multimodal.PhotoReasoningViewModel

// Model options
enum class ModelOption(val displayName: String, val modelName: String) {
    GPT_OSS_120B("gpt-oss-120b", "gpt-oss-120b"),
    GEMINI_FLASH_PREVIEW("Gemini 2.5 Flash", "gemini-2.5-flash"),
    GEMINI_FLASH_LITE("Gemini 2.0 Flash Lite", "gemini-2.0-flash-lite"),
    GEMINI_FLASH("Gemini 2.0 Flash", "gemini-2.0-flash"),
    GEMINI_FLASH_LITE_PREVIEW("Gemini 2.5 Flash Lite Preview", "gemini-2.5-flash-lite-preview-06-17"),
    GEMINI_PRO("Gemini 2.5 Pro", "gemini-2.5-pro"),
    GEMINI_FLASH_LIVE_PREVIEW("Gemini 2.5 Flash Live Preview", "gemini-2.5-flash-live-preview"),
    GEMMA_3_27B_IT("Gemma 3 27B IT", "gemma-3-27b-it"),
    GEMMA_3N_E4B_IT("Gemma 3n E4B it (online)", "gemma-3n-e4b-it")
}

val GenerativeViewModelFactory = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        viewModelClass: Class<T>,
        extras: CreationExtras
    ): T {
        val config = generationConfig {
            temperature = 0.0f
        }

        // Get the application context from extras
        val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])

        // Get the API key from MainActivity
        val mainActivity = MainActivity.getInstance()
        val apiKey = mainActivity?.getCurrentApiKey() ?: ""

        if (apiKey.isEmpty()) {
            throw IllegalStateException("API key is not available. Please set an API key.")
        }

        return with(viewModelClass) {
            when {
                isAssignableFrom(PhotoReasoningViewModel::class.java) -> {
                    val currentModel = GenerativeAiViewModelFactory.getCurrentModel()
                    
                    if (currentModel.modelName.contains("live")) {
                        // Live API models
                        val liveApiManager = LiveApiManager(apiKey, currentModel.modelName)
                        
                        // For Live API, we might not need a GenerativeModel at all
                        // or we use a fallback model for non-live operations
                        // Using the first non-live model as fallback
                        val fallbackModel = GenerativeModel(
                            modelName = ModelOption.GEMINI_FLASH_PREVIEW.modelName, // Using Gemini 2.5 Flash as fallback
                            apiKey = apiKey,
                            generationConfig = config
                        )
                        
                        val viewModel = PhotoReasoningViewModel(
                            fallbackModel, 
                            currentModel.modelName, 
                            liveApiManager
                        )
                        
                        // Don't connect immediately - let collectLiveApiMessages handle it with proper setup
                        viewModel
                    } else {
                        // Regular generative models
                        val generativeModel = GenerativeModel(
                            modelName = currentModel.modelName,
                            apiKey = apiKey,
                            generationConfig = config
                        )
                        PhotoReasoningViewModel(
                            generativeModel, 
                            currentModel.modelName,
                            null // No LiveApiManager for regular models
                        )
                    }
                }

                else ->
                    throw IllegalArgumentException("Unknown ViewModel class: ${viewModelClass.name}")
            }
        } as T
    }
}

object GenerativeAiViewModelFactory {
    private var currentModel: ModelOption = ModelOption.GPT_OSS_120B

    fun setModel(modelOption: ModelOption) {
        currentModel = modelOption
    }

    fun getCurrentModel(): ModelOption {
        return currentModel
    }
}
