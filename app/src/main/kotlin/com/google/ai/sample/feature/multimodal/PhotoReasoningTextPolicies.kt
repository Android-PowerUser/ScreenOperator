package com.google.ai.sample.feature.multimodal

import android.content.Context
import com.google.ai.sample.util.SystemMessageEntryPreferences

internal object PhotoReasoningTextPolicies {
    private const val RETRIEVAL_HEADER_PREFIX = "Retrieved information ["

    data class RetrievalResult(
        val heading: String,
        val content: String,
        val available: Boolean
    )

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
        builder.append("Retrievable information: ")
        entries.forEach { entry ->
            builder.append(entry.title).append(",\n")
        }
        return builder.toString()
    }

    fun resolveRetrievalRequest(context: Context, heading: String): RetrievalResult {
        val normalizedHeading = heading.trim()
        val entry = SystemMessageEntryPreferences.loadEntries(context).firstOrNull {
            it.title.equals(normalizedHeading, ignoreCase = true)
        }
        return if (entry != null) {
            RetrievalResult(
                heading = entry.title,
                content = entry.guide,
                available = true
            )
        } else {
            RetrievalResult(
                heading = normalizedHeading,
                content = "The information is not available",
                available = false
            )
        }
    }

    fun formatRetrievalResultForPrompt(result: RetrievalResult): String {
        return if (result.available) {
            "$RETRIEVAL_HEADER_PREFIX${result.heading}]:\n${result.content}"
        } else {
            "$RETRIEVAL_HEADER_PREFIX${result.heading}]:\nThe information is not available"
        }
    }

    fun isHeadingAlreadyRetrievedInChat(messages: List<PhotoReasoningMessage>, heading: String): Boolean {
        val marker = "$RETRIEVAL_HEADER_PREFIX$heading]"
        return messages.any { message ->
            message.text.contains(marker, ignoreCase = true)
        }
    }
}
