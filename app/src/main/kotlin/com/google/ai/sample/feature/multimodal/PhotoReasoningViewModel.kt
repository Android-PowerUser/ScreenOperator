package com.google.ai.sample.feature.multimodal

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
import androidx.lifecycle.ViewModel
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
// Removed duplicate StateFlow import
// Removed duplicate asStateFlow import
// import kotlinx.coroutines.isActive // Removed as we will use job.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
// import kotlin.coroutines.coroutineContext // Removed if not used
import java.util.concurrent.atomic.AtomicBoolean

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import android.util.Base64
import com.google.ai.sample.feature.live.LiveApiManager
import com.google.ai.sample.ApiProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PhotoReasoningViewModel(
    private var generativeModel: GenerativeModel,
    private val modelName: String,
    private val liveApiManager: LiveApiManager? = null
) : ViewModel() {

    private val isLiveMode: Boolean
        get() = liveApiManager != null

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
    private val TAG = "PhotoReasoningViewModel"

    private val _uiState: MutableStateFlow<PhotoReasoningUiState> =
        MutableStateFlow(PhotoReasoningUiState.Initial)
    val uiState: StateFlow<PhotoReasoningUiState> =
        _uiState.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _showStopNotificationFlow = MutableStateFlow(false)
    val showStopNotificationFlow: StateFlow<Boolean> = _showStopNotificationFlow.asStateFlow()
        
    // Keep track of the latest screenshot URI
    private var latestScreenshotUri: Uri? = null
    private var lastProcessedScreenshotUri: Uri? = null
    private var lastProcessedScreenshotTime: Long = 0L
    
    // Keep track of the current selected images
    private var currentSelectedImages: List<Bitmap> = emptyList()
    
    // Keep track of the current user input
    private var currentUserInput: String = ""
    
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

    // Added for retry on quota exceeded
    private var currentRetryAttempt = 0
    private var currentScreenInfoForPrompt: String? = null
    private var currentImageUrisForChat: List<String>? = null

    private val aiResultStreamReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ScreenCaptureService.ACTION_AI_STREAM_UPDATE) {
                val chunk = intent.getStringExtra(ScreenCaptureService.EXTRA_AI_STREAM_CHUNK)
                if (chunk != null) {
                    updateAiMessage(chunk, isPending = true)
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
                    saveChatHistory(MainActivity.getInstance()?.applicationContext)
                } else if (errorMessage != null) {
                    Log.e(TAG, "AI Call Error via Broadcast: $errorMessage")
                    if (context == null) {
                        Log.e(TAG, "Context null in receiver, cannot handle error")
                        return
                    }
                    val receiverContext = context
                    _uiState.value = PhotoReasoningUiState.Error(errorMessage)
                    _commandExecutionStatus.value = "Error during AI generation: $errorMessage"
                    _chatState.replaceLastPendingMessage()

                    val apiKeyManager = ApiKeyManager.getInstance(receiverContext)
                    val isQuotaError = isQuotaExceededError(errorMessage)
                    val currentModel = com.google.ai.sample.GenerativeAiViewModelFactory.getCurrentModel()
                    if (isQuotaError && currentRetryAttempt < MAX_RETRY_ATTEMPTS) {
                        val currentKey = apiKeyManager.getCurrentApiKey(currentModel.apiProvider)
                        if (currentKey != null) {
                            apiKeyManager.markKeyAsFailed(currentKey, currentModel.apiProvider)
                            val newKey = apiKeyManager.switchToNextAvailableKey(currentModel.apiProvider)
                            if (newKey != null) {
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
                    saveChatHistory(MainActivity.getInstance()?.applicationContext)
                }
                // Reset pending AI message if any (assuming updateAiMessage or error handling does this)
            }
        }
    }

    init {
        // ... other init logic
        val context = MainActivity.getInstance()?.applicationContext
        if (context != null) {
            val filter = IntentFilter(ScreenCaptureService.ACTION_AI_CALL_RESULT)
            LocalBroadcastManager.getInstance(context).registerReceiver(aiResultReceiver, filter)
            Log.d(TAG, "AIResultReceiver registered with LocalBroadcastManager.")

            val streamFilter = IntentFilter(ScreenCaptureService.ACTION_AI_STREAM_UPDATE)
            LocalBroadcastManager.getInstance(context).registerReceiver(aiResultStreamReceiver, streamFilter)
            Log.d(TAG, "AIResultStreamReceiver registered with LocalBroadcastManager.")
        } else {
            Log.e(TAG, "Failed to register AIResultReceiver: applicationContext is null at init.")
        }
    }

    override fun onCleared() {
        super.onCleared()
        liveApiManager?.close()
        val context = MainActivity.getInstance()?.applicationContext
        if (context != null) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(aiResultReceiver)
            Log.d(TAG, "AIResultReceiver unregistered with LocalBroadcastManager.")
            LocalBroadcastManager.getInstance(context).unregisterReceiver(aiResultStreamReceiver)
            Log.d(TAG, "AIResultStreamReceiver unregistered with LocalBroadcastManager.")
        }
        // ... other onCleared logic
    }

    private fun createChatWithSystemMessage(context: Context? = null): Chat {
        val ctx = context ?: MainActivity.getInstance()?.applicationContext
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
    val context = MainActivity.getInstance()?.applicationContext

    // Update the generative model with the current API key if retrying
    if (currentRetryAttempt > 0 && context != null) {
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

        // Clear previous commands
        _detectedCommands.value = emptyList()
        _commandExecutionStatus.value = ""

        // Add user message to chat history
        val userMessage = PhotoReasoningMessage(
            text = aiPromptText, // Use the combined text
            participant = PhotoParticipant.USER,
            imageUris = imageUrisForChat ?: emptyList(), // Use the new parameter here
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
                if (shouldContinueProcessing) { // Check flag before proceeding
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
        }
    }

    fun reason(
        userInput: String,
        selectedImages: List<Bitmap>,
        screenInfoForPrompt: String? = null,
        imageUrisForChat: List<String>? = null
    ) {
        val currentModel = com.google.ai.sample.GenerativeAiViewModelFactory.getCurrentModel()
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
                        imageUris = imageUrisForChat ?: emptyList(),
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
                    saveChatHistory(MainActivity.getInstance()?.applicationContext)
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
        val context = MainActivity.getInstance()?.applicationContext ?: return

        val apiKeyManager = ApiKeyManager.getInstance(context)
        val apiKey = apiKeyManager.getCurrentApiKey(ApiProvider.CEREBRAS)

        if (apiKey.isNullOrEmpty()) {
            _uiState.value = PhotoReasoningUiState.Error("Cerebras API key not found.")
            return
        }

        val combinedPromptText = (userInput + "\n\n" + (screenInfoForPrompt ?: "")).trim()

        // Add user message to chat history
        val userMessage = PhotoReasoningMessage(
            text = combinedPromptText,
            participant = PhotoParticipant.USER,
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

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val mediaType = "application/json".toMediaType()

                val requestBody = CerebrasRequest(
                    model = modelName,
                    messages = listOf(CerebrasMessage(role = "user", content = combinedPromptText))
                )
                val jsonBody = Json.encodeToString(requestBody)

                val request = Request.Builder()
                    .url("https://api.cerebras.ai/v1/chat/completions")
                    .post(jsonBody.toRequestBody(mediaType))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected code ${response.code} - ${response.body?.string()}")
                    }

                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val json = Json { ignoreUnknownKeys = true }
                        val cerebrasResponse = json.decodeFromString<CerebrasResponse>(responseBody)
                        val aiResponseText = cerebrasResponse.choices.firstOrNull()?.message?.content ?: "No response from model"

                        withContext(Dispatchers.Main) {
                            _uiState.value = PhotoReasoningUiState.Success(aiResponseText)
                            finalizeAiMessage(aiResponseText)
                            processCommands(aiResponseText)
                            saveChatHistory(context)
                        }
                    } else {
                        throw IOException("Empty response body")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Cerebras API call failed", e)
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
                val context = MainActivity.getInstance()?.applicationContext
                if (context != null) {
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
                            saveChatHistory(MainActivity.getInstance()?.applicationContext)
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
        if (isLiveMode) {
            // For live mode, close the connection
            liveApiManager?.close()
        }

        // Rest of the existing onStopClicked code
        _showStopNotificationFlow.value = false
        stopExecutionFlag.set(true)
        currentReasoningJob?.cancel()
        commandProcessingJob?.cancel()

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
             // If the last message was a successful model response, update it.
            messages[messages.size - 1] = lastMessage.copy(text = lastMessage.text + "\n\n[Stopped by user]")
        } else {
            // If no relevant model message, or last message was user/error, add a new model message
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


        // _uiState.value = PhotoReasoningUiState.Stopped; // No longer setting this as the final state.
        _commandExecutionStatus.value = "" // Set to empty string
        _detectedCommands.value = emptyList()
        Log.d(TAG, "Stop clicked, operations cancelled, command status cleared.")

        // Set a success state to indicate the stop operation itself was successful
        // and the UI can return to an idle/interactive state.
        _uiState.value = PhotoReasoningUiState.Success("Operation stopped.")
        Log.d(TAG, "UI updated to Success state after stop.")
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
                val apiKey = mainActivity?.getCurrentApiKey(currentModel.apiProvider) ?: ""
                putExtra(ScreenCaptureService.EXTRA_AI_API_KEY, apiKey)
                // Add the new extra for file paths
                putStringArrayListExtra(ScreenCaptureService.EXTRA_TEMP_FILE_PATHS, tempFilePaths)
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

    /**
     * Update the AI message in chat history
     */
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
        saveChatHistory(MainActivity.getInstance()?.applicationContext)
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
            saveChatHistory(MainActivity.getInstance()?.applicationContext)
        }
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
                message.contains("Service Unavailable (503)")
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
    val top_p: Int = 1,
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
        if (modelName == "gemma-3n-e4b-it") {
            // If the model is gemma-3n-e4b-it, we don't want to send the screenshot.
            // Instead, we'll just send the screen info.
            val genericAnalysisPrompt = ""
            reason(
                userInput = genericAnalysisPrompt,
                selectedImages = emptyList(),
                screenInfoForPrompt = screenInfo,
                imageUrisForChat = emptyList()
            )
            return
        }
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
