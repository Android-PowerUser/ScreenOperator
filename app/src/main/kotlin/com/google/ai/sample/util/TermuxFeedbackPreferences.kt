package com.google.ai.sample.util

import android.content.Context

object TermuxFeedbackPreferences {
    private const val PREF_NAME = "termux_feedback_prefs"
    private const val KEY_TERMUX_NOT_FOUND = "termux_not_found"

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
}
