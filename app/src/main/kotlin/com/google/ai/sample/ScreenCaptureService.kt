package com.google.ai.sample

import android.app.Activity // Make sure this import is present
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri // Added for broadcasting URI
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.ImagePart // For instance check
import com.google.ai.client.generativeai.type.FunctionCallPart // For logging AI response
import com.google.ai.client.generativeai.type.FunctionResponsePart // For logging AI response
import com.google.ai.client.generativeai.type.BlobPart // For logging AI response
import com.google.ai.client.generativeai.type.TextPart // For logging AI response
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.ai.sample.util.ModelDownloadManager
// Removed duplicate TextPart import
import com.google.ai.sample.feature.multimodal.dtos.ContentDto
import com.google.ai.sample.feature.multimodal.dtos.toSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenCaptureService : Service() {
    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "ScreenCaptureChannel"
        private const val NOTIFICATION_ID = 2001
        private const val NOTIFICATION_ID_AI = NOTIFICATION_ID + 1 // Or any distinct ID
        const val ACTION_START_CAPTURE = "com.google.ai.sample.START_CAPTURE"
        const val ACTION_TAKE_SCREENSHOT = "com.google.ai.sample.TAKE_SCREENSHOT" // New action
        const val ACTION_STOP_CAPTURE = "com.google.ai.sample.STOP_CAPTURE"   // New action
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_TAKE_SCREENSHOT_ON_START = "take_screenshot_on_start"

        // For triggering AI call execution in the service
        const val ACTION_EXECUTE_AI_CALL = "com.google.ai.sample.EXECUTE_AI_CALL"
        const val EXTRA_AI_INPUT_CONTENT_JSON = "com.google.ai.sample.EXTRA_AI_INPUT_CONTENT_JSON"
        const val EXTRA_AI_CHAT_HISTORY_JSON = "com.google.ai.sample.EXTRA_AI_CHAT_HISTORY_JSON"
        const val EXTRA_AI_MODEL_NAME = "com.google.ai.sample.EXTRA_AI_MODEL_NAME" // For service to create model
        const val EXTRA_AI_API_KEY = "com.google.ai.sample.EXTRA_AI_API_KEY"     // For service to create model
        const val EXTRA_AI_API_PROVIDER = "com.google.ai.sample.EXTRA_AI_API_PROVIDER" // For service to select API
        const val EXTRA_TEMP_FILE_PATHS = "com.google.ai.sample.EXTRA_TEMP_FILE_PATHS"


        // For broadcasting AI call results from the service
        const val ACTION_AI_CALL_RESULT = "com.google.ai.sample.AI_CALL_RESULT"
        const val EXTRA_AI_RESPONSE_TEXT = "com.google.ai.sample.EXTRA_AI_RESPONSE_TEXT"
        const val EXTRA_AI_ERROR_MESSAGE = "com.google.ai.sample.EXTRA_AI_ERROR_MESSAGE"
        const val ACTION_AI_STREAM_UPDATE = "com.google.ai.sample.AI_STREAM_UPDATE"
        const val EXTRA_AI_STREAM_CHUNK = "com.google.ai.sample.EXTRA_AI_STREAM_CHUNK"

        private var instance: ScreenCaptureService? = null

        fun isRunning(): Boolean = instance != null && instance?.isReady == true
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var isReady = false // Flag to indicate if MediaProjection is set up and active
    private val isScreenshotRequestedRef = java.util.concurrent.atomic.AtomicBoolean(false)
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var llmInference: LlmInference? = null

    // Callback for MediaProjection
    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.w(TAG, "MediaProjection session stopped externally (via callback). Cleaning up.")
            cleanup() // Perform full cleanup if projection stops unexpectedly
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "onCreate: Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}, isReady=$isReady, mediaProjectionIsNull=${mediaProjection==null}")

        when (intent?.action) {
            ACTION_START_CAPTURE -> {
                if (isReady && mediaProjection != null) {
                    Log.w(TAG, "MediaProjection already active, ignoring duplicate START_CAPTURE")
                    return START_STICKY
                }
                val notification = createNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                Log.d(TAG, "Service started in foreground for ACTION_START_CAPTURE.")

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                }

                Log.d(TAG, "onStartCommand (START_CAPTURE): resultCode=$resultCode, hasResultData=${resultData != null}")

                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    val takeScreenshotFlag = intent.getBooleanExtra(EXTRA_TAKE_SCREENSHOT_ON_START, false)
                    startCapture(resultCode, resultData, takeScreenshotFlag)
                } else {
                    Log.e(TAG, "Invalid parameters for START_CAPTURE: resultCode=$resultCode (expected ${Activity.RESULT_OK}), resultDataIsNull=${resultData == null}")
                    cleanup() // Use cleanup to stop foreground and self
                }
            }
            ACTION_TAKE_SCREENSHOT -> {
                Log.d(TAG, "Received ACTION_TAKE_SCREENSHOT.")
                if (isReady && mediaProjection != null) {
                    takeScreenshot()
                } else {
                    Log.e(TAG, "Service not ready or MediaProjection not available for TAKE_SCREENSHOT. isReady=$isReady, mediaProjectionIsNull=${mediaProjection == null}")
                    Toast.makeText(this, "Screenshot service not ready. Please re-grant permission if necessary.", Toast.LENGTH_LONG).show()
                    // Optionally, broadcast a failure or request MainActivity to re-initiate.
                    // If not ready, and this action is called, it implies a logic error or race condition.
                    // MainActivity should ideally prevent calling this if service isn't running/ready.
                }
            }
            ACTION_STOP_CAPTURE -> {
                Log.d(TAG, "Received ACTION_STOP_CAPTURE. Cleaning up.")
                cleanup()
            }
            ACTION_EXECUTE_AI_CALL -> {
                Log.d(TAG, "ACTION_EXECUTE_AI_CALL: Ensuring foreground state for AI processing.")
                val aiNotification = createAiOperationNotification()

                var startedForegroundForAi = false // Flag to track if we started foreground specifically for this call

                // Only start foreground if not already ready (i.e., not already in foreground with mediaProjection)
                if (!isReady) {
                    val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC // Safer type for AI/network, no special permissions needed
                    } else {
                        0 // Use none for older versions
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIFICATION_ID_AI, aiNotification, foregroundType)
                    } else {
                        startForeground(NOTIFICATION_ID_AI, aiNotification)
                    }
                    Log.d(TAG, "Started foreground with type ${foregroundType} for AI processing (since not ready).")
                    startedForegroundForAi = true
                } else {
                    Log.d(TAG, "Already in foreground with mediaProjection, skipping startForeground for AI.")
                }

                Log.d(TAG, "Received ACTION_EXECUTE_AI_CALL")
                // This service, already a Foreground Service for MediaProjection,
                // is now also responsible for executing AI calls to leverage foreground network priority.
                val inputContentJson = intent.getStringExtra(EXTRA_AI_INPUT_CONTENT_JSON)
                val chatHistoryJson = intent.getStringExtra(EXTRA_AI_CHAT_HISTORY_JSON)
                val modelName = intent.getStringExtra(EXTRA_AI_MODEL_NAME)
                val apiKey = intent.getStringExtra(EXTRA_AI_API_KEY)
                val apiProviderString = intent.getStringExtra(EXTRA_AI_API_PROVIDER)
                val apiProvider = ApiProvider.valueOf(apiProviderString ?: ApiProvider.GOOGLE.name)
                val tempFilePaths = intent.getStringArrayListExtra(EXTRA_TEMP_FILE_PATHS) ?: ArrayList()
                Log.d(TAG, "Received tempFilePaths for cleanup: $tempFilePaths")

                if (inputContentJson == null || chatHistoryJson == null || modelName == null || apiKey == null) {
                    Log.e(TAG, "Missing necessary data for AI call. inputContentJson: ${inputContentJson != null}, chatHistoryJson: ${chatHistoryJson != null}, modelName: ${modelName != null}, apiKey: ${apiKey != null}")
                    // Optionally broadcast an error back immediately
                    broadcastAiCallError("Missing parameters for AI call in service.")
                    // If we started foreground for this, stop it now (but keep service running)
                    if (startedForegroundForAi) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    }
                    return START_STICKY // Or START_NOT_STICKY if this is a fatal error for this call
                }

                serviceScope.launch {
                    var responseText: String? = null
                    var errorMessage: String? = null
                    try {
                        // Deserialize JSON to DTOs.
                        val chatHistoryDtos = Json.decodeFromString<List<ContentDto>>(chatHistoryJson)
                        val inputContentDto = Json.decodeFromString<ContentDto>(inputContentJson)

                        // Convert DTOs back to SDK types.
                        val chatHistory = chatHistoryDtos.map { it.toSdk() } // Uses ContentDto.toSdk()
                        val inputContent = inputContentDto.toSdk()           // Uses ContentDto.toSdk()

                        Log.d(TAG, "ACTION_EXECUTE_AI_CALL: Logging reloaded Bitmap properties after DTO conversion from file:")

                        // Log properties for inputContent's images
                        inputContent.parts.filterIsInstance<com.google.ai.client.generativeai.type.ImagePart>().forEachIndexed { index, imagePart ->
                            val bitmap = imagePart.image // This is the reloaded Bitmap
                            Log.d(TAG, "  InputContent Reloaded Image[${index}]: Width=${bitmap.width}, Height=${bitmap.height}, Config=${bitmap.config?.name ?: "null"}, HasAlpha=${bitmap.hasAlpha()}, IsMutable=${bitmap.isMutable}")
                        }

                        // Log properties for chat.history images
                        chatHistory.forEachIndexed { historyIndex, contentItem ->
                            contentItem.parts.filterIsInstance<com.google.ai.client.generativeai.type.ImagePart>().forEachIndexed { partIndex, imagePart ->
                                val bitmap = imagePart.image // This is the reloaded Bitmap
                                Log.d(TAG, "  History[${historyIndex}] Reloaded Image[${partIndex}]: Width=${bitmap.width}, Height=${bitmap.height}, Config=${bitmap.config?.name ?: "null"}, HasAlpha=${bitmap.hasAlpha()}, IsMutable=${bitmap.isMutable}")
                            }
                        }

                        Log.d(TAG, "ACTION_EXECUTE_AI_CALL: Saving reloaded Bitmaps for visual integrity check.")

                        // Save reloaded bitmaps from inputContent
                        inputContent.parts.filterIsInstance<com.google.ai.client.generativeai.type.ImagePart>().forEachIndexed { index, imagePart ->
                            val reloadedBitmap = imagePart.image
                            val reloadedBitmapDebugPath = com.google.ai.sample.util.ImageUtils.saveBitmapToTempFile(applicationContext, reloadedBitmap)
                            if (reloadedBitmapDebugPath != null) {
                                Log.d(TAG, "  InputContent Reloaded Image[${index}] (for debug) also saved to: $reloadedBitmapDebugPath. Compare with original.")
                            }
                        }

                        // Save reloaded bitmaps from chat.history
                        chatHistory.forEachIndexed { historyIndex, contentItem ->
                            contentItem.parts.filterIsInstance<com.google.ai.client.generativeai.type.ImagePart>().forEachIndexed { partIndex, imagePart ->
                                val reloadedBitmap = imagePart.image
                                val reloadedBitmapDebugPath = com.google.ai.sample.util.ImageUtils.saveBitmapToTempFile(applicationContext, reloadedBitmap)
                                if (reloadedBitmapDebugPath != null) {
                                    Log.d(TAG, "  History[${historyIndex}] Reloaded Image[${partIndex}] (for debug) also saved to: $reloadedBitmapDebugPath. Compare with original.")
                                }
                            }
                        }
                        try {
                            if (apiProvider == ApiProvider.OFFLINE_GEMMA) {
                                val result = callOfflineGemmaApi(chatHistory, inputContent)
                                responseText = result.first
                                errorMessage = result.second
                            } else if (apiProvider == ApiProvider.VERCEL) {
                                val result = callVercelApi(modelName, apiKey, chatHistory, inputContent)
                                responseText = result.first
                                errorMessage = result.second
                            } else {
                                val generativeModel = GenerativeModel(
                                    modelName = modelName,
                                    apiKey = apiKey
                                )
                                val tempChat = generativeModel.startChat(history = chatHistory)
                                val fullResponse = StringBuilder()
                                tempChat.sendMessageStream(inputContent).collect { chunk ->
                                    chunk.text?.let {
                                        fullResponse.append(it)
                                        val streamIntent = Intent(ACTION_AI_STREAM_UPDATE).apply {
                                            putExtra(EXTRA_AI_STREAM_CHUNK, it)
                                        }
                                        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(streamIntent)
                                    }
                                }
                                responseText = fullResponse.toString()
                            }
                        } catch (e: MissingFieldException) {
                            Log.e(TAG, "Serialization error, potentially a 503 error.", e)
                            // Check if the error message indicates a 503-like error
                            if (e.message?.contains("UNAVAILABLE") == true ||
                                e.message?.contains("503") == true ||
                                e.message?.contains("overloaded") == true) {
                                errorMessage = "Service Unavailable (503) - Retry with new key"
                            } else {
                                errorMessage = e.localizedMessage ?: "Serialization error"
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Direct error in AI call", e)
                            // Also check for 503 patterns in general exceptions
                            if (e.message?.contains("503") == true ||
                                e.message?.contains("overloaded") == true ||
                                e.message?.contains("UNAVAILABLE") == true) {
                                errorMessage = "Service Unavailable (503) - Retry with new key"
                            } else {
                                errorMessage = e.localizedMessage ?: "AI call failed"
                            }
                        }

                    } catch (e: Exception) {
                        // Catching general exceptions from model/chat operations or serialization
                        Log.e(TAG, "Outer error during AI call execution", e)

                        // Check if this is a 503-related error
                        if (e is MissingFieldException &&
                            (e.message?.contains("GRpcError") == true ||
                            e.message?.contains("UNAVAILABLE") == true ||
                            e.message?.contains("503") == true ||
                            e.message?.contains("overloaded") == true)) {
                            errorMessage = "Service Unavailable (503) - Retry with new key"
                        } else if (e.message?.contains("503") == true ||
                                e.message?.contains("overloaded") == true ||
                                e.message?.contains("UNAVAILABLE") == true) {
                            errorMessage = "Service Unavailable (503) - Retry with new key"
                        } else {
                            errorMessage = e.localizedMessage ?: "Unknown error"
                        }
                    }
                    finally {
                        // Broadcast the result (success or error) back to the ViewModel.
                        val resultIntent = Intent(ACTION_AI_CALL_RESULT).apply {
                            if (responseText != null && errorMessage == null) {
                                putExtra(EXTRA_AI_RESPONSE_TEXT, responseText)
                            }
                            if (errorMessage != null) {
                                putExtra(EXTRA_AI_ERROR_MESSAGE, errorMessage)
                            }
                        }

                        if (errorMessage != null || responseText != null) {
                            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(resultIntent)
                            Log.d(TAG, "Local broadcast sent for AI_CALL_RESULT. Error: $errorMessage, Response: ${responseText != null}")
                        }

                        // Comment: Clean up temporary image files passed from the ViewModel.
                        if (tempFilePaths.isNotEmpty()) {
                            Log.d(TAG, "Cleaning up ${tempFilePaths.size} temporary image files.")
                            for (filePath in tempFilePaths) {
                                val deleted = com.google.ai.sample.util.ImageUtils.deleteFile(filePath)
                                if (!deleted) {
                                    Log.w(TAG, "Failed to delete temporary file: $filePath")
                                }
                            }
                        } else {
                            Log.d(TAG, "No temporary image files to clean up.")
                        }

                        // If we started foreground specifically for this AI call (i.e., !isReady), stop foreground now
                        // but KEEP THE SERVICE RUNNING (no stopSelf())
                        if (startedForegroundForAi) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            Log.d(TAG, "Stopped foreground after AI call (since not ready), but service remains running.")
                        }
                    }
                }
                // START_STICKY to keep the service sticky/persistent
                return START_STICKY
            }
            else -> {
                Log.w(TAG, "Unknown or null action received: ${intent?.action}.")
                // If service is started with unknown action and not ready, stop it.
                if (!isReady) {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun broadcastAiCallError(message: String) {
        val errorIntent = Intent(ACTION_AI_CALL_RESULT).apply {
            `package` = applicationContext.packageName
            putExtra(EXTRA_AI_ERROR_MESSAGE, message)
        }
        applicationContext.sendBroadcast(errorIntent)
        Log.d(TAG, "Broadcast error sent for AI_CALL_RESULT: $message")
    }

    private fun createAiOperationNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID) // Reuse existing channel
            .setContentTitle("Screen Operator")
            .setContentText("Processing AI request...")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Replace with a proper app icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false) // AI operation is not typically as long as screen capture
            .build()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Capture Active")
            .setContentText("Ready to take screenshots")
            .setSmallIcon(android.R.drawable.ic_menu_camera) // Replace with a proper app icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for screen capture service"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    private fun startCapture(resultCode: Int, data: Intent, takeScreenshotOnStart: Boolean) {
        try {
            Log.d(TAG, "startCapture: Getting MediaProjection, takeScreenshotOnStart: $takeScreenshotOnStart")
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

            mediaProjection?.unregisterCallback(mediaProjectionCallback) // Unregister old before stopping
            mediaProjection?.stop() // Stop any existing projection

            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection is null after getMediaProjection call")
                isReady = false
                cleanup() // Use cleanup to stop foreground and self
                return
            }
            mediaProjection?.registerCallback(mediaProjectionCallback, Handler(Looper.getMainLooper()))
            isReady = true
            Log.d(TAG, "MediaProjection ready.")

            if (takeScreenshotOnStart) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if(isReady && mediaProjection != null) {
                        Log.d(TAG, "startCapture: Taking initial screenshot after delay because takeScreenshotOnStart was true.")
                        takeScreenshot()
                    } else {
                        Log.w(TAG, "startCapture: Conditions to take initial screenshot not met after delay, even though takeScreenshotOnStart was true. isReady=$isReady, mediaProjectionIsNull=${mediaProjection==null}")
                    }
                }, 500)
            } else {
                Log.d(TAG, "startCapture: MediaProjection initialized, but skipping immediate screenshot as takeScreenshotOnStart is false.")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in startCapture", e)
            isReady = false
            cleanup() // Use cleanup to stop foreground and self
        }
    }

    private fun takeScreenshot() {
        if (!isReady || mediaProjection == null) {
            Log.e(TAG, "Cannot take screenshot - service not ready or mediaProjection is null. isReady=$isReady, mediaProjectionIsNull=${mediaProjection == null}")
            return
        }
        isScreenshotRequestedRef.set(true)
        Log.d(TAG, "takeScreenshot: Preparing to capture. isScreenshotRequestedRef set to true.")

        try {
            // Check if we need to initialize VirtualDisplay and ImageReader
            if (virtualDisplay == null || imageReader == null) {
                val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val displayMetrics = DisplayMetrics()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val defaultDisplay = windowManager.defaultDisplay
                    if (defaultDisplay != null) {
                        defaultDisplay.getRealMetrics(displayMetrics)
                    } else {
                        val bounds = windowManager.currentWindowMetrics.bounds
                        displayMetrics.widthPixels = bounds.width()
                        displayMetrics.heightPixels = bounds.height()
                        displayMetrics.densityDpi = resources.displayMetrics.densityDpi
                    }
                } else {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay.getMetrics(displayMetrics)
                }

                val width = displayMetrics.widthPixels
                val height = displayMetrics.heightPixels
                val density = displayMetrics.densityDpi

                if (width <= 0 || height <= 0) {
                    Log.e(TAG, "Invalid display dimensions: ${width}x${height}. Cannot create ImageReader.")
                    return
                }
                Log.d(TAG, "Display dimensions: ${width}x${height}, density: $density")

                imageReader?.close() // Close previous reader if any
                virtualDisplay?.release() // Release previous display if any

                imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
                val localImageReader = imageReader ?: run {
                    Log.e(TAG, "ImageReader is null after creation attempt.")
                    return
                }

                localImageReader.setOnImageAvailableListener({ reader ->
                    if (isScreenshotRequestedRef.compareAndSet(true, false)) {
                        Log.d(TAG, "Screenshot request flag consumed, processing image.")
                        var image: android.media.Image? = null
                        try {
                            image = reader.acquireLatestImage()
                            if (image != null) {
                                val planes = image.planes
                                val buffer = planes[0].buffer
                                val pixelStride = planes[0].pixelStride
                                val rowStride = planes[0].rowStride
                                val rowPadding = rowStride - pixelStride * width

                                val bitmap = Bitmap.createBitmap(
                                    width + rowPadding / pixelStride,
                                    height,
                                    Bitmap.Config.ARGB_8888
                                )
                                bitmap.copyPixelsFromBuffer(buffer)
                                Log.d(TAG, "Bitmap created, proceeding to save.")
                                saveScreenshot(bitmap)
                            } else {
                                Log.w(TAG, "acquireLatestImage returned null despite requested flag.")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing image in listener", e)
                        } finally {
                            image?.close()
                            // Do NOT release VirtualDisplay or ImageReader here
                            // They will be reused for the next screenshot
                            Log.d(TAG, "Screenshot processed (or attempted), keeping resources for reuse.")
                        }
                    } else {
                        // Logic to discard the frame if no screenshot was formally requested
                        var imageToDiscard: android.media.Image? = null
                        try {
                            imageToDiscard = reader.acquireLatestImage()
                        } catch (e: Exception) {
                            // This catch is important because acquireLatestImage can fail if buffers are truly messed up
                            Log.e(TAG, "Error acquiring image to discard in OnImageAvailableListener else block", e)
                        } finally {
                            imageToDiscard?.close()
                        }
                    }
                }, Handler(Looper.getMainLooper()))

                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "ScreenCapture",
                    width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    localImageReader.surface,
                    object : VirtualDisplay.Callback() {
                        override fun onPaused() { Log.d(TAG, "VirtualDisplay paused") }
                        override fun onResumed() { Log.d(TAG, "VirtualDisplay resumed") }
                        override fun onStopped() { Log.d(TAG, "VirtualDisplay stopped") }
                    },
                    Handler(Looper.getMainLooper())
                )

                if (virtualDisplay == null) {
                    Log.e(TAG, "Failed to create VirtualDisplay.")
                    localImageReader.close() // Clean up the reader we just created
                    this.imageReader = null
                    return
                }
                Log.d(TAG, "VirtualDisplay and ImageReader initialized for reuse.")
            } else {
                // Resources already exist, just trigger a new capture
                Log.d(TAG, "Using existing VirtualDisplay and ImageReader.")
                // Force the ImageReader to capture a new frame
                // The listener is already set up and will handle the new image
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in takeScreenshot setup", e)
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
        }
    }

    private fun saveScreenshot(bitmap: Bitmap) {
        try {
            val picturesDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Screenshots")
            if (!picturesDir.exists()) {
                picturesDir.mkdirs()
            }

            // List existing screenshot files
            val screenshotFiles = picturesDir.listFiles { _, name ->
                name.startsWith("screenshot_") && name.endsWith(".png")
            }?.toMutableList() ?: mutableListOf()

            // Sort files by name (timestamp) to find the oldest
            screenshotFiles.sortBy { it.name }

            // If count is 100 or more, delete oldest ones until count is 99
            // This makes space for the new screenshot, keeping the total at a max of 100
            val maxScreenshots = 100
            var screenshotsToDelete = screenshotFiles.size - (maxScreenshots -1) // Number of files to delete to make space for the new one

            if (screenshotsToDelete > 0) {
                Log.i(TAG, "Max screenshots reached. Current count: ${screenshotFiles.size}. Attempting to delete $screenshotsToDelete oldest screenshot(s).")
                for (i in 0 until screenshotsToDelete) {
                    if (i < screenshotFiles.size) {
                        val oldestFile = screenshotFiles[i]
                        if (oldestFile.delete()) {
                            Log.i(TAG, "Deleted oldest screenshot: ${oldestFile.absolutePath}")
                        } else {
                            Log.e(TAG, "Failed to delete oldest screenshot: ${oldestFile.absolutePath}")
                        }
                    }
                }
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(picturesDir, "screenshot_$timestamp.png")

            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()

            Log.i(TAG, "Screenshot saved to: ${file.absolutePath}")

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    applicationContext,
                    "Screenshot saved to: Android/data/com.google.ai.sample/files/Pictures/Screenshots/",
                    Toast.LENGTH_LONG
                ).show()
            }

            val screenshotUri = Uri.fromFile(file)
            val intent = Intent(MainActivity.ACTION_MEDIAPROJECTION_SCREENSHOT_CAPTURED).apply {
                putExtra(MainActivity.EXTRA_SCREENSHOT_URI, screenshotUri.toString())
                `package` = applicationContext.packageName
            }
            applicationContext.sendBroadcast(intent)
            Log.d(TAG, "Sent broadcast ACTION_MEDIAPROJECTION_SCREENSHOT_CAPTURED with URI: $screenshotUri")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save screenshot", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, "Failed to save screenshot: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun cleanup() {
        Log.d(TAG, "cleanup() called. Cleaning up all MediaProjection resources.")
        try {
            isReady = false
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null

            mediaProjection?.unregisterCallback(mediaProjectionCallback)
            mediaProjection?.stop()
            mediaProjection = null

        llmInference?.close()
        llmInference = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during full cleanup", e)
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf() // This will trigger onDestroy eventually
            instance = null // Clear static instance
            Log.d(TAG, "Full cleanup finished, service fully stopped.")
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Service being destroyed")
        // Cleanup is called from ACTION_STOP_CAPTURE or if projection stops externally.
        // If service is killed by system, this ensures cleanup too.
        if (isReady || mediaProjection != null) { // Check if cleanup is actually needed
           cleanup()
        }
        serviceScope.cancel() // Cancel all coroutines in this scope
        instance = null // Ensure instance is cleared
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun callOfflineGemmaApi(chatHistory: List<Content>, inputContent: Content): Pair<String?, String?> {
        var responseText: String? = null
        var errorMessage: String? = null

        try {
            val inference = getLlmInference() ?: return Pair(null, "Offline model not found or failed to initialize. Please download it first.")

            // Construct prompt from history and input
            val promptBuilder = StringBuilder()

            // Add history
            chatHistory.forEach { content ->
                val role = if (content.role == "user") "user" else "model"
                content.parts.filterIsInstance<TextPart>().forEach {
                    promptBuilder.append("<start_of_turn>$role\n${it.text}<end_of_turn>\n")
                }
            }

            // Add current input
            inputContent.parts.filterIsInstance<TextPart>().forEach {
                promptBuilder.append("<start_of_turn>user\n${it.text}<end_of_turn>\n<start_of_turn>model\n")
            }

            val prompt = promptBuilder.toString()
            Log.d(TAG, "Offline prompt: $prompt")

            // Use generateResponse for simplicity in this broadcast-based architecture
            // but we can simulate streaming chunks if needed.
            // For now, just get the full response and send it.
            responseText = inference.generateResponse(prompt)

            // Broadcast the result as a stream chunk too, so the UI updates as if it was streaming
            if (responseText != null) {
                val streamIntent = Intent(ACTION_AI_STREAM_UPDATE).apply {
                    putExtra(EXTRA_AI_STREAM_CHUNK, responseText)
                }
                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(streamIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Offline Gemma call failed", e)
            errorMessage = e.localizedMessage ?: "Offline Gemma call failed"
        }

        return Pair(responseText, errorMessage)
    }

    private fun getLlmInference(): LlmInference? {
        if (llmInference != null) return llmInference

        try {
            val modelFile = ModelDownloadManager.getModelFile(applicationContext)
            if (!modelFile.exists()) {
                return null
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTopK(40)
                .build()

            llmInference = LlmInference.createFromOptions(applicationContext, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create LlmInference", e)
        }
        return llmInference
    }
}

// Data classes for Vercel API
@Serializable
data class VercelRequest(
    val model: String,
    val messages: List<VercelMessage>
)

@Serializable
data class VercelMessage(
    val role: String,
    val content: List<VercelContent>
)

@Serializable
data class VercelResponse(
    val choices: List<VercelChoice>
)

@Serializable
data class VercelChoice(
    val message: VercelResponseMessage
)

@Serializable
data class VercelResponseMessage(
    val role: String,
    val content: String
)

@Serializable
@JsonClassDiscriminator("type")
sealed class VercelContent

@Serializable
@SerialName("text")
data class VercelTextContent(@SerialName("text") val content: String) : VercelContent()

@Serializable
@SerialName("image_url")
data class VercelImageContent(@SerialName("image_url") val content: VercelImageUrl) : VercelContent()

@Serializable
data class VercelImageUrl(val url: String)

private fun Bitmap.toBase64(): String {
    val outputStream = java.io.ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
    return "data:image/jpeg;base64," + android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.DEFAULT)
}

private suspend fun callVercelApi(modelName: String, apiKey: String, chatHistory: List<Content>, inputContent: Content): Pair<String?, String?> {
    var responseText: String? = null
    var errorMessage: String? = null

    val json = Json {
        serializersModule = SerializersModule {
            polymorphic(VercelContent::class) {
                subclass(VercelTextContent::class, VercelTextContent.serializer())
                subclass(VercelImageContent::class, VercelImageContent.serializer())
            }
        }
        ignoreUnknownKeys = true
    }

    try {
        val messages = (chatHistory + inputContent).map { content ->
            val parts = content.parts.map { part ->
                when (part) {
                    is TextPart -> VercelTextContent(content = part.text)
                    is ImagePart -> VercelImageContent(content = VercelImageUrl(url = part.image.toBase64()))
                    else -> VercelTextContent(content = "") // Or handle other part types appropriately
                }
            }
            VercelMessage(role = if (content.role == "user") "user" else "assistant", content = parts)
        }

        val requestBody = VercelRequest(
            model = modelName,
            messages = messages
        )

        val client = OkHttpClient()
        val mediaType = "application/json".toMediaType()
        val jsonBody = json.encodeToString(VercelRequest.serializer(), requestBody)

        val request = Request.Builder()
            .url("https://ai-gateway.vercel.sh/v1/chat/completions")
            .post(jsonBody.toRequestBody(mediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                errorMessage = "Unexpected code ${response.code} - ${response.body?.string()}"
            } else {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val json = Json { ignoreUnknownKeys = true }
                    val vercelResponse = json.decodeFromString(VercelResponse.serializer(), responseBody)
                    responseText = vercelResponse.choices.firstOrNull()?.message?.content ?: "No response from model"
                } else {
                    errorMessage = "Empty response body"
                }
            }
        }
    } catch (e: Exception) {
        errorMessage = e.localizedMessage ?: "Vercel API call failed"
    }

    return Pair(responseText, errorMessage)
}
