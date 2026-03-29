package com.google.ai.sample

import android.content.Context
import android.provider.Settings
import android.util.Log

internal object AccessibilityServiceStateChecker {
    fun isEnabled(context: Context, tag: String): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            Log.e(tag, "Error finding accessibility setting: ${e.message}")
            return false
        }

        if (accessibilityEnabled != 1) {
            Log.d(tag, "Accessibility is not enabled")
            return false
        }

        val serviceString =
            "${context.packageName}/${ScreenOperatorAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

        val isEnabled = enabledServices.contains(serviceString)
        Log.d(tag, "Service $serviceString is ${if (isEnabled) "enabled" else "not enabled"}")
        return isEnabled
    }
}
