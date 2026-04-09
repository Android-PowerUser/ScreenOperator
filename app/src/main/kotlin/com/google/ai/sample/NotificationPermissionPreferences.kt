package com.google.ai.sample

import android.content.Context

internal object NotificationPermissionPreferences {
    private const val PREFS_NAME = "AppPrefs"
    private const val KEY_NOTIFICATION_RATIONALE_SHOWN = "notification_rationale_shown"

    fun hasShownNotificationRationale(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NOTIFICATION_RATIONALE_SHOWN, false)
    }

    fun setNotificationRationaleShown(context: Context, shown: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putBoolean(KEY_NOTIFICATION_RATIONALE_SHOWN, shown)
            apply()
        }
    }
}
