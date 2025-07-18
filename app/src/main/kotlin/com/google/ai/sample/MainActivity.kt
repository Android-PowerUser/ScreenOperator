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
import android.os.Build
import android.graphics.Rect
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
import android.view.ViewTreeObserver
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.google.ai.sample.feature.multimodal.PhotoReasoningRoute
import com.google.ai.sample.feature.multimodal.PhotoReasoningViewModel
import com.google.ai.sample.ui.theme.GenerativeAISample
import com.google.ai.sample.util.NotificationUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Keyboard Visibility
    private val _isKeyboardOpen = MutableStateFlow(false)
    val isKeyboardOpen: StateFlow<Boolean> = _isKeyboardOpen.asStateFlow()
    private var onGlobalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private var photoReasoningViewModel: PhotoReasoningViewModel? = null
    private lateinit var apiKeyManager: ApiKeyManager
    private var showApiKeyDialog by mutableStateOf(false)

    // Google Play Billing
    private lateinit var billingClient: BillingClient
    private var monthlyDonationProductDetails: ProductDetails? = null
    private val subscriptionProductId = "donation_monthly_2_90_eur"

    private var currentTrialState by mutableStateOf(TrialManager.TrialState.NOT_YET_STARTED_AWAITING_INTERNET)
    private var showTrialInfoDialog by mutableStateOf(false)
    private var trialInfoMessage by mutableStateOf("")

    // private var showPermissionRationaleDialog by mutableStateOf(false) // Deleted
    private var permissionRequestCount by mutableStateOf(0)

    // MediaProjection
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var mediaProjectionLauncher: ActivityResultLauncher<Intent>

    private var currentScreenInfoForScreenshot: String? = null

    private lateinit var navController: NavHostController
    private var isProcessingExplicitScreenshotRequest: Boolean = false
    private var onMediaProjectionPermissionGranted: (() -> Unit)? = null

    private val screenshotRequestHandler = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_REQUEST_MEDIAPROJECTION_SCREENSHOT) {
                Log.d(TAG, "Received request for screenshot via broadcast.")
                currentScreenInfoForScreenshot = intent.getStringExtra(EXTRA_SCREEN_INFO)
                Log.d(TAG, "Stored screenInfo for upcoming screenshot.")

                if (ScreenCaptureService.isRunning()) {
                    Log.d(TAG, "ScreenCaptureService is running. Calling takeAdditionalScreenshot().")
                    this@MainActivity.takeAdditionalScreenshot()
                } else {
                    Log.d(TAG, "ScreenCaptureService not running. Calling requestMediaProjectionPermission() to start it.")
                    this@MainActivity.isProcessingExplicitScreenshotRequest = true
                    this@MainActivity.requestMediaProjectionPermission()
                }
            }
        }
    }

    private val screenshotResultHandler = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_MEDIAPROJECTION_SCREENSHOT_CAPTURED) {
                val screenshotUriString = intent.getStringExtra(EXTRA_SCREENSHOT_URI)
                if (screenshotUriString != null) {
                    val screenshotUri = Uri.parse(screenshotUriString)
                    Log.d(TAG, "Received screenshot captured broadcast. URI: $screenshotUri")
                    Log.d(TAG, "Using screenInfo: ${currentScreenInfoForScreenshot?.substring(0, minOf(100, currentScreenInfoForScreenshot?.length ?: 0))}...")

                    photoReasoningViewModel?.addScreenshotToConversation(
                        screenshotUri,
                        this@MainActivity, // or applicationContext
                        currentScreenInfoForScreenshot
                    )
                    currentScreenInfoForScreenshot = null // Clear after use
                } else {
                    Log.e(TAG, "Screenshot URI was null in broadcast.")
                }
            }
        }
    }

    // Permission Launchers
    private lateinit var requestNotificationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestForegroundServicePermissionLauncher: ActivityResultLauncher<String>
    // private val requestPermissionLauncher = registerForActivityResult(...) // Deleted

    fun requestMediaProjectionPermission(onGranted: (() -> Unit)? = null) {
        Log.d(TAG, "Requesting MediaProjection permission")
        onMediaProjectionPermissionGranted = onGranted

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION) != PackageManager.PERMISSION_GRANTED) {
                requestForegroundServicePermissionLauncher.launch(android.Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION)
            }
        }

        // Ensure mediaProjectionManager is initialized before using it.
        // This should be guaranteed by its placement in onCreate.
        if (!::mediaProjectionManager.isInitialized) {
            Log.e(TAG, "requestMediaProjectionPermission: mediaProjectionManager not initialized!")
            // Optionally, initialize it here as a fallback, though it indicates an issue with onCreate ordering
            // mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            // Toast.makeText(this, "Error: Projection manager not ready. Please try again.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(intent)
    }

    fun takeAdditionalScreenshot() {
        if (ScreenCaptureService.isRunning()) {
            Log.d(TAG, "MainActivity: Instructing ScreenCaptureService to take an additional screenshot.")
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_TAKE_SCREENSHOT
            }
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
            Toast.makeText(this, "Screenshot service not active. Please grant permission first.", Toast.LENGTH_LONG).show()
            // Optionally, trigger permission request again if appropriate for the use case.
            // requestMediaProjectionPermission() // This might be too aggressive if called from unexpected places.
        }
    }

    fun stopScreenCaptureService() {
        if (ScreenCaptureService.isRunning()) { // Check if it's actually running to avoid errors
            Log.d(TAG, "MainActivity: Instructing ScreenCaptureService to stop.")
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_STOP_CAPTURE
            }
            startService(intent)
        } else {
            Log.d(TAG, "MainActivity: stopScreenCaptureService called, but service was not running.")
        }
    }

    // START: Added for Accessibility Service Status
    private val _isAccessibilityServiceEnabled = MutableStateFlow(false)
    val isAccessibilityServiceEnabledFlow: StateFlow<Boolean> = _isAccessibilityServiceEnabled.asStateFlow()
    // END: Added for Accessibility Service Status

    // START: Added for MediaProjection Permission Status
    private val _isMediaProjectionPermissionGranted = MutableStateFlow(false)
    val isMediaProjectionPermissionGrantedFlow: StateFlow<Boolean> = _isMediaProjectionPermissionGranted.asStateFlow()
    // END: Added for MediaProjection Permission Status

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

        when (currentTrialState) {
            TrialManager.TrialState.EXPIRED_INTERNET_TIME_CONFIRMED -> {
                trialInfoMessage = "Your 30-minute trial period has ended. Please subscribe to the app to continue using it."
                showTrialInfoDialog = true
                Log.d(TAG, "updateTrialState: Set message to \'$trialInfoMessage\', showTrialInfoDialog = true (EXPIRED)")
            }
            TrialManager.TrialState.ACTIVE_INTERNET_TIME_CONFIRMED,
            TrialManager.TrialState.PURCHASED,
            TrialManager.TrialState.NOT_YET_STARTED_AWAITING_INTERNET,
            TrialManager.TrialState.INTERNET_UNAVAILABLE_CANNOT_VERIFY -> {
                trialInfoMessage = ""
                showTrialInfoDialog = false
                Log.d(TAG, "updateTrialState: Cleared message, showTrialInfoDialog = false (ACTIVE, PURCHASED, AWAITING, OR UNAVAILABLE)")
            }
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
            Toast.makeText(this, "Donation process cancelled.", Toast.LENGTH_SHORT).show()
        } else {
            Log.e(TAG, "purchasesUpdatedListener: Billing error: ${billingResult.debugMessage} (Code: ${billingResult.responseCode})")
            Toast.makeText(this, "Error during donation process: ${billingResult.debugMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun getCurrentApiKey(): String? {
        val key = if (::apiKeyManager.isInitialized) {
            apiKeyManager.getCurrentApiKey()
        } else {
            null
        }
        Log.d(TAG, "getCurrentApiKey returning: ${if (key.isNullOrEmpty()) "null or empty" else "valid key"}")
        return key
    }

    internal fun refreshAccessibilityServiceStatus() {
        Log.d(TAG, "refreshAccessibilityServiceStatus called.")
        val service = packageName + "/" + ScreenOperatorAccessibilityService::class.java.canonicalName
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val isEnabled = enabledServices?.contains(service, ignoreCase = true) == true
        _isAccessibilityServiceEnabled.value = isEnabled // Update the flow
        Log.d(TAG, "Accessibility Service $service isEnabled: $isEnabled. Flow updated.")
        if (!isEnabled) {
            Log.d(TAG, "Accessibility Service not enabled.")
        }
    }

    internal fun requestManageExternalStoragePermission() {
        Log.d(TAG, "requestManageExternalStoragePermission (dummy) called.")
    }

    fun updateStatusMessage(message: String, isError: Boolean = false) {
        Toast.makeText(this, message, if (isError) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        if (isError) {
            Log.e(TAG, "updateStatusMessage (Error): $message")
        } else {
            Log.d(TAG, "updateStatusMessage (Info): $message")
        }
    }

    fun getPhotoReasoningViewModel(): PhotoReasoningViewModel? {
        Log.d(TAG, "getPhotoReasoningViewModel called.")
        return photoReasoningViewModel
    }

    fun setPhotoReasoningViewModel(viewModel: PhotoReasoningViewModel) {
        Log.d(TAG, "setPhotoReasoningViewModel called.")
        this.photoReasoningViewModel = viewModel
    }

    fun getAccessibilitySettingsIntent(): Intent {
        Log.d(TAG, "getAccessibilitySettingsIntent called.")
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    // private val requiredPermissions = ... // Deleted

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate: Activity creating.")
        super.onCreate(savedInstanceState)
        instance = this
        Log.d(TAG, "onCreate: MainActivity instance set.")

        apiKeyManager = ApiKeyManager.getInstance(this)
        Log.d(TAG, "onCreate: ApiKeyManager initialized.")
        if (apiKeyManager.getCurrentApiKey().isNullOrEmpty()) {
             showApiKeyDialog = true
             Log.d(TAG, "onCreate: No API key found, showApiKeyDialog set to true.")
        } else {
             Log.d(TAG, "onCreate: API key found.")
        }

        // Log.d(TAG, "onCreate: Calling checkAndRequestPermissions.") // Deleted
        // checkAndRequestPermissions() // Deleted
        Log.d(TAG, "onCreate: Calling setupBillingClient.")
        setupBillingClient()

        Log.d(TAG, "onCreate: Calling TrialManager.initializeTrialStateFlagsIfNecessary.")
        TrialManager.initializeTrialStateFlagsIfNecessary(this)

        Log.d(TAG, "onCreate: Setting up IntentFilter for trialStatusReceiver.")
        val intentFilter = IntentFilter().apply {
            addAction(TrialTimerService.ACTION_TRIAL_EXPIRED)
            addAction(TrialTimerService.ACTION_INTERNET_TIME_UNAVAILABLE)
            addAction(TrialTimerService.ACTION_INTERNET_TIME_AVAILABLE)
        }
        Log.d(TAG, "onCreate: Registering trialStatusReceiver.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(trialStatusReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(trialStatusReceiver, intentFilter)
        }
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

            // MediaProjection Initialisierung hier einfügen:
            // Initialize MediaProjectionManager
            Log.d(TAG, "onCreate: Initializing MediaProjectionManager")
            mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

            // Initialize MediaProjection launcher
            Log.d(TAG, "onCreate: Initializing MediaProjection launcher")
            mediaProjectionLauncher = registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    val shouldTakeScreenshotOnThisStart = this@MainActivity.isProcessingExplicitScreenshotRequest
                    Log.i(TAG, "MediaProjection permission granted. Starting ScreenCaptureService. Explicit request: $shouldTakeScreenshotOnThisStart")
                    val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                        action = ScreenCaptureService.ACTION_START_CAPTURE
                        putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                        putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data!!)
                        putExtra(ScreenCaptureService.EXTRA_TAKE_SCREENSHOT_ON_START, shouldTakeScreenshotOnThisStart)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION) == PackageManager.PERMISSION_GRANTED) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(serviceIntent)
                            } else {
                                startService(serviceIntent)
                            }
                        } else {
                            requestForegroundServicePermissionLauncher.launch(android.Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION)
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }
                    }
                    // If an explicit request led to this grant, reset the flag.
                    if (this@MainActivity.isProcessingExplicitScreenshotRequest) {
                        Log.d(TAG, "Resetting isProcessingExplicitScreenshotRequest flag after successful explicit grant.")
                        this@MainActivity.isProcessingExplicitScreenshotRequest = false
                    }
                    _isMediaProjectionPermissionGranted.value = true
                    onMediaProjectionPermissionGranted?.invoke()
                    onMediaProjectionPermissionGranted = null
                } else {
                    Log.w(TAG, "MediaProjection permission denied or cancelled by user.")
                    Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
                    // If an explicit request was denied, also reset the flag.
                    if (this@MainActivity.isProcessingExplicitScreenshotRequest) {
                        Log.d(TAG, "Resetting isProcessingExplicitScreenshotRequest flag after explicit denial.")
                        this@MainActivity.isProcessingExplicitScreenshotRequest = false
                    }
                    _isMediaProjectionPermissionGranted.value = false
                }
            }

        // Keyboard visibility listener
        val rootView = findViewById<View>(android.R.id.content)
        onGlobalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            if (keypadHeight > screenHeight * 0.15) { // 0.15 ratio is a common threshold
                if (!_isKeyboardOpen.value) {
                    _isKeyboardOpen.value = true
                    Log.d(TAG, "Keyboard visible")
                }
            } else {
                if (_isKeyboardOpen.value) {
                    _isKeyboardOpen.value = false
                    Log.d(TAG, "Keyboard hidden")
                }
            }
        }
        rootView.viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)

        Log.d(TAG, "onCreate: Calling setContent.")
        setContent {
            Log.d(TAG, "setContent: Composable content rendering. Current trial state: $currentTrialState")
            navController = rememberNavController()
            GenerativeAISample {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Log.d(TAG, "setContent: Rendering AppNavigation.")
                    AppNavigation(navController)

                    // if (showPermissionRationaleDialog) { ... } // Deleted block
                    if (showFirstLaunchInfoDialog) {
                        Log.d(TAG, "setContent: Rendering FirstLaunchInfoDialog.")
                        FirstLaunchInfoDialog(
                            onDismiss = {
                                showFirstLaunchInfoDialog = false
                                prefs.edit().putBoolean(PREF_KEY_FIRST_LAUNCH_INFO_SHOWN, true).apply()
                                Log.d(TAG, "FirstLaunchInfoDialog dismissed and preference set.")
                            }
                        )
                    } else if (showApiKeyDialog && currentTrialState != TrialManager.TrialState.EXPIRED_INTERNET_TIME_CONFIRMED) {
                        Log.d(TAG, "setContent: Rendering ApiKeyDialog. showApiKeyDialog=$showApiKeyDialog, currentTrialState=$currentTrialState")
                        ApiKeyDialog(
                            apiKeyManager = apiKeyManager,
                            isFirstLaunch = apiKeyManager.getApiKeys().isEmpty(),
                            onDismiss = {
                                Log.d(TAG, "ApiKeyDialog onDismiss called.")
                                showApiKeyDialog = false
                            }
                        )
                    } else {
                        Log.d(TAG, "setContent: Handling Trial State Dialogs. Current state: $currentTrialState, showTrialInfoDialog: $showTrialInfoDialog")
                        when (currentTrialState) {
                            TrialManager.TrialState.EXPIRED_INTERNET_TIME_CONFIRMED -> {
                                Log.d(TAG, "setContent: Rendering TrialExpiredDialog.")
                                TrialExpiredDialog(
                                    onPurchaseClick = {
                                        Log.d(TAG, "TrialExpiredDialog onPurchaseClick called.")
                                        initiateDonationPurchase()
                                    },
                                    onDismiss = { Log.d(TAG, "TrialExpiredDialog onDismiss called (should be persistent).") }
                                )
                            }
                            TrialManager.TrialState.NOT_YET_STARTED_AWAITING_INTERNET,
                            TrialManager.TrialState.INTERNET_UNAVAILABLE_CANNOT_VERIFY -> {
                                if (showTrialInfoDialog) {
                                    Log.d(TAG, "setContent: Rendering InfoDialog for AWAITING/UNAVAILABLE. Message: $trialInfoMessage")
                                    InfoDialog(message = trialInfoMessage, onDismiss = {
                                        Log.d(TAG, "InfoDialog onDismiss called.")
                                        showTrialInfoDialog = false
                                    })
                                } else {
                                    Log.d(TAG, "setContent: Not rendering InfoDialog for AWAITING/UNAVAILABLE because showTrialInfoDialog is false.")
                                }
                            }
                            TrialManager.TrialState.ACTIVE_INTERNET_TIME_CONFIRMED,
                            TrialManager.TrialState.PURCHASED -> {
                                Log.d(TAG, "setContent: No specific dialog for ACTIVE/PURCHASED states.")
                            }
                        }
                    }
                }
            }
        }
        Log.d(TAG, "onCreate: setContent finished.")

        NotificationUtil.createNotificationChannel(this) // Create channel

        // Register screenshot request handler
        Log.d(TAG, "Registering screenshotRequestHandler for ACTION_REQUEST_MEDIAPROJECTION_SCREENSHOT.")
        val requestFilter = IntentFilter(ACTION_REQUEST_MEDIAPROJECTION_SCREENSHOT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenshotRequestHandler, requestFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenshotRequestHandler, requestFilter)
        }

        // Register screenshot result handler
        Log.d(TAG, "Registering screenshotResultHandler for ACTION_MEDIAPROJECTION_SCREENSHOT_CAPTURED.")
        val resultFilter = IntentFilter(ACTION_MEDIAPROJECTION_SCREENSHOT_CAPTURED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenshotResultHandler, resultFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenshotResultHandler, resultFilter)
        }

        requestNotificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Notification permission granted.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notification permission denied. Stop via notification will not be available.", Toast.LENGTH_LONG).show()
            }
        }

        requestForegroundServicePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Foreground service permission granted.", Toast.LENGTH_SHORT).show()
                // The user has granted the permission, now we can start the service.
                // We need to get the result data from the media projection launcher again.
                // This is not ideal, but it's the only way to do it without major refactoring.
                val intent = mediaProjectionManager.createScreenCaptureIntent()
                mediaProjectionLauncher.launch(intent)
            } else {
                Toast.makeText(this, "Foreground service permission denied. The app may not function correctly.", Toast.LENGTH_LONG).show()
            }
        }

        if (photoReasoningViewModel != null) {
            lifecycleScope.launch {
                photoReasoningViewModel!!.showStopNotificationFlow.collect { show ->
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
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NOTIFICATION_RATIONALE_SHOWN, false)
    }

    fun setNotificationRationaleShown(shown: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putBoolean(KEY_NOTIFICATION_RATIONALE_SHOWN, shown)
            apply()
        }
    }

    @Composable
    fun AppNavigation(navController: NavHostController) {
        val isAppEffectivelyUsable = currentTrialState != TrialManager.TrialState.EXPIRED_INTERNET_TIME_CONFIRMED
        Log.d(TAG, "AppNavigation: isAppEffectivelyUsable = $isAppEffectivelyUsable (currentTrialState: $currentTrialState)")

        val alwaysAvailableRoutes = listOf("ApiKeyDialog", "ChangeModel")

        NavHost(navController = navController, startDestination = "menu") {
            composable("menu") {
                Log.d(TAG, "AppNavigation: Composing 'menu' screen.")
                MenuScreen(
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
                    onApiKeyButtonClicked = {
                        Log.d(TAG, "MenuScreen onApiKeyButtonClicked: Showing ApiKeyDialog.")
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
                    PhotoReasoningRoute()
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
        if (currentTrialState != TrialManager.TrialState.PURCHASED && currentTrialState != TrialManager.TrialState.EXPIRED_INTERNET_TIME_CONFIRMED) {
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
        if (::billingClient.isInitialized && billingClient.isReady) {
            Log.d(TAG, "setupBillingClient: BillingClient already initialized and ready.")
            return
        }
        if (::billingClient.isInitialized && billingClient.connectionState == BillingClient.ConnectionState.CONNECTING) {
            Log.d(TAG, "setupBillingClient: BillingClient already connecting.")
            return
        }

        billingClient = BillingClient.newBuilder(this)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
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
        if (!billingClient.isReady) {
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
        Log.d(TAG, "initiateDonationPurchase called.")
        if (!::billingClient.isInitialized) {
            Log.e(TAG, "initiateDonationPurchase: BillingClient not initialized.")
            updateStatusMessage("Payment service not initialized. Please try again later.", true)
            return
        }
        if (!billingClient.isReady) {
            Log.e(TAG, "initiateDonationPurchase: BillingClient not ready. Connection state: ${billingClient.connectionState}")
            updateStatusMessage("Payment service not ready. Please try again later.", true)
            if (billingClient.connectionState == BillingClient.ConnectionState.CLOSED || billingClient.connectionState == BillingClient.ConnectionState.DISCONNECTED){
                Log.d(TAG, "initiateDonationPurchase: BillingClient disconnected, attempting to reconnect.")
                billingClient.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(setupResult: BillingResult) {
                        Log.i(TAG, "initiateDonationPurchase (reconnect): onBillingSetupFinished. ResponseCode: ${setupResult.responseCode}")
                        if (setupResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.d(TAG, "initiateDonationPurchase (reconnect): Reconnection successful, retrying purchase.")
                            initiateDonationPurchase()
                        } else {
                             Log.e(TAG, "initiateDonationPurchase (reconnect): BillingClient setup failed after disconnect: ${setupResult.debugMessage}")
                        }
                    }
                    override fun onBillingServiceDisconnected() { Log.w(TAG, "initiateDonationPurchase (reconnect): BillingClient still disconnected.") }
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
            if (purchase.products.any { it == subscriptionProductId }) {
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
        if (!::billingClient.isInitialized || !billingClient.isReady) {
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
                    if (purchase.products.any { it == subscriptionProductId } && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
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
                            Toast.makeText(this@MainActivity, "Subscription is deactivated", Toast.LENGTH_LONG).show()
                        }
                        startTrialServiceIfNeeded()
                    } else {
                         Log.w(TAG, "queryActiveSubscriptions: No active sub from Google, but TrialManager says PURCHASED. This could be due to restored SharedPreferences without active subscription. Re-evaluating trial logic based on no internet time.")
                         updateTrialState(TrialManager.getTrialState(this, null))
                         if (currentTrialState == TrialManager.TrialState.EXPIRED_INTERNET_TIME_CONFIRMED) {
                            Log.i(TAG, "queryActiveSubscriptions: Subscription deactivated (no active sub, was purchased, now trial expired). Showing Toast.")
                            Toast.makeText(this@MainActivity, "Subscription is deactivated", Toast.LENGTH_LONG).show()
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
                        Toast.makeText(this@MainActivity, "Subscription is deactivated", Toast.LENGTH_LONG).show()
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

        // Unregister screenshot request handler (already there)
        Log.d(TAG, "Unregistering screenshotRequestHandler.")
        try {
            unregisterReceiver(screenshotRequestHandler)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Screenshot request handler was not registered or already unregistered.", e)
        }

        // Unregister screenshot result handler (already there)
        Log.d(TAG, "Unregistering screenshotResultHandler.")
    try {
        unregisterReceiver(screenshotResultHandler)
    } catch (e: IllegalArgumentException) { Log.w(TAG, "Screenshot result handler was not registered or already unregistered.", e) }

        // ... rest of existing onDestroy code (trialStatusReceiver, billingClient, etc.)
        Log.d(TAG, "onDestroy: Unregistering trialStatusReceiver.")
        try {
            unregisterReceiver(trialStatusReceiver)
            Log.d(TAG, "onDestroy: trialStatusReceiver unregistered successfully.")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "onDestroy: trialStatusReceiver was not registered or already unregistered.", e)
        }

        if (::billingClient.isInitialized && billingClient.isReady) {
            Log.d(TAG, "onDestroy: BillingClient is initialized and ready. Ending connection.")
            billingClient.endConnection()
            Log.d(TAG, "onDestroy: BillingClient connection ended.")
        }
        onGlobalLayoutListener?.let {
            findViewById<View>(android.R.id.content).viewTreeObserver.removeOnGlobalLayoutListener(it)
            Log.d(TAG, "onDestroy: Keyboard layout listener removed.")
        }
        if (this == instance) {
            instance = null
            Log.d(TAG, "onDestroy: MainActivity instance cleared.")
        }
        Log.d(TAG, "onDestroy: Finished.")
    }

    // private fun checkAndRequestPermissions() { ... } // Deleted

    companion object {
        private const val TAG = "MainActivity"
        private var instance: MainActivity? = null
        fun getInstance(): MainActivity? {
            Log.d(TAG, "getInstance() called. Returning instance: ${if(instance == null) "null" else "not null"}")
            return instance
        }
        private const val PREFS_NAME = "AppPrefs"
        private const val PREF_KEY_FIRST_LAUNCH_INFO_SHOWN = "firstLaunchInfoShown"
        private const val KEY_NOTIFICATION_RATIONALE_SHOWN = "notification_rationale_shown"

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
}

@Composable
fun FirstLaunchInfoDialog(onDismiss: () -> Unit) {
    Log.d("FirstLaunchInfoDialog", "Composing FirstLaunchInfoDialog")
    Dialog(onDismissRequest = {
        Log.d("FirstLaunchInfoDialog", "onDismissRequest called")
        onDismiss()
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Trial Information",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "You can try Screen Operator for 30 minutes before you have to subscribe to support the development of more features.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(
                    onClick = {
                        Log.d("FirstLaunchInfoDialog", "OK button clicked")
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("OK")
                }
            }
        }
    }
}

// @Composable // Deleted
// fun PermissionRationaleDialog(onDismiss: () -> Unit) { ... } // Deleted


@Composable
fun TrialExpiredDialog(
    onPurchaseClick: () -> Unit,
    onDismiss: () -> Unit 
) {
    Log.d("TrialExpiredDialog", "Composing TrialExpiredDialog")
    Dialog(onDismissRequest = {
        Log.d("TrialExpiredDialog", "onDismissRequest called (persistent dialog - user tried to dismiss)")
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Trial period expired",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Your 30-minute trial period has ended. Please subscribe to the app to continue using it.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        Log.d("TrialExpiredDialog", "Purchase button clicked")
                        onPurchaseClick()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Subscribe")
                }
            }
        }
    }
}

@Composable
fun InfoDialog( 
    message: String,
    onDismiss: () -> Unit
) {
    Log.d("InfoDialog", "Composing InfoDialog with message: $message")
    Dialog(onDismissRequest = {
        Log.d("InfoDialog", "onDismissRequest called")
        onDismiss()
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Information",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = {
                    Log.d("InfoDialog", "OK button clicked")
                    onDismiss()
                }) {
                    Text("OK")
                }
            }
        }
    }
}
