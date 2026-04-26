package com.google.ai.sample.feature.multimodal

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.provider.Settings
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import com.google.ai.sample.ApiProvider
import com.google.ai.sample.GenerativeViewModelFactory
import com.google.ai.sample.MainActivity
import com.google.ai.sample.ModelOption
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PhotoReasoningRoute(
    innerPadding: PaddingValues,  // Füge Parameter hinzu
    viewModelStoreOwner: androidx.lifecycle.ViewModelStoreOwner = checkNotNull(
        androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current
    ) { "ViewModelStoreOwner is required" }
) {
    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    
    // Scoped to MainActivity so it survives navigation and avoids duplicate initialization.
    val owner = mainActivity ?: viewModelStoreOwner
    val viewModel: PhotoReasoningViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        viewModelStoreOwner = owner, 
        factory = GenerativeViewModelFactory
    )
    val photoReasoningUiState by viewModel.uiState.collectAsState()
    val commandExecutionStatus by viewModel.commandExecutionStatus.collectAsState()
    val detectedCommands by viewModel.detectedCommands.collectAsState()
    val systemMessage by viewModel.systemMessage.collectAsState()
    val isInitialized by viewModel.isInitialized.collectAsState()
    val modelName by viewModel.modelNameState.collectAsState()
    val userInput by viewModel.userInput.collectAsState()
    val isGenerationRunning by viewModel.isGenerationRunningFlow.collectAsState()
    val isOfflineGpuModelLoaded by viewModel.isOfflineGpuModelLoadedFlow.collectAsState()
    val isInitializingOfflineModel by viewModel.isInitializingOfflineModelFlow.collectAsState()

    // Hoisted: var showNotificationRationaleDialog by rememberSaveable { mutableStateOf(false) }
    // This state will now be managed in PhotoReasoningRoute and passed down.


    val coroutineScope = rememberCoroutineScope()
    val imageRequestBuilder = ImageRequest.Builder(LocalContext.current)
    val imageLoader = ImageLoader.Builder(LocalContext.current).build()

    val isAccessibilityServiceEffectivelyEnabled by mainActivity?.isAccessibilityServiceEnabledFlow?.collectAsState() ?: mutableStateOf(false)
    val isMediaProjectionPermissionGranted by mainActivity?.isMediaProjectionPermissionGrantedFlow?.collectAsState() ?: mutableStateOf(false)
    val isKeyboardOpen by mainActivity?.isKeyboardOpen?.collectAsState() ?: mutableStateOf(false)

    val accessibilitySettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        mainActivity?.refreshAccessibilityServiceStatus()
    }

    DisposableEffect(viewModel, mainActivity) {
        mainActivity?.setPhotoReasoningViewModel(viewModel)
        mainActivity?.refreshAccessibilityServiceStatus()
        viewModel.loadSystemMessage(context)
        viewModel.collectLiveApiMessages()
        onDispose { }
    }

    PhotoReasoningScreen(
        innerPadding = innerPadding,  // Übergebe an PhotoReasoningScreen
        uiState = photoReasoningUiState,
        commandExecutionStatus = commandExecutionStatus,
        detectedCommands = detectedCommands,
        systemMessage = systemMessage,
        chatMessages = viewModel.chatMessagesFlow,
        onSystemMessageChanged = { message ->
            viewModel.updateSystemMessage(message, context)
        },
        onRestoreSystemMessageClicked = {
            viewModel.restoreSystemMessage(context)
        },
        onReasonClicked = { inputText, selectedItems ->
            coroutineScope.launch {
                val bitmaps = selectedItems.mapNotNull { uri ->
                    uriToBitmap(
                        uri = uri,
                        context = context,
                        imageRequestBuilder = imageRequestBuilder,
                        imageLoader = imageLoader
                    )
                }
                viewModel.reason(
                    userInput = inputText,
                    selectedImages = bitmaps,
                    screenInfoForPrompt = null, // User-initiated messages don't have prior screen context here
                    imageUrisForChat = selectedItems.map { it.toString() }
                )
            }
        },
        isAccessibilityServiceEnabled = isAccessibilityServiceEffectivelyEnabled,
        isMediaProjectionPermissionGranted = isMediaProjectionPermissionGranted,
        onEnableAccessibilityService = {
            try {
                // val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) // Corrected
                // accessibilitySettingsLauncher.launch(intent) // Corrected below
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                accessibilitySettingsLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Error opening Accessibility Settings.", Toast.LENGTH_LONG).show()
            }
        },
        onClearChatHistory = {
            mainActivity?.getPhotoReasoningViewModel()?.clearChatHistory(context)
        },
        isKeyboardOpen = isKeyboardOpen,
        onStopClicked = { viewModel.onStopClicked() },
        isInitialized = isInitialized,
        modelName = modelName,
        userQuestion = userInput,
        onUserQuestionChanged = { viewModel.updateUserInput(it) },
        isGenerationRunning = isGenerationRunning,
        isOfflineGpuModelLoaded = isOfflineGpuModelLoaded,
        isInitializingOfflineModel = isInitializingOfflineModel
    )
}

private suspend fun uriToBitmap(
    uri: Uri,
    context: android.content.Context,
    imageRequestBuilder: ImageRequest.Builder,
    imageLoader: ImageLoader
): Bitmap? = withContext(kotlinx.coroutines.Dispatchers.IO) {
    val mimeType = context.contentResolver.getType(uri).orEmpty()
    if (mimeType.startsWith("video/")) {
        return@withContext extractVideoFrame(context, uri)
    }

    val imageRequest = imageRequestBuilder.data(uri).precision(Precision.EXACT).build()
    return@withContext try {
        val result = imageLoader.execute(imageRequest)
        if (result is SuccessResult) (result.drawable as? BitmapDrawable)?.bitmap else null
    } catch (e: Exception) {
        null
    }
}

private fun extractVideoFrame(context: android.content.Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (e: Exception) {
        android.util.Log.e("PhotoReasoningRoute", "Error extracting video frame for URI: $uri", e)
        null
    } finally {
        retriever.release()
    }
}
