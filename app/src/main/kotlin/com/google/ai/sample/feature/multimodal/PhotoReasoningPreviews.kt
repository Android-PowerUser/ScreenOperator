package com.google.ai.sample.feature.multimodal

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.flow.MutableStateFlow
import com.google.ai.sample.util.Command
import com.google.ai.sample.util.SystemMessageEntry

@Composable
fun PhotoReasoningScreenPreviewWithContent() {
    MaterialTheme {
        PhotoReasoningScreen(
            innerPadding = PaddingValues(),
            uiState = PhotoReasoningUiState.Success("This is a preview of the photo reasoning screen."),
            commandExecutionStatus = "Command executed: Take screenshot",
            detectedCommands = listOf(
                Command.TakeScreenshot,
                Command.ClickButton("OK")
            ),
            systemMessage = "This is a system message for the AI",
            chatMessages = MutableStateFlow(listOf(
                PhotoReasoningMessage(text = "Hello, how can I help you?", participant = PhotoParticipant.USER),
                PhotoReasoningMessage(text = "I am here to help you. What do you want to know?", participant = PhotoParticipant.MODEL)
            )),
            isKeyboardOpen = false,
            onStopClicked = {},
            isInitialized = true
        )
    }
}

@Composable
@Preview(showSystemUi = true)
fun PhotoReasoningScreenPreviewEmpty() {
    MaterialTheme {
        PhotoReasoningScreen(
            innerPadding = PaddingValues(),
            chatMessages = MutableStateFlow<List<PhotoReasoningMessage>>(emptyList()),
            isKeyboardOpen = false,
            onStopClicked = {},
            isInitialized = true
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DatabaseListPopupPreview() {
    MaterialTheme {
        DatabaseListPopup(
            onDismissRequest = {},
            entries = listOf(
                SystemMessageEntry("Title 1", "Guide for prompt 1"),
                SystemMessageEntry("Title 2", "Another guide for prompt 2"),
                SystemMessageEntry("Title 3", "Yet another guide for prompt 3.")
            ),
            onNewClicked = {},
            onEntryClicked = {},
            onDeleteClicked = {},
            onImportCompleted = {}
        )
    }
}

@Preview(showBackground = true, name = "DatabaseListPopup Empty")
@Composable
fun DatabaseListPopupEmptyPreview() {
    MaterialTheme {
        DatabaseListPopup(onDismissRequest = {}, entries = emptyList(), onNewClicked = {}, onEntryClicked = {}, onDeleteClicked = {}, onImportCompleted = {})
    }
}

@Preview(showBackground = true, name = "Stop Button Preview")
@Composable
fun StopButtonPreview() {
    MaterialTheme {
        StopButton {}
    }
}

