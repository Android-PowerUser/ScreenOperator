package com.google.ai.sample.util

import android.content.Context

object TermuxFeedbackPreferences {
    private const val PREF_NAME = "termux_feedback_prefs"
    private const val KEY_TERMUX_NOT_FOUND = "termux_not_found"
    private const val KEY_TERMUX_PERMISSION_DENIAL_COUNT = "termux_permission_denial_count"

    fun markTermuxNotFound(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TERMUX_NOT_FOUND, true)
            .apply()
    }

    fun consumeTermuxNotFound(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val value = prefs.getBoolean(KEY_TERMUX_NOT_FOUND, false)
        if (value) {
            prefs.edit().putBoolean(KEY_TERMUX_NOT_FOUND, false).apply()
        }
        return value
    }

    fun incrementPermissionDenialCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val updated = (prefs.getInt(KEY_TERMUX_PERMISSION_DENIAL_COUNT, 0) + 1).coerceAtMost(3)
        prefs.edit().putInt(KEY_TERMUX_PERMISSION_DENIAL_COUNT, updated).apply()
        return updated
    }

    fun resetPermissionDenialCount(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_TERMUX_PERMISSION_DENIAL_COUNT, 0)
            .apply()
    }
}
