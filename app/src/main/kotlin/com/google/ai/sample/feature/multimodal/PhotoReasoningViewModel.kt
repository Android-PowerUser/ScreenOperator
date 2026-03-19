package com.google.ai.sample.feature.multimodal

import android.app.Application
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.ImagePart // For instance check
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.ai.sample.ApiKeyManager
import com.google.ai.sample.MainActivity
import com.google.ai.sample.ScreenCaptureService
import com.google.ai.sample.PhotoReasoningApplication
import com.google.ai.sample.ScreenOperatorAccessibilityService
import com.google.ai.sample.util.ChatHistoryPreferences
import com.google.ai.sample.util.Command
import com.google.ai.sample.util.CommandParser
import com.google.ai.sample.util.SystemMessagePreferences
import com.google.ai.sample.util.SystemMessageEntryPreferences // Added import
import com.google.ai.sample.util.SystemMessageEntry // Added import
import com.google.ai.sample.util.UserInputPreferences // Added import
import com.google.ai.sample.feature.multimodal.ModelDownloadManager // Added import
import com.google.ai.sample.ModelOption // Added import
import com.google.ai.sample.GenerativeAiViewModelFactory // Added import
import com.google.ai.sample.InferenceBackend // Added import
import com.google.ai.sample.feature.multimodal.dtos.toDto // Added for DTO mapping
import com.google.ai.sample.feature.multimodal.dtos.ImagePartDto // Required for path extraction
import kotlinx.coroutines.Dispatchers
import java.util.ArrayList // Required for StringArrayListExtra
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import com.google.mediapipe.tasks.genai.llminference.ProgressListener

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import android.util.Base64
import com.google.ai.sample.feature.live.LiveApiManager
import com.google.ai.sample.ApiProvider
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import com.google.ai.sample.webrtc.WebRTCSender
import com.google.ai.sample.webrtc.SignalingClient
import org.webrtc.IceCandidate

