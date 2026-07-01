package com.google.ai.sample.feature.multimodal

import com.google.ai.sample.util.ExecutionPolicyConfig
import com.google.ai.sample.util.OperationalTuningConfig

internal object PhotoReasoningScreenElementHistoryPolicy {
    private const val NO_LONGER_RELEVANT = "no longer relevant"

    // Read live (not a const) so the writer (ScreenOperatorAccessibilityService) and this
    // reader always agree on the exact same marker - if overridden in only one of the two
    // places, screen-element history trimming would silently stop firing instead of erroring.
    private val marker: String
        get() = OperationalTuningConfig.current().screenElementsMarker

    private fun screenElementsSectionRegex(): Regex {
        return Regex(
            pattern = "(?is)(${Regex.escape(marker)}\\s*).*",
            options = setOf(RegexOption.IGNORE_CASE)
        )
    }

    fun sanitizeMessages(messages: List<PhotoReasoningMessage>): List<PhotoReasoningMessage> {
        var remainingRelevant = ExecutionPolicyConfig.current().maxRelevantScreenElementMessages
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
        return text.contains(marker, ignoreCase = true)
    }

    private fun isAlreadyObsolete(text: String): Boolean {
        val markerIndex = text.indexOf(marker, ignoreCase = true)
        if (markerIndex < 0) return false
        val sectionText = text.substring(markerIndex + marker.length).trim()
        return sectionText.equals(NO_LONGER_RELEVANT, ignoreCase = true)
    }

    private fun replaceScreenElementsWithObsoleteMarker(text: String): String {
        return screenElementsSectionRegex().replace(text) { match ->
            "${match.groupValues[1]}$NO_LONGER_RELEVANT"
        }
    }
}
