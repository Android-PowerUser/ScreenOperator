package com.google.ai.sample

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.media.projection.MediaProjectionManager
import android.media.projection.MediaProjection
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.View
import android.widget.Toast
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.webkit.JavascriptInterface
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.PendingPurchasesParams
import com.google.ai.sample.feature.multimodal.PhotoReasoningRoute
import com.google.ai.sample.feature.multimodal.PhotoReasoningViewModel
import com.google.ai.sample.GenerativeAiViewModelFactory
import com.google.ai.sample.ui.theme.GenerativeAISample
import com.google.ai.sample.util.BroadcastReceiverCompat
import com.google.ai.sample.util.NotificationUtil
import com.google.ai.sample.util.TermuxExecutionModePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.google.ai.sample.GenerativeViewModelFactory
import androidx.activity.result.PickVisualMediaRequest
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision

class MainActivity : ComponentActivity() {

    // Keyboard Visibility
    private val _isKeyboardOpen = MutableStateFlow(false)
    val isKeyboardOpen: StateFlow<Boolean> = _isKeyboardOpen.asStateFlow()
    private val keyboardVisibilityObserver = KeyboardVisibilityObserver(TAG)

    private var photoReasoningViewModel: PhotoReasoningViewModel? = null
    internal lateinit var apiKeyManager: ApiKeyManager
    private var showApiKeyDialog by mutableStateOf(false)
    private var apiKeyDialogInitialProvider by mutableStateOf<ApiProvider?>(null)

    // Google Play Billing
    private lateinit var billingClient: BillingClient
    private var monthlyDonationProductDetails: ProductDetails? = null
    private val subscriptionProductId = "donation_monthly_2_90_eur"

    private var currentTrialState by mutableStateOf(TrialManager.TrialState.NOT_YET_STARTED_AWAITING_INTERNET)
    private var showTrialInfoDialog by mutableStateOf(false)
    private var trialInfoMessage by mutableStateOf("")

    private var permissionRequestCount by mutableStateOf(0)
    private var webViewHtmlContent by mutableStateOf<String?>(null)
    private var webViewInstance: WebView? = null
    private var webViewObserversStarted = false

    // MediaProjection
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var mediaProjectionLauncher: ActivityResultLauncher<Intent>
    private lateinit var webRtcMediaProjectionLauncher: ActivityResultLauncher<Intent>
    private lateinit var pickMediaLauncher: ActivityResultLauncher<PickVisualMediaRequest>

    private var currentScreenInfoForScreenshot: String? = null

    private lateinit var navController: NavHostController
    private var isProcessingExplicitScreenshotRequest: Boolean = false
    private var onMediaProjectionPermissionGranted: (() -> Unit)? = null
    private var onWebRtcMediaProjectionResult: ((Int, Intent) -> Unit)? = null
    private var onTermuxRunCommandPermissionResult: ((Boolean) -> Unit)? = null
    private val mediaProjectionServiceStarter by lazy { MediaProjectionServiceStarter(this) }

    // Payment dialog state
    private var showPaymentMethodDialog by mutableStateOf(false)
    private var showPayPalWebViewDialog by mutableStateOf(false)
    private var paypalSubscriptionId by mutableStateOf("")

