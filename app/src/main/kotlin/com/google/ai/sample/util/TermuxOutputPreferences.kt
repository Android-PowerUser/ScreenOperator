package com.google.ai.sample.util

import android.content.Context

object TermuxOutputPreferences {
    private const val PREF_NAME = "termux_output_prefs"
    private const val KEY_PENDING_OUTPUT = "pending_output"

    fun appendOutput(context: Context, output: String) {
        if (output.isBlank()) return
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_PENDING_OUTPUT, "").orEmpty()
        val merged = if (existing.isBlank()) output else "$existing\n\n$output"
        prefs.edit().putString(KEY_PENDING_OUTPUT, merged).apply()
    }

    fun consumeOutput(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val value = prefs.getString(KEY_PENDING_OUTPUT, "").orEmpty().trim()
        if (value.isBlank()) return null
        prefs.edit().remove(KEY_PENDING_OUTPUT).apply()
        return value
    }
}
