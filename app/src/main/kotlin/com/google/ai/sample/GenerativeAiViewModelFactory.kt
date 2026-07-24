package com.google.ai.sample

import android.content.Context
import com.google.ai.sample.MainActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.ai.sample.feature.multimodal.PhotoReasoningViewModel
import com.google.ai.sample.util.GenerationSettingsPreferences

// API providers - used for API key management in SharedPreferences
// Models are defined in WebView (index.html), but we need these enum values
// to store and retrieve API keys via WebViewBridge.getAllApiKeys/addApiKey/etc.
enum class ApiProvider {
    GOOGLE,
    PUTER,
    MISTRAL,
    GROQ,
    VERCEL,
    CEREBRAS,
    CLOUDFLARE,
    HUMAN_EXPERT
}

enum class ModelOption(
    val displayName: String,
    val modelName: String,
    val apiProvider: ApiProvider = ApiProvider.GOOGLE,
    val downloadUrl: String? = null,
    val size: String? = null,
    val supportsScreenshot: Boolean = true,
    val isOfflineModel: Boolean = false,
    val offlineModelFilename: String? = null,
    val offlineAlternateModelFilenames: List<String> = emptyList(),
    val offlineRequiredFilenames: List<String> = emptyList(),
    val additionalDownloadUrls: List<String> = emptyList(),
    val requiresVisionBackend: Boolean = false
) {
    // ── Offline models (need native LiteRT code) ─────────────────────────────
    GEMMA_3N_E4B_IT(
        "Gemma 3n E4B it (offline)",
        "gemma-3n-e4b-it",
        ApiProvider.GOOGLE,
        "https://huggingface.co/na5h13/gemma-3n-E4B-it-litert-lm/resolve/main/gemma-3n-E4B-it-int4.litertlm?download=true",
        "4.92 GB",
        supportsScreenshot = true,
        isOfflineModel = true,
        offlineModelFilename = "gemma-3n-e4b-it-int4.litertlm",
        offlineRequiredFilenames = listOf("gemma-3n-e4b-it-int4.litertlm")
    ),
    GEMMA_4_E4B_IT(
        "Gemma 4 E4B it (offline)",
        "gemma-4-e4b-it",
        ApiProvider.GOOGLE,
        "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm?download=true",
        "3.40 GB",
        isOfflineModel = true,
        offlineModelFilename = "gemma-4-E4B-it.litertlm",
        offlineRequiredFilenames = listOf("gemma-4-E4B-it.litertlm")
    ),
    QWEN3_5_4B_OFFLINE(
        "Qwen3.5 4B (offline)",
        "qwen3.5-4b-offline",
        ApiProvider.GOOGLE,
        "https://huggingface.co/Yoursmiling/Qwen3.5-4B-LiteRT/resolve/main/model_multimodal.litertlm?download=true",
        "6.3 GB",
        isOfflineModel = true,
        offlineModelFilename = "model_multimodal.litertlm",
        offlineAlternateModelFilenames = listOf("model_quantized.litertlm"),
        offlineRequiredFilenames = listOf(
            "model_multimodal.litertlm",
            "sentencepiece.model",
            "tokenizer.json",
            "tokenizer_config.json",
            "embedder_quantized.tflite",
            "vision_encoder_quantized.tflite",
            "vision_adapter_quantized.tflite",
            "model_multimodal_llm_metadata_multimodal.pb"
        ),
        additionalDownloadUrls = listOf(
            "https://huggingface.co/Yoursmiling/Qwen3.5-4B-LiteRT/resolve/main/sentencepiece.model?download=true",
            "https://huggingface.co/Yoursmiling/Qwen3.5-4B-LiteRT/resolve/main/tokenizer.json?download=true",
            "https://huggingface.co/Yoursmiling/Qwen3.5-4B-LiteRT/resolve/main/tokenizer_config.json?download=true",
            "https://huggingface.co/Yoursmiling/Qwen3.5-4B-LiteRT/resolve/main/embedder_quantized.tflite?download=true",
            "https://huggingface.co/Yoursmiling/Qwen3.5-4B-LiteRT/resolve/main/vision_encoder_quantized.tflite?download=true",
            "https://huggingface.co/Yoursmiling/Qwen3.5-4B-LiteRT/resolve/main/vision_adapter_quantized.tflite?download=true",
            "https://huggingface.co/Yoursmiling/Qwen3.5-4B-LiteRT/resolve/main/model_multimodal_llm_metadata_multimodal.pb?download=true"
        ),
        requiresVisionBackend = true
    ),

    // ── Special models (need native code paths) ──────────────────────────────
    HUMAN_EXPERT("Human Expert", "human-expert", ApiProvider.HUMAN_EXPERT),

    // ── Fallback for online models (handled entirely by WebView JS) ──────────
    // When an online model is selected, the WebView makes the API call via JavaScript.
    // This enum entry is only used as a placeholder for the native ViewModel infrastructure.
    ONLINE_MODEL("Online Model (WebView)", "online-model", ApiProvider.GOOGLE);

    /** Whether this model supports Temperature/TopP settings in UI */
    val supportsGenerationSettings: Boolean
        get() = this != HUMAN_EXPERT

    /** Whether this model supports TopK setting in UI/request payloads. */
    val supportsTopK: Boolean
        get() = this != HUMAN_EXPERT
}

