package com.google.ai.sample.util

import android.content.Context

object AppOpenFeedbackPreferences {
    private const val PREFS_NAME = "app_open_feedback_prefs"
    private const val KEY_APP_NOT_FOUND_PENDING = "app_not_found_pending"

    fun markAppNotFound(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_APP_NOT_FOUND_PENDING, true)
            .apply()
    }

    fun consumeAppNotFound(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = prefs.getBoolean(KEY_APP_NOT_FOUND_PENDING, false)
        if (pending) {
            prefs.edit().putBoolean(KEY_APP_NOT_FOUND_PENDING, false).apply()
        }
        return pending
    }
}
