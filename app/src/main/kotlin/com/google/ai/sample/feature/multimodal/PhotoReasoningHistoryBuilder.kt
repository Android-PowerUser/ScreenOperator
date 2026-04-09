package com.google.ai.sample.feature.multimodal

import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.content

internal object PhotoReasoningHistoryBuilder {
    fun buildInitialHistory(
        systemMessage: String,
        formattedDbEntries: String
    ): List<Content> {
        val initialHistory = mutableListOf<Content>()
        if (systemMessage.isNotBlank()) {
            initialHistory.add(content(role = "user") { text(systemMessage) })
        }
        if (formattedDbEntries.isNotBlank()) {
            initialHistory.add(content(role = "user") { text(formattedDbEntries) })
        }
        return initialHistory
    }

    fun buildHistoryFromMessages(
        messages: List<PhotoReasoningMessage>,
        systemMessage: String,
        formattedDbEntries: String
    ): List<Content> {
        val history = buildInitialHistory(systemMessage, formattedDbEntries).toMutableList()

        var currentUserContent = ""
        var currentModelContent = ""

        for (message in messages) {
            when (message.participant) {
                PhotoParticipant.USER -> {
                    if (currentModelContent.isNotEmpty()) {
                        history.add(content(role = "model") { text(currentModelContent) })
                        currentModelContent = ""
                    }
                    if (currentUserContent.isNotEmpty()) {
                        currentUserContent += "\n\n"
                    }
                    currentUserContent += message.text
                }

                PhotoParticipant.MODEL -> {
                    if (currentUserContent.isNotEmpty()) {
                        history.add(content(role = "user") { text(currentUserContent) })
                        currentUserContent = ""
                    }
                    if (currentModelContent.isNotEmpty()) {
                        currentModelContent += "\n\n"
                    }
                    currentModelContent += message.text
                }

                PhotoParticipant.ERROR -> {
                    continue
                }
            }
        }

        if (currentUserContent.isNotEmpty()) {
            history.add(content(role = "user") { text(currentUserContent) })
        }
        if (currentModelContent.isNotEmpty()) {
            history.add(content(role = "model") { text(currentModelContent) })
        }

        return history
    }
}
