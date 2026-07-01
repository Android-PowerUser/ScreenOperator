package com.google.ai.sample.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionPolicyConfigTest {

    @After
    fun tearDown() {
        // Process-wide state; never leak an override into other test classes.
        ExecutionPolicyConfig.clearRemoteOverride()
    }

    @Test
    fun defaultPolicy_isUnlimitedWithThreeRetainedScreenshots() {
        val policy = ExecutionPolicyConfig.current()
        assertEquals(0, policy.maxCommandsPerMessage)
        assertEquals(3, policy.maxRelevantScreenElementMessages)
        assertEquals(ExecutionPolicyConfig.DEFAULT_TEMPLATE, policy.truncationFeedbackTemplate)
    }

    @Test
    fun setRemoteOverride_parsesAllFields() {
        val json = """
            {
              "maxCommandsPerMessage": 2,
              "truncationFeedbackTemplate": "Only {executed} of {total} ran (limit {limit}).",
              "maxRelevantScreenElementMessages": 5
            }
        """.trimIndent()

        val applied = ExecutionPolicyConfig.setRemoteOverride(json)

        assertEquals(1, applied)
        val policy = ExecutionPolicyConfig.current()
        assertEquals(2, policy.maxCommandsPerMessage)
        assertEquals(5, policy.maxRelevantScreenElementMessages)
        assertEquals("Only 2 of 5 ran (limit 2).", policy.formatTruncationFeedback(total = 5, executed = 2))
    }

    @Test
    fun setRemoteOverride_negativeRetentionCount_fallsBackToDefault() {
        ExecutionPolicyConfig.setRemoteOverride("""{"maxRelevantScreenElementMessages": -1}""")
        assertEquals(3, ExecutionPolicyConfig.current().maxRelevantScreenElementMessages)
    }

    @Test
    fun setRemoteOverride_blankJson_isIgnored() {
        val applied = ExecutionPolicyConfig.setRemoteOverride("")
        assertEquals(0, applied)
        assertEquals(0, ExecutionPolicyConfig.current().maxCommandsPerMessage)
    }

    @Test
    fun setRemoteOverride_malformedJson_isIgnored() {
        val before = ExecutionPolicyConfig.current()
        val applied = ExecutionPolicyConfig.setRemoteOverride("not json at all")
        assertEquals(0, applied)
        assertEquals(before, ExecutionPolicyConfig.current())
    }

    @Test
    fun formatTruncationFeedback_fillsAllPlaceholders() {
        val policy = ExecutionPolicyConfig.Policy(maxCommandsPerMessage = 2)
        val text = policy.formatTruncationFeedback(total = 7, executed = 2)
        assertTrue(text.contains("7"))
        assertTrue(text.contains("2"))
        assertFalse(text.contains("{total}"))
        assertFalse(text.contains("{executed}"))
        assertFalse(text.contains("{limit}"))
    }
}