class PhotoReasoningViewModel(
    application: Application,
    private var generativeModel: GenerativeModel,
    private val modelName: String,
    private val liveApiManager: LiveApiManager? = null
) : AndroidViewModel(application) {

    private val isLiveMode: Boolean
        get() = liveApiManager != null

    private var llmInference: LlmInference? = null
    private val TAG = "PhotoReasoningViewModel"
    
    // WebRTC & Signaling
    private var webRTCSender: WebRTCSender? = null
    private var signalingClient: SignalingClient? = null
    private var lastMediaProjectionResultCode: Int = 0
    private var lastMediaProjectionResultData: Intent? = null
    


    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private val _uiState: MutableStateFlow<PhotoReasoningUiState> =
        MutableStateFlow(PhotoReasoningUiState.Initial)
    val uiState: StateFlow<PhotoReasoningUiState> =
        _uiState.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _showStopNotificationFlow = MutableStateFlow(false)
    val showStopNotificationFlow: StateFlow<Boolean> = _showStopNotificationFlow.asStateFlow()

    private val _isGenerationRunningFlow = MutableStateFlow(false)
    val isGenerationRunningFlow: StateFlow<Boolean> = _isGenerationRunningFlow.asStateFlow()

    private val _isOfflineGpuModelLoadedFlow = MutableStateFlow(false)
    val isOfflineGpuModelLoadedFlow: StateFlow<Boolean> = _isOfflineGpuModelLoadedFlow.asStateFlow()

    private val _isInitializingOfflineModelFlow = MutableStateFlow(false)
    val isInitializingOfflineModelFlow: StateFlow<Boolean> = _isInitializingOfflineModelFlow.asStateFlow()
        
    // Keep track of the latest screenshot URI
    private var latestScreenshotUri: Uri? = null
    private var lastProcessedScreenshotUri: Uri? = null
    private var lastProcessedScreenshotTime: Long = 0L
    
    // Keep track of the current selected images
    private var currentSelectedImages: List<Bitmap> = emptyList()
    
    // Keep track of the current user input
    private var currentUserInput: String = ""

    // Observable state for the input field to persist across configuration changes
    private val _userInput = MutableStateFlow("")
    val userInput: StateFlow<String> = _userInput.asStateFlow()

    fun updateUserInput(text: String) {
        _userInput.value = text
        val context = getApplication<Application>().applicationContext
        UserInputPreferences.saveUserInput(context, text)
    }
    
    // Keep track of detected commands
    private val _detectedCommands = MutableStateFlow<List<Command>>(emptyList())
    val detectedCommands: StateFlow<List<Command>> = _detectedCommands.asStateFlow()
    
    // Keep track of command execution status
    private val _commandExecutionStatus = MutableStateFlow<String>("")
    val commandExecutionStatus: StateFlow<String> = _commandExecutionStatus.asStateFlow()
    
    // System message state
    private val _systemMessage = MutableStateFlow<String>("")
    val systemMessage: StateFlow<String> = _systemMessage.asStateFlow()

    private val _modelNameState = MutableStateFlow(this.modelName)
    val modelNameState: StateFlow<String> = _modelNameState.asStateFlow()
    
    // Chat history state
    private val _chatState = ChatState()
    val chatMessages: List<PhotoReasoningMessage>
        get() = _chatState.messages
    
    // Chat history state flow for UI updates
    private val _chatMessagesFlow = MutableStateFlow<List<PhotoReasoningMessage>>(emptyList())
    val chatMessagesFlow: StateFlow<List<PhotoReasoningMessage>> = _chatMessagesFlow.asStateFlow()
    
    // ImageLoader and ImageRequestBuilder for processing images
    private var imageLoader: ImageLoader? = null
    private var imageRequestBuilder: ImageRequest.Builder? = null
    
    // Chat instance for maintaining conversation context
    private var chat = generativeModel.startChat(
        history = emptyList()
    )
    
    // Maximum number of retry attempts for API calls
    private val MAX_RETRY_ATTEMPTS = 3
    private var currentReasoningJob: Job? = null
    private var commandProcessingJob: Job? = null
    private val stopExecutionFlag = AtomicBoolean(false)

    // Track how many commands have been executed incrementally during streaming
    // to avoid re-executing already-executed commands
    private var incrementalCommandCount = 0

    // Mistral rate limiting: track last request time to enforce 1-second minimum interval
    private var lastMistralRequestTimeMs = 0L
    private val MISTRAL_MIN_INTERVAL_MS = 1000L

    // Accumulated full text during streaming for incremental command parsing
    private var streamingAccumulatedText = StringBuilder()

    // Added for retry on quota exceeded
    private var currentRetryAttempt = 0
    private var currentScreenInfoForPrompt: String? = null
    private var currentImageUrisForChat: List<String>? = null

    private val sseJson = Json { ignoreUnknownKeys = true }

    /**
     * Liest einen OpenAI-kompatiblen SSE-Stream zeilenweise.
     * Ruft [onChunk] mit dem jeweils akkumulierten Text auf.
     * Gibt den vollständigen Text zurück.
     */
    private suspend fun streamOpenAIResponse(
        body: okhttp3.ResponseBody,
        onChunk: suspend (accumulatedText: String) -> Unit
    ): String {
        val accumulated = StringBuilder()
        val reader = body.charStream().buffered()
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: break
                if (l.startsWith("data: ")) {
                    val data = l.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    if (data.isEmpty()) continue
                    try {
                        val chunk = sseJson.decodeFromString<OpenAIStreamChunk>(data)
                        val delta = chunk.choices.firstOrNull()?.delta?.content
                        if (!delta.isNullOrEmpty()) {
                            accumulated.append(delta)
                            onChunk(accumulated.toString())
                        }
                    } catch (e: Exception) {
                        // Fehlerhafte Chunk überspringen
                    }
                }
            }
        } finally {
            reader.close()
        }
        return accumulated.toString()
    }

    private val aiResultStreamReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ScreenCaptureService.ACTION_AI_STREAM_UPDATE) {
                val chunk = intent.getStringExtra(ScreenCaptureService.EXTRA_AI_STREAM_CHUNK)
                if (chunk != null) {
                    updateAiMessage(chunk, isPending = true)
                    // Real-time command execution during streaming
                    streamingAccumulatedText.append(chunk)
                    processCommandsIncrementally(streamingAccumulatedText.toString())
                }
            }
        }
    }

    private val aiResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ScreenCaptureService.ACTION_AI_CALL_RESULT) {
                _showStopNotificationFlow.value = false // AI call finished one way or another
                val responseText = intent.getStringExtra(ScreenCaptureService.EXTRA_AI_RESPONSE_TEXT)
                val errorMessage = intent.getStringExtra(ScreenCaptureService.EXTRA_AI_ERROR_MESSAGE)

                if (responseText != null && errorMessage == null) {
                    Log.d(TAG, "AI Call Success via Broadcast: $responseText")
                    _uiState.value = PhotoReasoningUiState.Success(responseText)
                    finalizeAiMessage(responseText)
                    processCommands(responseText)
                    saveChatHistory(getApplication<Application>())
                } else if (errorMessage != null) {
                    Log.e(TAG, "AI Call Error via Broadcast: $errorMessage")
                    val receiverContext = context ?: getApplication<Application>()
                    _uiState.value = PhotoReasoningUiState.Error(errorMessage)
                    _commandExecutionStatus.value = "Error during AI generation: $errorMessage"
                    _chatState.replaceLastPendingMessage()

                    val apiKeyManager = ApiKeyManager.getInstance(receiverContext)
                    val isQuotaError = isQuotaExceededError(errorMessage)
                    val isHighDemand = isHighDemandError(errorMessage)
                    val currentModel = com.google.ai.sample.GenerativeAiViewModelFactory.getCurrentModel()
                    
                    // Point 14: Don't switch keys for high-demand 503 errors
                    if (isHighDemand) {
                        Log.d(TAG, "High demand error detected - not switching API keys")
                        _chatState.addMessage(
                            PhotoReasoningMessage(
                                text = "This model is currently experiencing high demand. Please try again later.",
                                participant = PhotoParticipant.ERROR
                            )
                        )
                        _chatMessagesFlow.value = chatMessages
                        saveChatHistory(getApplication<Application>())
                        return
                    }
                    
                    if (isQuotaError && currentRetryAttempt < MAX_RETRY_ATTEMPTS) {
                        val currentKey = apiKeyManager.getCurrentApiKey(currentModel.apiProvider)
                        if (currentKey != null) {
                            apiKeyManager.markKeyAsFailed(currentKey, currentModel.apiProvider)
                            if (newKey != null && newKey != currentKey) {
                                // Increment retry attempt
                                currentRetryAttempt++
                                // Remove the last user message (pending already removed)
                                val messages = _chatState.getAllMessages().toMutableList()
                                if (messages.isNotEmpty() && messages.last().participant == PhotoParticipant.USER) {
                                    messages.removeAt(messages.lastIndex)
                                    _chatState.setAllMessages(messages)
                                }
                                // Retry by calling performReasoning with stored parameters
                                performReasoning(
                                    currentUserInput,
                                    currentSelectedImages,
                                    currentScreenInfoForPrompt,
                                    currentImageUrisForChat
                                )
                                return // Exit without adding error message
                            }
                        }
                    }

                    // Normal error handling if not quota or max retries reached
                    _chatState.addMessage(
                        PhotoReasoningMessage(
                            text = errorMessage,
                            participant = PhotoParticipant.ERROR
                        )
                    )
                    _chatMessagesFlow.value = chatMessages
                    saveChatHistory(getApplication<Application>())
                }
                // Reset pending AI message if any (assuming updateAiMessage or error handling does this)
            }
        }
    }

    init {
        // Initialize model if it's the offline one and already downloaded
        val currentModel = com.google.ai.sample.GenerativeAiViewModelFactory.getCurrentModel()
        val context = getApplication<Application>().applicationContext
        if (currentModel == ModelOption.GEMMA_3N_E4B_IT) {
            if (ModelDownloadManager.isModelDownloaded(context)) {
                // Point 7 & 16: Initialize model asynchronously to not block UI
                viewModelScope.launch(Dispatchers.IO) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = PhotoReasoningUiState.Loading
                    }
                    _isInitializingOfflineModelFlow.value = true
                    val error = initializeOfflineModel(context)
                    _isInitializingOfflineModelFlow.value = false
                    withContext(Dispatchers.Main) {
                        if (error != null) {
                            _uiState.value = PhotoReasoningUiState.Error(error)
                        } else {
                            _uiState.value = PhotoReasoningUiState.Success("Model initialized.")
                        }
                        refreshStopButtonState()
                    }
                }
            }
        }

        // Register receivers after initialization block to ensure properties are initialized
        val filter = IntentFilter(ScreenCaptureService.ACTION_AI_CALL_RESULT)
        LocalBroadcastManager.getInstance(context).registerReceiver(aiResultReceiver, filter)
        Log.d(TAG, "AIResultReceiver registered with LocalBroadcastManager.")

        val streamFilter = IntentFilter(ScreenCaptureService.ACTION_AI_STREAM_UPDATE)
        LocalBroadcastManager.getInstance(context).registerReceiver(aiResultStreamReceiver, streamFilter)
        Log.d(TAG, "AIResultStreamReceiver registered with LocalBroadcastManager.")
    }

    /**
     * Initialize the offline model. Returns null on success, or an error message on failure.
     */
    private fun initializeOfflineModel(context: Context): String? {
        try {
            if (llmInference == null) {
                val modelFile = ModelDownloadManager.getModelFile(context)
                if (modelFile != null && modelFile.exists()) {
                    // Load backend preference
                    GenerativeAiViewModelFactory.loadBackendPreference(context)
                    val backend = GenerativeAiViewModelFactory.getBackend()
                    
                    val optionsBuilder = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(4096)
                    
                    // Set preferred backend (CPU or GPU)
                    if (backend == InferenceBackend.GPU) {
                        optionsBuilder.setPreferredBackend(LlmInference.Backend.GPU)
                        Log.d(TAG, "Offline model: using GPU backend")
                    } else {
                        optionsBuilder.setPreferredBackend(LlmInference.Backend.CPU)
                        Log.d(TAG, "Offline model: using CPU backend")
                    }
                    
                    llmInference = LlmInference.createFromOptions(context, optionsBuilder.build())
                    Log.d(TAG, "Offline model initialized with backend=$backend")
                    return null // Success
                }
            }
            return null // Already initialized or no model file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize offline model", e)
            val msg = e.message ?: e.toString()
            return if (msg.contains("memory", ignoreCase = true) || msg.contains("RAM", ignoreCase = true) || msg.contains("OOM", ignoreCase = true) || msg.contains("alloc", ignoreCase = true) || msg.contains("out of", ignoreCase = true)) {
                "Not enough RAM to load the model on GPU. Try switching to CPU."
            } else {
                "Offline model could not be initialized: $msg"
            }
        }
    }
    
    fun reinitializeOfflineModel(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Point 3: Properly close existing instance and free GPU resources
                try {
                    llmInference?.close()
                    Log.d(TAG, "LlmInference closed for reinit")
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing existing LlmInference for reinit", e)
                }
                llmInference = null
                
                // Force garbage collection and wait for GPU resources to be freed
                System.gc()
                delay(500)
                
                // Re-initialize with new settings
                val initError = initializeOfflineModel(context)
                
                withContext(Dispatchers.Main) {
                    if (initError != null) {
                        Log.e(TAG, "Failed to reinitialize offline model: $initError")
                        _uiState.value = PhotoReasoningUiState.Error(initError)
                    } else {
                        refreshStopButtonState()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reinitialize offline model", e)
            } finally {
                _isInitializingOfflineModelFlow.value = false
                refreshStopButtonState()
            }
        }
    }

    private fun isGenerationRunning(): Boolean {
        val lastMessage = _chatState.getAllMessages().lastOrNull()
        val hasPendingModelMessage =
            lastMessage?.participant == PhotoParticipant.MODEL && lastMessage.isPending
        val hasActiveJob =
            currentReasoningJob?.isActive == true || commandProcessingJob?.isActive == true
        return hasPendingModelMessage || hasActiveJob
    }

    private fun isOfflineGpuModelLoaded(): Boolean {
        return com.google.ai.sample.GenerativeAiViewModelFactory.getCurrentModel() == ModelOption.GEMMA_3N_E4B_IT &&
            com.google.ai.sample.GenerativeAiViewModelFactory.getBackend() == InferenceBackend.GPU &&
            llmInference != null
    }

    private fun refreshStopButtonState() {
        _isGenerationRunningFlow.value = isGenerationRunning()
        _isOfflineGpuModelLoadedFlow.value = isOfflineGpuModelLoaded()
    }

    fun closeOfflineModel() {
        try {
            llmInference?.close()
            llmInference = null
            System.gc()
            refreshStopButtonState()
            Log.d(TAG, "Offline model explicitly closed to free RAM")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing offline model", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        liveApiManager?.close()
        val context = getApplication<Application>().applicationContext
        LocalBroadcastManager.getInstance(context).unregisterReceiver(aiResultReceiver)
        Log.d(TAG, "AIResultReceiver unregistered with LocalBroadcastManager.")
        LocalBroadcastManager.getInstance(context).unregisterReceiver(aiResultStreamReceiver)
        Log.d(TAG, "AIResultStreamReceiver unregistered with LocalBroadcastManager.")

        try {
            // Point 3: Properly close LlmInference to free GPU/RAM
            llmInference?.close()
            Log.d(TAG, "LlmInference closed in onCleared")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing LlmInference", e)
        }
        llmInference = null
        System.gc() // Help free GPU resources
        
        // WebRTC cleanup
        webRTCSender?.stop()
        signalingClient?.disconnect()
    }

    private fun createChatWithSystemMessage(context: Context? = null): Chat {
        val ctx = context ?: getApplication<Application>()
        val history = mutableListOf<Content>()
        if (_systemMessage.value.isNotBlank()) {
            history.add(content(role = "user") { text(_systemMessage.value) })
        }
        ctx?.let {
            val formattedDbEntries = formatDatabaseEntriesAsText(it)
            if (formattedDbEntries.isNotBlank()) {
                history.add(content(role = "user") { text(formattedDbEntries) })
            }
        }
        return generativeModel.startChat(history = history)
    }

    private fun ensureInitialized(context: Context?) {
        if (!_isInitialized.value && context != null) {
            loadSystemMessage(context)
        }
    }

    private fun performReasoning(
        userInput: String,
        selectedImages: List<Bitmap>,
        screenInfoForPrompt: String? = null,
        imageUrisForChat: List<String>? = null
    ) {
    // Get context for rebuildChatHistory
    val context = getApplication<Application>().applicationContext

    // Update the generative model with the current API key if retrying
    if (currentRetryAttempt > 0) {
        val apiKeyManager = ApiKeyManager.getInstance(context)
        val currentModel = com.google.ai.sample.GenerativeAiViewModelFactory.getCurrentModel()
        val currentKey = apiKeyManager.getCurrentApiKey(currentModel.apiProvider)
        if (currentKey != null) {
            generativeModel = GenerativeModel(
                modelName = modelName,
                apiKey = currentKey
            )
            // Recreate chat with new model
            chat = createChatWithSystemMessage(context)
        }
    }

    // Rebuild chat history from _chatState to ensure all messages are included
    if (context != null) {
        rebuildChatHistory(context)
    }

        if (chat.history.isEmpty() && _systemMessage.value.isNotBlank()) {
            Log.w(TAG, "performReasoning - Chat history is empty but system message exists. Recreating chat instance.")
            chat = createChatWithSystemMessage(context)
        }
        Log.d(TAG, "performReasoning() called. User input: '$userInput', Image count: ${selectedImages.size}, ScreenInfo: ${screenInfoForPrompt != null}, ImageUris: ${imageUrisForChat != null}")
        _uiState.value = PhotoReasoningUiState.Loading
        Log.d(TAG, "Setting _showStopNotificationFlow to true")
        _showStopNotificationFlow.value = true
        Log.d(TAG, "_showStopNotificationFlow value is now: ${_showStopNotificationFlow.value}")
        stopExecutionFlag.set(false)

        val combinedPromptTextBuilder = StringBuilder(userInput)
        if (screenInfoForPrompt != null && screenInfoForPrompt.isNotBlank()) { // Added isNotBlank check
            combinedPromptTextBuilder.append("\n\n$screenInfoForPrompt")
        }
        val aiPromptText = combinedPromptTextBuilder.toString()

        val prompt = "FOLLOW THE INSTRUCTIONS STRICTLY: $aiPromptText"

        // Store the current user input and selected images
        currentUserInput = aiPromptText // Store the full prompt including screen context for retry
        currentSelectedImages = selectedImages
        currentScreenInfoForPrompt = screenInfoForPrompt
        currentImageUrisForChat = imageUrisForChat

        // Clear previous commands and reset incremental tracking
        _detectedCommands.value = emptyList()
        _commandExecutionStatus.value = ""
        incrementalCommandCount = 0
        streamingAccumulatedText.clear()
        CommandParser.clearBuffer()

        // Add user message to chat history
        val userMessage = PhotoReasoningMessage(
            text = aiPromptText, // Use the combined text
            participant = PhotoParticipant.USER,
            imageUris = if (com.google.ai.sample.GenerativeAiViewModelFactory.getCurrentModel().supportsScreenshot) (imageUrisForChat ?: emptyList()) else emptyList(),
            isPending = false
        )
        Log.d(TAG, "performReasoning: Adding user message to _chatState. Text: \"${userMessage.text.take(100)}...\", Images: ${userMessage.imageUris.size}")
        val messages = _chatState.getAllMessages().toMutableList()
        messages.add(userMessage)
        Log.d(TAG, "performReasoning: _chatState now has ${messages.size} messages.")

        // Add AI message with pending status
        val pendingAiMessage = PhotoReasoningMessage(
            text = "",
            participant = PhotoParticipant.MODEL,
            isPending = true
        )
        messages.add(pendingAiMessage)
        _chatState.setAllMessages(messages)
        _chatMessagesFlow.value = _chatState.getAllMessages()

        currentReasoningJob?.cancel() // Cancel any previous reasoning job
        currentReasoningJob = PhotoReasoningApplication.applicationScope.launch(Dispatchers.IO) {
            var shouldContinueProcessing = true
            // Create content with the current images and prompt
            val inputContent = content {
                // Ensure line for original request: 136
                if (currentReasoningJob?.isActive != true) {
                    shouldContinueProcessing = false
                    // No return here
                }
                val currentModel = com.google.ai.sample.GenerativeAiViewModelFactory.getCurrentModel()
                if (shouldContinueProcessing && currentModel.supportsScreenshot) { // Check flag and support before proceeding
                    for (bitmap in selectedImages) {
                        // Ensure line for original request: 138
                        if (currentReasoningJob?.isActive != true) {
                            shouldContinueProcessing = false
                            break // Break from the for loop
                        }
                        if (!shouldContinueProcessing) break // Check flag again in case it was set by the outer check
                        image(bitmap)
                    }
                }
                if (shouldContinueProcessing) { // Check flag before proceeding
                    // Ensure line for original request: 141
                    if (currentReasoningJob?.isActive != true) {
                        shouldContinueProcessing = false
                        // No return here
                    }
                }
                if (shouldContinueProcessing) { // Check flag before proceeding
                    text(prompt)
                }
            }

            if (!shouldContinueProcessing) {
                // If processing should not continue, we might need to update UI state
                // For now, the existing check below should handle it.
                // If specific UI updates are needed here, they can be added.
                return@launch
            }

            if (currentReasoningJob?.isActive != true) return@launch // Check for cancellation outside content block
            sendMessageWithRetry(inputContent, 0)
            refreshStopButtonState()
        }
        refreshStopButtonState()
    }

    fun reason(
        userInput: String,
        selectedImages: List<Bitmap>,
        screenInfoForPrompt: String? = null,
        imageUrisForChat: List<String>? = null
    ) {
        val currentModel = com.google.ai.sample.GenerativeAiViewModelFactory.getCurrentModel()

    // Task 12: Clear any stale error state from previous model interactions
    if (_uiState.value is PhotoReasoningUiState.Error) {
        _uiState.value = PhotoReasoningUiState.Initial
    }

        // Check for Human Expert model
        if (currentModel == ModelOption.HUMAN_EXPERT) {
             // If we already have a specialized session running, maybe just send the text?
             // For now, we assume the user hits "Send" to trigger the connection + task post.
             
             // Initial task post message
             val userMessage = PhotoReasoningMessage(
                 text = userInput,
                 participant = PhotoParticipant.USER,
                 imageUris = if (currentModel.supportsScreenshot) (imageUrisForChat ?: emptyList()) else emptyList(),
                 isPending = false
             )
             _chatState.addMessage(userMessage)
             
             _uiState.value = PhotoReasoningUiState.Loading
             
             // We need to ensure we have MediaProjection permission.
             // The UI (PhotoReasoningScreen) calls requestMediaProjectionPermission before calling reason()
             // if permission is missing. So here we should ideally rely on onMediaProjectionPermissionGranted
             // having been called or already having the intent.
             
             // But valid intent handling happens in onMediaProjectionPermissionGranted.
             // If reason() is called, it means we likely have permission or it was just granted.
             
             // Check if we are already connected?
             if (signalingClient == null) {
                 startHumanExpertSession(userInput)
             } else {
                 // Already connected, just post the new task text or send via DataChannel if paired
                 postTaskToHumanExpert(userInput)
             }
             return
        }

        // Check for offline model (Gemma)
        if (currentModel == ModelOption.GEMMA_3N_E4B_IT) {
            val context = getApplication<Application>().applicationContext

            if (!ModelDownloadManager.isModelDownloaded(context)) {
                _uiState.value = PhotoReasoningUiState.Error("Model not downloaded.")
                return
            }

            // Ensure system message and DB are loaded
            ensureInitialized(context)

            // Reset incremental command tracking for this new reasoning
            incrementalCommandCount = 0
            streamingAccumulatedText.clear()
            CommandParser.clearBuffer()
            _detectedCommands.value = emptyList()
            _commandExecutionStatus.value = ""

            // Build the combined prompt with system message + DB entries + user input
            val systemMsg = _systemMessage.value
            val dbEntries = formatDatabaseEntriesAsText(context)
            val combinedPromptParts = mutableListOf<String>()
            if (systemMsg.isNotBlank()) {
                combinedPromptParts.add(systemMsg)
            }
            if (dbEntries.isNotBlank()) {
                combinedPromptParts.add(dbEntries)
            }
            if (screenInfoForPrompt != null && screenInfoForPrompt.isNotBlank()) {
                combinedPromptParts.add(screenInfoForPrompt)
            }
            combinedPromptParts.add(userInput)
            val fullPrompt = combinedPromptParts.joinToString("\n\n")

            // Add user message to chat state
            val userMessageText = if (screenInfoForPrompt != null && screenInfoForPrompt.isNotBlank()) {
                "$userInput\n\n$screenInfoForPrompt"
            } else {
                userInput
            }
            val userMessage = PhotoReasoningMessage(
                text = userMessageText,
                participant = PhotoParticipant.USER,
                imageUris = if (currentModel.supportsScreenshot) (imageUrisForChat ?: emptyList()) else emptyList(),
                isPending = false
            )
            _chatState.addMessage(userMessage)

            // Add pending AI message
            val pendingAiMessage = PhotoReasoningMessage(
                text = "",
                participant = PhotoParticipant.MODEL,
                isPending = true
            )
            _chatState.addMessage(pendingAiMessage)
            _chatMessagesFlow.value = _chatState.getAllMessages()

            _uiState.value = PhotoReasoningUiState.Loading
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // Initialize model if needed
                    var initError: String? = null
                    if (llmInference == null) {
                        withContext(Dispatchers.Main) {
                            replaceAiMessageText("Initializing offline model...", isPending = true)
                        }
                        // Use Default dispatcher for CPU-intensive model loading
                        _isInitializingOfflineModelFlow.value = true
                        refreshStopButtonState()
                        initError = withContext(Dispatchers.Default) {
                            initializeOfflineModel(context)
                        }
                        _isInitializingOfflineModelFlow.value = false
                    }

                    if (llmInference == null) {
                        val errorMsg = initError ?: "Offline model could not be initialized."
                        withContext(Dispatchers.Main) {
                            _uiState.value = PhotoReasoningUiState.Error(errorMsg)
                            _chatState.replaceLastPendingMessage()
                            _chatState.addMessage(
                                PhotoReasoningMessage(
                                    text = "Error: $errorMsg",
                                    participant = PhotoParticipant.ERROR
                                )
                            )
                            _chatMessagesFlow.value = _chatState.getAllMessages()
                            refreshStopButtonState()
                        }
                        return@launch
                    }

                    refreshStopButtonState()

                    Log.d(TAG, "Sending streaming prompt to offline model (length: ${fullPrompt.length})")

                    // Use generateResponseAsync with ProgressListener for streaming
                    val sb = StringBuilder()
                    val finalResponse = llmInference!!.generateResponseAsync(fullPrompt) { partialResult, done ->
                        val token = partialResult ?: ""
                        sb.append(token)
                        viewModelScope.launch(Dispatchers.Main) {
                            if (!done) {
                                replaceAiMessageText(sb.toString(), isPending = true)
                                // Real-time command execution during offline streaming
                                processCommandsIncrementally(sb.toString())
                            }
                        }
                    }.get()

                    withContext(Dispatchers.Main) {
                        _uiState.value = PhotoReasoningUiState.Success(finalResponse)
                        finalizeAiMessage(finalResponse)
                        processCommands(finalResponse)
                        saveChatHistory(context)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Offline inference failed", e)
                    withContext(Dispatchers.Main) {
                        saveChatHistory(context)
                        refreshStopButtonState()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Offline inference failed", e)
                    withContext(Dispatchers.Main) {
                        _uiState.value = PhotoReasoningUiState.Error("Offline inference failed: ${e.message}")
                        _chatState.replaceLastPendingMessage()
                        _chatState.addMessage(
                            PhotoReasoningMessage(
                                text = "Offline inference failed: ${e.message}",
                                participant = PhotoParticipant.ERROR
                            )
                        )
                        _chatMessagesFlow.value = _chatState.getAllMessages()
                        saveChatHistory(context)
                        refreshStopButtonState()
                    }
                } finally {
                    _isInitializingOfflineModelFlow.value = false
                    refreshStopButtonState()
                }
            }
            return
        }

        if (currentModel.apiProvider == ApiProvider.MISTRAL) {
            reasonWithMistral(userInput, selectedImages, screenInfoForPrompt, imageUrisForChat)
            return
        }

        if (currentModel.apiProvider == ApiProvider.PUTER) {
            reasonWithPuter(userInput, selectedImages, screenInfoForPrompt, imageUrisForChat)
            return
        }

        if (currentModel.apiProvider == ApiProvider.CEREBRAS) {
            reasonWithCerebras(userInput, selectedImages, screenInfoForPrompt)
            return
        }

        if (isLiveMode) {
            // For live mode, handle differently
            viewModelScope.launch {
                try {
                    // Ensure connection
                    if (liveApiManager?.connectionState?.value != LiveApiManager.ConnectionState.CONNECTED) {
                        liveApiManager?.connect()
                        // Wait for connection
                        delay(2000)
                    }

                    // Add user message to chat history
                    val combinedPromptText = if (screenInfoForPrompt != null && screenInfoForPrompt.isNotBlank()) {
                        "$userInput\n\n$screenInfoForPrompt"
                    } else {
                        userInput
                    }

                    val userMessage = PhotoReasoningMessage(
                        text = combinedPromptText,
                        participant = PhotoParticipant.USER,
                        imageUris = if (currentModel.supportsScreenshot) (imageUrisForChat ?: emptyList()) else emptyList(),
                        isPending = false
                    )
                    _chatState.addMessage(userMessage)
                    _chatMessagesFlow.value = _chatState.getAllMessages()

                    // Add pending AI message
                    val pendingAiMessage = PhotoReasoningMessage(
                        text = "",
                        participant = PhotoParticipant.MODEL,
                        isPending = true
                    )
                    _chatState.addMessage(pendingAiMessage)
                    _chatMessagesFlow.value = _chatState.getAllMessages()

                    _uiState.value = PhotoReasoningUiState.Loading

                    // Convert images to base64
                    val imageDataList = selectedImages.map { it.toBase64() }

                    // Send message with images
                    val prompt = "FOLLOW THE INSTRUCTIONS STRICTLY: $combinedPromptText"
                    liveApiManager?.sendMessage(prompt, imageDataList.ifEmpty { null })

                } catch (e: Exception) {
                    Log.e(TAG, "Error in live mode reasoning", e)
                    _uiState.value = PhotoReasoningUiState.Error(e.message ?: "Unknown error")
                    _chatState.replaceLastPendingMessage()
                    _chatState.addMessage(
                        PhotoReasoningMessage(
                            text = "Error: ${e.message}",
                            participant = PhotoParticipant.ERROR
                        )
                    )
                    _chatMessagesFlow.value = _chatState.getAllMessages()
                    saveChatHistory(getApplication<Application>())
                }
            }
        } else {
            // Regular mode - existing code
            val context = MainActivity.getInstance()?.applicationContext
            if (context == null) {
                Log.e(TAG, "Context not available, cannot proceed with reasoning")
                _uiState.value = PhotoReasoningUiState.Error("Application not ready")
                return
            }
            
            // Task 3: Sync model before API call
            val apiKeyManager = ApiKeyManager.getInstance(context)
            val currentKey = apiKeyManager.getCurrentApiKey(currentModel.apiProvider)
            if (currentKey != null && currentModel != ModelOption.GEMMA_3N_E4B_IT && currentModel != ModelOption.HUMAN_EXPERT) {
                val genSettings = com.google.ai.sample.util.GenerationSettingsPreferences.loadSettings(context, currentModel.modelName)
                val config = com.google.ai.client.generativeai.type.generationConfig {
                    temperature = genSettings.temperature
                    topP = genSettings.topP
                    topK = genSettings.topK
                }
                generativeModel = GenerativeModel(
                    modelName = currentModel.modelName,
                    apiKey = currentKey,
                    generationConfig = config
                )
                _modelNameState.value = currentModel.modelName
            }
            
            ensureInitialized(context)
            currentRetryAttempt = 0
            performReasoning(userInput, selectedImages, screenInfoForPrompt, imageUrisForChat)
        }
    }

    private fun reasonWithCerebras(
        userInput: String,
        selectedImages: List<Bitmap>,
        screenInfoForPrompt: String? = null
    ) {
        _uiState.value = PhotoReasoningUiState.Loading
        val context = getApplication<Application>().applicationContext
        val apiKeyManager = ApiKeyManager.getInstance(context)

        val initialApiKey = apiKeyManager.getCurrentApiKey(ApiProvider.CEREBRAS)
        if (initialApiKey.isNullOrEmpty()) {
            _uiState.value = PhotoReasoningUiState.Error("Cerebras API key not found.")
            return
        }

        val combinedPromptText = (userInput + "\n\n" + (screenInfoForPrompt ?: "")).trim()

        _chatState.addMessage(PhotoReasoningMessage(text = combinedPromptText, participant = PhotoParticipant.USER, isPending = false))
        _chatState.addMessage(PhotoReasoningMessage(text = "", participant = PhotoParticipant.MODEL, isPending = true))
        _chatMessagesFlow.value = _chatState.getAllMessages()

        incrementalCommandCount = 0
        streamingAccumulatedText.clear()
        CommandParser.clearBuffer()
        _detectedCommands.value = emptyList()
        _commandExecutionStatus.value = ""

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apiMessages = mutableListOf<CerebrasMessage>()
                if (_systemMessage.value.isNotBlank())
                    apiMessages.add(CerebrasMessage(role = "user", content = _systemMessage.value))
                val formattedDbEntries = formatDatabaseEntriesAsText(context)
                if (formattedDbEntries.isNotBlank())
                    apiMessages.add(CerebrasMessage(role = "user", content = formattedDbEntries))
                _chatState.getAllMessages()
                    .filter { !it.isPending && it.participant != PhotoParticipant.ERROR }
                    .forEach { message ->
                        val role = if (message.participant == PhotoParticipant.USER) "user" else "assistant"
                        apiMessages.add(CerebrasMessage(role = role, content = message.text))
                    }

                // CerebrasRequest braucht stream-Feld — inline als JSON-String um Datenklasse nicht zu ändern
                val streamingBody = """{"model":"$modelName","messages":${Json.encodeToString(apiMessages)},"max_completion_tokens":1024,"temperature":0.2,"top_p":1.0,"stream":true}"""
                val mediaType = "application/json".toMediaType()
                val client = OkHttpClient()

                fun buildRequest(key: String) = Request.Builder()
                    .url("https://api.cerebras.ai/v1/chat/completions")
                    .post(streamingBody.toRequestBody(mediaType))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer $key")
                    .build()

                var currentKey = initialApiKey
                var response = client.newCall(buildRequest(currentKey)).execute()

                if (response.code == 429) {
                    response.close()
                    apiKeyManager.markKeyAsFailed(currentKey, ApiProvider.CEREBRAS)
                    val nextKey = apiKeyManager.switchToNextAvailableKey(ApiProvider.CEREBRAS)
                    if (nextKey != null && nextKey != currentKey) {
                        currentKey = nextKey
                        response = client.newCall(buildRequest(currentKey)).execute()
                    } else {
                        throw IOException("Cerebras rate limit reached. Please add another API key.")
                    }
                }

                if (!response.isSuccessful) {
                    val errBody = response.body?.string()
                    response.close()
                    throw IOException("Unexpected code ${response.code} - $errBody")
                }

                val body = response.body ?: throw IOException("Empty response body from Cerebras")
                val aiResponseText = streamOpenAIResponse(body) { accText ->
                    withContext(Dispatchers.Main) {
                        replaceAiMessageText(accText, isPending = true)
                        processCommandsIncrementally(accText)
                    }
                }
                response.close()

                withContext(Dispatchers.Main) {
                    _uiState.value = PhotoReasoningUiState.Success(aiResponseText)
                    finalizeAiMessage(aiResponseText)
                    processCommands(aiResponseText)
                    saveChatHistory(context)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Cerebras API call failed", e)
                    _uiState.value = PhotoReasoningUiState.Error(e.message ?: "Unknown error")
                    _chatState.replaceLastPendingMessage()
                    _chatState.addMessage(PhotoReasoningMessage(text = "Error: ${e.message}", participant = PhotoParticipant.ERROR))
                    _chatMessagesFlow.value = _chatState.getAllMessages()
                    saveChatHistory(context)
                }
            }
        }
    }
    
