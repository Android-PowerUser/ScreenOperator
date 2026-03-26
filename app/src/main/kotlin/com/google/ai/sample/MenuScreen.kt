package com.google.ai.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.sp
import android.widget.Toast
import android.Manifest // For Manifest.permission.POST_NOTIFICATIONS
import androidx.compose.material3.AlertDialog // For the rationale dialog
import androidx.compose.runtime.saveable.rememberSaveable
import android.util.Log
import android.os.Environment
import android.os.StatFs
import com.google.ai.sample.feature.multimodal.ModelDownloadManager
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import java.io.File

/**
 * Modifier, der sicherstellt dass horizontale Touch-Events für Slider
 * nicht von einer übergeordneten LazyColumn abgefangen werden.
 * Behebt den Swipe-Bug wo das Wischen über Slider in LazyColumn hakelt.
 *
 * Konsumiert nur horizontale Drag-Events auf Main-Pass-Ebene,
 * damit die LazyColumn nicht vorzeitig das Scrollen übernimmt.
 * Der Slider selbst behält die volle Kontrolle über die Interaktion.
 */
fun Modifier.sliderFriendly(): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        // Ersten Touch abwarten (keine Konsumation, nur beobachten)
        val down = awaitFirstDown(requireUnconsumed = false)
        var lastX = down.position.x
        var isDraggingHorizontally = false

        // Weitere Pointer-Events beobachten
        do {
            val event = awaitPointerEvent(pass = PointerEventPass.Main)
            val change = event.changes.firstOrNull() ?: break

            val dx = kotlin.math.abs(change.position.x - lastX)
            val dy = kotlin.math.abs(change.position.y - change.previousPosition.y)

            // Wenn horizontale Bewegung dominiert, konsumiere den Event
            // damit die LazyColumn nicht vertikal scrollt
            if (dx > dy || isDraggingHorizontally) {
                isDraggingHorizontally = true
                change.consume()
            }
            lastX = change.position.x
        } while (event.changes.any { it.pressed })
    }
}

data class MenuItem(
    val routeId: String,
    val titleResId: Int,
    val descriptionResId: Int
)

