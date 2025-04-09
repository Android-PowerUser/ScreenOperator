/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.sample.feature.multimodal

import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.ai.sample.GenerativeViewModelFactory
import coil.size.Precision
import com.google.ai.sample.R
import com.google.ai.sample.ScreenOperatorAccessibilityService
import com.google.ai.sample.util.UriSaver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import android.content.Context
import android.util.Log
import android.widget.Toast

@Composable
internal fun PhotoReasoningRoute(
    viewModel: PhotoReasoningViewModel = viewModel(factory = GenerativeViewModelFactory)
) {
    val photoReasoningUiState by viewModel.uiState.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val imageRequestBuilder = ImageRequest.Builder(LocalContext.current)
    val imageLoader = ImageLoader.Builder(LocalContext.current).build()
    val context = LocalContext.current

    PhotoReasoningScreen(
        uiState = photoReasoningUiState,
        onReasonClicked = { inputText, selectedItems ->
            coroutineScope.launch {
                Log.d("PhotoReasoningScreen", "Go button clicked, taking screenshot")
                
                // Show a toast to indicate we're taking a screenshot
                Toast.makeText(context, "Taking screenshot...", Toast.LENGTH_SHORT).show()
                
                // Take a screenshot first
                ScreenOperatorAccessibilityService.takeScreenshot {
                    // This will be called after the screenshot is taken
                    coroutineScope.launch {
                        Log.d("PhotoReasoningScreen", "Screenshot callback triggered")
                        
                        // Give some time for the screenshot to be saved and processed
                        delay(2500)
                        
                        // Get the latest screenshot
                        val screenshotFile = ScreenOperatorAccessibilityService.getLatestScreenshot()
                        val updatedItems = selectedItems.toMutableList()
                        
                        // Add the screenshot to the list if it exists
                        if (screenshotFile != null && screenshotFile.exists()) {
                            try {
                                val screenshotUri = Uri.fromFile(screenshotFile)
                                updatedItems.add(screenshotUri)
                                Log.d("PhotoReasoningScreen", "Added screenshot: ${screenshotFile.absolutePath}")
                                
                                // Show a toast to indicate the screenshot was added
                                Toast.makeText(context, "Screenshot added", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e("PhotoReasoningScreen", "Error adding screenshot: ${e.message}")
                                Toast.makeText(context, "Error adding screenshot: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Log.e("PhotoReasoningScreen", "Screenshot file not found or doesn't exist")
                            Toast.makeText(context, "Screenshot not found", Toast.LENGTH_SHORT).show()
                            
                            // Try to get the URI directly as a fallback
                            val screenshotUri = ScreenOperatorAccessibilityService.getLatestScreenshotUri()
                            if (screenshotUri != null) {
                                updatedItems.add(screenshotUri)
                                Log.d("PhotoReasoningScreen", "Added screenshot from URI")
                                Toast.makeText(context, "Screenshot added from URI", Toast.LENGTH_SHORT).show()
                            }
                        }
                        
                        // Process all images including the screenshot
                        val bitmaps = updatedItems.mapNotNull {
                            Log.d("PhotoReasoningScreen", "Processing image: $it")
                            val imageRequest = imageRequestBuilder
                                .data(it)
                                // Scale the image down to 768px for faster uploads deaktiviert um genaue Auflösungen feedback zu bekommen
                                // .size(size = 768)
                                .precision(Precision.EXACT)
                                .build()
                            try {
                                val result = imageLoader.execute(imageRequest)
                                if (result is SuccessResult) {
                                    Log.d("PhotoReasoningScreen", "Successfully processed image")
                                    return@mapNotNull (result.drawable as BitmapDrawable).bitmap
                                } else {
                                    Log.e("PhotoReasoningScreen", "Failed to process image: result is not SuccessResult")
                                    return@mapNotNull null
                                }
                            } catch (e: Exception) {
                                Log.e("PhotoReasoningScreen", "Error processing image: ${e.message}")
                                return@mapNotNull null
                            }
                        }
                        
                        Log.d("PhotoReasoningScreen", "Processed ${bitmaps.size} images")
                        
                        // Send to AI
                        viewModel.reason(inputText, bitmaps)
                    }
                }
            }
        }
    )
}

@Composable
fun PhotoReasoningScreen(
    uiState: PhotoReasoningUiState = PhotoReasoningUiState.Loading,
    onReasonClicked: (String, List<Uri>) -> Unit = { _, _ -> }
) {
    var userQuestion by rememberSaveable { mutableStateOf("") }
    val imageUris = rememberSaveable(saver = UriSaver()) { mutableStateListOf() }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { imageUri ->
        imageUri?.let {
            imageUris.add(it)
        }
    }

    Column(
        modifier = Modifier
            .padding(all = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(top = 16.dp)
            ) {
                IconButton(
                    onClick = {
                        pickMedia.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .padding(all = 4.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.add_image),
                    )
                }
                OutlinedTextField(
                    value = userQuestion,
                    label = { Text(stringResource(R.string.reason_label)) },
                    placeholder = { Text(stringResource(R.string.reason_hint)) },
                    onValueChange = { userQuestion = it },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                )
                TextButton(
                    onClick = {
                        if (userQuestion.isNotBlank()) {
                            onReasonClicked(userQuestion, imageUris.toList())
                        }
                    },
                    modifier = Modifier
                        .padding(all = 4.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    Text(stringResource(R.string.action_go))
                }
            }
            LazyRow(
                modifier = Modifier.padding(all = 8.dp)
            ) {
                items(imageUris) { imageUri ->
                    AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(4.dp)
                            .requiredSize(72.dp)
                    )
                }
            }
        }
        when (uiState) {
            PhotoReasoningUiState.Initial -> {
                // Nothing is shown
            }

            PhotoReasoningUiState.Loading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(all = 8.dp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    CircularProgressIndicator()
                }
            }

            is PhotoReasoningUiState.Success -> {
                Card(
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(all = 16.dp)
                            .fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = "Person Icon",
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier
                                .requiredSize(36.dp)
                                .drawBehind {
                                    drawCircle(color = Color.White)
                                }
                        )
                        Text(
                            text = uiState.outputText, // TODO(thatfiredev): Figure out Markdown support
                            color = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }

            is PhotoReasoningUiState.Error -> {
                Card(
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = uiState.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(all = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
@Preview(showSystemUi = true)
fun PhotoReasoningScreenPreview() {
    PhotoReasoningScreen()
}