val GenerativeViewModelFactory = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
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
            if (currentModel.supportsTopK) {
                topK = genSettings.topK.coerceAtLeast(1)
            }
        }

        // Get the API key from MainActivity
        val mainActivity = MainActivity.getInstance()
        val apiKey = if (currentModel.isOfflineModel || currentModel == ModelOption.HUMAN_EXPERT) {
            "offline-no-key-needed" // Dummy key for offline/human expert models
        } else {
            mainActivity?.getCurrentApiKey(currentModel.apiProvider) ?: ""
        }

        if (apiKey.isEmpty()) {
            throw IllegalStateException("API key for ${currentModel.apiProvider} is not available. Please set an API key.")
        }

        val createdViewModel = with(modelClass) {
            when {
                isAssignableFrom(PhotoReasoningViewModel::class.java) -> {
                    // All online models are handled by WebView JavaScript.
                    // The native GenerativeModel is only used as a placeholder for the
                    // ViewModel infrastructure. The actual API calls go through JS.
                    val generativeModel = GenerativeModel(
                        modelName = currentModel.modelName,
                        apiKey = apiKey,
                        generationConfig = config
                    )
                    PhotoReasoningViewModel(
                        application,
                        generativeModel, 
                        currentModel.modelName,
                        null // No LiveApiManager - live models are handled by WebView
                    )
                }

                else ->
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }

        return modelClass.cast(createdViewModel)
    }
}

enum class InferenceBackend {
    CPU, GPU
}

object GenerativeAiViewModelFactory {
    private var currentModel: ModelOption = ModelOption.ONLINE_MODEL
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
        // On startup, first check if a custom model was persisted as active (JSON-defined models
        // take precedence over built-in enum values - consistent with setSelectedModel architecture)
        val customModelId = com.google.ai.sample.util.CustomModelPreferences.loadActiveModelId(context)
        if (customModelId != null) {
            // Re-load custom models JSON so the registry is populated before we try to activate
            val savedJson = com.google.ai.sample.util.CustomModelPreferences.loadModelsJson(context)
            if (savedJson != null) {
                com.google.ai.sample.util.CustomModelRegistry.setModels(savedJson)
                if (com.google.ai.sample.util.CustomModelRegistry.setActiveModelId(customModelId)) {
                    // Custom model restored successfully; keep a safe built-in model as the
                    // underlying ModelOption (used by the ViewModel factory for non-custom paths)
                    currentModel = loadBuiltInModelPreference(context)
                    return
                }
            }
            // Persisted custom model ID is no longer in config - clear stale reference
            com.google.ai.sample.util.CustomModelPreferences.saveActiveModelId(context, null)
        }
        currentModel = loadBuiltInModelPreference(context)
    }

    private fun loadBuiltInModelPreference(context: Context): ModelOption {
        val prefs = context.getSharedPreferences("inference_prefs", Context.MODE_PRIVATE)
        val modelNameStr = prefs.getString("selected_model", ModelOption.ONLINE_MODEL.name)
        return try {
            ModelOption.valueOf(modelNameStr ?: ModelOption.ONLINE_MODEL.name)
        } catch (e: IllegalArgumentException) {
            // Model ID from preferences doesn't match any enum entry (e.g., it was an
            // online model that has been removed from the enum). Fall back to ONLINE_MODEL
            // since the WebView will handle the actual model selection.
            ModelOption.ONLINE_MODEL
        }
    }
}