    private val screenshotRequestHandler = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_REQUEST_MEDIAPROJECTION_SCREENSHOT) {
                handleScreenshotRequest(intent)
            }
        }
    }

    private val screenshotResultHandler = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_MEDIAPROJECTION_SCREENSHOT_CAPTURED) {
                handleScreenshotResult(intent)
            }
        }
    }

    // Permission Launchers
    private lateinit var requestNotificationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestForegroundServicePermissionLauncher: ActivityResultLauncher<String>
    private val foregroundMediaProjectionPermission = android.Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION


    fun requestTermuxRunCommandPermission(onResult: (Boolean) -> Unit) {
        Log.d(TAG, "Requesting Termux RUN_COMMAND permission without pre-check")
        onTermuxRunCommandPermissionResult = onResult
        ActivityCompat.requestPermissions(
            this,
            arrayOf(TERMUX_RUN_COMMAND_PERMISSION),
            REQUEST_CODE_TERMUX_RUN_COMMAND_PERMISSION
        )
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_TERMUX_RUN_COMMAND_PERMISSION) {
            val isGranted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Termux RUN_COMMAND permission result: $isGranted")
            onTermuxRunCommandPermissionResult?.invoke(isGranted)
            onTermuxRunCommandPermissionResult = null
        }
    }

    fun requestMediaProjectionPermission(onGranted: (() -> Unit)? = null) {
        Log.d(TAG, "Requesting MediaProjection permission")
        onMediaProjectionPermissionGranted = onGranted

        requestForegroundMediaProjectionPermissionIfMissing()

        launchCaptureIntent(mediaProjectionLauncher, "requestMediaProjectionPermission")
    }

    private fun hasForegroundMediaProjectionPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, foregroundMediaProjectionPermission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestForegroundMediaProjectionPermissionIfMissing(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasForegroundMediaProjectionPermission()) {
            requestForegroundServicePermissionLauncher.launch(foregroundMediaProjectionPermission)
            return true
        }
        return false
    }

    /**
     * Request a fresh MediaProjection permission specifically for WebRTC (Human Expert).
     * This does NOT start ScreenCaptureService - the result is passed directly to the callback.
     */
    fun requestMediaProjectionForWebRTC(onResult: (Int, Intent) -> Unit) {
        Log.d(TAG, "Requesting MediaProjection permission for WebRTC")
        onWebRtcMediaProjectionResult = onResult

        launchCaptureIntent(webRtcMediaProjectionLauncher, "requestMediaProjectionForWebRTC")
    }

    private fun initializeMediaProjection() {
        Log.d(TAG, "onCreate: Initializing MediaProjectionManager")
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        Log.d(TAG, "onCreate: Initializing MediaProjection launcher")
        mediaProjectionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            handleMediaProjectionResult(result.resultCode, result.data)
        }

        webRtcMediaProjectionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            handleWebRtcMediaProjectionResult(result.resultCode, result.data)
        }

        pickMediaLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { 
                Log.d(TAG, "Selected image/video URI from picker: $it")
                val isVideo = contentResolver.getType(it)?.startsWith("video/") == true
                webViewInstance?.post { 
                    webViewInstance?.evaluateJavascript("window.onImagePicked('$it', $isVideo)", null)
                }
            }
        }
    }

    private fun handleMediaProjectionResult(resultCode: Int, resultData: Intent?) {
        if (resultCode == Activity.RESULT_OK && resultData != null) {
            handleMediaProjectionPermissionGranted(resultCode, resultData)
        } else {
            handleMediaProjectionPermissionDenied()
        }
    }

    private fun handleMediaProjectionPermissionGranted(resultCode: Int, resultData: Intent) {
        val shouldTakeScreenshotOnThisStart = isProcessingExplicitScreenshotRequest
        Log.i(TAG, "MediaProjection permission granted. Starting ScreenCaptureService. Explicit request: $shouldTakeScreenshotOnThisStart")

        photoReasoningViewModel?.onMediaProjectionPermissionGranted(resultCode, resultData)

        val serviceIntent = MainActivityMediaProjectionIntents.startCapture(
            context = this,
            resultCode = resultCode,
            resultData = resultData,
            takeScreenshotOnStart = shouldTakeScreenshotOnThisStart
        )
        if (!requestForegroundMediaProjectionPermissionIfMissing()) {
            mediaProjectionServiceStarter.start(serviceIntent)
        }

        resetExplicitScreenshotRequestFlagIfNeeded("successful explicit grant")
        _isMediaProjectionPermissionGranted.value = true
        onMediaProjectionPermissionGranted?.invoke()
        onMediaProjectionPermissionGranted = null
    }

    private fun handleMediaProjectionPermissionDenied() {
        Log.w(TAG, "MediaProjection permission denied or cancelled by user.")
        Toast.makeText(this, com.google.ai.sample.util.UiStringsConfig.get("toast_screen_capture_permission_denied", "Screen capture permission denied"), Toast.LENGTH_SHORT).show()
        resetExplicitScreenshotRequestFlagIfNeeded("explicit denial")
        _isMediaProjectionPermissionGranted.value = false
    }

    private fun handleWebRtcMediaProjectionResult(resultCode: Int, resultData: Intent?) {
        if (resultCode == Activity.RESULT_OK && resultData != null) {
            handleWebRtcMediaProjectionPermissionGranted(resultCode, resultData)
        } else {
            handleWebRtcMediaProjectionPermissionDenied()
        }
    }

    private fun handleWebRtcMediaProjectionPermissionGranted(resultCode: Int, resultData: Intent) {
        Log.i(TAG, "WebRTC MediaProjection permission granted. Starting keep-alive service.")

        val serviceIntent = MainActivityMediaProjectionIntents.keepAliveForWebRtc(this)
        mediaProjectionServiceStarter.start(serviceIntent)

        onWebRtcMediaProjectionResult?.invoke(resultCode, resultData)
        onWebRtcMediaProjectionResult = null
    }

    private fun handleWebRtcMediaProjectionPermissionDenied() {
        Log.w(TAG, "WebRTC MediaProjection permission denied.")
        Toast.makeText(this, com.google.ai.sample.util.UiStringsConfig.get("toast_screen_capture_permission_denied", "Screen capture permission denied"), Toast.LENGTH_SHORT).show()
        onWebRtcMediaProjectionResult = null
    }

    private fun setupKeyboardVisibilityListener() {
        val rootView = findViewById<View>(android.R.id.content)
        keyboardVisibilityObserver.start(rootView, _isKeyboardOpen)
    }

    private fun registerScreenshotReceivers() {
        Log.d(TAG, "Registering screenshotRequestHandler for ACTION_REQUEST_MEDIAPROJECTION_SCREENSHOT.")
        val requestFilter = IntentFilter(ACTION_REQUEST_MEDIAPROJECTION_SCREENSHOT)
        BroadcastReceiverCompat.register(this, screenshotRequestHandler, requestFilter)

        Log.d(TAG, "Registering screenshotResultHandler for ACTION_MEDIAPROJECTION_SCREENSHOT_CAPTURED.")
        val resultFilter = IntentFilter(ACTION_MEDIAPROJECTION_SCREENSHOT_CAPTURED)
        BroadcastReceiverCompat.register(this, screenshotResultHandler, resultFilter)
    }

    private fun registerPermissionLaunchers() {
        requestNotificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, com.google.ai.sample.util.UiStringsConfig.get("toast_notification_permission_granted", "Notification permission granted."), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, com.google.ai.sample.util.UiStringsConfig.get("toast_notification_permission_denied", "Notification permission denied. Stop via notification will not be available."), Toast.LENGTH_LONG).show()
            }
        }

        requestForegroundServicePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, com.google.ai.sample.util.UiStringsConfig.get("toast_foreground_service_permission_granted", "Foreground service permission granted."), Toast.LENGTH_SHORT).show()
                launchCaptureIntent(mediaProjectionLauncher, "requestForegroundServicePermissionLauncher")
            } else {
                Toast.makeText(this, com.google.ai.sample.util.UiStringsConfig.get("toast_foreground_service_permission_denied", "Foreground service permission denied. The app may not function correctly."), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun observeStopNotificationFlow() {
        val viewModel = photoReasoningViewModel
        if (viewModel != null) {
            lifecycleScope.launch {
                viewModel.showStopNotificationFlow.collect { show ->
                    Log.d(TAG, "showStopNotificationFlow collected value: $show")
                    if (show) {
                        Log.d(TAG, "Calling showStopOperationNotification()")
                        showStopOperationNotification()
                    } else {
                        Log.d(TAG, "Calling cancelStopOperationNotification()")
                        cancelStopOperationNotification()
                    }
                }
            }
        } else {
            Log.w(TAG, "photoReasoningViewModel is null at the end of onCreate. Notification flow collection might be delayed or not start if VM is set much later or never.")
        }
    }

    private fun handleScreenshotRequest(intent: Intent) {
        Log.d(TAG, "Received request for screenshot via broadcast.")
        currentScreenInfoForScreenshot = MainActivityScreenshotIntents.extractScreenInfo(intent)
        Log.d(TAG, "Stored screenInfo for upcoming screenshot.")

        when (MainActivityScreenshotFlowDecider.decide(ScreenCaptureService.isRunning())) {
            MainActivityScreenshotFlowDecider.Action.TAKE_ADDITIONAL_SCREENSHOT -> {
                Log.d(TAG, "ScreenCaptureService is running. Calling takeAdditionalScreenshot().")
                takeAdditionalScreenshot()
            }
            MainActivityScreenshotFlowDecider.Action.REQUEST_PERMISSION -> {
                Log.d(TAG, "ScreenCaptureService not running. Calling requestMediaProjectionPermission() to start it.")
                isProcessingExplicitScreenshotRequest = true
                requestMediaProjectionPermission()
            }
        }
    }

    private fun handleScreenshotResult(intent: Intent) {
        val screenshotUri = MainActivityScreenshotIntents.extractScreenshotUri(intent)
        if (screenshotUri != null) {
            Log.d(TAG, "Received screenshot captured broadcast. URI: $screenshotUri")
            Log.d(TAG, "Using screenInfo: ${currentScreenInfoForScreenshot?.substring(0, minOf(100, currentScreenInfoForScreenshot?.length ?: 0))}...")

            photoReasoningViewModel?.addScreenshotToConversation(
                screenshotUri,
                this,
                currentScreenInfoForScreenshot
            )
            currentScreenInfoForScreenshot = null
        } else {
            Log.e(TAG, "Screenshot URI was null in broadcast.")
        }
    }

    private fun isMediaProjectionManagerInitialized(caller: String): Boolean {
        if (!::mediaProjectionManager.isInitialized) {
            Log.e(TAG, "$caller: mediaProjectionManager not initialized!")
            return false
        }
        return true
    }

    private fun launchCaptureIntent(
        launcher: ActivityResultLauncher<Intent>,
        caller: String
    ) {
        if (!isMediaProjectionManagerInitialized(caller)) {
            return
        }
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        launcher.launch(intent)
    }

    private fun resetExplicitScreenshotRequestFlagIfNeeded(reason: String) {
        if (isProcessingExplicitScreenshotRequest) {
            Log.d(TAG, "Resetting isProcessingExplicitScreenshotRequest flag after $reason.")
            isProcessingExplicitScreenshotRequest = false
        }
    }

    fun takeAdditionalScreenshot() {
        if (ScreenCaptureService.isRunning()) {
            Log.d(TAG, "MainActivity: Instructing ScreenCaptureService to take an additional screenshot.")
            val intent = MainActivityMediaProjectionIntents.takeScreenshot(this)
            // Use startService as the service is already foreground if running.
            // If it somehow wasn't foreground but running, this still works.
            startService(intent)
        } else {
            Log.w(TAG, "MainActivity: takeAdditionalScreenshot called but service is not running. Requesting permission first.")
            // Store a flag or handle the screenInfo persistence if this call implies an immediate need
            // For now, rely on the standard flow where screenInfo is passed with the initial request.
            // This situation (service not running but takeAdditionalScreenshot called directly)
            // should ideally be handled by the caller checking isRunning() first.
            // If called from screenshotRequestHandler, it would have called requestMediaProjectionPermission instead.
            Toast.makeText(this, com.google.ai.sample.util.UiStringsConfig.get("toast_screenshot_service_not_active", "Screenshot service not active. Please grant permission first."), Toast.LENGTH_LONG).show()
            // Optionally, trigger permission request again if appropriate for the use case.
            // requestMediaProjectionPermission() // This might be too aggressive if called from unexpected places.
        }
    }

    fun stopScreenCaptureService() {
        if (ScreenCaptureService.isRunning()) { // Check if it's actually running to avoid errors
            Log.d(TAG, "MainActivity: Instructing ScreenCaptureService to stop.")
            val intent = MainActivityMediaProjectionIntents.stopCapture(this)
            startService(intent)
        } else {
            Log.d(TAG, "MainActivity: stopScreenCaptureService called, but service was not running.")
        }
    }

    private val _isAccessibilityServiceEnabled = MutableStateFlow(false)
    val isAccessibilityServiceEnabledFlow: StateFlow<Boolean> = _isAccessibilityServiceEnabled.asStateFlow()

    private val _isMediaProjectionPermissionGranted = MutableStateFlow(false)
    val isMediaProjectionPermissionGrantedFlow: StateFlow<Boolean> = _isMediaProjectionPermissionGranted.asStateFlow()

    // SharedPreferences for first launch info
    private lateinit var prefs: SharedPreferences
    private var showFirstLaunchInfoDialog by mutableStateOf(false)

    private val trialStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.i(TAG, "trialStatusReceiver: Received broadcast: ${intent?.action}")
            when (intent?.action) {
                TrialTimerService.ACTION_TRIAL_EXPIRED -> {
                    Log.i(TAG, "trialStatusReceiver: ACTION_TRIAL_EXPIRED received. Updating trial state.")
                    updateTrialState(TrialManager.getTrialState(this@MainActivity, null))
                }
                TrialTimerService.ACTION_INTERNET_TIME_UNAVAILABLE -> {
                    Log.i(TAG, "trialStatusReceiver: ACTION_INTERNET_TIME_UNAVAILABLE received. Current state: $currentTrialState")
                    updateTrialState(TrialManager.getTrialState(this@MainActivity, null))
                }
                TrialTimerService.ACTION_INTERNET_TIME_AVAILABLE -> {
                    val internetTime = intent.getLongExtra(TrialTimerService.EXTRA_CURRENT_UTC_TIME_MS, 0L)
                    Log.i(TAG, "trialStatusReceiver: ACTION_INTERNET_TIME_AVAILABLE received. InternetTime: $internetTime")
                    if (internetTime > 0) {
                        Log.d(TAG, "trialStatusReceiver: Valid internet time received. Calling TrialManager.startTrialIfNecessaryWithInternetTime.")
                        TrialManager.startTrialIfNecessaryWithInternetTime(this@MainActivity, internetTime)
                        Log.d(TAG, "trialStatusReceiver: Calling TrialManager.getTrialState with received internet time.")
                        val newState = TrialManager.getTrialState(this@MainActivity, internetTime)
                        Log.i(TAG, "trialStatusReceiver: State from TrialManager after internet time: $newState. Updating local state.")
                        updateTrialState(newState)
                    } else {
                        Log.w(TAG, "trialStatusReceiver: ACTION_INTERNET_TIME_AVAILABLE received, but internetTime is 0 or less. Checking state with null time.")
                        updateTrialState(TrialManager.getTrialState(this@MainActivity, null))
                    }
                }
                else -> {
                     Log.w(TAG, "trialStatusReceiver: Received unknown action: ${intent?.action}")
                }
            }
        }
    }

    private fun updateTrialState(newState: TrialManager.TrialState) {
        Log.d(TAG, "updateTrialState called with newState: $newState. Current local state: $currentTrialState")
        val oldState = currentTrialState
        currentTrialState = newState
        Log.i(TAG, "updateTrialState: Trial state updated from $oldState to $currentTrialState")
        val uiModel = TrialStateUiModelResolver.resolve(currentTrialState)
        trialInfoMessage = uiModel.infoMessage
        showTrialInfoDialog = uiModel.shouldShowInfoDialog
        Log.d(
            TAG,
            "updateTrialState: trialInfoMessage='${uiModel.infoMessage}', showTrialInfoDialog=${uiModel.shouldShowInfoDialog}"
        )

        // Notify the WebView so JS can update its UI (e.g. hide the Pro button after purchase).
        val isExpired = newState == TrialManager.TrialState.EXPIRED_INTERNET_TIME_CONFIRMED
        val isPurchased = newState == TrialManager.TrialState.PURCHASED
        val escapedMsg = escapeForJs(uiModel.infoMessage)
        webViewInstance?.post {
            webViewInstance?.evaluateJavascript(
                "window.onTrialStateChanged && window.onTrialStateChanged($isExpired, $isPurchased, '$escapedMsg')",
                null
            )
        }
    }

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        Log.i(TAG, "purchasesUpdatedListener: BillingResponseCode: ${billingResult.responseCode}, Message: ${billingResult.debugMessage}")
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            Log.d(TAG, "purchasesUpdatedListener: Purchases list size: ${purchases.size}")
            for (purchase in purchases) {
                Log.d(TAG, "purchasesUpdatedListener: Processing purchase: ${purchase.orderId}, Products: ${purchase.products}, State: ${purchase.purchaseState}")
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.i(TAG, "purchasesUpdatedListener: User cancelled the purchase flow.")
            Toast.makeText(this, com.google.ai.sample.util.UiStringsConfig.get("toast_support_cancelled", "Support cancelled."), Toast.LENGTH_SHORT).show()
        } else {
            Log.e(TAG, "purchasesUpdatedListener: Billing error: ${billingResult.debugMessage} (Code: ${billingResult.responseCode})")
            Toast.makeText(this, com.google.ai.sample.util.UiStringsConfig.get("toast_donation_error", "Error during donation process: {0}", billingResult.debugMessage), Toast.LENGTH_LONG).show()
        }
    }

    fun getCurrentApiKey(provider: ApiProvider): String? {
        val key = if (::apiKeyManager.isInitialized) {
            apiKeyManager.getCurrentApiKey(provider)
        } else {
            null
        }
        Log.d(TAG, "getCurrentApiKey for provider $provider returning: ${if (key.isNullOrEmpty()) "null or empty" else "valid key"}")
        return key
    }

    internal fun refreshAccessibilityServiceStatus() {
        Log.d(TAG, "refreshAccessibilityServiceStatus called.")
        val isEnabled = AccessibilityServiceStatusResolver.isServiceEnabled(
            contentResolver = contentResolver,
            packageName = packageName
        )
        _isAccessibilityServiceEnabled.value = isEnabled // Update the flow
        Log.d(TAG, "Accessibility Service isEnabled: $isEnabled. Flow updated.")
        if (!isEnabled) {
            Log.d(TAG, "Accessibility Service not enabled.")
        }
        val js = "window.onAccessibilityStateChanged && window.onAccessibilityStateChanged($isEnabled)"
        webViewInstance?.post { webViewInstance?.evaluateJavascript(js, null) }
    }

    internal fun requestManageExternalStoragePermission() {
        Log.d(TAG, "requestManageExternalStoragePermission (dummy) called.")
    }

    fun updateStatusMessage(message: String, isError: Boolean = false) {
        MainActivityStatusNotifier.showStatusMessage(
            context = this,
            tag = TAG,
            message = message,
            isError = isError
        )
    }

    fun getPhotoReasoningViewModel(): PhotoReasoningViewModel? {
        Log.d(TAG, "getPhotoReasoningViewModel called.")
        return photoReasoningViewModel
    }

    fun setPhotoReasoningViewModel(viewModel: PhotoReasoningViewModel) {
        Log.d(TAG, "setPhotoReasoningViewModel called.")
        this.photoReasoningViewModel = viewModel
    }

    /**
     * Opens the Accessibility Settings screen on the UI thread.
     * Safe to call from any thread (e.g. @JavascriptInterface background thread).
     */
    fun openAccessibilitySettings() {
        Log.d(TAG, "openAccessibilitySettings called.")
        runOnUiThread {
            try {
                startActivity(getAccessibilitySettingsIntent())
            } catch (e: Exception) {
                Log.e(TAG, "openAccessibilitySettings: failed to start activity", e)
            }
        }
    }

    fun getAccessibilitySettingsIntent(): Intent {
        Log.d(TAG, "getAccessibilitySettingsIntent called.")
        val componentName = "$packageName/${ScreenOperatorAccessibilityService::class.java.canonicalName}"
        // On Android 13+ we can jump directly to the service's detail page
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("extra_accessibility_shortcut_target_service", componentName)
                }
                // Verify something can handle this intent before returning it
                if (packageManager.resolveActivity(intent, 0) != null) {
                    Log.d(TAG, "getAccessibilitySettingsIntent: using targeted intent for $componentName")
                    return intent
                }
            } catch (e: Exception) {
                Log.w(TAG, "getAccessibilitySettingsIntent: targeted intent failed, falling back", e)
            }
        }
        // Fallback: open the general Accessibility Settings list
        Log.d(TAG, "getAccessibilitySettingsIntent: using general accessibility settings")
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }


    /**
     * On-disk cache file for the remote WebView `index.html`, so the next app start can show
     * the WebView UI immediately (from cache) instead of waiting for a network round trip,
     * while a fresh copy is fetched in the background and swapped in - replacing the cache -
     * as soon as it successfully arrives. If the network fetch fails, the cached copy (and
     * whatever is currently displayed) is left untouched.
     */
    private val webViewCacheFile: File
        get() = File(filesDir, "webview_index_cache.html")

    private fun readWebViewCache(): String? {
        return try {
            val f = webViewCacheFile
            if (f.exists() && f.length() > 0) {
                val content = f.readText(Charsets.UTF_8)
                Log.d(TAG, "readWebViewCache: Loaded cached HTML ({} Zeichen).".format(content.length))
                content
            } else {
                Log.d(TAG, "readWebViewCache: No cache file present yet.")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "readWebViewCache: Failed to read cache file", e)
            null
        }
    }

    private fun writeWebViewCache(content: String) {
        try {
            webViewCacheFile.writeText(content, Charsets.UTF_8)
            Log.d(TAG, "writeWebViewCache: Cache file updated ({} Zeichen).".format(content.length))
        } catch (e: Exception) {
            Log.e(TAG, "writeWebViewCache: Failed to write cache file", e)
        }
    }

    private fun loadWebViewContent() {
        val htmlUrl = "https://android-poweruser.github.io/ScreenOperator/index.html"

        // 1) Show the cached copy immediately, if any, so the WebView appears instantly on
        //    every start after the first successful load - without waiting on the network.
        if (webViewHtmlContent == null) {
            readWebViewCache()?.let { cached ->
                webViewHtmlContent = cached
            }
        }

        // 2) Always attempt a fresh network fetch in the background (regardless of whether a
        //    cached/in-memory copy is already showing), so a newly published index.html
        //    replaces the cached one for this session and for the next app start. This is the
        //    same "always try network, replace cache on success" behavior loadWebViewContent
        //    had before, just no longer gated on webViewHtmlContent being null so the swap can
        //    still happen after the cache has already been shown.
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(htmlUrl).build()
                val response = client.newCall(request).execute()
                val responseCode = response.code
                val body = response.body?.string()
                response.close()

                if (response.isSuccessful && !body.isNullOrBlank()) {
                    Log.d(TAG, "loadWebViewContent: HTML erfolgreich geladen ({} Zeichen).".format(body.length))
                    // Persist to disk first so the cache is updated even if this is the very
                    // first successful load of the app (nothing to compare against yet).
                    writeWebViewCache(body)
                    withContext(Dispatchers.Main) {
                        // Only trigger a (re)load of the WebView if the content actually
                        // changed, to avoid an unnecessary reload/flash when the fetched HTML
                        // is identical to what's already showing (e.g. cache hit + unchanged
                        // remote file).
                        if (webViewHtmlContent != body) {
                            webViewHtmlContent = body
                        }
                    }
                } else {
                    Log.e(TAG, "loadWebViewContent: Laden fehlgeschlagen. responseCode={}".format(responseCode))
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadWebViewContent: Exception beim Laden des HTML-Inhalts", e)
                // Network/parse failure: intentionally leave the cache file and the currently
                // displayed webViewHtmlContent (cached or null) untouched.
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate: Activity creating.")
        super.onCreate(savedInstanceState)
        // Erweitere den oberen Bereich (Inhalt unter transparenter Status Bar, keine Lücke)
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        instance = this
        Log.d(TAG, "onCreate: MainActivity instance set.")

        apiKeyManager = ApiKeyManager.getInstance(this)
        Log.d(TAG, "onCreate: ApiKeyManager initialized.")
        // Initialize ViewModel early so WebView mode can use it without PhotoReasoningRoute
        val vm: PhotoReasoningViewModel = ViewModelProvider(this as ViewModelStoreOwner, GenerativeViewModelFactory).get(PhotoReasoningViewModel::class.java)
        photoReasoningViewModel = vm
        Log.d(TAG, "onCreate: PhotoReasoningViewModel initialized early for WebView mode.")
        // API key dialog logic removed from onCreate as requested.
        // It will be triggered when needed (e.g., when the user tries to use an online model).

        Log.d(TAG, "onCreate: Calling setupBillingClient.")
        setupBillingClient()

        Log.d(TAG, "onCreate: Loading Model Preference.")
        GenerativeAiViewModelFactory.loadModelPreference(this)

        Log.d(TAG, "onCreate: Calling TrialManager.initializeTrialStateFlagsIfNecessary.")
        TrialManager.initializeTrialStateFlagsIfNecessary(this)

        Log.d(TAG, "onCreate: Setting up IntentFilter for trialStatusReceiver.")
        val intentFilter = IntentFilter().apply {
            addAction(TrialTimerService.ACTION_TRIAL_EXPIRED)
            addAction(TrialTimerService.ACTION_INTERNET_TIME_UNAVAILABLE)
            addAction(TrialTimerService.ACTION_INTERNET_TIME_AVAILABLE)
        }
        Log.d(TAG, "onCreate: Registering trialStatusReceiver.")
        BroadcastReceiverCompat.register(this, trialStatusReceiver, intentFilter)
        Log.d(TAG, "onCreate: trialStatusReceiver registered.")

        Log.d(TAG, "onCreate: Performing initial trial state check. Calling TrialManager.getTrialState with null time (will use local time).")
        val initialTrialState = TrialManager.getTrialState(this, null)
        Log.i(TAG, "onCreate: Initial trial state from TrialManager: $initialTrialState. Updating local state.")
        updateTrialState(initialTrialState) // This sets currentTrialState

        // Initialize SharedPreferences and check for first launch info dialog
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isFirstLaunchInfoShown = prefs.getBoolean(PREF_KEY_FIRST_LAUNCH_INFO_SHOWN, false)

        if (!isFirstLaunchInfoShown && currentTrialState != TrialManager.TrialState.EXPIRED_INTERNET_TIME_CONFIRMED) {
            Log.d(TAG, "onCreate: This is the first launch where info hasn't been shown and trial is not expired. Setting showFirstLaunchInfoDialog to true.")
            showFirstLaunchInfoDialog = true
        } else if (isFirstLaunchInfoShown) {
            Log.d(TAG, "onCreate: First launch info dialog has already been shown.")
        } else { // !isFirstLaunchInfoShown && currentTrialState == EXPIRED
            Log.d(TAG, "onCreate: First launch info not shown, but trial is already expired. Not showing FirstLaunchInfoDialog.")
        }

        Log.d(TAG, "onCreate: Calling startTrialServiceIfNeeded based on current state: $currentTrialState")
        startTrialServiceIfNeeded()

        // Initial check for accessibility service status
        refreshAccessibilityServiceStatus()

        initializeMediaProjection()

        setupKeyboardVisibilityListener()

        Log.d(TAG, "onCreate: Starting fetch of remote WebView HTML content.")
        loadWebViewContent()
        registerNetworkCallback()

        Log.d(TAG, "onCreate: Calling setContent.")
        setContent {
            GenerativeAISample {
                Scaffold { innerPadding ->
                    val htmlContent = webViewHtmlContent
                    // ── Trial/payment dialogs (native Compose) ─────────────────────────────
                    // NOTE: This native dialog UI has been superseded by the WebView JS trial
                    // engine (see index.html: TrialState machine, _initTrialState,
                    // window.onTrialStateChanged, showTrialExpiredDialog / showTrialInfoDialogUI /
                    // openPaymentMethodDialog). Rendering both at once showed the dialogs twice
                    // whenever the WebView UI was active. Commented out rather than deleted so
                    // it stays available as the dialog UI for the `htmlContent == null` fallback
                    // path below, in case remote WebView content ever fails to load - see that
                    // branch's own (still-active) FirstLaunchInfoDialog/PaymentMethodDialog calls
                    // further down in this file.
                    //
                    // TrialStateDialogs(
                    //     trialState = currentTrialState,
                    //     showTrialInfoDialog = showTrialInfoDialog,
                    //     trialInfoMessage = trialInfoMessage,
                    //     onDismissTrialInfo = {
                    //         showTrialInfoDialog = false
                    //         prefs.edit().putBoolean(PREF_KEY_FIRST_LAUNCH_INFO_SHOWN, true).apply()
                    //     },
                    //     onPurchaseClick = { initiateDonationPurchase() }
                    // )
                    //
                    // if (showPaymentMethodDialog) {
                    //     PaymentMethodDialog(
                    //         onDismiss = { showPaymentMethodDialog = false },
                    //         onPayPalClick = {
                    //             showPaymentMethodDialog = false
                    //             Toast.makeText(this@MainActivity, com.google.ai.sample.util.UiStringsConfig.get("toast_paypal_fallback_unavailable", "PayPal ist in dieser Fallback-UI noch nicht verfügbar."), Toast.LENGTH_LONG).show()
                    //         },
                    //         onGooglePlayClick = {
                    //             showPaymentMethodDialog = false
                    //             launchGooglePlayBilling()
                    //         }
                    //     )
                    // }
                    // Only show these dialogs natively when the WebView content itself hasn't
                    // loaded yet (fallback UI branch), so there is no chance of double-rendering
                    // once the WebView is showing its own JS-driven trial dialogs.
                    if (htmlContent == null) {
                        TrialStateDialogs(
                            trialState = currentTrialState,
                            showTrialInfoDialog = showTrialInfoDialog,
                            trialInfoMessage = trialInfoMessage,
                            onDismissTrialInfo = {
                                showTrialInfoDialog = false
                                prefs.edit().putBoolean(PREF_KEY_FIRST_LAUNCH_INFO_SHOWN, true).apply()
                            },
                            onPurchaseClick = { initiateDonationPurchase() }
                        )
                        if (showPaymentMethodDialog) {
                            PaymentMethodDialog(
                                onDismiss = { showPaymentMethodDialog = false },
                                onPayPalClick = {
                                    showPaymentMethodDialog = false
                                    Toast.makeText(this@MainActivity, com.google.ai.sample.util.UiStringsConfig.get("toast_paypal_fallback_unavailable", "PayPal ist in dieser Fallback-UI noch nicht verfügbar."), Toast.LENGTH_LONG).show()
                                },
                                onGooglePlayClick = {
                                    showPaymentMethodDialog = false
                                    launchGooglePlayBilling()
                                }
                            )
                        }
                    }
                    // ─────────────────────────────────────────────────────────────────────

                    if (htmlContent != null) {
                        Log.d(TAG, "setContent: Remote content available, showing WebView.")
                        AndroidView(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                // Ensures the WebView is actually shrunk (real native resize) when
                                // the soft keyboard opens, instead of just being overlaid by it.
                                // Without this, window.innerHeight/visualViewport and CSS vh units
                                // inside the WebView never change when the keyboard shows, which
                                // made it impossible for the web UI to size itself correctly
                                // relative to the keyboard (e.g. the system message textarea).
                                .imePadding(),
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = false
                                    settings.allowFileAccess = true
                                    settings.allowContentAccess = true
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    settings.setSupportZoom(true)
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    // Mirror the system font scale so WebView text sizes match
                                    // native Compose text (which scales with sp units automatically).
                                    val fontScale = resources.configuration.fontScale
                                    settings.textZoom = (fontScale * 100).toInt()

                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        settings.safeBrowsingEnabled = true
                                    }

                                    // Dark mode: API 33+ uses algorithmicDarkeningAllowed so that
                                    // prefers-color-scheme CSS/matchMedia responds to the system setting.
                                    // API 29–32: FORCE_DARK_AUTO checks the theme's isLightTheme flag,
                                    // which is unreliable when the app theme extends a Light parent.
                                    // We therefore read the system night-mode flag directly and use
                                    // FORCE_DARK_ON / FORCE_DARK_OFF explicitly.
                                    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
                                    } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                                        val isNightMode =
                                            (resources.configuration.uiMode and
                                                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                                            android.content.res.Configuration.UI_MODE_NIGHT_YES
                                        @Suppress("DEPRECATION")
                                        WebSettingsCompat.setForceDark(
                                            settings,
                                            if (isNightMode) WebSettingsCompat.FORCE_DARK_ON
                                            else             WebSettingsCompat.FORCE_DARK_OFF
                                        )
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            Log.d(TAG, "WebView page rendered: {}".format(url))
                                            view?.post {
                                                view.evaluateJavascript("window.onAndroidReady && window.onAndroidReady()", null)
                                                // Push the current trial state so JS can update its UI on first load.
                                                val isExpired = currentTrialState == TrialManager.TrialState.EXPIRED_INTERNET_TIME_CONFIRMED
                                                val isPurchased = currentTrialState == TrialManager.TrialState.PURCHASED
                                                val escapedMsg = escapeForJs(trialInfoMessage)
                                                view.evaluateJavascript(
                                                    "window.onTrialStateChanged && window.onTrialStateChanged($isExpired, $isPurchased, '$escapedMsg')",
                                                    null
                                                )
                                            }
                                            observeViewModelForWebView()
                                        }

                                        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                                            super.onReceivedError(view, errorCode, description, failingUrl)
                                            Log.e(TAG, "WebView error: {}".format(description))
                                        }
                                    }

                                    // Wichtig: per loadDataWithBaseURL mit explizitem "text/html" laden,
                                    // statt loadUrl() direkt aufzurufen.
                                    // GitHub Pages liefert "text/html" korrekt; raw.githubusercontent.com
                                    // lieferte "text/plain", was den Inhalt als Rohtext anzeigte.
                                    this@MainActivity.webViewInstance = this
                                    addJavascriptInterface(WebViewBridge(this@MainActivity), "Android")
                                    // Tag remembers which HTML string is currently loaded, so `update`
                                    // below can detect when a background network refresh has replaced
                                    // the (possibly cached) content that was shown at factory-time.
                                    tag = htmlContent
                                    loadDataWithBaseURL(
                                        "https://android-poweruser.github.io/ScreenOperator/",
                                        htmlContent,
                                        "text/html",
                                        "UTF-8",
                                        null
                                    )
                                }
                            },
                            update = { webView ->
                                // Called on every recomposition with the latest `htmlContent`.
                                // If it differs from what's currently loaded (e.g. the initial
                                // cache-based load was just replaced by a fresh network fetch in
                                // loadWebViewContent()), reload the WebView with the new content
                                // so the cached version gets visibly replaced, per the caching
                                // behavior: show cache instantly, then swap in network content
                                // once it successfully arrives.
                                if (webView.tag != htmlContent) {
                                    Log.d(TAG, "AndroidView.update: webViewHtmlContent changed, reloading WebView with fresh content.")
                                    webView.tag = htmlContent
                                    webView.loadDataWithBaseURL(
                                        "https://android-poweruser.github.io/ScreenOperator/",
                                        htmlContent,
                                        "text/html",
                                        "UTF-8",
                                        null
                                    )
                                }
                            }
                        )
                    } else {
                        Log.d(TAG, "setContent: Remote content not ready yet, showing normal app UI.")
                        navController = rememberNavController()
                        AppNavigation(navController = navController, innerPadding = innerPadding)

                        if (showFirstLaunchInfoDialog) {
                            FirstLaunchInfoDialog(onDismiss = {
                                showFirstLaunchInfoDialog = false
                                prefs.edit().putBoolean(PREF_KEY_FIRST_LAUNCH_INFO_SHOWN, true).apply()
                            })
                        }

                        if (showApiKeyDialog) {
                            ApiKeyDialogSection(
                                apiKeyManager = apiKeyManager,
                                isFirstLaunch = false,
                                initialProvider = apiKeyDialogInitialProvider,
                                onDismiss = {
                                    showApiKeyDialog = false
                                    apiKeyDialogInitialProvider = null
                                }
                            )
                        }
                    }
                }
            }
        }
        Log.d(TAG, "onCreate: setContent finished.")

        NotificationUtil.createNotificationChannel(this) // Create channel

        registerScreenshotReceivers()
        registerPermissionLaunchers()
        observeStopNotificationFlow()

    }

    fun showStopOperationNotification() {
        Log.d(TAG, "MainActivity.showStopOperationNotification() entered.")
        NotificationUtil.showStopNotification(this)
        Log.d(TAG, "MainActivity.showStopOperationNotification() finished call to NotificationUtil.")
    }

    fun cancelStopOperationNotification() {
        NotificationUtil.cancelStopNotification(this)
    }

    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun hasShownNotificationRationale(): Boolean {
        return NotificationPermissionPreferences.hasShownNotificationRationale(this)
    }

    fun setNotificationRationaleShown(shown: Boolean) {
        NotificationPermissionPreferences.setNotificationRationaleShown(this, shown)
    }

    @Composable
    fun AppNavigation(navController: NavHostController, innerPadding: PaddingValues) {  // Füge Parameter innerPadding hinzu
        val isAppEffectivelyUsable = currentTrialState != TrialManager.TrialState.EXPIRED_INTERNET_TIME_CONFIRMED
        Log.d(TAG, "AppNavigation: isAppEffectivelyUsable = $isAppEffectivelyUsable (currentTrialState: $currentTrialState)")

        val alwaysAvailableRoutes = listOf("ApiKeyDialog", "ChangeModel")

        NavHost(navController = navController, startDestination = "menu") {
            composable("menu") {
                Log.d(TAG, "AppNavigation: Composing 'menu' screen.")
                MenuScreen(
                    innerPadding = innerPadding,  // Übergebe innerPadding an MenuScreen
                    onItemClicked = { routeId ->
                        Log.d(TAG, "MenuScreen onItemClicked: routeId='$routeId', isAppEffectivelyUsable=$isAppEffectivelyUsable")
                        if (alwaysAvailableRoutes.contains(routeId) || isAppEffectivelyUsable) {
                            if (routeId == "SHOW_API_KEY_DIALOG_ACTION") {
                                Log.d(TAG, "MenuScreen: Navigating to show ApiKeyDialog directly.")
                                showApiKeyDialog = true
                            } else {
                                Log.d(TAG, "MenuScreen: Navigating to route: $routeId")
                                navController.navigate(routeId)
                            }
                        } else {
                            Log.w(TAG, "MenuScreen: Navigation to '$routeId' blocked due to trial state.")
                        }
                    },
                    onApiKeyButtonClicked = { provider ->
                        Log.d(TAG, "MenuScreen onApiKeyButtonClicked: Showing ApiKeyDialog. Provider: $provider")
                        apiKeyDialogInitialProvider = provider
                        showApiKeyDialog = true
                    },
                    onDonationButtonClicked = {
                        Log.d(TAG, "MenuScreen onDonationButtonClicked: Initiating subscription purchase.")
                        initiateDonationPurchase()
                    },
                    isPurchased = (currentTrialState == TrialManager.TrialState.PURCHASED),
                    isTrialExpired = currentTrialState == TrialManager.TrialState.EXPIRED_INTERNET_TIME_CONFIRMED
                )
            }
            composable("photo_reasoning") {
                Log.d(TAG, "AppNavigation: Composing 'photo_reasoning' screen. isAppEffectivelyUsable=$isAppEffectivelyUsable")
                if (isAppEffectivelyUsable) {
                    PhotoReasoningRoute(innerPadding = innerPadding)  // Übergebe innerPadding an PhotoReasoningRoute
                } else {
                    Log.w(TAG, "AppNavigation: 'photo_reasoning' blocked. Popping back stack.")
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                }
            }
        }
    }

    private fun startTrialServiceIfNeeded() {
        Log.d(TAG, "startTrialServiceIfNeeded called. Current state: $currentTrialState")
        if (MainActivityBillingStateEvaluator.shouldStartTrialService(currentTrialState)) {
            Log.i(TAG, "Starting TrialTimerService because current state is: $currentTrialState")
            val serviceIntent = Intent(this, TrialTimerService::class.java)
            serviceIntent.action = TrialTimerService.ACTION_START_TIMER
            try {
                startService(serviceIntent)
                Log.d(TAG, "startTrialServiceIfNeeded: startService call succeeded.")
            } catch (e: Exception) {
                Log.e(TAG, "startTrialServiceIfNeeded: Failed to start TrialTimerService", e)
            }
        } else {
            Log.i(TAG, "TrialTimerService not started. State: $currentTrialState (Purchased or Expired)")
        }
    }

    private fun setupBillingClient() {
        Log.d(TAG, "setupBillingClient called.")
        if (MainActivityBillingClientState.isInitializedAndReady(::billingClient.isInitialized, if (::billingClient.isInitialized) billingClient.isReady else false)) {
            Log.d(TAG, "setupBillingClient: BillingClient already initialized and ready.")
            return
        }
        if (MainActivityBillingClientState.isConnecting(::billingClient.isInitialized, if (::billingClient.isInitialized) billingClient.connectionState else BillingClient.ConnectionState.DISCONNECTED)) {
            Log.d(TAG, "setupBillingClient: BillingClient already connecting.")
            return
        }

        billingClient = BillingClient.newBuilder(this)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()
        Log.d(TAG, "setupBillingClient: BillingClient built. Starting connection.")

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                Log.i(TAG, "onBillingSetupFinished: ResponseCode: ${billingResult.responseCode}, Message: ${billingResult.debugMessage}")
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "BillingClient setup successful.")
                    Log.d(TAG, "onBillingSetupFinished: Querying product details and active subscriptions.")
                    queryProductDetails()
                    queryActiveSubscriptions()
                } else {
                    Log.e(TAG, "BillingClient setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "onBillingServiceDisconnected: BillingClient service disconnected. Will attempt to reconnect on next relevant action or onResume.")
            }
        })
    }

    private fun queryProductDetails() {
        Log.d(TAG, "queryProductDetails called.")
        if (!MainActivityBillingClientState.isInitializedAndReady(::billingClient.isInitialized, if (::billingClient.isInitialized) billingClient.isReady else false)) {
            Log.w(TAG, "queryProductDetails: BillingClient not ready. Cannot query.")
            return
        }
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(subscriptionProductId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        Log.d(TAG, "queryProductDetails: Querying for product ID: $subscriptionProductId")

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            Log.i(TAG, "queryProductDetailsAsync result: ResponseCode: ${billingResult.responseCode}, Message: ${billingResult.debugMessage}")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                monthlyDonationProductDetails = productDetailsList.find { it.productId == subscriptionProductId }
                if (monthlyDonationProductDetails != null) {
                    Log.i(TAG, "Product details loaded: ${monthlyDonationProductDetails?.name}, ID: ${monthlyDonationProductDetails?.productId}")
                } else {
                    Log.w(TAG, "Product details for $subscriptionProductId not found in the list despite OK response.")
                }
            } else {
                Log.e(TAG, "Failed to query product details: ${billingResult.debugMessage}. List size: ${productDetailsList.size}")
            }
        }
    }

    private fun initiateDonationPurchase() {
        Log.d(TAG, "initiateDonationPurchase called. Showing Payment Method Dialog.")
        showPaymentMethodDialog = true
    }

    private fun launchGooglePlayBilling() {
        if (!::billingClient.isInitialized) {
            Log.e(TAG, "launchGooglePlayBilling: BillingClient not initialized.")
            updateStatusMessage("Payment service not initialized. Please try again later.", true)
            return
        }
        if (!billingClient.isReady) {
            Log.e(TAG, "launchGooglePlayBilling: BillingClient not ready. Connection state: ${billingClient.connectionState}")
            updateStatusMessage("Payment service not ready. Please try again later.", true)
            if (MainActivityBillingClientState.shouldReconnect(billingClient.connectionState)) {
                Log.d(TAG, "launchGooglePlayBilling: BillingClient disconnected, attempting to reconnect.")
                billingClient.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(setupResult: BillingResult) {
                        Log.i(TAG, "launchGooglePlayBilling (reconnect): onBillingSetupFinished. ResponseCode: ${setupResult.responseCode}")
                        if (setupResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.d(TAG, "launchGooglePlayBilling (reconnect): Reconnection successful, retrying purchase.")
                            launchGooglePlayBilling()
                        } else {
                             Log.e(TAG, "launchGooglePlayBilling (reconnect): BillingClient setup failed after disconnect: ${setupResult.debugMessage}")
                        }
                    }
                    override fun onBillingServiceDisconnected() { Log.w(TAG, "launchGooglePlayBilling (reconnect): BillingClient still disconnected.") }
                })
            }
            return
        }
        if (monthlyDonationProductDetails == null) {
            Log.e(TAG, "initiateDonationPurchase: Product details not loaded yet.")
            updateStatusMessage("Subscription information is loading. Please wait a moment and try again.", true)
            Log.d(TAG, "initiateDonationPurchase: Attempting to reload product details.")
            queryProductDetails()
            return
        }

        monthlyDonationProductDetails?.let { productDetails ->
            Log.d(TAG, "initiateDonationPurchase: Product details available: ${productDetails.name}")
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken == null) {
                Log.e(TAG, "No offer token found for product: ${productDetails.productId}. SubscriptionOfferDetails size: ${productDetails.subscriptionOfferDetails?.size}")
                updateStatusMessage("subscription offer not found.", true)
                return@let
            }
            Log.d(TAG, "initiateDonationPurchase: Offer token found: $offerToken")
            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build()
            )
            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build()
            Log.d(TAG, "initiateDonationPurchase: Launching billing flow.")
            val billingResult = billingClient.launchBillingFlow(this, billingFlowParams)
            Log.i(TAG, "initiateDonationPurchase: Billing flow launch result: ${billingResult.responseCode} - ${billingResult.debugMessage}")
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.e(TAG, "Failed to launch billing flow: ${billingResult.debugMessage}")
                updateStatusMessage("Error starting donation process: ${billingResult.debugMessage}", true)
            }
        } ?: run {
            Log.e(TAG, "initiateDonationPurchase: Subscription product details are null even after check. This shouldn't happen.")
            updateStatusMessage("subscription product not available.", true)
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        Log.i(TAG, "handlePurchase called for purchase: OrderId: ${purchase.orderId}, Products: ${purchase.products}, State: ${purchase.purchaseState}, Token: ${purchase.purchaseToken}, Ack: ${purchase.isAcknowledged}")
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            Log.d(TAG, "handlePurchase: Purchase state is PURCHASED.")
            if (MainActivityBillingStateEvaluator.containsSubscriptionProduct(purchase, subscriptionProductId)) {
                Log.d(TAG, "handlePurchase: Purchase contains target product ID: $subscriptionProductId")
                if (!purchase.isAcknowledged) {
                    Log.i(TAG, "handlePurchase: Purchase not acknowledged. Acknowledging now.")
                    val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(acknowledgePurchaseParams) { ackBillingResult ->
                        Log.i(TAG, "handlePurchase (acknowledgePurchase): Result code: ${ackBillingResult.responseCode}, Message: ${ackBillingResult.debugMessage}")
                        if (ackBillingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.i(TAG, "Subscription purchase acknowledged successfully.")
                            updateStatusMessage("Thank you for your subscription!")
                            TrialManager.markAsPurchased(this)
                            updateTrialState(TrialManager.getTrialState(this, null)) // Update state after purchase
                            Log.d(TAG, "handlePurchase: Stopping TrialTimerService as app is purchased.")
                            val stopIntent = Intent(this, TrialTimerService::class.java)
                            stopIntent.action = TrialTimerService.ACTION_STOP_TIMER
                            startService(stopIntent)
                        } else {
                            Log.e(TAG, "Failed to acknowledge purchase: ${ackBillingResult.debugMessage}")
                            updateStatusMessage("Error confirming purchase: ${ackBillingResult.debugMessage}", true)
                        }
                    }
                } else {
                    Log.i(TAG, "handlePurchase: Subscription already acknowledged.")
                    updateStatusMessage("Subscription already active.")
                    TrialManager.markAsPurchased(this)
                    updateTrialState(TrialManager.getTrialState(this, null)) // Update state after purchase
                }
            } else {
                Log.w(TAG, "handlePurchase: Purchase is PURCHASED but does not contain the target product ID ($subscriptionProductId). Products: ${purchase.products}")
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            Log.i(TAG, "handlePurchase: Purchase state is PENDING.")
            updateStatusMessage("Your payment is being processed.")
        } else {
            Log.w(TAG, "handlePurchase: Purchase state is UNSPECIFIED_STATE or other: ${purchase.purchaseState}")
        }
    }

    private fun queryActiveSubscriptions() {
        Log.d(TAG, "queryActiveSubscriptions called.")
        if (!MainActivityBillingClientState.isInitializedAndReady(::billingClient.isInitialized, if (::billingClient.isInitialized) billingClient.isReady else false)) {
            Log.w(TAG, "queryActiveSubscriptions: BillingClient not initialized or not ready. Cannot query. isInitialized: ${::billingClient.isInitialized}, isReady: ${if(::billingClient.isInitialized) billingClient.isReady else "N/A"}")
            return
        }
        Log.d(TAG, "queryActiveSubscriptions: Querying for SUBS type purchases.")
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { billingResult, purchases ->
            Log.i(TAG, "queryActiveSubscriptions result: ResponseCode: ${billingResult.responseCode}, Message: ${billingResult.debugMessage}, Purchases count: ${purchases.size}")
            var isSubscribedLocally = false
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { purchase ->
                    Log.d(TAG, "queryActiveSubscriptions: Checking purchase - Products: ${purchase.products}, State: ${purchase.purchaseState}")
                    if (MainActivityBillingStateEvaluator.isPurchasedSubscription(purchase, subscriptionProductId)) {
                        Log.i(TAG, "queryActiveSubscriptions: Active subscription found for $subscriptionProductId.")
                        isSubscribedLocally = true
                        if (!purchase.isAcknowledged) {
                            Log.d(TAG, "queryActiveSubscriptions: Found active, unacknowledged subscription. Handling purchase.")
                            handlePurchase(purchase) 
                        } else {
                            Log.d(TAG, "queryActiveSubscriptions: Found active, acknowledged subscription.")
                            if (currentTrialState != TrialManager.TrialState.PURCHASED) {
                                TrialManager.markAsPurchased(this)
                                updateTrialState(TrialManager.getTrialState(this, null))
                            }
                            Log.d(TAG, "queryActiveSubscriptions: Stopping TrialTimerService due to active acknowledged subscription.")
                            val stopIntent = Intent(this, TrialTimerService::class.java)
                            stopIntent.action = TrialTimerService.ACTION_STOP_TIMER
                            startService(stopIntent)
                        }
                        return@forEach 
                    }
                }
                if (isSubscribedLocally) {
                    Log.i(TAG, "queryActiveSubscriptions: User has an active subscription (final check after loop). Ensuring state is PURCHASED.")
                    if (currentTrialState != TrialManager.TrialState.PURCHASED) {
                         TrialManager.markAsPurchased(this)
                         updateTrialState(TrialManager.getTrialState(this, null))
                         val stopIntent = Intent(this, TrialTimerService::class.java)
                         stopIntent.action = TrialTimerService.ACTION_STOP_TIMER
                         startService(stopIntent)
                    }
                } else {
                    Log.i(TAG, "queryActiveSubscriptions: User has no active subscription for $subscriptionProductId. Re-evaluating trial logic.")
                    if (TrialManager.isPurchased(this@MainActivity)) {
                        Log.w(TAG, "queryActiveSubscriptions: No active subscription found by Google Play Billing, but app was previously marked as purchased. Clearing purchase mark.")
                        TrialManager.clearPurchaseMark(this@MainActivity)
                    }
                    if (TrialManager.getTrialState(this, null) != TrialManager.TrialState.PURCHASED) {
                        Log.d(TAG, "queryActiveSubscriptions: No active sub, and TrialManager confirms not purchased. Re-evaluating trial state and starting service if needed.")
                        updateTrialState(TrialManager.getTrialState(this, null))
                        if (currentTrialState == TrialManager.TrialState.EXPIRED_INTERNET_TIME_CONFIRMED) {
                            Log.i(TAG, "queryActiveSubscriptions: Subscription deactivated (no active sub and trial expired). Showing Toast.")
                            Toast.makeText(this@MainActivity, com.google.ai.sample.util.UiStringsConfig.get("toast_subscription_deactivated", "Subscription is deactivated"), Toast.LENGTH_LONG).show()
                        }
                        startTrialServiceIfNeeded()
                    } else {
                         Log.w(TAG, "queryActiveSubscriptions: No active sub from Google, but TrialManager says PURCHASED. This could be due to restored SharedPreferences without active subscription. Re-evaluating trial logic based on no internet time.")
                         updateTrialState(TrialManager.getTrialState(this, null))
                         if (currentTrialState == TrialManager.TrialState.EXPIRED_INTERNET_TIME_CONFIRMED) {
                            Log.i(TAG, "queryActiveSubscriptions: Subscription deactivated (no active sub, was purchased, now trial expired). Showing Toast.")
                            Toast.makeText(this@MainActivity, com.google.ai.sample.util.UiStringsConfig.get("toast_subscription_deactivated", "Subscription is deactivated"), Toast.LENGTH_LONG).show()
                         }
                         startTrialServiceIfNeeded()
                    }
                }
            } else {
                Log.e(TAG, "Failed to query active subscriptions: ${billingResult.debugMessage}")
                Log.d(TAG, "queryActiveSubscriptions: Query failed. Re-evaluating trial state based on no internet time and starting service if needed.")
                if (TrialManager.isPurchased(this@MainActivity)) {
                    Log.w(TAG, "queryActiveSubscriptions: Failed to query active subscriptions, but app was previously marked as purchased. Clearing purchase mark.")
                    TrialManager.clearPurchaseMark(this@MainActivity)
                }
                if (TrialManager.getTrialState(this, null) != TrialManager.TrialState.PURCHASED) {
                    updateTrialState(TrialManager.getTrialState(this, null))
                    if (currentTrialState == TrialManager.TrialState.EXPIRED_INTERNET_TIME_CONFIRMED) {
                        Log.i(TAG, "queryActiveSubscriptions: Subscription deactivated (query failed, trial expired). Showing Toast.")
                        Toast.makeText(this@MainActivity, com.google.ai.sample.util.UiStringsConfig.get("toast_subscription_deactivated", "Subscription is deactivated"), Toast.LENGTH_LONG).show()
                    }
                    startTrialServiceIfNeeded()
                }
            }
        }
    }

    override fun onResume() {
        Log.d(TAG, "onResume: Activity resuming.")
        super.onResume()
        instance = this
        Log.d(TAG, "onResume: MainActivity instance set.")
        Log.d(TAG, "onResume: Calling refreshAccessibilityServiceStatus.")
        refreshAccessibilityServiceStatus() 

        Log.d(TAG, "onResume: Checking BillingClient status.")
        if (::billingClient.isInitialized && billingClient.isReady) {
            Log.d(TAG, "onResume: BillingClient is initialized and ready. Querying active subscriptions.")
            queryActiveSubscriptions() 
        } else if (::billingClient.isInitialized && (billingClient.connectionState == BillingClient.ConnectionState.DISCONNECTED || billingClient.connectionState == BillingClient.ConnectionState.CLOSED) ) {
            Log.w(TAG, "onResume: Billing client initialized but disconnected/closed (State: ${billingClient.connectionState}). Attempting to reconnect via setupBillingClient.")
            setupBillingClient() 
        } else if (!::billingClient.isInitialized) {
            Log.w(TAG, "onResume: Billing client not initialized. Calling setupBillingClient.")
            setupBillingClient() 
        } else {
            Log.d(TAG, "onResume: Billing client initializing or in an intermediate state (State: ${billingClient.connectionState}). Default trial logic will apply for now. QueryActiveSubs will be called by setup if it succeeds.")
            Log.d(TAG, "onResume: Updating trial state and starting service if needed (pending billing client). Current state: $currentTrialState")
            updateTrialState(TrialManager.getTrialState(this, null))
            startTrialServiceIfNeeded()
        }
        Log.d(TAG, "onResume: Finished.")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Activity destroying.")
        super.onDestroy()

        stopScreenCaptureService() // Call to stop the service

        BroadcastReceiverCompat.unregister(this, screenshotRequestHandler, "screenshotRequestHandler", TAG)
        BroadcastReceiverCompat.unregister(this, screenshotResultHandler, "screenshotResultHandler", TAG)
        BroadcastReceiverCompat.unregister(this, trialStatusReceiver, "trialStatusReceiver", TAG)

        if (::billingClient.isInitialized && billingClient.isReady) {
            Log.d(TAG, "onDestroy: BillingClient is initialized and ready. Ending connection.")
            billingClient.endConnection()
            Log.d(TAG, "onDestroy: BillingClient connection ended.")
        }
        keyboardVisibilityObserver.stop(findViewById(android.R.id.content))
        Log.d(TAG, "onDestroy: Keyboard layout listener removed.")
        if (this == instance) {
            instance = null
            Log.d(TAG, "onDestroy: MainActivity instance cleared.")
        }
        Log.d(TAG, "onDestroy: Finished.")
    }


    companion object {
        private const val TAG = "MainActivity"
        private var instance: MainActivity? = null
        fun getInstance(): MainActivity? {
            Log.d(TAG, "getInstance() called. Returning instance: ${if(instance == null) "null" else "not null"}")
            return instance
        }
        private const val PREFS_NAME = "AppPrefs"
        private const val PREF_KEY_FIRST_LAUNCH_INFO_SHOWN = "firstLaunchInfoShown"
        private const val TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
        private const val REQUEST_CODE_TERMUX_RUN_COMMAND_PERMISSION = 1001

        // New Broadcast Actions for MediaProjection Screenshot Flow
        const val ACTION_REQUEST_MEDIAPROJECTION_SCREENSHOT = "com.google.ai.sample.REQUEST_MEDIAPROJECTION_SCREENSHOT"
        const val ACTION_MEDIAPROJECTION_SCREENSHOT_CAPTURED = "com.google.ai.sample.MEDIAPROJECTION_SCREENSHOT_CAPTURED"
        const val EXTRA_SCREENSHOT_URI = "com.google.ai.sample.EXTRA_SCREENSHOT_URI"
        // Optional: For passing screen info text if decided later
        const val EXTRA_SCREEN_INFO = "com.google.ai.sample.EXTRA_SCREEN_INFO"
    }


    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == NotificationUtil.ACTION_STOP_OPERATION) {
            Log.d(TAG, "ACTION_STOP_OPERATION received from notification.")
            photoReasoningViewModel?.onStopClicked() ?: run {
                Log.w(TAG, "PhotoReasoningViewModel not initialized when trying to handle stop action from notification.")
            }
        }
    }

    /**
     * Called by [WebViewBridge] after the selected model has been changed from the WebView UI.
     * Mirrors the offline-model load/unload handling performed in MenuScreen when the model
     * is changed from the native UI.
     */
    fun onModelChangedFromWebView() {
        Log.d(TAG, "onModelChangedFromWebView called.")
        val currentModel = GenerativeAiViewModelFactory.getCurrentModel()
        val vm = photoReasoningViewModel
        if (currentModel.isOfflineModel) {
            Log.d(TAG, "onModelChangedFromWebView: New model is an offline model. Reinitializing offline model.")
            vm?.reinitializeOfflineModel(this)
        } else {
            Log.d(TAG, "onModelChangedFromWebView: New model is not an offline model. Closing any loaded offline model.")
            vm?.closeOfflineModel()
        }
    }

    /**
     * Called by [WebViewBridge] when the user sends a chat message from the WebView UI.
     * Supports passing a list of media URIs selected via the + button.
     */
    fun sendMessageFromWebView(text: String, selectedImages: List<Uri>) {
        Log.d(TAG, "sendMessageFromWebView called with ${selectedImages.size} images.")

        // Mirror the native send-button logic: ask for MediaProjection permission before sending
        // when the active model supports screenshots, unless it is the Human Expert model (which
        // manages its own WebRTC-based projection separately).
        val currentModel = GenerativeAiViewModelFactory.getCurrentModel()
        val modelName = currentModel.name
        val requiresScreenCapturePermission = currentModel.supportsScreenshot && modelName != "HUMAN_EXPERT"
        if (!_isMediaProjectionPermissionGranted.value && requiresScreenCapturePermission) {
            Log.d(TAG, "sendMessageFromWebView: MediaProjection not yet granted. Requesting permission first.")
            requestMediaProjectionPermission {
                // Permission was just granted – now do the actual send.
                doSendMessageFromWebView(text, selectedImages)
            }
            return
        }

        doSendMessageFromWebView(text, selectedImages)
    }

    private fun doSendMessageFromWebView(text: String, selectedImages: List<Uri>) {
        lifecycleScope.launch {
            val bitmaps = selectedImages.mapNotNull { uri ->
                uriToBitmap(uri)
            }
            photoReasoningViewModel?.reason(
                userInput = text,
                selectedImages = bitmaps,
                screenInfoForPrompt = null,
                imageUrisForChat = selectedImages.map { it.toString() }
            )
        }
    }

    private suspend fun uriToBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val mimeType = contentResolver.getType(uri).orEmpty()
        if (mimeType.startsWith("video/")) {
            return@withContext extractVideoFrame(uri)
        }

        val imageLoader = ImageLoader.Builder(this@MainActivity).build()
        val imageRequest = ImageRequest.Builder(this@MainActivity)
            .data(uri)
            .precision(Precision.EXACT)
            .build()
        return@withContext try {
            val result = imageLoader.execute(imageRequest)
            if (result is SuccessResult) (result.drawable as? BitmapDrawable)?.bitmap else null
        } catch (e: Exception) {
            null
        }
    }

    private fun extractVideoFrame(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting video frame for URI: $uri", e)
            null
        } finally {
            retriever.release()
        }
    }

    fun openImagePicker() {
        Log.d(TAG, "openImagePicker called via Bridge.")
        pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }

    override fun onBackPressed() {
        val wv = webViewInstance
        // Wenn wir nicht im WebView-Inhalt sind (htmlContent == null), nutzen wir standard back.
        // Wenn WebView aktiv ist, fragen wir JS ob es ein "back" innerhalb der UI gibt.
        if (wv != null && wv.visibility == View.VISIBLE) {
            wv.evaluateJavascript("window.onBackPressed && window.onBackPressed()") { result ->
                // JS gibt "true" zurück wenn es den Event konsumiert hat, sonst "false" oder "null"
                val cleanedResult = result?.replace("\"", "")?.trim()
                if (cleanedResult != "true") {
                    runOnUiThread { super.onBackPressed() }
                }
            }
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Called by [WebViewBridge] when the user requests to start a donation/subscription
     * purchase from the WebView UI.
     */
    fun initiateDonationFromWebView() {
        Log.d(TAG, "initiateDonationFromWebView called. Launching Google Play billing directly (PaymentMethodDialog lives in the non-WebView branch).")
        launchGooglePlayBilling()
    }

    /**
     * Called by [WebViewBridge] to persist the "execute Termux commands in background" preference
     * when toggled from the WebView UI.
     */
    fun setTermuxBackgroundFromWebView(background: Boolean) {
        Log.d(TAG, "setTermuxBackgroundFromWebView called with background=$background")
        TermuxExecutionModePreferences.setExecuteInBackground(this, background)
        val toastMessage = if (background) {
            "Termux commands are executed in the background"
        } else {
            "Termux commands are executed in the foreground"
        }
        Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
    }

    /**
     * Evaluates a JavaScript expression in the active WebView on the UI thread.
     * No-op if no WebView is currently loaded.
     */
    fun evaluateWebViewJs(js: String) {
        webViewInstance?.post {
            webViewInstance?.evaluateJavascript(js, null)
        }
    }

    /**
     * Escapes a string so it can be safely embedded inside a single-quoted JS string literal
     * passed to [WebView.evaluateJavascript]. Delegates to [WebViewBridge.jsEscape] so both
     * directions of the bridge use identical escaping logic.
     */
    private fun escapeForJs(s: String): String = WebViewBridge.jsEscape(s)

    private fun observeViewModelForWebView() {
        if (webViewObserversStarted) return
        webViewObserversStarted = true
        val vm = photoReasoningViewModel ?: return
        val wv = webViewInstance ?: return
        lifecycleScope.launch {
            vm.uiState.collect { state ->
                val js = when (state) {
                    is com.google.ai.sample.feature.multimodal.PhotoReasoningUiState.Loading ->
                        "window.onAiMessage && window.onAiMessage('', true)"
                    is com.google.ai.sample.feature.multimodal.PhotoReasoningUiState.Success -> {
                        val escaped = escapeForJs(state.outputText)
                        "window.onAiMessage && window.onAiMessage('$escaped', false)"
                    }
                    is com.google.ai.sample.feature.multimodal.PhotoReasoningUiState.Error -> {
                        val escaped = escapeForJs("[Error] " + state.errorMessage)
                        "window.onAiMessage && window.onAiMessage('$escaped', false)"
                    }
                    is com.google.ai.sample.feature.multimodal.PhotoReasoningUiState.Stopped ->
                        "window.onAiMessage && window.onAiMessage('', false)"
                    else -> null
                }
                if (js != null) wv.post { wv.evaluateJavascript(js, null) }
            }
        }
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                vm.isGenerationRunningFlow,
                vm.isOfflineGpuModelLoadedFlow
            ) { running, offline -> Pair(running, offline) }.collect { (running, offline) ->
                val js = "window.onGenerationStateChanged && window.onGenerationStateChanged($running, $offline)"
                wv.post { wv.evaluateJavascript(js, null) }
            }
        }
        lifecycleScope.launch {
            vm.commandExecutionStatus.collect { status ->
                if (status.isNotBlank()) {
                    val escaped = escapeForJs(status)
                    val js = "window.onCommandStatus && window.onCommandStatus('$escaped')"
                    wv.post { wv.evaluateJavascript(js, null) }
                }
            }
        }
        lifecycleScope.launch {
            vm.systemMessage.collect { msg ->
                wv.post {
                    wv.evaluateJavascript("window.onSystemMessageChanged && window.onSystemMessageChanged('${escapeForJs(msg)}')", null)
                }
            }
        }
        lifecycleScope.launch {
            vm.customModelRequestEvents.collect { payloadJson ->
                val escaped = escapeForJs(payloadJson)
                wv.post {
                    wv.evaluateJavascript("window.onCustomModelRequest && window.onCustomModelRequest('$escaped')", null)
                }
            }
        }
        // Observe chat messages so the WebView user bubble is updated with the full message text
        // (including screen elements and Termux output) once native code has assembled it.
        // The WebView's sendMessage() adds the bubble with only the typed text; this corrects it.
        var lastObservedUserMessageId: String? = null
        lifecycleScope.launch {
            vm.chatMessagesFlow.collect { messages ->
                val lastUser = messages.lastOrNull {
                    it.participant == com.google.ai.sample.feature.multimodal.PhotoParticipant.USER && !it.isPending
                }
                if (lastUser == null) return@collect
                if (lastUser.id != lastObservedUserMessageId) {
                    lastObservedUserMessageId = lastUser.id
                    val escaped = escapeForJs(lastUser.text)
                    wv.post {
                        wv.evaluateJavascript("window.onUserMessage && window.onUserMessage('$escaped')", null)
                    }
                }
            }
        }
    }

    /**
     * Called by [WebViewBridge] with a streaming chunk (accumulated text so far) of a custom,
     * fully JSON-defined model's response (see [com.google.ai.sample.util.CustomModelRegistry]).
     */
    fun customModelPartialResponseFromWebView(text: String) {
        photoReasoningViewModel?.onCustomModelPartialResponse(text)
    }

    /** Called by [WebViewBridge] with the final, complete response text of a custom model's turn. */
    fun customModelFinalResponseFromWebView(text: String) {
        photoReasoningViewModel?.onCustomModelFinalResponse(text)
    }

    /** Called by [WebViewBridge] when a custom model's turn failed in JavaScript. */
    fun customModelErrorFromWebView(message: String) {
        photoReasoningViewModel?.onCustomModelError(message)
    }

    private fun registerNetworkCallback() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.d(TAG, "Network available. Checking if WebView content needs loading. Current: ${if (webViewHtmlContent == null) "null" else "loaded"}")
                if (webViewHtmlContent == null) {
                    loadWebViewContent()
                }
            }
        })
    }
}

