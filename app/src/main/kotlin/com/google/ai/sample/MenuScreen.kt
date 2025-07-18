package com.google.ai.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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

data class MenuItem(
    val routeId: String,
    val titleResId: Int,
    val descriptionResId: Int
)

@Composable
fun MenuScreen(
    onItemClicked: (String) -> Unit = { },
    onApiKeyButtonClicked: () -> Unit = { },
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

    LazyColumn(
        Modifier
            .padding(top = 16.dp, bottom = 16.dp)
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
                        onClick = { onApiKeyButtonClicked() },
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
                            val orderedModels = listOf(
                                ModelOption.GEMINI_FLASH_LITE,
                                ModelOption.GEMINI_FLASH,
                                ModelOption.GEMINI_FLASH_LITE_PREVIEW,
                                ModelOption.GEMINI_FLASH_PREVIEW,
                                ModelOption.GEMINI_PRO,
                                ModelOption.GEMMA_3N_E4B_IT,
                                ModelOption.GEMMA_3_27B_IT
                            )

                            orderedModels.forEach { modelOption ->
                                DropdownMenuItem(
                                    text = { Text(modelOption.displayName) },
                                    onClick = {
                                        selectedModel = modelOption
                                        GenerativeAiViewModelFactory.setModel(modelOption)
                                        expanded = false
                                    },
                                    enabled = true // Always enabled
                                )
                            }
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
                        text = stringResource(menuItem.titleResId),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(menuItem.descriptionResId),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    TextButton(
                        onClick = {
                            if (isTrialExpired) {
                                Toast.makeText(context, "Please subscribe to the app to continue.", Toast.LENGTH_LONG).show()
                            } else {
                                if (menuItem.routeId == "photo_reasoning") {
                                    val mainActivity = context as? MainActivity
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
                            text = "Support more Features",
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
                val annotatedText = buildAnnotatedString {
                    append("Preview models could be deactivated by Google without being handed over to the final release. Gemma 3n E4B it cannot handle screenshots in the API. There are rate limits for free use of Gemini models. The less powerful the models are, the more you can use them. The limits range from a maximum of 10 to 30 calls per minute. After each screenshot (every 2-3 seconds) the LLM must respond again. More information is available at ")

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
}

@Preview(showSystemUi = true)
@Composable
fun MenuScreenPreview() {
    // Preview with trial not expired
    MenuScreen(isTrialExpired = false, isPurchased = false)
}

@Preview(showSystemUi = true)
@Composable
fun MenuScreenPurchasedPreview() {
    MenuScreen(isTrialExpired = false, isPurchased = true)
}

@Preview(showSystemUi = true)
@Composable
fun MenuScreenTrialExpiredPreview() {
    // Preview with trial expired
    MenuScreen(isTrialExpired = true, isPurchased = false)
}

