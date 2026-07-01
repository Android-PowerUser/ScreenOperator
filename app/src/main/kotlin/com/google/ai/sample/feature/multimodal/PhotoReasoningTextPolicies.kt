package com.google.ai.sample.feature.multimodal

import android.content.Context
import com.google.ai.sample.util.SystemMessageEntryPreferences

internal object PhotoReasoningTextPolicies {
    // Read live (not a const) so formatRetrievalResultForPrompt() and
    // isHeadingAlreadyRetrievedInChat() always agree on the exact same marker - if this were
    // overridden in only one of the two places, "already retrieved" detection would silently
    // break (every retrieval would look new forever, or the marker would never be found again).
    private val retrievalHeaderPrefix: String
        get() = com.google.ai.sample.util.OperationalTuningConfig.current().retrievalHeaderPrefix

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
        return com.google.ai.sample.util.ErrorClassificationConfig.current().isQuotaExceededError(message)
    }

    /**
     * Point 14: Check if error is a high-demand 503 (UNAVAILABLE) error.
     * These should NOT trigger API key switching.
     */
    fun isHighDemandError(message: String): Boolean {
        return com.google.ai.sample.util.ErrorClassificationConfig.current().isHighDemandError(message)
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
            "$retrievalHeaderPrefix${result.heading}]:\n${result.content}"
        } else {
            "$retrievalHeaderPrefix${result.heading}]:\nThe information is not available"
        }
    }

    fun isHeadingAlreadyRetrievedInChat(messages: List<PhotoReasoningMessage>, heading: String): Boolean {
        val marker = "$retrievalHeaderPrefix$heading]"
        return messages.any { message ->
            message.text.contains(marker, ignoreCase = true)
        }
    }
}
