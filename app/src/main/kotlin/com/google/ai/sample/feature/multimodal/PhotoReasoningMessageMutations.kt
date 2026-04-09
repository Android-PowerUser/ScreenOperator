package com.google.ai.sample.feature.multimodal

internal object PhotoReasoningMessageMutations {
    fun appendUserAndPendingModelMessages(
        chatState: PhotoReasoningChatState,
        userMessage: PhotoReasoningMessage
    ): List<PhotoReasoningMessage> {
        chatState.addMessage(userMessage)
        chatState.addMessage(
            PhotoReasoningMessage(
                text = "",
                participant = PhotoParticipant.MODEL,
                isPending = true
            )
        )
        return chatState.getAllMessages()
    }

    fun appendErrorMessage(
        chatState: PhotoReasoningChatState,
        errorText: String
    ): List<PhotoReasoningMessage> {
        chatState.replaceLastPendingMessage()
        chatState.addMessage(
            PhotoReasoningMessage(
                text = errorText,
                participant = PhotoParticipant.ERROR
            )
        )
        return chatState.getAllMessages()
    }

    fun finalizeAiMessage(
        chatState: PhotoReasoningChatState,
        finalText: String
    ): List<PhotoReasoningMessage> {
        val messages = chatState.getAllMessages().toMutableList()
        val lastMessageIndex = messages.indexOfLast {
            it.participant == PhotoParticipant.MODEL && it.isPending
        }

        if (lastMessageIndex != -1) {
            messages[lastMessageIndex] = messages[lastMessageIndex].copy(text = finalText, isPending = false)
            chatState.setAllMessages(messages)
        } else {
            chatState.addMessage(
                PhotoReasoningMessage(
                    text = finalText,
                    participant = PhotoParticipant.MODEL,
                    isPending = false
                )
            )
        }
        return chatState.getAllMessages()
    }

    fun updateAiMessage(
        chatState: PhotoReasoningChatState,
        text: String,
        isPending: Boolean
    ): List<PhotoReasoningMessage> {
        val messages = chatState.getAllMessages().toMutableList()
        val lastAiMessageIndex = messages.indexOfLast { it.participant == PhotoParticipant.MODEL }

        if (lastAiMessageIndex != -1 && messages[lastAiMessageIndex].isPending) {
            val updatedMessage = messages[lastAiMessageIndex].let {
                it.copy(text = it.text + text, isPending = isPending)
            }
            messages[lastAiMessageIndex] = updatedMessage
        } else {
            messages.add(PhotoReasoningMessage(text = text, participant = PhotoParticipant.MODEL, isPending = isPending))
        }

        chatState.setAllMessages(messages)
        return chatState.getAllMessages()
    }

    fun replaceAiMessageText(
        chatState: PhotoReasoningChatState,
        text: String,
        isPending: Boolean
    ): List<PhotoReasoningMessage> {
        val messages = chatState.getAllMessages().toMutableList()
        val lastAiMessageIndex = messages.indexOfLast { it.participant == PhotoParticipant.MODEL }

        if (lastAiMessageIndex != -1 && messages[lastAiMessageIndex].isPending) {
            messages[lastAiMessageIndex] = messages[lastAiMessageIndex].copy(text = text, isPending = isPending)
        } else {
            messages.add(PhotoReasoningMessage(text = text, participant = PhotoParticipant.MODEL, isPending = isPending))
        }

        chatState.setAllMessages(messages)
        return chatState.getAllMessages()
    }
}
