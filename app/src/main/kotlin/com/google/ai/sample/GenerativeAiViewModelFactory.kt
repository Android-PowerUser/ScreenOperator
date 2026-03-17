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
import com.google.ai.sample.util.GenerationSettingsPreferences

// Model options
enum class ApiProvider {
    VERCEL,
    GOOGLE,
    CEREBRAS,
    MISTRAL,
    PUTER,
    HUMAN_EXPERT
}

enum class ModelOption(
    val displayName: String,
    val modelName: String,
    val apiProvider: ApiProvider = ApiProvider.GOOGLE,
    val downloadUrl: String? = null,
    val size: String? = null,
    val supportsScreenshot: Boolean = true
) {
    PUTER_GLM5("GLM-5 (Puter)", "z-ai/glm-5", ApiProvider.PUTER, supportsScreenshot = false),
    MISTRAL_LARGE_3("Mistral Large 3", "mistral-large-latest", ApiProvider.MISTRAL),
    GPT_5_1_CODEX_MAX("GPT-5.1 Codex Max (Vercel)", "openai/gpt-5.1-codex-max", ApiProvider.VERCEL),
    GPT_5_1_CODEX_MINI("GPT-5.1 Codex Mini (Vercel)", "openai/gpt-5.1-codex-mini", ApiProvider.VERCEL),
    GPT_5_NANO("GPT-5 Nano (Vercel)", "openai/gpt-5-nano", ApiProvider.VERCEL),
    GPT_OSS_120B("GPT-OSS 120B (Cerebras)", "gpt-oss-120b", ApiProvider.CEREBRAS, supportsScreenshot = false),
    GEMINI_3_FLASH("Gemini 3 Flash", "gemini-3-flash-preview"),
    GEMINI_PRO("Gemini 2.5 Pro", "gemini-2.5-pro"),
    GEMINI_FLASH_PREVIEW("Gemini 2.5 Flash", "gemini-2.5-flash"),
    GEMINI_FLASH_LIVE_PREVIEW("Gemini 2.5 Flash Live Preview", "gemini-live-2.5-flash-native-audio"),
    GEMINI_FLASH_LITE_PREVIEW("Gemini 2.5 Flash Lite Preview", "gemini-2.5-flash-lite-preview-06-17"),
    GEMINI_FLASH("Gemini 2.0 Flash", "gemini-2.0-flash"),
    GEMINI_FLASH_LITE("Gemini 2.0 Flash Lite", "gemini-2.0-flash-lite"),
    GEMMA_3_27B_IT("Gemma 3 27B IT", "gemma-3-27b-it", supportsScreenshot = false),
    GEMMA_3N_E4B_IT(
        "Gemma 3n E4B it (offline)",
        "gemma-3n-e4b-it",
        ApiProvider.GOOGLE,
        "https://huggingface.co/na5h13/gemma-3n-E4B-it-litert-lm/resolve/main/gemma-3n-E4B-it-int4.litertlm?download=true",
        "4.92 GB"
    ),
    HUMAN_EXPERT("Human Expert", "human-expert", ApiProvider.HUMAN_EXPERT);

    /** Whether this model supports TopK/TopP/Temperature settings */
    val supportsGenerationSettings: Boolean
        get() = this != HUMAN_EXPERT
}

val GenerativeViewModelFactory = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        viewModelClass: Class<T>,
        extras: CreationExtras
    ): T {
        // Get the application context from extras
        val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
        val currentModel = GenerativeAiViewModelFactory.getCurrentModel()
        
        // Load per-model generation settings
        val genSettings = GenerationSettingsPreferences.loadSettings(application.applicationContext, currentModel.modelName)
        val config = generationConfig {
            temperature = genSettings.temperature
            topP = genSettings.topP
            topK = genSettings.topK
        }

        // Get the API key from MainActivity
        val mainActivity = MainActivity.getInstance()
        val apiKey = if (currentModel == ModelOption.GEMMA_3N_E4B_IT || currentModel == ModelOption.HUMAN_EXPERT) {
            "offline-no-key-needed" // Dummy key for offline/human expert models
        } else {
            mainActivity?.getCurrentApiKey(currentModel.apiProvider) ?: ""
        }

        if (apiKey.isEmpty()) {
            throw IllegalStateException("API key for ${currentModel.apiProvider} is not available. Please set an API key.")
        }

        return with(viewModelClass) {
            when {
                isAssignableFrom(PhotoReasoningViewModel::class.java) -> {
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
                            application,
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
                            application,
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

enum class InferenceBackend {
    CPU, GPU
}

object GenerativeAiViewModelFactory {
    private var currentModel: ModelOption = ModelOption.MISTRAL_LARGE_3
    private var currentBackend: InferenceBackend = InferenceBackend.GPU

    fun setModel(modelOption: ModelOption, context: Context? = null) {
        currentModel = modelOption
        if (context != null) {
            val prefs = context.getSharedPreferences("inference_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("selected_model", modelOption.name).apply()
        }
    }

    fun getCurrentModel(): ModelOption {
        return currentModel
    }

    fun setBackend(backend: InferenceBackend, context: Context) {
        currentBackend = backend
        val prefs = context.getSharedPreferences("inference_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("preferred_backend", backend.name).apply()
    }

    fun getBackend(): InferenceBackend {
        return currentBackend
    }

    fun loadBackendPreference(context: Context) {
        val prefs = context.getSharedPreferences("inference_prefs", Context.MODE_PRIVATE)
        val backendName = prefs.getString("preferred_backend", InferenceBackend.GPU.name)
        currentBackend = try {
            InferenceBackend.valueOf(backendName ?: InferenceBackend.GPU.name)
        } catch (e: IllegalArgumentException) {
            InferenceBackend.GPU
        }
    }

    fun loadModelPreference(context: Context) {
        val prefs = context.getSharedPreferences("inference_prefs", Context.MODE_PRIVATE)
        val modelNameStr = prefs.getString("selected_model", ModelOption.MISTRAL_LARGE_3.name)
        currentModel = try {
            ModelOption.valueOf(modelNameStr ?: ModelOption.MISTRAL_LARGE_3.name)
        } catch (e: IllegalArgumentException) {
            ModelOption.MISTRAL_LARGE_3
        }
    }
}
