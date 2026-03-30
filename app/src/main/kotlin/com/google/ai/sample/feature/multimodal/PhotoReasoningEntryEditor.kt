package com.google.ai.sample.feature.multimodal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.ai.sample.util.SystemMessageEntry

// Reuse shared colors from PhotoReasoningScreen.kt

@Composable
fun EditEntryPopup(
    entry: SystemMessageEntry?,
    onDismissRequest: () -> Unit,
    onSaveClicked: (title: String, guide: String, originalEntry: SystemMessageEntry?) -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false) 
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.7f).padding(16.dp), 
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkYellow1) 
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                var titleInput by rememberSaveable { mutableStateOf(entry?.title ?: "") }
                var guideInput by rememberSaveable { mutableStateOf(entry?.guide ?: "") }

                Text("Title", style = MaterialTheme.typography.labelMedium, color = Color.Black.copy(alpha = 0.7f)) 
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    placeholder = { Text("App/Task", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors( 
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        disabledContainerColor = Color.White,
                        cursorColor = Color.Black, 
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Guide", style = MaterialTheme.typography.labelMedium, color = Color.Black.copy(alpha = 0.7f)) 
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = guideInput,
                    onValueChange = { guideInput = it },
                    placeholder = { Text("Write a guide for an LLM on how it should perform certain tasks to be successful", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        disabledContainerColor = Color.White,
                        cursorColor = Color.Black, 
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                    ),
                    minLines = 5
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onSaveClicked(titleInput, guideInput, entry) },
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Save") }
            }
        }
    }
}

@Preview(showBackground = true, name = "EditEntryPopup New")
@Composable
fun EditEntryPopupNewPreview() {
    MaterialTheme {
        EditEntryPopup(entry = null, onDismissRequest = {}, onSaveClicked = { _, _, _ -> })
    }
}

@Preview(showBackground = true, name = "EditEntryPopup Edit")
@Composable
fun EditEntryPopupEditPreview() {
    MaterialTheme {
        EditEntryPopup(entry = SystemMessageEntry("Existing Title", "Existing Guide"), onDismissRequest = {}, onSaveClicked = { _, _, _ -> })
    }
}

val SystemMessageEntrySaver = Saver<SystemMessageEntry?, List<String?>>(
    save = { entry -> if (entry == null) listOf(null, null) else listOf(entry.title, entry.guide) },
    restore = { list ->
        val title = list[0]
        val guide = list[1]
        if (title != null && guide != null) SystemMessageEntry(title, guide) else null
    }
)
