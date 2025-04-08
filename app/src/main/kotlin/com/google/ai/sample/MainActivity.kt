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

package com.google.ai.sample

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.ai.sample.feature.chat.ChatRoute
import com.google.ai.sample.feature.multimodal.PhotoReasoningRoute
import com.google.ai.sample.feature.text.SummarizeRoute
import com.google.ai.sample.ui.theme.GenerativeAISample

class MainActivity : ComponentActivity() {
    // Add screenshot manager
    private lateinit var screenshotManager: ScreenshotManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize screenshot manager
        screenshotManager = ScreenshotManager.getInstance(this)

        setContent {
            GenerativeAISample {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "menu") {
                        composable("menu") {
                            MenuScreen(onItemClicked = { routeId ->
                                navController.navigate(routeId)
                            })
                        }
                        composable("summarize") {
                            SummarizeRoute()
                        }
                        composable("photo_reasoning") {
                            PhotoReasoningRoute()
                        }
                        composable("chat") {
                            ChatRoute()
                        }
                    }
                }
            }
        }
        
        // Request screenshot permission when the app starts
        screenshotManager.requestScreenshotPermission(this)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == ScreenshotManager.REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                val success = screenshotManager.handlePermissionResult(resultCode, data)
                if (success) {
                    Toast.makeText(this, "Screenshot permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to get screenshot permission", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Screenshot permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroy() {
        // Release screenshot manager resources
        screenshotManager.release()
        super.onDestroy()
    }
}
