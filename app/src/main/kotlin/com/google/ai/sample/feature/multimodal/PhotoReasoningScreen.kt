package com.google.ai.sample.feature.multimodal

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.provider.Settings
import android.widget.Toast 
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items 
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.lazy.LazyListState
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.ai.sample.GenerativeViewModelFactory
import com.google.ai.sample.MainActivity
import coil.size.Precision
import com.google.ai.sample.R
import com.google.ai.sample.ScreenOperatorAccessibilityService
import com.google.ai.sample.util.Command
import com.google.ai.sample.util.SystemMessageEntry
import com.google.ai.sample.util.SystemMessageEntryPreferences
import com.google.ai.sample.util.UriSaver
import com.google.ai.sample.util.shareTextFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import android.util.Log
import kotlinx.serialization.SerializationException

@Composable
fun PhotoReasoningScreen(
    innerPadding: PaddingValues,  // Füge Parameter hinzu
    uiState: PhotoReasoningUiState = PhotoReasoningUiState.Initial,
    commandExecutionStatus: String = "",
    detectedCommands: List<Command> = emptyList(),
    systemMessage: String = "",
    chatMessages: StateFlow<List<PhotoReasoningMessage>>,
    onSystemMessageChanged: (String) -> Unit = {},
    onRestoreSystemMessageClicked: () -> Unit = {},
    onReasonClicked: (String, List<Uri>) -> Unit = { _, _ -> },
    isAccessibilityServiceEnabled: Boolean = false,
    isMediaProjectionPermissionGranted: Boolean = false,
    onEnableAccessibilityService: () -> Unit = {},
    onClearChatHistory: () -> Unit = {},
    isKeyboardOpen: Boolean,
    onStopClicked: () -> Unit = {},
    isInitialized: Boolean = true,
    modelName: String = "",
    userQuestion: String = "",
    onUserQuestionChanged: (String) -> Unit = {},
    isGenerationRunning: Boolean = false,
    isOfflineGpuModelLoaded: Boolean = false,
    isInitializingOfflineModel: Boolean = false
) {
    val imageUris = rememberSaveable(saver = UriSaver()) { mutableStateListOf() }
    var isSystemMessageFocused by rememberSaveable { mutableStateOf(false) }
    var showDatabaseListPopup by rememberSaveable { mutableStateOf(false) }
    var showEditEntryPopup by rememberSaveable { mutableStateOf(false) }
    var entryToEdit: SystemMessageEntry? by rememberSaveable(stateSaver = SystemMessageEntrySaver) { mutableStateOf(null) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var systemMessageEntries by rememberSaveable { mutableStateOf(emptyList<SystemMessageEntry>()) }
    val focusManager = LocalFocusManager.current
    val messages by chatMessages.collectAsState()

    LaunchedEffect(Unit) {
        systemMessageEntries = SystemMessageEntryPreferences.loadEntries(context)
    }
     LaunchedEffect(showDatabaseListPopup) {
        if (showDatabaseListPopup) {
            systemMessageEntries = SystemMessageEntryPreferences.loadEntries(context)
        }
    }

    BackHandler(enabled = isSystemMessageFocused && !isKeyboardOpen) {
        focusManager.clearFocus()
        isSystemMessageFocused = false
    }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { imageUris.add(it) }
    }

    LaunchedEffect(messages.size, commandExecutionStatus, detectedCommands.size) {
        val chatMessageCount = messages.size
        var targetIndex = -1 // Default to no scroll if no items

        if (messages.isNotEmpty()) {
            targetIndex = chatMessageCount - 1 // Last chat message
        }

        val commandStatusPresent = commandExecutionStatus.isNotEmpty()
        val detectedCommandsPresent = detectedCommands.isNotEmpty()

        if (commandStatusPresent) {
            targetIndex = chatMessageCount // Index of command status card (0-based from chat messages)
        }
        if (detectedCommandsPresent) {
            targetIndex = chatMessageCount + (if (commandStatusPresent) 1 else 0) // Index of detected commands card
        }

        val totalItems = chatMessageCount +
                         (if (commandStatusPresent) 1 else 0) +
                         (if (detectedCommandsPresent) 1 else 0)

        if (targetIndex >= 0 && targetIndex < totalItems) {
            listState.animateScrollToItem(targetIndex)
        } else if (totalItems > 0) { // Fallback for safety, if targetIndex is somehow out of initial bounds but items exist
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(
                start = innerPadding.calculateStartPadding(LocalLayoutDirection.current) + 16.dp,
                end = innerPadding.calculateEndPadding(LocalLayoutDirection.current) + 16.dp,
                top = 10.dp,  // Kleines bisschen nach unten gerückt (verhindert Kollision mit Status-Bar-Schrift)
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            )
            .imePadding(), // NEU: Verschiebt das Layout nach oben wenn die Tastatur erscheint
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "System Message",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onRestoreSystemMessageClicked() },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = BorderStroke(1.dp, Color.Black),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Restore\nSystem Message",
                            fontSize = 8.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 9.sp
                        )
                    }
                    Button(
                        onClick = { showDatabaseListPopup = true },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = BorderStroke(1.dp, Color.Black)
                    ) { Text("Database") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                val systemMessageHeight = when {
                    isSystemMessageFocused && isKeyboardOpen -> 450.dp
                    isSystemMessageFocused && !isKeyboardOpen -> 1000.dp
                    else -> 120.dp
                }
                val currentMinLines = if (systemMessageHeight == 120.dp) 3 else 1
                val currentMaxLines = if (systemMessageHeight == 120.dp) 5 else Int.MAX_VALUE
                OutlinedTextField(
                    value = systemMessage,
                    onValueChange = onSystemMessageChanged,
                    placeholder = { Text("Enter a system message here that will be sent with every request") },
                    modifier = Modifier.fillMaxWidth().heightIn(max = systemMessageHeight)
                        .onFocusChanged { focusState -> isSystemMessageFocused = focusState.isFocused },
                    minLines = currentMinLines,
                    maxLines = currentMaxLines
                )
            }
        }

        if (!isAccessibilityServiceEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Accessibility Service is not enabled", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("The click functionality requires the Accessibility Service. Please enable it in the settings.", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        onEnableAccessibilityService()
                        Toast.makeText(context, "Open Accessibility Settings..." as CharSequence, Toast.LENGTH_SHORT).show()
                    }) { Text("Activate Accessibility Service") }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(messages) { message ->
                    when (message.participant) {
                        PhotoParticipant.USER -> {
                            // If index == 0, it's the first message, show the undo button
                            val isFirstMessage = messages.indexOf(message) == 0
                            UserChatBubble(
                                text = message.text,
                                isPending = message.isPending,
                                imageUris = message.imageUris,
                                showUndo = isFirstMessage,
                                onUndoClicked = {
                                    // Set the text back to the input box
                                    onUserQuestionChanged(message.text)
                                    // Clear chat history
                                    onClearChatHistory()
                                }
                            )
                        }
                        PhotoParticipant.MODEL -> ModelChatBubble(message.text, message.isPending)
                        PhotoParticipant.ERROR -> ErrorChatBubble(message.text)
                    }
                }

                if (commandExecutionStatus.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp).wrapContentHeight(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Command Status:", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(commandExecutionStatus, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }

                if (detectedCommands.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp).wrapContentHeight(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Detected Commands:", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                detectedCommands.forEachIndexed { index, command ->
                                    val commandText = when (command) {
                                        is Command.ClickButton -> "Click on button: \"${command.buttonText}\""
                                        is Command.TapCoordinates -> "Tap coordinates: (${command.x}, ${command.y})"
                                        is Command.TakeScreenshot -> "Take screenshot"
                                        else -> command::class.simpleName ?: "Unknown Command"
                                    }
                                    Text("${index + 1}. $commandText", color = MaterialTheme.colorScheme.onTertiaryContainer)
                                    if (index < detectedCommands.size - 1) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                listState = listState
            )
        }

        val isGemma = com.google.ai.sample.GenerativeAiViewModelFactory.getCurrentModel().isOfflineModel
        val isLoading = uiState is PhotoReasoningUiState.Loading
        val showStopButton = isGenerationRunning || isLoading || isOfflineGpuModelLoaded || isGemma
        val stopButtonText = if (isGenerationRunning || isLoading) "Stop" else "Model Unload"
        val showTextFieldRow = (!isGenerationRunning && !isLoading) || isInitializingOfflineModel

        if (showTextFieldRow) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(top = 16.dp)) {
                        Column(modifier = Modifier.padding(all = 4.dp).align(Alignment.CenterVertically)) {
                            IconButton(onClick = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.padding(bottom = 4.dp)) {
                                Icon(Icons.Rounded.Add, stringResource(R.string.add_image))
                            }
                            IconButton(onClick = onClearChatHistory, modifier = Modifier.padding(top = 4.dp).drawBehind {
                                drawCircle(color = Color.Black, radius = size.minDimension / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
                            }) { Text("New", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                        }
                        OutlinedTextField(
                            value = userQuestion,
                            label = { Text(stringResource(R.string.reason_label)) },
                            placeholder = { Text(stringResource(R.string.reason_hint)) },
                            onValueChange = onUserQuestionChanged,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        IconButton(
                            onClick = {
                                val mainActivity = context as? MainActivity

                                // Always check accessibility service (needed for both live and regular models)
                                if (!isAccessibilityServiceEnabled) {
                                    onEnableAccessibilityService()
                                    Toast.makeText(context, "Enable the Accessibility service for Screen Operator", Toast.LENGTH_LONG).show()
                                    return@IconButton
                                }

                                // Check MediaProjection for all models except offline and human-expert
                                // Human Expert uses its own MediaProjection for WebRTC, not ScreenCaptureService
                                if (!isMediaProjectionPermissionGranted && !com.google.ai.sample.GenerativeAiViewModelFactory.getCurrentModel().isOfflineModel && modelName != "human-expert") {
                                    mainActivity?.requestMediaProjectionPermission {
                                        // This block will be executed after permission is granted
                                        if (userQuestion.isNotBlank()) {
                                            onReasonClicked(userQuestion, imageUris.toList())
                                            onUserQuestionChanged("")
                                            imageUris.clear()
                                        }
                                    }
                                    Toast.makeText(context, "Requesting screen capture permission...", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }

                                if (userQuestion.isNotBlank()) {
                                    onReasonClicked(userQuestion, imageUris.toList())
                                    onUserQuestionChanged("")
                                    imageUris.clear()
                                }
                            },
                            enabled = isInitialized && userQuestion.isNotBlank(),
                            modifier = Modifier.padding(all = 4.dp).align(Alignment.CenterVertically)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                stringResource(R.string.action_go),
                                tint = if (isInitialized && userQuestion.isNotBlank())
                                    MaterialTheme.colorScheme.primary else Color.Gray,
                            )
                        }
                    } // Closes Row
                    LazyRow(modifier = Modifier.padding(all = 8.dp)) {
                        items(imageUris) { uri -> AsyncImage(uri, null, Modifier.padding(4.dp).requiredSize(72.dp)) }
                    }
                } // Closes Column
            } // Closes Card
        }
        
        // Stop button: zeigt 'Stop' bei aktiver Generierung, 'Model Unload' bei geladenem GPU-Modell oder Offline-Selektion
        if (showStopButton) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onStopClicked,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(stopButtonText, color = Color.White)
            }
        }
        }

        // Popups remain outside the main content flow, attached to the screen Column
        if (showDatabaseListPopup) {
            DatabaseListPopup(
                onDismissRequest = { showDatabaseListPopup = false },
                entries = systemMessageEntries,
                onNewClicked = {
                    entryToEdit = null 
                    showEditEntryPopup = true
                },
                onEntryClicked = { entry ->
                    entryToEdit = entry 
                    showEditEntryPopup = true
                },
                onDeleteClicked = { entry ->
                    SystemMessageEntryPreferences.deleteEntry(context, entry)
                    systemMessageEntries = SystemMessageEntryPreferences.loadEntries(context) 
                },
                onImportCompleted = { 
                    systemMessageEntries = SystemMessageEntryPreferences.loadEntries(context)
                }
            )
        }

        if (showEditEntryPopup) {
            EditEntryPopup(
                entry = entryToEdit,
                onDismissRequest = { showEditEntryPopup = false },
                onSaveClicked = { title, guide, originalEntry ->
                    val currentEntry = SystemMessageEntry(title.trim(), guide.trim()) 
                    if (title.isBlank() || guide.isBlank()) { 
                        Toast.makeText(context, "Title and Guide cannot be empty." as CharSequence, Toast.LENGTH_SHORT).show()
                        return@EditEntryPopup
                    }
                    if (originalEntry == null) { 
                        val existingEntry = systemMessageEntries.find { it.title.equals(currentEntry.title, ignoreCase = true) }
                        if (existingEntry == null) {
                            SystemMessageEntryPreferences.addEntry(context, currentEntry)
                            showEditEntryPopup = false
                            systemMessageEntries = SystemMessageEntryPreferences.loadEntries(context) 
                        } else {
                            Toast.makeText(context, "An entry with this title already exists." as CharSequence, Toast.LENGTH_SHORT).show()
                            return@EditEntryPopup 
                        }
                    } else { 
                        val existingEntryWithNewTitle = systemMessageEntries.find { it.title.equals(currentEntry.title, ignoreCase = true) && it.guide != originalEntry.guide }
                        if (existingEntryWithNewTitle != null && originalEntry.title != currentEntry.title) {
                            Toast.makeText(context, "Another entry with this new title already exists." as CharSequence, Toast.LENGTH_SHORT).show()
                            return@EditEntryPopup 
                        }
                        SystemMessageEntryPreferences.updateEntry(context, originalEntry, currentEntry)
                        showEditEntryPopup = false
                        systemMessageEntries = SystemMessageEntryPreferences.loadEntries(context) 
                    }
                }
            )
        }
    }
