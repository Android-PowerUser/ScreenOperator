package com.google.ai.sample.feature.multimodal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoReasoningScreenElementHistoryPolicyTest {
    @Test
    fun sanitizeMessages_keepsOnlyThreeLatestScreenElementSectionsRelevant() {
        val messages = (1..4).map { index ->
            PhotoReasoningMessage(
                text = "Screenshot $index\n\nScreen elements:\n$index. Button $index",
                participant = PhotoParticipant.USER
            )
        }

        val sanitized = PhotoReasoningScreenElementHistoryPolicy.sanitizeMessages(messages)

        assertEquals("Screenshot 1\n\nScreen elements:\nno longer relevant", sanitized[0].text)
        assertTrue(sanitized[1].text.contains("Button 2"))
        assertTrue(sanitized[2].text.contains("Button 3"))
        assertTrue(sanitized[3].text.contains("Button 4"))
    }
}
