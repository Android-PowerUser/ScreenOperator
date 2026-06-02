package com.google.ai.sample.util

import android.content.Context

object TermuxOutputPreferences {
    private const val PREF_NAME = "termux_output_prefs"
    private const val KEY_PENDING_OUTPUT = "pending_output"
    private const val PROCESS_COMPLETED_PROMPT = "[Process completed - press Enter]"

    fun appendOutput(context: Context, output: String) {
        val sanitizedOutput = removeProcessCompletedPrompt(output).trim()
        if (sanitizedOutput.isBlank()) return
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_PENDING_OUTPUT, "").orEmpty()
        val merged = if (existing.isBlank()) sanitizedOutput else "$existing\n\n$sanitizedOutput"
        val committed = prefs.edit().putString(KEY_PENDING_OUTPUT, merged).commit()
        if (!committed) {
            throw IllegalStateException("Failed to persist pending Termux output")
        }
    }

    fun consumeOutput(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val value = removeProcessCompletedPrompt(prefs.getString(KEY_PENDING_OUTPUT, "").orEmpty()).trim()
        if (value.isBlank()) return null
        val committed = prefs.edit().remove(KEY_PENDING_OUTPUT).commit()
        if (!committed) {
            throw IllegalStateException("Failed to clear consumed Termux output")
        }
        return value
    }

    fun removeProcessCompletedPrompt(output: String): String {
        val lines = output.lineSequence().toList()
        val promptIndex = lines.indexOfLast { it.isNotBlank() }
        if (promptIndex < 0 || lines[promptIndex].trim() != PROCESS_COMPLETED_PROMPT) {
            return output
        }
        return lines.take(promptIndex).joinToString("\n")
    }
}