@Composable
fun MenuScreen(
    innerPadding: PaddingValues,
    onItemClicked: (String) -> Unit = { },
    onApiKeyButtonClicked: (ApiProvider?) -> Unit = { },
    onDonationButtonClicked: () -> Unit = { },
    isTrialExpired: Boolean = false, // New parameter to indicate trial status
    isPurchased: Boolean = false
) {
    val context = LocalContext.current
    var showRationaleDialogForPhotoReasoning by rememberSaveable { mutableStateOf(false) }
    val menuItems = listOf(
        MenuItem("photo_reasoning", R.string.menu_reason_title, R.string.menu_reason_description)
    )

    // Get current model
    val currentModel = GenerativeAiViewModelFactory.getCurrentModel()
    var selectedModel by remember { mutableStateOf(currentModel) }
    var expanded by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadDialogModel by remember { mutableStateOf<ModelOption?>(null) }
    var showHumanExpertSupportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // API Key Management Button
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(all = 16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "API Key Management",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onApiKeyButtonClicked(null) },
                            enabled = true, // Always enabled
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(text = "Change API Key")
                        }
                    }
                }
            }

            // Model Selection
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(all = 16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "Model Selection",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Current model: ${selectedModel.displayName}",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.weight(1f))

                            Button(
                                onClick = { expanded = true },
                                enabled = true // Always enabled
                            ) {
                                Text("Change Model")
                            }

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                val orderedModels = ModelOption.values().toList()

                                orderedModels.forEach { modelOption ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(modelOption.displayName + (modelOption.size?.let { " - $it" } ?: ""))
                                        },
                                        onClick = {
                                            expanded = false
                                            val wasOfflineModel = selectedModel == ModelOption.GEMMA_3N_E4B_IT
                                            
                                            if (modelOption == ModelOption.GEMMA_3N_E4B_IT) {
                                                val isDownloaded = ModelDownloadManager.isModelDownloaded(context)
                                                if (!isDownloaded) {
                                                    downloadDialogModel = modelOption
                                                    showDownloadDialog = true
                                                } else {
                                                    selectedModel = modelOption
                                                    GenerativeAiViewModelFactory.setModel(modelOption, context)
                                                    
                                                    // Task 15: Initialize offline model upon selection
                                                    val mainActivity = context as? MainActivity
                                                    mainActivity?.getPhotoReasoningViewModel()?.reinitializeOfflineModel(context)
                                                }
                                            } else if (modelOption == ModelOption.HUMAN_EXPERT) {
                                                if (isPurchased) {
                                                    selectedModel = modelOption
                                                    GenerativeAiViewModelFactory.setModel(modelOption, context)
                                                    if (wasOfflineModel) {
                                                        // Task 19: Close offline model to free RAM
                                                        val mainActivity = context as? MainActivity
                                                        mainActivity?.getPhotoReasoningViewModel()?.closeOfflineModel()
                                                    }
                                                } else {
                                                    showHumanExpertSupportDialog = true
                                                }
                                            } else {
                                                selectedModel = modelOption
                                                GenerativeAiViewModelFactory.setModel(modelOption, context)
                                                if (wasOfflineModel) {
                                                    // Task 19: Close offline model to free RAM
                                                    val mainActivity = context as? MainActivity
                                                    mainActivity?.getPhotoReasoningViewModel()?.closeOfflineModel()
                                                }
                                            }
                                        },
                                        enabled = true // Always enabled
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // CPU/GPU Selection - only visible when offline model is selected
            if (selectedModel == ModelOption.GEMMA_3N_E4B_IT) {
                item {
                    val currentBackend = remember { mutableStateOf(GenerativeAiViewModelFactory.getBackend()) }
                    
                    // Load preference on first composition
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        GenerativeAiViewModelFactory.loadBackendPreference(context)
                        currentBackend.value = GenerativeAiViewModelFactory.getBackend()
                    }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(all = 16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = "Prozessor-Auswahl (CPU / GPU)",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Aktuell: ${if (currentBackend.value == InferenceBackend.GPU) "GPU" else "CPU"}",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        GenerativeAiViewModelFactory.setBackend(InferenceBackend.GPU, context)
                                        currentBackend.value = InferenceBackend.GPU
                                        val mainActivity = context as? MainActivity
                                        mainActivity?.getPhotoReasoningViewModel()?.closeOfflineModel()
                                        Toast.makeText(context, "GPU selected – Model stopped. Will load on next generation", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = if (currentBackend.value == InferenceBackend.GPU)
                                        ButtonDefaults.buttonColors()
                                    else
                                        ButtonDefaults.outlinedButtonColors(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("GPU")
                                }

                                Button(
                                    onClick = {
                                        GenerativeAiViewModelFactory.setBackend(InferenceBackend.CPU, context)
                                        currentBackend.value = InferenceBackend.CPU
                                        val mainActivity = context as? MainActivity
                                        mainActivity?.getPhotoReasoningViewModel()?.closeOfflineModel()
                                        Toast.makeText(context, "CPU selected – Model stopped. Will load on next generation", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = if (currentBackend.value == InferenceBackend.CPU)
                                        ButtonDefaults.buttonColors()
                                    else
                                        ButtonDefaults.outlinedButtonColors(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("CPU")
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Models on the GPU require a lot of RAM, and ZRAM should be disabled, but they are faster and still consume significantly less power than on the CPU. " +
                                    "The language model cannot use RAM swap with the GPU. However, for the stability of your phone, you should still use memory RAM swap. " +
                                    "For models on the CPU, a cache file is written once and quickly available again, but in addition, it permanently uses half of the language model as memory consumption.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Generation Settings (TopK, TopP, Temperature) for current model
            if (selectedModel.supportsGenerationSettings) {
                item {
                    val genSettings = remember(selectedModel) {
                        mutableStateOf(
                            com.google.ai.sample.util.GenerationSettingsPreferences.loadSettings(
                                context, selectedModel.modelName
                            )
                        )
                    }
                    var tempSlider by remember(selectedModel) { mutableStateOf(genSettings.value.temperature) }
                    var topPSlider by remember(selectedModel) { mutableStateOf(genSettings.value.topP) }
                    var topKSlider by remember(selectedModel) { mutableStateOf(genSettings.value.topK.toFloat()) }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(all = 16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = "Generation Settings (${selectedModel.displayName})",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Temperature Slider (0.0 - 2.0)
                            Text(
                                text = "Temperature: ${"%.2f".format(tempSlider)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            androidx.compose.material3.Slider(
                                value = tempSlider,
                                onValueChange = { newVal ->
                                    tempSlider = newVal
                                },
                                onValueChangeFinished = {
                                    genSettings.value = genSettings.value.copy(temperature = tempSlider)
                                    com.google.ai.sample.util.GenerationSettingsPreferences.saveSettings(
                                        context, selectedModel.modelName, genSettings.value
                                    )
                                },
                                valueRange = 0f..2f,
                                steps = 0,
                                modifier = Modifier.fillMaxWidth().sliderFriendly()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // TopP Slider (0.0 - 1.0)
                            Text(
                                text = "Top P: ${"%.2f".format(topPSlider)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            androidx.compose.material3.Slider(
                                value = topPSlider,
                                onValueChange = { newVal ->
                                    topPSlider = newVal
                                },
                                onValueChangeFinished = {
                                    genSettings.value = genSettings.value.copy(topP = topPSlider)
                                    com.google.ai.sample.util.GenerationSettingsPreferences.saveSettings(
                                        context, selectedModel.modelName, genSettings.value
                                    )
                                },
                                valueRange = 0f..1f,
                                steps = 0,
                                modifier = Modifier.fillMaxWidth().sliderFriendly()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // TopK Slider (0 - 100)
                            Text(
                                text = "Top K: ${Math.round(topKSlider)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            androidx.compose.material3.Slider(
                                value = topKSlider,
                                onValueChange = { newVal ->
                                    topKSlider = newVal
                                },
                                onValueChangeFinished = {
                                    genSettings.value = genSettings.value.copy(topK = Math.round(topKSlider))
                                    com.google.ai.sample.util.GenerationSettingsPreferences.saveSettings(
                                        context, selectedModel.modelName, genSettings.value
                                    )
                                },
                                valueRange = 0f..100f,
                                steps = 0,
                                modifier = Modifier.fillMaxWidth().sliderFriendly()
                            )

                            if (selectedModel == ModelOption.GEMMA_3N_E4B_IT) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Note: LlmInference (offline model) may not support all generation parameters.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Menu Items
            items(menuItems) { menuItem ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(all = 16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = if (menuItem.routeId == "photo_reasoning" && selectedModel == ModelOption.HUMAN_EXPERT) "Operate with human expert" else stringResource(menuItem.titleResId),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (menuItem.routeId == "photo_reasoning" && selectedModel == ModelOption.HUMAN_EXPERT) "A human expert uses screen mirroring and operates with tap's" else stringResource(menuItem.descriptionResId),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        TextButton(
                            onClick = {
                                if (isTrialExpired) {
                                    Toast.makeText(context, "Please support the development of the app so that you can continue using it \uD83C\uDF89", Toast.LENGTH_LONG).show()
                                } else {
                                    if (menuItem.routeId == "photo_reasoning") {
                                        val mainActivity = context as? MainActivity
                                        val activeModel = GenerativeAiViewModelFactory.getCurrentModel()
                                        // Check API Key for online models
                                        if (activeModel.apiProvider != ApiProvider.GOOGLE || !activeModel.modelName.contains("litert")) { // Simple check, refine if needed. Actually offline model has specific Enum
                                             if (activeModel != ModelOption.GEMMA_3N_E4B_IT && activeModel != ModelOption.HUMAN_EXPERT) {
                                                 val apiKey = mainActivity?.getCurrentApiKey(activeModel.apiProvider)
                                                 if (apiKey.isNullOrEmpty()) {
                                                     // Show API Key Dialog
                                                     onApiKeyButtonClicked(activeModel.apiProvider) // Or a specific callback to show dialog
                                                     return@TextButton
                                                 }
                                             }
                                        }

                                        if (mainActivity != null) { // Ensure mainActivity is not null
                                            if (!mainActivity.isNotificationPermissionGranted()) {
                                                Log.d("MenuScreen", "Notification permission NOT granted.")
                                                if (mainActivity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) && !mainActivity.hasShownNotificationRationale()) {
                                                    Log.d("MenuScreen", "Showing notification rationale dialog.")
                                                    showRationaleDialogForPhotoReasoning = true
                                                    // onItemClicked will be called from dialog
                                                } else {
                                                    Log.d("MenuScreen", "Rationale not needed or already handled. Requesting permission directly.")
                                                    // mainActivity.requestNotificationPermission()
                                                    onItemClicked(menuItem.routeId) // Proceed to navigate
                                                }
                                            } else {
                                                Log.d("MenuScreen", "Notification permission ALREADY granted.")
                                                onItemClicked(menuItem.routeId) // Proceed to navigate
                                            }
                                        } else {
                                            Log.e("MenuScreen", "MainActivity instance is null. Cannot check/request permission.")
                                            onItemClicked(menuItem.routeId) // Proceed to navigate anyway
                                        }
                                    } else {
                                        // For other menu items, navigate directly
                                        onItemClicked(menuItem.routeId)
                                    }
                                }
                            },
                            enabled = !isTrialExpired, // Disable button if trial is expired
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(text = stringResource(R.string.action_try))
                        }
                    }
                }
            }

            // Donation Button Card (Should always be enabled)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(all = 16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isPurchased) {
                            Text(
                                text = "Thank you for supporting the development! 🎉💛",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = "Support Improvements\n                \uD83C\uDF89",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = onDonationButtonClicked,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(text = "Pro (2,90 €/Month)")
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)
                    val annotatedText = buildAnnotatedString {
                        append("• ")
                        withStyle(boldStyle) { append("Preview Models") }
                        append(" could be deactivated by Google without being handed over to the final release.\n")
                        append("• ")
                        withStyle(boldStyle) { append("API Keys") }
                        append(" are automatically switched if multiple are inserted and one is exhausted.\n")

                        append("• ")
                        withStyle(boldStyle) { append("GPT-oss 120b") }
                        append(" is a pure text model.\n")
                        append("• ")

                        withStyle(boldStyle) { append("Gemma 27B IT") }
                        append(" cannot handle screenshots in the API.\n")
                        append("• GPT models (")
                        withStyle(boldStyle) { append("Vercel") }
                        append(") have a free budget of \$5 per month and a credit card is necessary.\n")
                        append("GPT-5.1 Input: \$1.25/M Output: \$10.00/M\n")
                        append("GPT-5.1 mini Input: \$0.25/M Output: \$2.00/M\n")
                        append("GPT-5 nano Input: \$0.05/M Output: \$0.40/M\n")
                        append("• When a language model repeats a token, Top K and Top P must be lowered.\n")
                        append("• There are ")
                        withStyle(boldStyle) { append("rate limits") }
                        append(" for free use of ")
                        withStyle(boldStyle) { append("Gemini models") }
                        append(". The less powerful the models are, the more you can use them. The limits range from a maximum of 5 to 30 calls per minute. After each screenshot (every 2-3 seconds) the LLM must respond again. More information is available at ")

                        pushStringAnnotation(tag = "URL", annotation = "https://ai.google.dev/gemini-api/docs/rate-limits")
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                            append("https://ai.google.dev/gemini-api/docs/rate-limits")
                        }
                        pop()
                    }

                    val uriHandler = LocalUriHandler.current

                    ClickableText(
                        text = annotatedText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = 16.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = { offset ->
                            // Allow clicking links even if trial is expired
                            annotatedText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                .firstOrNull()?.let { annotation ->
                                    uriHandler.openUri(annotation.item)
                                }
                        }
                    )
                }
            }
        }
    }

    if (showRationaleDialogForPhotoReasoning) {
        val mainActivity = LocalContext.current as? MainActivity
        AlertDialog(
            onDismissRequest = {
                showRationaleDialogForPhotoReasoning = false
                // If dismissed, still proceed to the item, permission will be asked by OS if needed later by other flows, or user can try again.
                // Or, we can choose not to navigate if they dismiss. For now, let's navigate.
                 mainActivity?.let { onItemClicked("photo_reasoning") }
            },
            title = { Text("Notification Permission") },
            text = { Text("You can grant notification permission if you want to be able to stop Screen Operator via notifications.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        Log.d("MenuScreen", "Rationale dialog OK clicked.")
                        showRationaleDialogForPhotoReasoning = false
                        mainActivity?.setNotificationRationaleShown(true)
                        Log.d("MenuScreen", "Requesting notification permission from rationale dialog.")
                        // mainActivity?.requestNotificationPermission()
                        // Log after to see if it's called immediately or if requestNotificationPermission is suspending (it's not)
                        Log.d("MenuScreen", "Navigating to photo_reasoning after rationale OK.")
                        mainActivity?.let { onItemClicked("photo_reasoning") }
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        Log.d("MenuScreen", "Rationale dialog Cancel clicked or dismissed.")
                        showRationaleDialogForPhotoReasoning = false
                        Log.d("MenuScreen", "Navigating to photo_reasoning after rationale cancel/dismiss.")
                        mainActivity?.let { onItemClicked("photo_reasoning") }
                    }
                ) { Text("Cancel") }
            }
        )
    }

    if (showDownloadDialog && downloadDialogModel != null) {
        val statFs = StatFs(Environment.getExternalStorageDirectory().path)
        val bytesAvailable = statFs.availableBlocksLong * statFs.blockSizeLong
        val gbAvailable = bytesAvailable.toDouble() / (1024 * 1024 * 1024)
        val formattedGbAvailable = String.format("%.2f", gbAvailable)
        
        val dlState by ModelDownloadManager.downloadState.collectAsState()

        AlertDialog(
            onDismissRequest = {
                if (dlState is ModelDownloadManager.DownloadState.Idle || dlState is ModelDownloadManager.DownloadState.Completed || dlState is ModelDownloadManager.DownloadState.Error) {
                    showDownloadDialog = false
                }
                // Don't dismiss while downloading/paused
            },
            title = { Text("Download Model (4.92 GB)") },
            text = {
                Column {
                    when (val state = dlState) {
                        is ModelDownloadManager.DownloadState.Idle -> {
                            Text("Should the Gemma 3n E4B be downloaded?\n\n$formattedGbAvailable GB of storage available.")
                        }
                        is ModelDownloadManager.DownloadState.Downloading -> {
                            Text("Downloading...")
                            Spacer(modifier = Modifier.height(8.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${ModelDownloadManager.formatBytes(state.bytesDownloaded)} / ${if (state.totalBytes > 0) ModelDownloadManager.formatBytes(state.totalBytes) else "?"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "${"%.1f".format(state.progress * 100)}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        is ModelDownloadManager.DownloadState.Paused -> {
                            Text("Download paused.")
                            Spacer(modifier = Modifier.height(8.dp))
                            val progress = if (state.totalBytes > 0) state.bytesDownloaded.toFloat() / state.totalBytes else 0f
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${ModelDownloadManager.formatBytes(state.bytesDownloaded)} / ${if (state.totalBytes > 0) ModelDownloadManager.formatBytes(state.totalBytes) else "?"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        is ModelDownloadManager.DownloadState.Completed -> {
                            Text("Download complete! ✅")
                        }
                        is ModelDownloadManager.DownloadState.Error -> {
                            Text("Error: ${state.message}")
                        }
                    }
                }
            },
            confirmButton = {
                when (dlState) {
                    is ModelDownloadManager.DownloadState.Idle -> {
                        TextButton(
                            onClick = {
                                downloadDialogModel?.downloadUrl?.let { url ->
                                    ModelDownloadManager.downloadModel(context, url)
                                    // Task 2: Request notification permission when download starts
                                    val mainActivity = context as? MainActivity
                                    if (mainActivity != null && !mainActivity.isNotificationPermissionGranted()) {
                                        mainActivity.requestNotificationPermission()
                                    }
                                }
                            }
                        ) { Text("Download") }
                    }
                    is ModelDownloadManager.DownloadState.Downloading -> {
                        TextButton(onClick = { ModelDownloadManager.pauseDownload() }) { Text("Pause") }
                    }
                    is ModelDownloadManager.DownloadState.Paused -> {
                        TextButton(
                            onClick = {
                                downloadDialogModel?.downloadUrl?.let { url ->
                                    ModelDownloadManager.resumeDownload(context, url)
                                }
                            }
                        ) { Text("Resume") }
                    }
                    is ModelDownloadManager.DownloadState.Completed -> {
                        TextButton(onClick = {
                            // Set model only after download is completed (Point 17)
                            downloadDialogModel?.let {
                                selectedModel = it
                                GenerativeAiViewModelFactory.setModel(it, context)
                            }
                            showDownloadDialog = false
                        }) { Text("Close") }
                    }
                    is ModelDownloadManager.DownloadState.Error -> {
                        TextButton(
                            onClick = {
                                downloadDialogModel?.downloadUrl?.let { url ->
                                    ModelDownloadManager.downloadModel(context, url)
                                }
                            }
                        ) { Text("Retry") }
                    }
                }
            },
            dismissButton = {
                when (dlState) {
                    is ModelDownloadManager.DownloadState.Idle -> {
                        TextButton(onClick = { showDownloadDialog = false }) { Text("Cancel") }
                    }
                    is ModelDownloadManager.DownloadState.Downloading,
                    is ModelDownloadManager.DownloadState.Paused -> {
                        TextButton(
                            onClick = {
                                ModelDownloadManager.cancelDownload(context)
                                showDownloadDialog = false
                            }
                        ) { Text("Cancel Download") }
                    }
                    is ModelDownloadManager.DownloadState.Completed -> { /* No dismiss button */ }
                    is ModelDownloadManager.DownloadState.Error -> {
                        TextButton(onClick = { showDownloadDialog = false }) { Text("Close") }
                    }
                }
            }
        )
    }

    // Human Expert Support Dialog (Task 4)
    if (showHumanExpertSupportDialog) {
        AlertDialog(
            onDismissRequest = { showHumanExpertSupportDialog = false },
            title = { Text("Human Expert") },
            text = {
                Text("To ensure that a human expert accepts the task, please support the expert.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showHumanExpertSupportDialog = false
                        onDonationButtonClicked()
                    }
                ) {
                    Text("Support \uD83C\uDF89")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHumanExpertSupportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Preview(showSystemUi = true)
@Composable
fun MenuScreenPreview() {
    // Preview with trial not expired
    MenuScreen(innerPadding = PaddingValues(), isTrialExpired = false, isPurchased = false)
}

@Preview(showSystemUi = true)
@Composable
fun MenuScreenPurchasedPreview() {
    MenuScreen(innerPadding = PaddingValues(), isTrialExpired = false, isPurchased = true)
}

@Preview(showSystemUi = true)
@Composable
fun MenuScreenTrialExpiredPreview() {
    // Preview with trial expired
    MenuScreen(innerPadding = PaddingValues(), isTrialExpired = true, isPurchased = false)
}
