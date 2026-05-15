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
        val committed = prefs.edit().putString(KEY_PENDING_OUTPUT, merged).commit()
        if (!committed) {
            throw IllegalStateException("Failed to persist pending Termux output")
        }
    }

    fun consumeOutput(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val value = prefs.getString(KEY_PENDING_OUTPUT, "").orEmpty().trim()
        if (value.isBlank()) return null
        val committed = prefs.edit().remove(KEY_PENDING_OUTPUT).commit()
        if (!committed) {
            throw IllegalStateException("Failed to clear consumed Termux output")
        }
        return value
    }

    fun peekOutput(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val value = prefs.getString(KEY_PENDING_OUTPUT, "").orEmpty().trim()
        return value.ifBlank { null }
    }
}
