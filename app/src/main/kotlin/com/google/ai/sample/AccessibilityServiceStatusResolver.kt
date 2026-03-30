package com.google.ai.sample

import android.content.ContentResolver
import android.provider.Settings

internal object AccessibilityServiceStatusResolver {
    fun isServiceEnabled(contentResolver: ContentResolver, packageName: String): Boolean {
        val service = "$packageName/${ScreenOperatorAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(service, ignoreCase = true) == true
    }
}
