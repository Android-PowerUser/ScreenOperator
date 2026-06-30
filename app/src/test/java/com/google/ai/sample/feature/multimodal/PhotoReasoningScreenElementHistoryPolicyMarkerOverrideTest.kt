package com.google.ai.sample.feature.multimodal

import com.google.ai.sample.util.OperationalTuningConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoReasoningScreenElementHistoryPolicyMarkerOverrideTest {

    @After
    fun tearDown() {
        OperationalTuningConfig.clearRemoteOverride()
    }

    @Test
    fun default_marker_matchesOriginalHardcodedString() {
        assertEquals("Screen elements:", OperationalTuningConfig.current().screenElementsMarker)
    }

    @Test
    fun hasScreenElements_usesOverriddenMarker() {
        OperationalTuningConfig.setRemoteOverride("""{"screenElementsMarker": "UI elements:"}""")

        assertTrue(PhotoReasoningScreenElementHistoryPolicy.hasScreenElements("Screenshot 1\n\nUI elements:\n1. Button"))
        // The old default no longer matches once overridden - writer and reader stay in sync
        // because both read the same live config value, so this is expected, not a bug.
        assertEquals(
            false,
            PhotoReasoningScreenElementHistoryPolicy.hasScreenElements("Screenshot 1\n\nScreen elements:\n1. Button")
        )
    }

    @Test
    fun sanitizeMessages_worksWithOverriddenMarker() {
        OperationalTuningConfig.setRemoteOverride("""{"screenElementsMarker": "UI elements:"}""")

        val messages = (1..4).map { index ->
            PhotoReasoningMessage(
                text = "Screenshot $index\n\nUI elements:\n$index. Button $index",
                participant = PhotoParticipant.USER
            )
        }

        val sanitized = PhotoReasoningScreenElementHistoryPolicy.sanitizeMessages(messages)

        assertEquals("Screenshot 1\n\nUI elements:\nno longer relevant", sanitized[0].text)
        assertTrue(sanitized[1].text.contains("Button 2"))
    }
}
