package com.google.ai.sample.feature.multimodal

import android.content.Context
import com.google.ai.sample.util.SystemMessageEntryPreferences

internal object PhotoReasoningTextPolicies {
    fun buildPromptWithScreenInfo(userInput: String, screenInfoForPrompt: String?): String {
        return if (screenInfoForPrompt != null && screenInfoForPrompt.isNotBlank()) {
            "$userInput\n\n$screenInfoForPrompt"
        } else {
            userInput
        }
    }

    fun isQuotaExceededError(message: String): Boolean {
        return message.contains("exceeded your current quota") ||
            message.contains("code 429") ||
            message.contains("Too Many Requests", ignoreCase = true) ||
            message.contains("rate_limit", ignoreCase = true)
    }

    /**
     * Point 14: Check if error is a high-demand 503 (UNAVAILABLE) error.
     * These should NOT trigger API key switching.
     */
    fun isHighDemandError(message: String): Boolean {
        return message.contains("Service Unavailable (503)") ||
            message.contains("UNAVAILABLE") ||
            message.contains("high demand") ||
            message.contains("overloaded")
    }

    /**
     * Helper function to format database entries as text.
     */
    fun formatDatabaseEntriesAsText(context: Context): String {
        val entries = SystemMessageEntryPreferences.loadEntries(context)
        if (entries.isEmpty()) {
            return ""
        }
        val builder = StringBuilder()
        builder.append("Available System Guides:\n---\n")
        for (entry in entries) {
            builder.append("Title: ${entry.title}\n")
            builder.append("Guide: ${entry.guide}\n")
            builder.append("---\n")
        }
        return builder.toString()
    }
}
