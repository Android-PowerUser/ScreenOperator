package com.google.ai.sample

import android.content.Context
import com.google.ai.sample.MainActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.ai.sample.feature.multimodal.PhotoReasoningViewModel

// Model options
enum class ModelOption(val displayName: String, val modelName: String) {
    GEMINI_FLASH_LITE("Gemini 2.0 Flash Lite", "gemini-2.0-flash-lite"),
    GEMINI_FLASH("Gemini 2.0 Flash", "gemini-2.0-flash"),
    GEMINI_FLASH_LITE_PREVIEW("Gemini 2.5 Flash Lite Preview", "gemini-2.5-flash-lite-preview-06-17"),
    GEMINI_FLASH_PREVIEW("Gemini 2.5 Flash", "gemini-2.5-flash"),
    GEMINI_PRO("Gemini 2.5 Pro", "gemini-2.5-pro"),
    GEMMA_3N_E4B_IT("Gemma 3n E4B it (online)", "gemma-3n-e4b-it"),
    GEMMA_3_27B_IT("Gemma 3 27B IT", "gemma-3-27b-it")
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
                    // Initialize a GenerativeModel with the currently selected model
                    // for multimodal text generation
                    val generativeModel = GenerativeModel(
                        modelName = currentModel.modelName,
                        apiKey = apiKey,
                        generationConfig = config
                    )
                    // Pass the ApiKeyManager to the ViewModel for key rotation
                    val apiKeyManager = ApiKeyManager.getInstance(application)
                    PhotoReasoningViewModel(generativeModel, currentModel.modelName)
                }

                else ->
                    throw IllegalArgumentException("Unknown ViewModel class: ${viewModelClass.name}")
            }
        } as T
    }
}

object GenerativeAiViewModelFactory {
    private var currentModel: ModelOption = ModelOption.GEMINI_FLASH_PREVIEW

    fun setModel(modelOption: ModelOption) {
        currentModel = modelOption
    }

    fun getCurrentModel(): ModelOption {
        return currentModel
    }
}
