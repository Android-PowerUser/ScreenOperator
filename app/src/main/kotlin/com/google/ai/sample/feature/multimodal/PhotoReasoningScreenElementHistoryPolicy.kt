package com.google.ai.sample.feature.multimodal

internal object PhotoReasoningScreenElementHistoryPolicy {
    private const val MAX_RELEVANT_SCREEN_ELEMENT_MESSAGES = 3
    private const val MARKER = "Screen elements:"
    private const val NO_LONGER_RELEVANT = "no longer relevant"
    private val screenElementsSectionRegex = Regex(
        pattern = "(?is)(Screen elements:\\s*).*",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    fun sanitizeMessages(messages: List<PhotoReasoningMessage>): List<PhotoReasoningMessage> {
        var remainingRelevant = MAX_RELEVANT_SCREEN_ELEMENT_MESSAGES
        val keepRelevantIds = messages
            .asReversed()
            .filter { hasScreenElements(it.text) && !isAlreadyObsolete(it.text) }
            .mapNotNull { message ->
                if (remainingRelevant > 0) {
                    remainingRelevant--
                    message.id
                } else {
                    null
                }
            }
            .toSet()

        return messages.map { message ->
            if (hasScreenElements(message.text) && !isAlreadyObsolete(message.text) && message.id !in keepRelevantIds) {
                message.copy(text = replaceScreenElementsWithObsoleteMarker(message.text))
            } else {
                message
            }
        }
    }

    fun hasScreenElements(text: String): Boolean {
        return text.contains(MARKER, ignoreCase = true)
    }

    private fun isAlreadyObsolete(text: String): Boolean {
        val markerIndex = text.indexOf(MARKER, ignoreCase = true)
        if (markerIndex < 0) return false
        val sectionText = text.substring(markerIndex + MARKER.length).trim()
        return sectionText.equals(NO_LONGER_RELEVANT, ignoreCase = true)
    }

    private fun replaceScreenElementsWithObsoleteMarker(text: String): String {
        return screenElementsSectionRegex.replace(text) { match ->
            "${match.groupValues[1]}$NO_LONGER_RELEVANT"
        }
    }
}
