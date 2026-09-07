package com.google.ai.sample

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import android.app.Application
import android.util.Log

/**
 * Application class for maintaining application-wide state and resources
 */
class PhotoReasoningApplication : Application() {
    
    companion object {
        private const val TAG = "PhotoReasoningApp"
        
        // Application-wide CoroutineScope that is not tied to any lifecycle
        // This scope will continue to run even when the app is in the background
        val applicationScope = CoroutineScope(
            SupervisorJob() + 
            Dispatchers.Default + 
            CoroutineExceptionHandler { _, throwable ->
                Log.e(TAG, "Uncaught exception in application scope: ${throwable.message}", throwable)
            }
        )
        
        // Instance of the application for global access
        private lateinit var instance: PhotoReasoningApplication
        
        fun getInstance(): PhotoReasoningApplication {
            return instance
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "Application created")

        // Re-apply any custom (fully JSON-defined, JS-driven) model definitions and the
        // previously active selection, so a custom model keeps working across app restarts.
        com.google.ai.sample.util.CustomModelPreferences.loadModelsJson(this)?.let { savedJson ->
            val installed = com.google.ai.sample.util.CustomModelRegistry.setModels(savedJson)
            Log.d(TAG, "Restored $installed custom model definition(s) from preferences")
        }
        com.google.ai.sample.util.CustomModelPreferences.loadActiveModelId(this)?.let { savedId ->
            com.google.ai.sample.util.CustomModelRegistry.setActiveModelId(savedId)
        }

        // Re-apply any custom action type definitions (new action kinds with regex + JS handler)
        // previously received from the WebView bundle, so they keep working across app restarts
        // before the WebView has re-fetched and re-applied its config for the current session.
        com.google.ai.sample.util.CustomActionTypePreferences.load(this)?.let { savedJson ->
            val installed = com.google.ai.sample.util.CommandParser.setCustomActionTypes(savedJson)
            Log.d(TAG, "Restored $installed custom action type(s) from preferences")
        }

    }
}