private fun reasonWithMistral(
    userInput: String,
    selectedImages: List<Bitmap>,
    screenInfoForPrompt: String? = null,
    imageUrisForChat: List<String>? = null
) {
    _uiState.value = PhotoReasoningUiState.Loading
    val context = getApplication<Application>().applicationContext
    val apiKeyManager = ApiKeyManager.getInstance(context)

    val initialApiKey = apiKeyManager.getCurrentApiKey(ApiProvider.MISTRAL)
    if (initialApiKey.isNullOrEmpty()) {
        _uiState.value = PhotoReasoningUiState.Error("Mistral API key not found.")
        return
    }

    val combinedPromptText = (userInput + "\n\n" + (screenInfoForPrompt ?: "")).trim()

    _chatState.addMessage(PhotoReasoningMessage(
        text = combinedPromptText,
        participant = PhotoParticipant.USER,
        imageUris = if (com.google.ai.sample.GenerativeAiViewModelFactory.getCurrentModel().supportsScreenshot)
            (imageUrisForChat ?: emptyList()) else emptyList(),
        isPending = false
    ))
    _chatState.addMessage(PhotoReasoningMessage(text = "", participant = PhotoParticipant.MODEL, isPending = true))
    _chatMessagesFlow.value = _chatState.getAllMessages()

    incrementalCommandCount = 0
    streamingAccumulatedText.clear()
    CommandParser.clearBuffer()
    _detectedCommands.value = emptyList()
    _commandExecutionStatus.value = ""

    viewModelScope.launch(Dispatchers.IO) {
        // Rate limiting: nur die verbleibende Zeit warten
        val elapsed = System.currentTimeMillis() - lastMistralRequestTimeMs
        if (lastMistralRequestTimeMs > 0 && elapsed < MISTRAL_MIN_INTERVAL_MS) {
            delay(MISTRAL_MIN_INTERVAL_MS - elapsed)
        }

        try {
            val currentModel = com.google.ai.sample.GenerativeAiViewModelFactory.getCurrentModel()
            val genSettings = com.google.ai.sample.util.GenerationSettingsPreferences.loadSettings(context, currentModel.modelName)

            // Nachrichten aufbauen
            val apiMessages = mutableListOf<MistralMessage>()
            val systemContent = mutableListOf<MistralContent>()
            if (_systemMessage.value.isNotBlank())
                systemContent.add(MistralTextContent(text = _systemMessage.value))
            val formattedDbEntries = formatDatabaseEntriesAsText(context)
            if (formattedDbEntries.isNotBlank())
                systemContent.add(MistralTextContent(text = "Additional context from database:\n$formattedDbEntries"))
            if (systemContent.isNotEmpty())
                apiMessages.add(MistralMessage(role = "system", content = systemContent))

            _chatState.getAllMessages()
                .filter { !it.isPending && it.participant != PhotoParticipant.ERROR }
                .forEach { message ->
                    val role = if (message.participant == PhotoParticipant.USER) "user" else "assistant"
                    val contentParts = mutableListOf<MistralContent>()
                    if (message.text.isNotBlank()) contentParts.add(MistralTextContent(text = message.text))
                    if (contentParts.isNotEmpty()) apiMessages.add(MistralMessage(role = role, content = contentParts))
                }

            if (selectedImages.isNotEmpty() && apiMessages.isNotEmpty() && currentModel.supportsScreenshot) {
                val lastUserMsg = apiMessages.last()
                if (lastUserMsg.role == "user") {
                    val updatedContent = lastUserMsg.content.toMutableList()
                    for (bitmap in selectedImages)
                        updatedContent.add(MistralImageContent(imageUrl = MistralImageUrl(url = "data:image/jpeg;base64,${bitmap.toBase64()}")))
                    apiMessages[apiMessages.lastIndex] = lastUserMsg.copy(content = updatedContent)
                }
            }

            val jsonSerializer = Json {
                serializersModule = SerializersModule {
                    polymorphic(MistralContent::class) {
                        subclass(MistralTextContent::class)
                        subclass(MistralImageContent::class)
                    }
                }
                ignoreUnknownKeys = true
            }
            val requestBody = MistralRequest(
                model = currentModel.modelName,
                messages = apiMessages,
                temperature = genSettings.temperature.toDouble().coerceAtLeast(0.01),
                top_p = genSettings.topP.toDouble().coerceAtLeast(0.01),
                max_tokens = 4096,
                stream = true
            )
            val jsonBody = jsonSerializer.encodeToString(MistralRequest.serializer(), requestBody)
            val mediaType = "application/json".toMediaType()
            val client = OkHttpClient()

            fun buildRequest(key: String) = Request.Builder()
                .url("https://api.mistral.ai/v1/chat/completions")
                .post(jsonBody.toRequestBody(mediaType))
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $key")
                .build()

            var currentKey = initialApiKey
            var response = client.newCall(buildRequest(currentKey)).execute()
            lastMistralRequestTimeMs = System.currentTimeMillis()

            if (response.code == 429) {
                response.close()
                apiKeyManager.markKeyAsFailed(currentKey, ApiProvider.MISTRAL)
                val nextKey = apiKeyManager.switchToNextAvailableKey(ApiProvider.MISTRAL)
                if (nextKey != null && nextKey != currentKey) {
                    currentKey = nextKey
                    val elapsed2 = System.currentTimeMillis() - lastMistralRequestTimeMs
                    if (elapsed2 < MISTRAL_MIN_INTERVAL_MS) delay(MISTRAL_MIN_INTERVAL_MS - elapsed2)
                    response = client.newCall(buildRequest(currentKey)).execute()
                    lastMistralRequestTimeMs = System.currentTimeMillis()
                } else {
                    throw IOException("Mistral rate limit reached. Please add another API key.")
                }
            }

            if (!response.isSuccessful) {
                val errBody = response.body?.string()
                response.close()
                throw IOException("Mistral Error ${response.code}: $errBody")
            }

            val body = response.body ?: throw IOException("Empty response body from Mistral")
            val aiResponseText = streamOpenAIResponse(body) { accText ->
                withContext(Dispatchers.Main) {
                    replaceAiMessageText(accText, isPending = true)
                    processCommandsIncrementally(accText)
                }
            }
            response.close()

            withContext(Dispatchers.Main) {
                _uiState.value = PhotoReasoningUiState.Success(aiResponseText)
                finalizeAiMessage(aiResponseText)
                processCommands(aiResponseText)
                saveChatHistory(context)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Log.e(TAG, "Mistral API call failed", e)
                _uiState.value = PhotoReasoningUiState.Error(e.message ?: "Unknown error")
                _chatState.replaceLastPendingMessage()
                _chatState.addMessage(PhotoReasoningMessage(text = "Error: ${e.message}", participant = PhotoParticipant.ERROR))
                _chatMessagesFlow.value = _chatState.getAllMessages()
                saveChatHistory(context)
            }
        }
    }
}

    private fun reasonWithPuter(
        userInput: String,
        selectedImages: List<Bitmap>,
        screenInfoForPrompt: String?,
        imageUrisForChat: List<String>?
    ) {
        val apiKey = com.google.ai.sample.MainActivity.getInstance()?.getCurrentApiKey(ApiProvider.PUTER) ?: ""
        if (apiKey.isEmpty()) {
            _uiState.value = PhotoReasoningUiState.Error("Puter Authentication Token (API Key) is missing")
            return
        }

        val context = getApplication<Application>().applicationContext
        val currentModel = com.google.ai.sample.GenerativeAiViewModelFactory.getCurrentModel()
        val genSettings = com.google.ai.sample.util.GenerationSettingsPreferences.loadSettings(context, currentModel.modelName)

        val userMessageText = if (!screenInfoForPrompt.isNullOrBlank()) {
            "$userInput\n\n$screenInfoForPrompt"
        } else {
            userInput
        }

        val userMessage = PhotoReasoningMessage(
            text = userMessageText,
            participant = PhotoParticipant.USER,
            imageUris = if (currentModel.supportsScreenshot) (imageUrisForChat ?: emptyList()) else emptyList(),
            isPending = false
        )
        _chatState.addMessage(userMessage)

        val pendingAiMessage = PhotoReasoningMessage(
            text = "",
            participant = PhotoParticipant.MODEL,
            isPending = true
        )
        _chatState.addMessage(pendingAiMessage)
        _chatMessagesFlow.value = _chatState.getAllMessages()

        _uiState.value = PhotoReasoningUiState.Loading

        // Reset tracking vars
        incrementalCommandCount = 0
        streamingAccumulatedText.clear()
        CommandParser.clearBuffer()
        _detectedCommands.value = emptyList()
        _commandExecutionStatus.value = ""

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apiMessages = mutableListOf<com.google.ai.sample.network.PuterMessage>()

                // Add System Message and DB Entries
                val systemContent = mutableListOf<com.google.ai.sample.network.PuterContent>()
                if (_systemMessage.value.isNotBlank()) {
                    systemContent.add(com.google.ai.sample.network.PuterTextContent(text = _systemMessage.value))
                }
                val formattedDbEntries = formatDatabaseEntriesAsText(context)
                if (formattedDbEntries.isNotBlank()) {
                    systemContent.add(com.google.ai.sample.network.PuterTextContent(text = "Additional context from database:\n$formattedDbEntries"))
                }
                if (systemContent.isNotEmpty()) {
                    apiMessages.add(com.google.ai.sample.network.PuterMessage(role = "system", content = systemContent))
                }

                // Add Chat History (exclude the last added user message)
                val allMessages = _chatState.getAllMessages()
                // exclude the last pending message and the last user message we just added
                val historyMessages = allMessages.filter { !it.isPending && it.participant != PhotoParticipant.ERROR }.dropLast(1)
                
                historyMessages.forEach { message ->
                    val role = if (message.participant == PhotoParticipant.USER) "user" else "assistant"
                    val contentParts = mutableListOf<com.google.ai.sample.network.PuterContent>()
                    if (message.text.isNotBlank()) {
                        contentParts.add(com.google.ai.sample.network.PuterTextContent(text = message.text))
                    }
                    if (contentParts.isNotEmpty()) {
                        apiMessages.add(com.google.ai.sample.network.PuterMessage(role = role, content = contentParts))
                    }
                }

                // Add Current User Request (Text + Images)
                val currentContentParts = mutableListOf<com.google.ai.sample.network.PuterContent>()
                if (userMessageText.isNotBlank()) {
                    currentContentParts.add(com.google.ai.sample.network.PuterTextContent(text = userMessageText))
                }
                if (currentModel.supportsScreenshot) {
                    for (bitmap in selectedImages) {
                        val base64Uri = com.google.ai.sample.network.PuterApiClient.bitmapToBase64DataUri(bitmap)
                        currentContentParts.add(com.google.ai.sample.network.PuterImageContent(image_url = com.google.ai.sample.network.PuterImageUrl(url = base64Uri)))
                    }
                }
                if (currentContentParts.isNotEmpty()) {
                    apiMessages.add(com.google.ai.sample.network.PuterMessage(role = "user", content = currentContentParts))
                }

                val requestBody = com.google.ai.sample.network.PuterRequest(
                    model = currentModel.modelName,
                    messages = apiMessages,
                    temperature = genSettings.temperature.toDouble(),
                    top_p = genSettings.topP.toDouble(),
                    max_tokens = 4096,
                    stream = true
                )

                val apiKeyManager = ApiKeyManager.getInstance(context)
                var currentPuterKey = apiKey
                val puterJson = com.google.ai.sample.network.PuterApiClient.jsonConfig
                val jsonBody = puterJson.encodeToString(com.google.ai.sample.network.PuterRequest.serializer(), requestBody)
                val mediaType = "application/json".toMediaType()

                fun buildPuterRequest(key: String) = okhttp3.Request.Builder()
                    .url("https://api.puter.com/puterai/openai/v1/chat/completions")
                    .post(jsonBody.toRequestBody(mediaType))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer $key")
                    .build()

                var httpResponse = com.google.ai.sample.network.PuterApiClient.client.newCall(buildPuterRequest(currentPuterKey)).execute()

                if (httpResponse.code == 429) {
                    httpResponse.close()
                    apiKeyManager.markKeyAsFailed(currentPuterKey, ApiProvider.PUTER)
                    val nextKey = apiKeyManager.switchToNextAvailableKey(ApiProvider.PUTER)
                    if (nextKey != null && nextKey != currentPuterKey) {
                        currentPuterKey = nextKey
                        httpResponse = com.google.ai.sample.network.PuterApiClient.client.newCall(buildPuterRequest(currentPuterKey)).execute()
                    } else {
                        throw java.io.IOException("Puter rate limit reached. Please add another API key.")
                    }
                }

                if (!httpResponse.isSuccessful) {
                    val errBody = httpResponse.body?.string()
                    httpResponse.close()
                    throw java.io.IOException("Puter Error ${httpResponse.code}: $errBody")
                }

                val body = httpResponse.body ?: throw java.io.IOException("Empty response from Puter")
                val aiResponseText = streamOpenAIResponse(body) { accText ->
                    withContext(Dispatchers.Main) {
                        replaceAiMessageText(accText, isPending = true)
                        processCommandsIncrementally(accText)
                    }
                }
                httpResponse.close()

                withContext(Dispatchers.Main) {
                    _uiState.value = PhotoReasoningUiState.Success(aiResponseText)
                    finalizeAiMessage(aiResponseText)
                    processCommands(aiResponseText)
                    saveChatHistory(context)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Puter API call failed", e)
                    _uiState.value = PhotoReasoningUiState.Error(e.message ?: "Unknown error")
                    _chatState.replaceLastPendingMessage()
                    _chatState.addMessage(
                        PhotoReasoningMessage(
                            text = "Error: ${e.message}",
                            participant = PhotoParticipant.ERROR
                        )
                    )
                    _chatMessagesFlow.value = _chatState.getAllMessages()
                    saveChatHistory(context)
                }
            }
        }
    }

    fun collectLiveApiMessages() {
        if (liveApiManager != null) {
            // Set system message and history when connecting
            viewModelScope.launch {
                val context = getApplication<Application>().applicationContext
                ensureInitialized(context)

                // Convert chat history to format for Live API
                    val historyPairs = mutableListOf<Pair<String, String>>()

                    // Add system message and DB entries as initial context
                    if (_systemMessage.value.isNotBlank()) {
                        historyPairs.add(Pair("user", _systemMessage.value))
                    }

                    val formattedDbEntries = formatDatabaseEntriesAsText(context)
                    if (formattedDbEntries.isNotBlank()) {
                        historyPairs.add(Pair("user", formattedDbEntries))
                    }

                    // Add chat history
                    val messages = _chatState.getAllMessages()
                    messages.forEach { msg ->
                        when (msg.participant) {
                            PhotoParticipant.USER -> {
                                if (msg.text.isNotBlank() && !msg.isPending) {
                                    historyPairs.add(Pair("user", msg.text))
                                }
                            }
                            PhotoParticipant.MODEL -> {
                                if (msg.text.isNotBlank() && !msg.isPending) {
                                    historyPairs.add(Pair("model", msg.text))
                                }
                            }
                            PhotoParticipant.ERROR -> {
                                // Skip error messages for history
                            }
                        }
                    }

                liveApiManager.setSystemMessageAndHistory(_systemMessage.value, historyPairs)
            }

            // Collect messages
            viewModelScope.launch {
                liveApiManager.messages.collect { message ->
                    if (message.isNotEmpty() && message != "Connected" && message != "Connected and ready") {
                        val messages = _chatState.getAllMessages().toMutableList()
                        val lastIndex = messages.indexOfLast {
                            it.participant == PhotoParticipant.MODEL && it.isPending
                        }

                        if (lastIndex != -1) {
                            messages[lastIndex] = messages[lastIndex].copy(
                                text = message,
                                isPending = true // Keep pending
                            )
                            _chatState.setAllMessages(messages)
                            _chatMessagesFlow.value = messages
                        }

                        _uiState.value = PhotoReasoningUiState.Success(message)
                    }
                }
            }

            // Collect turn complete signals
            viewModelScope.launch {
                liveApiManager.turnComplete.collect { isComplete ->
                    if (isComplete) {
                        Log.d(TAG, "Turn complete received, finalizing message")
                        val messages = _chatState.getAllMessages().toMutableList()
                        val lastIndex = messages.indexOfLast {
                            it.participant == PhotoParticipant.MODEL && it.isPending
                        }

                        if (lastIndex != -1) {
                            val finalMessage = messages[lastIndex].copy(isPending = false)
                            messages[lastIndex] = finalMessage
                            _chatState.setAllMessages(messages)
                            _chatMessagesFlow.value = messages

                            // Process commands
                            processCommands(finalMessage.text)

                            // Save chat history
                            saveChatHistory(getApplication<Application>())
                        }
                    }
                }
            }

            // Collect connection state
            viewModelScope.launch {
                liveApiManager.connectionState.collect { state ->
                    when (state) {
                        LiveApiManager.ConnectionState.ERROR -> {
                            _uiState.value = PhotoReasoningUiState.Error("Connection error")
                            _chatState.replaceLastPendingMessage()
                        }
                        LiveApiManager.ConnectionState.CONNECTED -> {
                            Log.d(TAG, "Live API connected")
                        }
                        LiveApiManager.ConnectionState.DISCONNECTED -> {
                            Log.d(TAG, "Live API disconnected")
                        }
                        LiveApiManager.ConnectionState.CONNECTING -> {
                            Log.d(TAG, "Live API connecting...")
                        }
                    }
                }
            }
        }
    }

    fun onStopClicked() {
        _showStopNotificationFlow.value = false

        val generationRunning = isGenerationRunning()

        // Kein aktiver Lauf: zweiter Klick => Modell entladen, keine Chat-Nachricht
        if (!generationRunning) {
            if (isOfflineGpuModelLoaded()) {
                closeOfflineModel()
                Log.d(TAG, "Stop clicked while idle: offline GPU model closed to free RAM")
            } else {
                refreshStopButtonState()
                Log.d(TAG, "Stop clicked while idle: nothing to stop and no offline GPU model loaded")
            }
            return
        }

        // Aktive Generierung: nur stoppen, Modell NICHT direkt schließen
        if (isLiveMode) {
            liveApiManager?.close()
        }

        stopExecutionFlag.set(true)
        currentReasoningJob?.cancel()
        commandProcessingJob?.cancel()
        // NEU:
        ScreenOperatorAccessibilityService.clearCommandQueue()

        val messages = _chatState.getAllMessages().toMutableList()
        val lastMessage = messages.lastOrNull()
        val statusMessage = "Operation stopped by user."

        if (lastMessage != null && lastMessage.participant == PhotoParticipant.MODEL && lastMessage.isPending) {
            messages.removeAt(messages.lastIndex)
            messages.add(
                PhotoReasoningMessage(
                    text = statusMessage,
                    participant = PhotoParticipant.MODEL,
                    isPending = false
                )
            )
        } else if (lastMessage != null && lastMessage.participant == PhotoParticipant.MODEL && !lastMessage.isPending) {
            messages[messages.lastIndex] =
                lastMessage.copy(text = lastMessage.text + "\n\n[Stopped by user]")
        } else {
            messages.add(
                PhotoReasoningMessage(
                    text = statusMessage,
                    participant = PhotoParticipant.MODEL,
                    isPending = false
                )
            )
        }

        _chatState.setAllMessages(messages)
        _chatMessagesFlow.value = _chatState.getAllMessages()
        _commandExecutionStatus.value = ""
        _detectedCommands.value = emptyList()
        Log.d(TAG, "Stop clicked, operations cancelled, command status cleared.")

        _uiState.value = PhotoReasoningUiState.Success("Operation stopped.")
        Log.d(TAG, "UI updated to Success state after stop.")

        refreshStopButtonState()
    }

    /**
     * Send a message to the AI with retry logic for 503 errors
     *
     * @param inputContent The content to send
     * @param retryCount The current retry count
     */
    // This method now delegates the AI call to ScreenCaptureService
    // to ensure it runs with foreground priority and avoids background network restrictions.
    private suspend fun sendMessageWithRetry(inputContent: Content, retryCount: Int) {
        Log.d(TAG, "sendMessageWithRetry: Delegating AI call to ScreenCaptureService.")

        val context = MainActivity.getInstance()?.applicationContext
        if (context == null) {
            Log.e(TAG, "sendMessageWithRetry: Context is null, cannot delegate AI call.")
            _uiState.value = PhotoReasoningUiState.Error("Application context not available for AI call.")
            _showStopNotificationFlow.value = false
            return
        }

        try {
            // Serialize Content and History.
            // This assumes Content and List<Content> are @Serializable or have custom serializers.
            // Add @file:UseSerializers(ContentSerializer::class, PartSerializer::class etc.) if needed at top of file
            // Or create DTOs. For this subtask, we'll assume direct serialization is possible.

            Log.d(TAG, "sendMessageWithRetry: Preparing to serialize chat.history. Current chat.history size: ${chat.history.size}")
            chat.history.forEachIndexed { index, content ->
                Log.d(TAG, "  ViewModel chat.history Content[$index]: role=${content.role}, parts=${content.parts.joinToString { part ->
                    when(part) {
                        is com.google.ai.client.generativeai.type.TextPart -> "Text(\"${part.text.take(50)}...\")"
                        is com.google.ai.client.generativeai.type.ImagePart -> "Image"
                        is com.google.ai.client.generativeai.type.BlobPart -> "Blob(${part.mimeType})"
                        is com.google.ai.client.generativeai.type.FunctionCallPart -> "FunctionCall(${part.name}, args=${part.args})"
                        is com.google.ai.client.generativeai.type.FunctionResponsePart -> "FunctionResponse(${part.name})"
                        else -> "UnknownPart"
                    }
                }}")
            }

            Log.d(TAG, "sendMessageWithRetry: Logging original Bitmap properties before DTO conversion and saving:")

            // Log properties for inputContent's images
            inputContent.parts.filterIsInstance<com.google.ai.client.generativeai.type.ImagePart>().forEachIndexed { index, imagePart ->
                val bitmap = imagePart.image
                Log.d(TAG, "  InputContent Image[${index}]: Width=${bitmap.width}, Height=${bitmap.height}, Config=${bitmap.config?.name ?: "null"}, HasAlpha=${bitmap.hasAlpha()}, IsMutable=${bitmap.isMutable}")
            }

            // Log properties for chat.history images
            chat.history.forEachIndexed { historyIndex, contentItem ->
                contentItem.parts.filterIsInstance<com.google.ai.client.generativeai.type.ImagePart>().forEachIndexed { partIndex, imagePart ->
                    val bitmap = imagePart.image
                    Log.d(TAG, "  History[${historyIndex}] Image[${partIndex}]: Width=${bitmap.width}, Height=${bitmap.height}, Config=${bitmap.config?.name ?: "null"}, HasAlpha=${bitmap.hasAlpha()}, IsMutable=${bitmap.isMutable}")
                }
            }

            val inputContentDto = inputContent.toDto(context) // Pass context
            val chatHistoryDtos = chat.history.map { it.toDto(context) } // Pass context

            val inputContentJson = Json.encodeToString(inputContentDto)
            val chatHistoryJson = Json.encodeToString(chatHistoryDtos)

            // Collect Temporary File Paths
            val tempFilePaths = ArrayList<String>()
            inputContentDto.parts.forEach { partDto ->
                if (partDto is ImagePartDto) {
                    tempFilePaths.add(partDto.imageFilePath)
                }
            }
            chatHistoryDtos.forEach { contentDto ->
                contentDto.parts.forEach { partDto ->
                    if (partDto is ImagePartDto) {
                        tempFilePaths.add(partDto.imageFilePath)
                    }
                }
            }
            Log.d(TAG, "Collected temporary file paths to send to service: $tempFilePaths")

            val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_EXECUTE_AI_CALL
                putExtra(ScreenCaptureService.EXTRA_AI_INPUT_CONTENT_JSON, inputContentJson)
                putExtra(ScreenCaptureService.EXTRA_AI_CHAT_HISTORY_JSON, chatHistoryJson)
                putExtra(ScreenCaptureService.EXTRA_AI_MODEL_NAME, generativeModel.modelName) // Pass model name
                val mainActivity = MainActivity.getInstance()
                val currentModel = com.google.ai.sample.GenerativeAiViewModelFactory.getCurrentModel()
                val apiKey = com.google.ai.sample.MainActivity.getInstance()?.getCurrentApiKey(currentModel.apiProvider) ?: ""
                putExtra(ScreenCaptureService.EXTRA_AI_API_KEY, apiKey)
                // Add the new extra for file paths
                putStringArrayListExtra(ScreenCaptureService.EXTRA_TEMP_FILE_PATHS, tempFilePaths)
                putExtra(ScreenCaptureService.EXTRA_AI_API_PROVIDER, currentModel.apiProvider.name)
            }
            context.startService(serviceIntent)
            Log.d(TAG, "sendMessageWithRetry: Sent intent to ScreenCaptureService to execute AI call.")
            // The UI state (_uiState.value = PhotoReasoningUiState.Loading) and
            // _showStopNotificationFlow.value = true should have been set by the calling method (performReasoning)
            // The receiver will handle setting them to false or success/error state.

        } catch (e: Exception) {
            Log.e(TAG, "sendMessageWithRetry: Error serializing or starting service for AI call.", e)
            _uiState.value = PhotoReasoningUiState.Error("Error preparing AI call: ${e.localizedMessage}")
            _showStopNotificationFlow.value = false
            // Also update chat with this local error
            _chatState.replaceLastPendingMessage()
            _chatState.addMessage(
                PhotoReasoningMessage(
                    text = "Error preparing AI call: ${e.localizedMessage}",
                    participant = PhotoParticipant.ERROR
                )
            )
            _chatMessagesFlow.value = chatMessages
            saveChatHistory(context)
        }
    }

    // === Human Expert / WebRTC Logic ===

    fun onMediaProjectionPermissionGranted(resultCode: Int, data: Intent) {
        Log.d(TAG, "onMediaProjectionPermissionGranted: Storing result. Code=$resultCode")
        lastMediaProjectionResultCode = resultCode
        lastMediaProjectionResultData = data
        
        // If we were waiting to start a session, we could start it here.
        // For now, if the user just clicked "Human Expert" and granted permission, 
        // they might expect the connection to start. 
        // But startHumanExpertSession is already called in reason() if permission was already there.
        // If permission wasn't there, reason() wasn't called (MainActivity blocked it?).
        // Actually MainActivity.requestMediaProjectionPermission callback invokes the lambda passed to it.
        // That lambda calls reason(). So reason() will be called immediately after this.
    }

    private fun startHumanExpertSession(taskText: String) {
        if (signalingClient != null) {
            // Already connected
            postTaskToHumanExpert(taskText)
            return
        }

        _uiState.value = PhotoReasoningUiState.Loading
        _chatState.addMessage(PhotoReasoningMessage(text = "Connecting to Human Expert network...", participant = PhotoParticipant.MODEL, isPending = true))
        _chatMessagesFlow.value = _chatState.getAllMessages()

        // Initialize WebRTC Sender
        webRTCSender = WebRTCSender(getApplication(), object : WebRTCSender.WebRTCSenderListener {
            override fun onLocalICECandidate(candidate: IceCandidate) {
                signalingClient?.sendICECandidate(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
            }

            override fun onConnectionStateChanged(state: String) {
                Log.d(TAG, "WebRTC State: $state")
                viewModelScope.launch(Dispatchers.Main) {
                    if (state == "CONNECTED") {
                        _commandExecutionStatus.value = "Expert connected. Sharing screen."
                        replaceAiMessageText("Expert connected! They can now see your screen and control your device.", isPending = false)
                    } else if (state == "DISCONNECTED" || state == "FAILED") {
                         _commandExecutionStatus.value = "Expert disconnected."
                    }
                }
            }

            override fun onTapReceived(x: Float, y: Float) {
               dispatchTap(x, y)
            }

            // Task 9: Handle incoming text from Human Operator
            override fun onTextReceived(text: String) {
                viewModelScope.launch(Dispatchers.Main) {
                    val newMessage = PhotoReasoningMessage(
                        text = "Operator: $text",
                        participant = PhotoParticipant.MODEL,
                        isPending = false
                    )
                    _chatState.addMessage(newMessage)
                    _chatMessagesFlow.value = _chatState.getAllMessages()
                }
            }

            override fun onError(message: String) {
                Log.e(TAG, "WebRTC Error: $message")
                viewModelScope.launch(Dispatchers.Main) {
                     _uiState.value = PhotoReasoningUiState.Error("Video stream error: $message")
                }
            }
        })
        webRTCSender?.initialize()

        // Initialize Signaling
        signalingClient = SignalingClient(object : SignalingClient.SignalingListener {
            override fun onTaskPosted(taskId: String) {
                viewModelScope.launch(Dispatchers.Main) {
                    val msg = "Task posted. Waiting for an expert to claim it..."
                    replaceAiMessageText(msg, isPending = true)
                }
            }

            override fun onTaskClaimed(taskId: String) {
                Log.d(TAG, "Task claimed! Requesting fresh MediaProjection for WebRTC.")
                viewModelScope.launch(Dispatchers.Main) {
                    replaceAiMessageText("Expert found! Requesting screen capture permission...", isPending = true)
                    
                    // Request a fresh MediaProjection specifically for WebRTC.
                    // MainActivity startet bereits ACTION_KEEP_ALIVE_FOR_WEBRTC BEVOR dieser Callback gerufen wird.
                    // Kein weiterer startForegroundService()-Aufruf nötig - verhindert ForegroundServiceDidNotStartInTimeException.
                    val mainActivity = MainActivity.getInstance()
                    if (mainActivity != null) {
                        mainActivity.requestMediaProjectionForWebRTC { resultCode, resultData ->
                            Log.d(TAG, "WebRTC MediaProjection granted. Service läuft bereits via KEEP_ALIVE. Starte Screen Capture.")
                            replaceAiMessageText("Establishing video connection...", isPending = true)
                            
                            // KEIN startForegroundService() hier - MainActivity hat bereits ACTION_KEEP_ALIVE_FOR_WEBRTC gesendet.
                            // Das vermeidet doppelten Service-Start und ForegroundServiceDidNotStartInTimeException.
                            
                            viewModelScope.launch {
                                // Kurze Verzögerung zur Stabilisierung des Foreground-Services
                                delay(300)
                                try {
                                    // Start screen capture for WebRTC with fresh permission data
                                    webRTCSender?.startScreenCapture(resultData)
                                    webRTCSender?.createPeerConnection()
                                    
                                    // Create Offer
                                    webRTCSender?.createOffer { sdp ->
                                        signalingClient?.sendOffer(sdp)
                                    }
                                } catch (e: SecurityException) {
                                    Log.e(TAG, "SecurityException beim WebRTC Screen Capture - MediaProjection Token ungültig?", e)
                                    viewModelScope.launch(Dispatchers.Main) {
                                        _uiState.value = PhotoReasoningUiState.Error("Screen capture permission expired. Please try again.")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Fehler beim Starten des WebRTC Screen Capture", e)
                                    viewModelScope.launch(Dispatchers.Main) {
                                        _uiState.value = PhotoReasoningUiState.Error("Video connection failed: ${e.message}")
                                    }
                                }
                            }
                        }
                    } else {
                        Log.e(TAG, "MainActivity not available for MediaProjection request")
                        _uiState.value = PhotoReasoningUiState.Error("Cannot request screen capture - activity not available")
                    }
                }
            }

            override fun onSDPAnswer(sdp: String) {
                webRTCSender?.setRemoteAnswer(sdp)
            }

            override fun onICECandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
                webRTCSender?.addIceCandidate(candidate, sdpMid, sdpMLineIndex)
            }

            override fun onPeerDisconnected() {
                 viewModelScope.launch(Dispatchers.Main) {
                    _commandExecutionStatus.value = "Expert disconnected."
                    replaceAiMessageText("Expert disconnected.", isPending = false)
                    webRTCSender?.stop()
                }
            }

            override fun onError(message: String) {
                 viewModelScope.launch(Dispatchers.Main) {
                    _uiState.value = PhotoReasoningUiState.Error("Signaling error: $message")
                }
            }
        })
        
        // Post the task immediately
        Log.d(TAG, "Signaling initialized. Posting task.")
        postTaskToHumanExpert(taskText)
    }

    private fun postTaskToHumanExpert(text: String) {
         val context = getApplication<Application>().applicationContext
         val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
         val supportId = prefs.getString("payment_support_id", null)
         
         signalingClient?.postTask(text, hasScreenshot = false, supportId = supportId) // Capture live stream instead
    }

    private fun dispatchTap(x: Float, y: Float) {
        Log.d(TAG, "Dispatching tap: ($x, $y)")
        // Convert normalized to screen coordinates? 
        // Command.TapCoordinates usually expects absolute pixels.
        // ScreenOperatorAccessibilityService.executeCommand handles logic.
        // But wait, the web client sends normalized (0-1).
        
        // We need the screen dimensions.
        val displayMetrics = getApplication<Application>().resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        val absX = (x * screenWidth).toInt()
        val absY = (y * screenHeight).toInt()
        
        val command = Command.TapCoordinates(absX.toString(), absY.toString())
        ScreenOperatorAccessibilityService.executeCommand(command)
        
        viewModelScope.launch(Dispatchers.Main) {
            _commandExecutionStatus.value = "Expert tapped at ($absX, $absY)"
        }
    }



    private fun finalizeAiMessage(finalText: String) {
        Log.d(TAG, "finalizeAiMessage: Finalizing AI message.")
        val messages = _chatState.getAllMessages().toMutableList()
        val lastMessageIndex = messages.indexOfLast { it.participant == PhotoParticipant.MODEL && it.isPending }

        if (lastMessageIndex != -1) {
            messages[lastMessageIndex] = messages[lastMessageIndex].copy(text = finalText, isPending = false)
            _chatState.setAllMessages(messages)
            _chatMessagesFlow.value = _chatState.getAllMessages()
        } else {
            // This case should ideally not happen if streaming is working correctly
            // but as a fallback, we can just add the final message.
            _chatState.addMessage(
                PhotoReasoningMessage(
                    text = finalText,
                    participant = PhotoParticipant.MODEL,
                    isPending = false
                )
            )
            _chatMessagesFlow.value = _chatState.getAllMessages()
        }
        saveChatHistory(getApplication<Application>())
        refreshStopButtonState()
    }

    private fun updateAiMessage(text: String, isPending: Boolean = false) {
        Log.d(TAG, "updateAiMessage: Adding to _chatState. Text: \"${text.take(100)}...\", Participant: MODEL, IsPending: $isPending")

        // Get a copy of current messages
        val messages = _chatState.getAllMessages().toMutableList()
        val lastAiMessageIndex = messages.indexOfLast { it.participant == PhotoParticipant.MODEL }

        if (lastAiMessageIndex != -1 && messages[lastAiMessageIndex].isPending) {
            // If the last AI message is pending, append the new text
            val updatedMessage = messages[lastAiMessageIndex].let {
                it.copy(text = it.text + text, isPending = isPending)
            }
            messages[lastAiMessageIndex] = updatedMessage
        } else {
            // Otherwise, add a new AI message
            messages.add(PhotoReasoningMessage(text = text, participant = PhotoParticipant.MODEL, isPending = isPending))
        }

        // Set all messages atomically
        _chatState.setAllMessages(messages)

        // Update the flow
        _chatMessagesFlow.value = _chatState.getAllMessages()
        Log.d(TAG, "updateAiMessage: _chatState now has ${_chatState.messages.size} messages.")

        // Save chat history after updating message
        if (!stopExecutionFlag.get() || text.contains("stopped by user", ignoreCase = true)) {
            saveChatHistory(getApplication<Application>())
        }
    }

    /**
     * Replace the last pending AI message text (for streaming where the caller accumulates tokens).
     * Unlike updateAiMessage which appends, this sets the full text directly.
     */
    private fun replaceAiMessageText(text: String, isPending: Boolean = true) {
        val messages = _chatState.getAllMessages().toMutableList()
        val lastAiMessageIndex = messages.indexOfLast { it.participant == PhotoParticipant.MODEL }

        if (lastAiMessageIndex != -1 && messages[lastAiMessageIndex].isPending) {
            // Replace the full text (not append)
            messages[lastAiMessageIndex] = messages[lastAiMessageIndex].copy(text = text, isPending = isPending)
        } else {
            // No pending message found, add a new one
            messages.add(PhotoReasoningMessage(text = text, participant = PhotoParticipant.MODEL, isPending = isPending))
        }

        _chatState.setAllMessages(messages)
        _chatMessagesFlow.value = _chatState.getAllMessages()
    }

    /**
     * Update the system message
     */
    fun updateSystemMessage(message: String, context: Context) {
        _systemMessage.value = message
        
        // Save to SharedPreferences for persistence
        SystemMessagePreferences.saveSystemMessage(context, message)
    }
    
    /**
     * Load the system message from SharedPreferences
     */
    fun loadSystemMessage(context: Context?) {
        if (context == null) {
            Log.w(TAG, "Cannot load system message: context is null")
            return
        }
        val message = SystemMessagePreferences.loadSystemMessage(context)
        _systemMessage.value = message
        
        // Also load chat history
        loadChatHistory(context) // This line calls rebuildChatHistory internally
        chat = createChatWithSystemMessage(context)

        // Load persisted user input
        val persistedInput = UserInputPreferences.loadUserInput(context)
        _userInput.value = persistedInput

        _isInitialized.value = true // Add this line
    }

    /**
     * Restore the system message to its default value
     */
    fun restoreSystemMessage(context: Context) {
        val defaultMessage = SystemMessagePreferences.getDefaultSystemMessage()
        updateSystemMessage(defaultMessage, context)
    }

    private fun isQuotaExceededError(message: String): Boolean {
        return message.contains("exceeded your current quota") ||
               message.contains("code 429") ||
               message.contains("Too Many Requests", ignoreCase = true) ||
               message.contains("rate_limit", ignoreCase = true)
    }

    /**
     * Point 14: Check if error is a high-demand 503 (UNAVAILABLE) error.
     * These should NOT trigger API key switching.
     */
    private fun isHighDemandError(message: String): Boolean {
        return message.contains("Service Unavailable (503)") ||
                message.contains("UNAVAILABLE") ||
                message.contains("high demand") ||
                message.contains("overloaded")
    }

    /**
     * Helper function to format database entries as text.
     */
    private fun formatDatabaseEntriesAsText(context: Context): String {
        val entries = SystemMessageEntryPreferences.loadEntries(context)
        if (entries.isEmpty()) {
            return ""
        }
        val builder = StringBuilder()
        builder.append("Available System Guides:\n---\n")
        for (entry in entries) {
            builder.append("Title: ${entry.title}\n")
            builder.append("Guide: ${entry.guide}\n")
            builder.append("---\n")
        }
        return builder.toString()
    }
    
    /**
     * Incrementally process commands during streaming.
     * Parses the full accumulated text but only executes NEW commands
     * (i.e., commands beyond incrementalCommandCount).
     * This avoids re-executing commands that were already executed in earlier chunks.
     * 
     * Skips takeScreenshot() during streaming since we don't want to interrupt generation.
     * The final processCommands() call after streaming ends handles takeScreenshot.
     */
    private fun processCommandsIncrementally(accumulatedText: String) {
        if (stopExecutionFlag.get()) return
        
        try {
            // Parse all commands from the full accumulated text
            // Use a fresh parse (not the buffer-based one) to get all commands in order
            val allCommands = CommandParser.parseCommands(accumulatedText, clearBuffer = true)
            
            if (allCommands.size > incrementalCommandCount) {
                // There are new commands to execute
                val newCommands = allCommands.subList(incrementalCommandCount, allCommands.size)
                Log.d(TAG, "Incremental: Found ${newCommands.size} new commands (total: ${allCommands.size}, already executed: $incrementalCommandCount)")
                
                for (command in newCommands) {
                    if (stopExecutionFlag.get()) break
                    
                    // Skip takeScreenshot during streaming - it will be handled by final processCommands
                    if (command is Command.TakeScreenshot) {
                        Log.d(TAG, "Incremental: Skipping takeScreenshot during streaming (will be handled at end)")
                        incrementalCommandCount++
                        continue
                    }
                    
                    try {
                        Log.d(TAG, "Incremental: Executing command: $command")
                        _commandExecutionStatus.value = "Executing: $command"
                        ScreenOperatorAccessibilityService.executeCommand(command)
                        
                        // Track as executed
                        val currentCommands = _detectedCommands.value.toMutableList()
                        currentCommands.add(command)
                        _detectedCommands.value = currentCommands
                    } catch (e: Exception) {
                        Log.e(TAG, "Incremental: Error executing command: ${e.message}", e)
                    }
                    
                    incrementalCommandCount++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Incremental command parsing error: ${e.message}", e)
        }
    }

    /**
     * Process commands found in the AI response
     */
private fun processCommands(text: String) {
    commandProcessingJob?.cancel() // Cancel any previous command processing
    commandProcessingJob = PhotoReasoningApplication.applicationScope.launch(Dispatchers.Main) {
        if (commandProcessingJob?.isActive != true || stopExecutionFlag.get()) return@launch // Check for cancellation
        try {
            // Parse commands from the text
            val commands = CommandParser.parseCommands(text)

            // Check if takeScreenshot command is present
            val hasTakeScreenshotCommand = commands.any { it is Command.TakeScreenshot }

            if (commands.isNotEmpty()) {
                if (commandProcessingJob?.isActive != true || stopExecutionFlag.get()) return@launch
                Log.d(TAG, "Found ${commands.size} commands in response")

                // Update the detected commands
                val currentCommands = _detectedCommands.value.toMutableList()
                currentCommands.addAll(commands)
                _detectedCommands.value = currentCommands

                // Update status to show commands were detected
                val commandDescriptions = commands.joinToString("; ") { command ->
                    command.toString()
                }
                _commandExecutionStatus.value = "Commands detected: $commandDescriptions"

                // Execute the commands
                for (command in commands) {
                    if (commandProcessingJob?.isActive != true || stopExecutionFlag.get()) { // Check for cancellation before executing each command
                        Log.d(TAG, "Command execution stopped before executing: $command")
                        _commandExecutionStatus.value = "Command execution stopped."
                        break // Exit loop if cancelled
                    }
                    try {
                        Log.d(TAG, "Executing command: $command")
                        ScreenOperatorAccessibilityService.executeCommand(command)
                        // Check immediately after execution attempt if a stop was requested
                        if (stopExecutionFlag.get()) {
                            Log.d(TAG, "Command execution stopped after attempting: $command")
                            _commandExecutionStatus.value = "Command execution stopped."
                            break
                        }
                    } catch (e: Exception) {
                        if (commandProcessingJob?.isActive != true || stopExecutionFlag.get()) break // Exit loop if cancelled during error handling
                        Log.e(TAG, "Error executing command: ${e.message}", e)
                        _commandExecutionStatus.value = "Error during command execution: ${e.message}"
                    }
                }
                 if (stopExecutionFlag.get()){
                    _commandExecutionStatus.value = "Command processing loop was stopped."
                }
            }

            // Toast anzeigen wenn kein takeScreenshot Command gefunden wurde
            if (!hasTakeScreenshotCommand && !text.contains("takeScreenshot()", ignoreCase = true)) {
                val context = MainActivity.getInstance()
                if (context != null) {
                    Toast.makeText(context, "The AI stopped Screen Operator", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
             if (commandProcessingJob?.isActive != true || stopExecutionFlag.get()) return@launch
            Log.e(TAG, "Error processing commands: ${e.message}", e)
            _commandExecutionStatus.value = "Error during command processing: ${e.message}"
        } finally {
            if (stopExecutionFlag.get()){
                 _commandExecutionStatus.value = "Command processing finished after stop request."
            }
            refreshStopButtonState()
        }
    }
}

// Data classes for Cerebras API
@Serializable
data class CerebrasRequest(
    val model: String,
    val messages: List<CerebrasMessage>,
    val max_completion_tokens: Int = 1024,
    val temperature: Double = 0.2,
    val top_p: Double = 1.0,
    val stream: Boolean = false
)

@Serializable
data class CerebrasMessage(
    val role: String,
    val content: String
)

@Serializable
data class CerebrasResponse(
    val choices: List<CerebrasChoice>
)

@Serializable
data class CerebrasChoice(
    val message: CerebrasResponseMessage
)

@Serializable
data class CerebrasResponseMessage(
    val role: String,
    val content: String
)

// Data classes for Mistral API
@Serializable
data class MistralRequest(
    val model: String,
    val messages: List<MistralMessage>,
    val max_tokens: Int = 4096,
    val temperature: Double = 0.7,
    val top_p: Double = 1.0,
    val stream: Boolean = false
)

@Serializable
data class MistralMessage(
    val role: String,
    val content: List<MistralContent>
)

@Serializable
@JsonClassDiscriminator("type")
sealed class MistralContent

@Serializable
@kotlinx.serialization.SerialName("text")
data class MistralTextContent(val text: String) : MistralContent()

@Serializable
@kotlinx.serialization.SerialName("image_url")
data class MistralImageContent(@kotlinx.serialization.SerialName("image_url") val imageUrl: MistralImageUrl) : MistralContent()

@Serializable
data class MistralImageUrl(val url: String)

@Serializable
data class MistralResponse(
    val choices: List<MistralChoice>
)

@Serializable
data class MistralChoice(
    val message: MistralResponseMessage
)

@Serializable
data class MistralResponseMessage(
    val role: String,
    val content: String
)



    /**
     * Save chat history to SharedPreferences
     */
    private fun saveChatHistory(context: Context?) {
        context?.let {
            ChatHistoryPreferences.saveChatMessages(it, chatMessages)
        }
    }
    
    /**
     * Load chat history from SharedPreferences
     */
    fun loadChatHistory(context: Context) {
        val savedMessages = ChatHistoryPreferences.loadChatMessages(context)
        if (savedMessages.isNotEmpty()) {
            _chatState.setAllMessages(savedMessages)
            _chatMessagesFlow.value = _chatState.getAllMessages()
            
            if (isLiveMode) {
                // For live mode, update the Live API with the loaded history
                viewModelScope.launch {
                    collectLiveApiMessages() // This will set the history
                }
            } else {
                // For regular mode, rebuild the chat history
                rebuildChatHistory(context)
            }
        }
    }
    
    /**
     * Rebuild the chat history for the AI based on the current messages
     */
    private fun rebuildChatHistory(context: Context) { // Added context parameter
        Log.d(TAG, "rebuildChatHistory: Starting. Input _chatState.messages size: ${_chatState.messages.size}")
        // Convert the current chat messages to Content objects for the chat history
        val history = mutableListOf<Content>()

        // 1. Active System Message
        if (_systemMessage.value.isNotBlank()) {
            history.add(content(role = "user") { text(_systemMessage.value) })
        }

        // 2. Formatted Database Entries
        val formattedDbEntries = formatDatabaseEntriesAsText(context)
        if (formattedDbEntries.isNotBlank()) {
            history.add(content(role = "user") { text(formattedDbEntries) })
        }
        
        // 3. Group messages by participant to create proper conversation turns
        var currentUserContent = ""
        var currentModelContent = ""
        
        for (message in chatMessages) {
            when (message.participant) {
                PhotoParticipant.USER -> {
                    // If we have model content and are now seeing a user message,
                    // add the model content to history and reset
                    if (currentModelContent.isNotEmpty()) {
                        history.add(content(role = "model") { text(currentModelContent) })
                        currentModelContent = ""
                    }
                    
                    // Append to current user content
                    if (currentUserContent.isNotEmpty()) {
                        currentUserContent += "\n\n"
                    }
                    currentUserContent += message.text
                }
                PhotoParticipant.MODEL -> {
                    // If we have user content and are now seeing a model message,
                    // add the user content to history and reset
                    if (currentUserContent.isNotEmpty()) {
                        history.add(content(role = "user") { text(currentUserContent) })
                        currentUserContent = ""
                    }
                    
                    // Append to current model content
                    if (currentModelContent.isNotEmpty()) {
                        currentModelContent += "\n\n"
                    }
                    currentModelContent += message.text
                }
                PhotoParticipant.ERROR -> {
                    // Errors are not included in the AI history
                    continue
                }
            }
            Log.d(TAG, "  Processed PhotoReasoningMessage: role=${message.participant}, text collected for current SDK Content part: \"${ (if (message.participant == PhotoParticipant.USER) currentUserContent else currentModelContent).take(100) }...\"")
        }
        
        // Add any remaining content
        if (currentUserContent.isNotEmpty()) {
            history.add(content(role = "user") { text(currentUserContent) })
        }
        if (currentModelContent.isNotEmpty()) {
            history.add(content(role = "model") { text(currentModelContent) })
        }
        
        Log.d(TAG, "rebuildChatHistory: Finished processing. Generated SDK history size: ${history.size}")
        history.forEachIndexed { index, content ->
            Log.d(TAG, "  Generated SDK.Content[$index]: role=${content.role}, parts=${content.parts.joinToString { part ->
                when(part) {
                    is com.google.ai.client.generativeai.type.TextPart -> "Text(\"${part.text.take(50)}...\")"
                    is com.google.ai.client.generativeai.type.ImagePart -> "Image"
                    is com.google.ai.client.generativeai.type.BlobPart -> "Blob(${part.mimeType})"
                    is com.google.ai.client.generativeai.type.FunctionCallPart -> "FunctionCall(${part.name}, args=${part.args})"
                    is com.google.ai.client.generativeai.type.FunctionResponsePart -> "FunctionResponse(${part.name})"
                    else -> "UnknownPart"
                }
            }}")
        }

        // Create a new chat with the rebuilt history
        if (history.isNotEmpty()) {
            chat = generativeModel.startChat(
                history = history
            )
        } else {
            // Ensure chat is reset even if history is empty (e.g. only system message was there and it's now blank)
            chat = generativeModel.startChat(history = emptyList())
        }
    }
    
        /**
     * Clear the chat history
     */
    fun clearChatHistory(context: Context? = null) {
        // Clear visible messages completely for UI
        _chatState.setAllMessages(emptyList())

        // Create new chat with system message and DB entries in history (for AI context only, not visible in UI)
        val initialHistory = mutableListOf<Content>()
        if (_systemMessage.value.isNotBlank()) {
            initialHistory.add(content(role = "user") { text(_systemMessage.value) })
        }
        context?.let { ctx ->
            val formattedDbEntries = formatDatabaseEntriesAsText(ctx)
            if (formattedDbEntries.isNotBlank()) {
                initialHistory.add(content(role = "user") { text(formattedDbEntries) })
            }
        }
        chat = generativeModel.startChat(history = initialHistory.toList())
        
        // Update the flow with empty messages
        _chatMessagesFlow.value = emptyList()
        
        // Clear from SharedPreferences if context is provided
        context?.let {
            ChatHistoryPreferences.clearChatMessages(it)
        }

        // WICHTIG: LiveApiManager auch aktualisieren!
        if (isLiveMode && liveApiManager != null) {
            // Nur System Message und DB Entries als History setzen, keine Chat-Messages
            val historyPairs = mutableListOf<Pair<String, String>>()

            if (_systemMessage.value.isNotBlank()) {
                historyPairs.add(Pair("user", _systemMessage.value))
            }

            context?.let { ctx ->
                val formattedDbEntries = formatDatabaseEntriesAsText(ctx)
                if (formattedDbEntries.isNotBlank()) {
                    historyPairs.add(Pair("user", formattedDbEntries))
                }
            }

            liveApiManager.setSystemMessageAndHistory(_systemMessage.value, historyPairs)
        }

        // Reset retry attempt counter
        currentRetryAttempt = 0

        // Clear any pending jobs
        currentReasoningJob?.cancel()
        commandProcessingJob?.cancel()

        // Reset UI state
        _uiState.value = PhotoReasoningUiState.Initial
        _commandExecutionStatus.value = ""
        _detectedCommands.value = emptyList()
    }
    
    /**
     * Add a screenshot to the conversation
     * 
     * @param screenshotUri URI of the screenshot
     * @param context Application context
     * @param screenInfo Optional information about screen elements (null if not available)
     */
    fun addScreenshotToConversation(
        screenshotUri: Uri,
        context: Context,
        screenInfo: String? = null
    ) {
        if (screenshotUri == Uri.EMPTY) {
            // This case is for gemma-3n-e4b-it, where we don't have a screenshot.
            // We just want to send the screen info.
            val genericAnalysisPrompt = ""
            reason(
                userInput = genericAnalysisPrompt,
                selectedImages = emptyList(),
                screenInfoForPrompt = screenInfo,
                imageUrisForChat = emptyList()
            )
            return
        }
        val currentTime = System.currentTimeMillis()
        if (screenshotUri == lastProcessedScreenshotUri && (currentTime - lastProcessedScreenshotTime) < 2000) { // 2-second debounce window
            Log.w(TAG, "addScreenshotToConversation: Debouncing duplicate/rapid call for URI $screenshotUri")
            return // Exit the function early if it's a duplicate call within the window
        }
        lastProcessedScreenshotUri = screenshotUri
        lastProcessedScreenshotTime = currentTime

        PhotoReasoningApplication.applicationScope.launch(Dispatchers.Main) {
            try {
                Log.d(TAG, "Adding screenshot to conversation: $screenshotUri")
                
                // Store the latest screenshot URI
                latestScreenshotUri = screenshotUri
                
                // Initialize ImageLoader and ImageRequestBuilder if needed
                if (imageLoader == null) {
                    imageLoader = ImageLoader.Builder(context).build()
                }
                if (imageRequestBuilder == null) {
                    imageRequestBuilder = ImageRequest.Builder(context)
                }
                
                // Update status
                _commandExecutionStatus.value = "Processing screenshot..."
                
                // Show toast
                Toast.makeText(context, "Processing screenshot...", Toast.LENGTH_SHORT).show()
                
                // Process the screenshot
                val imageRequest = imageRequestBuilder!!
                    .data(screenshotUri)
                    .precision(Precision.EXACT)
                    .build()
                
                try {
                    val result = imageLoader!!.execute(imageRequest)
                    if (result is SuccessResult) {
                        Log.d(TAG, "Successfully processed screenshot")
                        val bitmap = (result.drawable as BitmapDrawable).bitmap
                        
                        // Add the screenshot to the current images
                        val updatedImages = currentSelectedImages.toMutableList()
                        updatedImages.add(bitmap)
                        
                        // Update the current selected images - only keep the latest screenshot
                        currentSelectedImages = listOf(bitmap)
                        
                        // Update status
                        _commandExecutionStatus.value = "Screenshot added, sending to AI..."
                        
                        // Show toast
                        Toast.makeText(context, "Screenshot added, sending to AI...", Toast.LENGTH_SHORT).show()
                        
                        // Create prompt with screen information if available
                        val genericAnalysisPrompt = ""
                        
                        // Re-send the query with only the latest screenshot
                        reason(
                            userInput = genericAnalysisPrompt,
                            selectedImages = listOf(bitmap),
                            screenInfoForPrompt = screenInfo,
                            imageUrisForChat = listOf(screenshotUri.toString()) // Add this argument
                        )
                        
                        // Show a toast to indicate the screenshot was added
                        Toast.makeText(context, "Screenshot added to conversation", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e(TAG, "Failed to process screenshot: result is not SuccessResult")
                        _commandExecutionStatus.value = "Error processing screenshot"
                        Toast.makeText(context, "Error processing screenshot", Toast.LENGTH_SHORT).show()
                        
                        // Add error message to chat
                        _chatState.addMessage(
                            PhotoReasoningMessage(
                                text = "Error processing screenshot",
                                participant = PhotoParticipant.ERROR
                            )
                        )
                        _chatMessagesFlow.value = chatMessages
                        
                        // Save chat history after adding error message
                        saveChatHistory(context)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing screenshot: ${e.message}", e)
                    _commandExecutionStatus.value = "Error processing screenshot: ${e.message}"
                    Toast.makeText(context, "Error processing screenshot: ${e.message}", Toast.LENGTH_SHORT).show()
                    
                    // Add error message to chat
                    _chatState.addMessage(
                        PhotoReasoningMessage(
                            text = "Error processing screenshot: ${e.message}",
                            participant = PhotoParticipant.ERROR
                        )
                    )
                    _chatMessagesFlow.value = chatMessages
                    
                    // Save chat history after adding error message
                    saveChatHistory(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding screenshot to conversation: ${e.message}", e)
                _commandExecutionStatus.value = "Error adding screenshot: ${e.message}"
                Toast.makeText(context, "Error adding screenshot: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
        /**
     * Chat state management class
     */
    private class ChatState {
        private val _messages = mutableListOf<PhotoReasoningMessage>()
        val messages: List<PhotoReasoningMessage>
            get() = _messages.toList() // Return a copy to prevent concurrent modification

        fun addMessage(message: PhotoReasoningMessage) {
            _messages.add(message)
        }

        fun clearMessages() {
            _messages.clear()
        }

        fun replaceLastPendingMessage() {
            val lastPendingIndex = _messages.indexOfLast { it.isPending }
            if (lastPendingIndex >= 0) {
                _messages.removeAt(lastPendingIndex)
            }
        }

        fun updateLastMessageText(newText: String) {
            if (_messages.isNotEmpty()) {
                val lastMessage = _messages.last()
                _messages[_messages.size - 1] = lastMessage.copy(text = newText, isPending = false)
            }
        }
        
        // Add this method to get all messages atomically
        fun getAllMessages(): List<PhotoReasoningMessage> {
            return _messages.toList()
        }
        
        // Add this method to set all messages atomically
        fun setAllMessages(messages: List<PhotoReasoningMessage>) {
            _messages.clear()
            _messages.addAll(messages)
        }
    }
}

/** Thrown when Mistral returns HTTP 429 (rate limit). */
private class MistralRateLimitException(message: String) : IOException(message)

@Serializable
data class OpenAIStreamChunk(
    val choices: List<OpenAIStreamChoice>
)

@Serializable
data class OpenAIStreamChoice(
    val delta: OpenAIStreamDelta
)

@Serializable
data class OpenAIStreamDelta(
    val content: String? = null
)
