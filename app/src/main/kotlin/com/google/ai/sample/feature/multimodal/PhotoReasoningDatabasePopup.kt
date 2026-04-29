package com.google.ai.sample.feature.multimodal

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.ai.sample.util.SystemMessageEntry
import com.google.ai.sample.util.SystemMessageEntryPreferences
import com.google.ai.sample.util.shareTextFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// Reuse shared colors from PhotoReasoningScreen.kt

@Composable
fun DatabaseListPopup(
    onDismissRequest: () -> Unit,
    entries: List<SystemMessageEntry>,
    onNewClicked: () -> Unit,
    onEntryClicked: (SystemMessageEntry) -> Unit,
    onDeleteClicked: (SystemMessageEntry) -> Unit,
    onImportCompleted: () -> Unit
) {
    val TAG_IMPORT_PROCESS = "ImportProcess"
    val scope = rememberCoroutineScope()
    var entryMenuToShow: SystemMessageEntry? by remember { mutableStateOf(null) }
    var selectionModeActive by rememberSaveable { mutableStateOf(false) }
    var selectedEntryTitles by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var selectAllChecked by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    var entryToConfirmOverwrite by remember { mutableStateOf<Pair<SystemMessageEntry, SystemMessageEntry>?>(null) }
    var remainingEntriesToImport by remember { mutableStateOf<List<SystemMessageEntry>>(emptyList()) }
    var skipAllDuplicates by remember { mutableStateOf(false) }

    // processImportedEntries is defined within DatabaseListPopup, so it has access to context, onImportCompleted, etc.
    fun processImportedEntries( 
        imported: List<SystemMessageEntry>,
        currentSystemEntries: List<SystemMessageEntry>
    ) {
        val TAG_IMPORT_PROCESS_FUNCTION = "ImportProcessFunction" 
        Log.d(TAG_IMPORT_PROCESS_FUNCTION, "Starting processImportedEntries. Imported: ${imported.size}, Current: ${currentSystemEntries.size}, SkipAll: $skipAllDuplicates")

        var newCount = 0
        var updatedCount = 0 
        var skippedCount = 0
        val entriesToProcess = imported.toMutableList()

        while (entriesToProcess.isNotEmpty()) {
            val newEntry = entriesToProcess.removeAt(0)
            Log.d(TAG_IMPORT_PROCESS_FUNCTION, "Processing entry: Title='${newEntry.title}'. Remaining in batch: ${entriesToProcess.size}")
            val existingEntry = currentSystemEntries.find { it.title.equals(newEntry.title, ignoreCase = true) }

            if (existingEntry != null) {
                Log.d(TAG_IMPORT_PROCESS_FUNCTION, "Duplicate found for title: '${newEntry.title}'. Existing guide: '${existingEntry.guide.take(50)}', New guide: '${newEntry.guide.take(50)}'")
                if (skipAllDuplicates) {
                    Log.d(TAG_IMPORT_PROCESS_FUNCTION, "Skipping duplicate '${newEntry.title}' due to skipAllDuplicates flag.")
                    skippedCount++
                    continue
                }
                Log.d(TAG_IMPORT_PROCESS_FUNCTION, "Calling askForOverwrite for '${newEntry.title}'.")
                entryToConfirmOverwrite = Pair(existingEntry, newEntry) 
                remainingEntriesToImport = entriesToProcess.toList() 
                return 
            } else {
                Log.i(TAG_IMPORT_PROCESS_FUNCTION, "Adding new entry: Title='${newEntry.title}'")
                SystemMessageEntryPreferences.addEntry(context, newEntry)
                newCount++
            }
        }
        Log.i(TAG_IMPORT_PROCESS_FUNCTION, "Finished processing batch. newCount=$newCount, updatedCount=$updatedCount, skippedCount=$skippedCount")
        val summary = "Import finished: $newCount added, $updatedCount updated, $skippedCount skipped."
        Toast.makeText(context, summary as CharSequence, Toast.LENGTH_LONG).show()
        onImportCompleted() 
        skipAllDuplicates = false 
    }


    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            Log.d(TAG_IMPORT_PROCESS, "FilePickerLauncher onResult triggered.")
            if (uri == null) {
                Log.w(TAG_IMPORT_PROCESS, "URI is null, no file selected or operation cancelled.")
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "No file selected." as CharSequence, Toast.LENGTH_SHORT).show()
                }
                return@rememberLauncherForActivityResult
            }

            Log.i(TAG_IMPORT_PROCESS, "Selected file URI: $uri")
            scope.launch(Dispatchers.Main) {
                Toast.makeText(context, "File selected: $uri. Starting import..." as CharSequence, Toast.LENGTH_SHORT).show()
            }

            scope.launch(Dispatchers.IO) { 
                try {
                    Log.d(TAG_IMPORT_PROCESS, "Attempting to open InputStream for URI: $uri on thread: ${Thread.currentThread().name}")
                    
                    var fileSize = -1L
                    try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            fileSize = pfd.statSize
                        }
                        Log.i(TAG_IMPORT_PROCESS, "Estimated file size: $fileSize bytes.")
                    } catch (e: Exception) {
                        Log.w(TAG_IMPORT_PROCESS, "Could not determine file size for URI: $uri. Will proceed without size check.", e)
                    }

                    val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 
                    if (fileSize != -1L && fileSize > MAX_FILE_SIZE_BYTES) {
                        Log.e(TAG_IMPORT_PROCESS, "File size ($fileSize bytes) exceeds limit of $MAX_FILE_SIZE_BYTES bytes.")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "File is too large (max 10MB)." as CharSequence, Toast.LENGTH_LONG).show()
                        }
                        return@launch 
                    }
                     if (fileSize == 0L) { 
                         Log.w(TAG_IMPORT_PROCESS, "Imported file is empty (0 bytes).")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Imported file is empty." as CharSequence, Toast.LENGTH_LONG).show()
                        }
                        return@launch 
                    }

                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        Log.i(TAG_IMPORT_PROCESS, "InputStream opened. Reading text on thread: ${Thread.currentThread().name}")
                        val jsonString = inputStream.bufferedReader().readText() 
                        Log.i(TAG_IMPORT_PROCESS, "File content read. Size: ${jsonString.length} chars.")
                        Log.v(TAG_IMPORT_PROCESS, "File content snippet: ${jsonString.take(500)}")

                        if (jsonString.isBlank()) {
                            Log.w(TAG_IMPORT_PROCESS, "Imported file content is blank.")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Imported file content is blank." as CharSequence, Toast.LENGTH_LONG).show()
                            }
                            return@use 
                        }

                        Log.d(TAG_IMPORT_PROCESS, "Attempting to parse JSON string on thread: ${Thread.currentThread().name}")
                        val parsedEntries = Json.decodeFromString(ListSerializer(SystemMessageEntry.serializer()), jsonString)
                        Log.i(TAG_IMPORT_PROCESS, "JSON parsed. Found ${parsedEntries.size} entries.")

                        val currentSystemEntries = SystemMessageEntryPreferences.loadEntries(context) 
                        Log.d(TAG_IMPORT_PROCESS, "Current system entries loaded: ${currentSystemEntries.size} entries.")

                        withContext(Dispatchers.Main) { 
                            Log.d(TAG_IMPORT_PROCESS, "Switching to Main thread for processImportedEntries: ${Thread.currentThread().name}")
                            skipAllDuplicates = false 
                            processImportedEntries(
                                imported = parsedEntries,
                                currentSystemEntries = currentSystemEntries
                            )
                        }
                    } ?: Log.w(TAG_IMPORT_PROCESS, "ContentResolver.openInputStream returned null for URI: $uri (second check).")
                } catch (oom: OutOfMemoryError) {
                    Log.e(TAG_IMPORT_PROCESS, "Out of memory during file import for URI: $uri on thread: ${Thread.currentThread().name}", oom)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Error importing file: Out of memory. File may be too large or contain too many entries." as CharSequence,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG_IMPORT_PROCESS, "Error during file import for URI: $uri on thread: ${Thread.currentThread().name}", e)
                    withContext(Dispatchers.Main) {
                        val errorMessage = e.message ?: "Unknown error during import."
                        Toast.makeText(context, "Error importing file: $errorMessage" as CharSequence, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    if (entryToConfirmOverwrite != null) {
        val (existingEntry, newEntry) = checkNotNull(entryToConfirmOverwrite)
        OverwriteConfirmationDialog(
            entryTitle = newEntry.title,
            onConfirm = {
                Log.d(TAG_IMPORT_PROCESS, "Overwrite confirmed for title: '${newEntry.title}'")
                SystemMessageEntryPreferences.updateEntry(context, existingEntry, newEntry)
                Toast.makeText(context, "Entry '${newEntry.title}' overwritten." as CharSequence, Toast.LENGTH_SHORT).show()
                entryToConfirmOverwrite = null
                val currentSystemEntriesAfterUpdate = SystemMessageEntryPreferences.loadEntries(context) 
                Log.d(TAG_IMPORT_PROCESS, "Continuing with remaining ${remainingEntriesToImport.size} entries after dialog (Confirm).")
                processImportedEntries( 
                    imported = remainingEntriesToImport,
                    currentSystemEntries = currentSystemEntriesAfterUpdate
                )
            },
            onDeny = { 
                Log.d(TAG_IMPORT_PROCESS, "Overwrite denied for title: '${newEntry.title}'")
                entryToConfirmOverwrite = null
                val currentSystemEntriesAfterDeny = SystemMessageEntryPreferences.loadEntries(context) 
                Log.d(TAG_IMPORT_PROCESS, "Continuing with remaining ${remainingEntriesToImport.size} entries after dialog (Deny).")
                processImportedEntries( 
                    imported = remainingEntriesToImport,
                    currentSystemEntries = currentSystemEntriesAfterDeny
                )
            },
            onSkipAll = { 
                Log.d(TAG_IMPORT_PROCESS, "Skip All selected for title: '${newEntry.title}'")
                skipAllDuplicates = true
                entryToConfirmOverwrite = null
                val currentSystemEntriesAfterSkipAll = SystemMessageEntryPreferences.loadEntries(context) 
                Log.d(TAG_IMPORT_PROCESS, "Continuing with remaining ${remainingEntriesToImport.size} entries after dialog (SkipAll).")
                processImportedEntries( 
                    imported = remainingEntriesToImport,
                    currentSystemEntries = currentSystemEntriesAfterSkipAll
                )
            },
            onDismiss = { 
                Log.d(TAG_IMPORT_PROCESS, "Overwrite dialog dismissed for title: '${entryToConfirmOverwrite?.second?.title}'. Import process for this batch might halt.")
                entryToConfirmOverwrite = null 
                remainingEntriesToImport = emptyList() 
                skipAllDuplicates = false
                Toast.makeText(context, "Import cancelled for remaining items." as CharSequence, Toast.LENGTH_SHORT).show()
                onImportCompleted() 
            }
        )
    }


    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f) 
                .fillMaxHeight(0.85f), 
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkYellow1)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp) 
                    .fillMaxSize()
            ) {
                val displayRowCount = 15
                val newButtonRowIndex = entries.size 

                LazyColumn(modifier = Modifier.weight(1f)) { 
                    items(displayRowCount) { rowIndex ->
                        val currentAlternatingColor = if (rowIndex % 2 == 0) DarkYellow1 else DarkYellow2

                        when {
                            rowIndex < entries.size -> {
                                val entry = entries[rowIndex]
                                val isSelected = selectedEntryTitles.contains(entry.title)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(currentAlternatingColor)
                                        .clickable {
                                            if (selectionModeActive) {
                                                val entryTitle = entry.title
                                                val isCurrentlySelected = selectedEntryTitles.contains(entryTitle)
                                                selectedEntryTitles = if (isCurrentlySelected) {
                                                    selectedEntryTitles - entryTitle
                                                } else {
                                                    selectedEntryTitles + entryTitle
                                                }
                                                selectAllChecked = if (selectedEntryTitles.size == entries.size && entries.isNotEmpty()) true else if (selectedEntryTitles.isEmpty()) false else false
                                            } else {
                                                onEntryClicked(entry) 
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selectionModeActive) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { isChecked ->
                                                val entryTitle = entry.title
                                                selectedEntryTitles = if (isChecked) {
                                                    selectedEntryTitles + entryTitle
                                                } else {
                                                    selectedEntryTitles - entryTitle
                                                }
                                                selectAllChecked = if (selectedEntryTitles.size == entries.size && entries.isNotEmpty()) true else if (selectedEntryTitles.isEmpty()) false else false
                                            },
                                            modifier = Modifier.padding(end = 8.dp),
                                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(BorderStroke(1.dp, Color.Black), shape = RoundedCornerShape(12.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(entry.title, color = Color.Black, style = MaterialTheme.typography.bodyLarge)
                                    }
                                    if (!selectionModeActive) {
                                        Box {
                                            IconButton(onClick = { entryMenuToShow = entry }) {
                                                Icon(Icons.Filled.MoreVert, "More options", tint = Color.Black)
                                            }
                                            DropdownMenu(
                                                expanded = entryMenuToShow == entry,
                                                onDismissRequest = { entryMenuToShow = null }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Delete") },
                                                    onClick = {
                                                        onDeleteClicked(entry)
                                                        entryMenuToShow = null
                                                    }
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.width(48.dp)) 
                                    }
                                }
                            }
                            rowIndex == newButtonRowIndex -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(currentAlternatingColor)
                                        .padding(8.dp).clickable(onClick = onNewClicked),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End 
                                ) {
                                    Text("The headings are sent to the AI and the content is included on request", color = Color.Black.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Button(onClick = onNewClicked, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), modifier = Modifier.padding(start = 8.dp)) {
                                        Text("New")
                                    }
                                }
                            }
                            else -> {
                                Box(modifier = Modifier.fillMaxWidth().height(56.dp).background(currentAlternatingColor).padding(16.dp)) {}
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    if (selectionModeActive) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectAllChecked,
                                onCheckedChange = { isChecked ->
                                    selectAllChecked = isChecked
                                    selectedEntryTitles = if (isChecked) entries.map { it.title }.toSet() else emptySet()
                                },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text("All", color = Color.Black, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(80.dp)) // Placeholder for alignment
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { filePickerLauncher.launch("*/*") }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), modifier = Modifier.padding(end = 8.dp)) { Text("Import") }
                        Button(
                            onClick = {
                                if (selectionModeActive) { 
                                    if (selectedEntryTitles.isEmpty()) {
                                        Toast.makeText(context, "No entries selected for export." as CharSequence, Toast.LENGTH_SHORT).show()
                                    } else {
                                        val entriesToExport = entries.filter { selectedEntryTitles.contains(it.title) }
                                        val jsonString = Json.encodeToString(ListSerializer(SystemMessageEntry.serializer()), entriesToExport)
                                        shareTextFile(context, "Database.txt", jsonString)
                                    }
                                    selectionModeActive = false
                                    selectedEntryTitles = emptySet()
                                    selectAllChecked = false
                                } else { 
                                    selectionModeActive = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text("Export") } // Text is now always "Export"
                    }
                }
            }
        }
    }
}


@Composable
fun OverwriteConfirmationDialog(
    entryTitle: String,
    onConfirm: () -> Unit,
    onDeny: () -> Unit,
    onSkipAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Overwrite") },
        text = { Text("An entry with the title \"$entryTitle\" already exists. Do you want to overwrite its guide?") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Yes") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSkipAll) { Text("Skip All") }
                TextButton(onClick = onDeny) { Text("No") }
            }
        }
    )
}
