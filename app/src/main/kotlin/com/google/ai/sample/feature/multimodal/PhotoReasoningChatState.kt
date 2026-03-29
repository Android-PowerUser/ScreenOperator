package com.google.ai.sample.feature.multimodal

class PhotoReasoningChatState(
    messages: List<PhotoReasoningMessage> = emptyList()
) {
    private val _messages: MutableList<PhotoReasoningMessage> = messages.toMutableList()
    val messages: List<PhotoReasoningMessage>
        get() = _messages.toList()

    fun addMessage(msg: PhotoReasoningMessage) {
        _messages.add(msg)
    }

    fun replaceLastPendingMessage() {
        val lastPendingIndex = _messages.indexOfLast { it.isPending }
        if (lastPendingIndex >= 0) {
            _messages.removeAt(lastPendingIndex)
        }
    }
    
    fun clearMessages() {
        _messages.clear()
    }

    fun updateLastMessageText(newText: String) {
        if (_messages.isNotEmpty()) {
            val lastMessage = _messages.last()
            _messages[_messages.size - 1] = lastMessage.copy(text = newText, isPending = false)
        }
    }

    fun getAllMessages(): List<PhotoReasoningMessage> {
        return _messages.toList()
    }

    fun setAllMessages(messages: List<PhotoReasoningMessage>) {
        _messages.clear()
        _messages.addAll(messages)
    }
}
