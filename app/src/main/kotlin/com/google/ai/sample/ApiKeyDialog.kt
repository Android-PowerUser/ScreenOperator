package com.google.ai.sample

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Dialog for API key input and management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyDialog(
    apiKeyManager: ApiKeyManager,
    isFirstLaunch: Boolean = false,
    initialProvider: ApiProvider? = null,
    onDismiss: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf(initialProvider ?: ApiProvider.VERCEL) }
    val apiKeys = remember { mutableStateMapOf<ApiProvider, List<String>>() }
    var selectedKeyIndex by remember { mutableStateOf(apiKeyManager.getCurrentKeyIndex(selectedProvider)) }
    val context = LocalContext.current

    fun loadKeysForProvider(provider: ApiProvider) {
        apiKeys[provider] = apiKeyManager.getApiKeys(provider)
        selectedKeyIndex = apiKeyManager.getCurrentKeyIndex(provider)
    }

    // Load initial keys
    LaunchedEffect(Unit) {
        loadKeysForProvider(ApiProvider.VERCEL)
        loadKeysForProvider(ApiProvider.GOOGLE)
        loadKeysForProvider(ApiProvider.CEREBRAS)
        loadKeysForProvider(ApiProvider.MISTRAL)
    }

    Dialog(onDismissRequest = {
        onDismiss()
    }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = if (isFirstLaunch) "API Key Required" else "Manage API Keys",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Provider selection
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(ApiProvider.VERCEL, ApiProvider.CEREBRAS, ApiProvider.GOOGLE, ApiProvider.MISTRAL, ApiProvider.PUTER).forEach { provider ->
                        FilterChip(
                            selected = selectedProvider == provider,
                            onClick = {
                                selectedProvider = provider
                                loadKeysForProvider(provider)
                            },
                            label = { Text(provider.name.replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Get API Key button
                Button(
                    onClick = {
                        val url = when (selectedProvider) {
                            ApiProvider.GOOGLE -> "https://makersuite.google.com/app/apikey"
                            ApiProvider.CEREBRAS -> "https://cloud.cerebras.ai/"
                            ApiProvider.VERCEL -> "https://vercel.com/ai-gateway"
                            ApiProvider.MISTRAL -> "https://console.mistral.ai/home?profile_dialog=api-keys"
                            ApiProvider.PUTER -> "https://puter.com/dashboard#account"
                            ApiProvider.HUMAN_EXPERT -> return@Button
                        }

                        if (selectedProvider == ApiProvider.PUTER) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Puter Link", url)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Link is in the clipboard.", Toast.LENGTH_SHORT).show()
                            Toast.makeText(context, "After the sign up paste the link in the Browser", Toast.LENGTH_LONG).show()
                        }

                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    val buttonText = if (selectedProvider == ApiProvider.PUTER) {
                        "Get Auth Token for Puter"
                    } else {
                        "Get API Key for ${selectedProvider.name.replaceFirstChar { it.uppercase() }}"
                    }
                    Text(buttonText)
                }

                // Input and Add section
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            errorMessage = ""
                        },
                        label = { 
                            val labelText = if (selectedProvider == ApiProvider.PUTER) {
                                "Enter PUTER Auth Token"
                            } else {
                                "Enter ${selectedProvider.name.replaceFirstChar { it.uppercase() }} API Key"
                            }
                            Text(labelText)
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (apiKeyInput.isBlank()) {
                                errorMessage = "API key cannot be empty"
                                return@Button
                            }
                            val added = apiKeyManager.addApiKey(apiKeyInput, selectedProvider)
                            if (added) {
                                loadKeysForProvider(selectedProvider)
                                apiKeyInput = ""
                            } else {
                                errorMessage = "API key already exists for ${selectedProvider.name}"
                            }
                        }
                    ) {
                        Text("Add")
                    }
                }

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )
                }

                // List of keys for the selected provider
                val currentKeys = apiKeys[selectedProvider] ?: emptyList()
                if (currentKeys.isNotEmpty()) {
                    Text(
                        text = "Saved ${selectedProvider.name.replaceFirstChar { it.uppercase() }} Keys",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                    ) {
                        itemsIndexed(currentKeys) { index, key ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = index == selectedKeyIndex,
                                    onClick = {
                                        apiKeyManager.setCurrentKeyIndex(index, selectedProvider)
                                        selectedKeyIndex = index
                                    }
                                )
                                Text(
                                    text = key.take(10) + "..." + key.takeLast(5),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp)
                                )
                                IconButton(
                                    onClick = {
                                        apiKeyManager.removeApiKey(key, selectedProvider)
                                        loadKeysForProvider(selectedProvider)
                                    }
                                ) {
                                    Text("✕", textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }

                // Close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                        TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
