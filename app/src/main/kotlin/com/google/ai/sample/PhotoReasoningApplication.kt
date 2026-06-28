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

        // Re-apply any command pattern overrides that were previously received from the
        // WebView bundle, so alternate command syntax for new models keeps working even
        // before the WebView has re-fetched/re-applied its config in this session.
        com.google.ai.sample.util.CommandPatternOverridesPreferences.load(this)?.let { savedJson ->
            val applied = com.google.ai.sample.util.CommandParser.setRemotePatternOverrides(savedJson)
            Log.d(TAG, "Restored $applied command pattern override(s) from preferences")
        }

        // Re-apply any custom (fully JSON-defined, JS-driven) model definitions and the
        // previously active selection, so a custom model keeps working across app restarts.
        com.google.ai.sample.util.CustomModelPreferences.loadModelsJson(this)?.let { savedJson ->
            val installed = com.google.ai.sample.util.CustomModelRegistry.setModels(savedJson)
            Log.d(TAG, "Restored $installed custom model definition(s) from preferences")
        }
        com.google.ai.sample.util.CustomModelPreferences.loadActiveModelId(this)?.let { savedId ->
            com.google.ai.sample.util.CustomModelRegistry.setActiveModelId(savedId)
        }

        // Re-apply any model identifier overrides (corrected/replacement wire-level model
        // names for existing built-in models) previously received from the WebView bundle.
        com.google.ai.sample.util.ModelIdentifierOverridePreferences.load(this)?.let { savedJson ->
            val applied = com.google.ai.sample.util.ModelIdentifierOverrides.setRemoteOverrides(savedJson)
            Log.d(TAG, "Restored $applied model identifier override(s) from preferences")
        }

        // Re-apply any offline model download overrides (corrected URL/size/extra files for
        // existing built-in offline models) previously received from the WebView bundle.
        com.google.ai.sample.util.OfflineModelOverridePreferences.load(this)?.let { savedJson ->
            val applied = com.google.ai.sample.util.OfflineModelOverrides.setRemoteOverrides(savedJson)
            Log.d(TAG, "Restored $applied offline model override(s) from preferences")
        }

        // Re-apply any custom action type definitions (new action kinds with regex + JS handler)
        // previously received from the WebView bundle, so they keep working across app restarts
        // before the WebView has re-fetched and re-applied its config for the current session.
        com.google.ai.sample.util.CustomActionTypePreferences.load(this)?.let { savedJson ->
            val installed = com.google.ai.sample.util.CommandParser.setCustomActionTypes(savedJson)
            Log.d(TAG, "Restored $installed custom action type(s) from preferences")
        }

        // Re-apply any execution policy override (max commands executed per AI response, plus
        // the feedback wording sent back when commands get dropped for exceeding it) previously
        // received from the WebView bundle.
        com.google.ai.sample.util.ExecutionPolicyOverridesPreferences.load(this)?.let { savedJson ->
            val applied = com.google.ai.sample.util.ExecutionPolicyConfig.setRemoteOverride(savedJson)
            Log.d(TAG, "Restored execution policy override from preferences (applied=$applied)")
        }
    }
}
