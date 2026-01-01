package com.google.ai.sample

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
    onDismiss: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf(ApiProvider.CEREBRAS) }
    val apiKeys = remember { mutableStateMapOf<ApiProvider, List<String>>() }
    var selectedKeyIndex by remember { mutableStateOf(apiKeyManager.getCurrentKeyIndex(selectedProvider)) }
    val context = LocalContext.current

    fun loadKeysForProvider(provider: ApiProvider) {
        apiKeys[provider] = apiKeyManager.getApiKeys(provider)
        selectedKeyIndex = apiKeyManager.getCurrentKeyIndex(provider)
    }

    // Load initial keys
    LaunchedEffect(Unit) {
        loadKeysForProvider(ApiProvider.GOOGLE)
        loadKeysForProvider(ApiProvider.CEREBRAS)
    }

    Dialog(onDismissRequest = {
        if (!isFirstLaunch || (apiKeys[ApiProvider.GOOGLE]?.isNotEmpty() == true || apiKeys[ApiProvider.CEREBRAS]?.isNotEmpty() == true)) {
            onDismiss()
        }
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    listOf(ApiProvider.CEREBRAS, ApiProvider.GOOGLE).forEach { provider ->
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
                        val url = if (selectedProvider == ApiProvider.GOOGLE) {
                            "https://makersuite.google.com/app/apikey"
                        } else {
                            "https://cloud.cerebras.ai/"
                        }
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text("Get API Key for ${selectedProvider.name.replaceFirstChar { it.uppercase() }}")
                }

                // Input and Add section
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            errorMessage = ""
                        },
                        label = { Text("Enter ${selectedProvider.name.replaceFirstChar { it.uppercase() }} API Key") },
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
                     if (!isFirstLaunch || (apiKeys[ApiProvider.GOOGLE]?.isNotEmpty() == true || apiKeys[ApiProvider.CEREBRAS]?.isNotEmpty() == true)) {
                        TextButton(onClick = onDismiss) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}
