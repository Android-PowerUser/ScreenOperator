package com.google.ai.sample.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorClassificationConfigTest {

    @After
    fun tearDown() {
        ErrorClassificationConfig.clearRemoteOverride()
    }

    @Test
    fun defaults_matchOriginalHardcodedSubstrings() {
        val policy = ErrorClassificationConfig.current()
        assertTrue(policy.isQuotaExceededError("You have exceeded your current quota, please retry"))
        assertTrue(policy.isQuotaExceededError("Error code 429"))
        assertTrue(policy.isQuotaExceededError("Too Many Requests"))
        assertTrue(policy.isQuotaExceededError("rate_limit_exceeded"))
        assertFalse(policy.isQuotaExceededError("some unrelated error"))

        assertTrue(policy.isHighDemandError("Service Unavailable (503)"))
        assertTrue(policy.isHighDemandError("model is currently overloaded"))
        assertFalse(policy.isHighDemandError("some unrelated error"))
    }

    @Test
    fun matching_isCaseInsensitive() {
        val policy = ErrorClassificationConfig.current()
        assertTrue(policy.isQuotaExceededError("EXCEEDED YOUR CURRENT QUOTA"))
        assertTrue(policy.isHighDemandError("HIGH DEMAND right now"))
    }

    @Test
    fun setRemoteOverride_replacesCategoryEntirely() {
        val json = """{"quotaExceededSubstrings": ["custom quota phrase"]}"""
        ErrorClassificationConfig.setRemoteOverride(json)

        val policy = ErrorClassificationConfig.current()
        assertTrue(policy.isQuotaExceededError("this has a custom quota phrase in it"))
        // No longer matches the built-in default substring, since the override replaces the list.
        assertFalse(policy.isQuotaExceededError("code 429"))
        // Omitted field keeps its built-in default.
        assertTrue(policy.isHighDemandError("overloaded"))
    }

    @Test
    fun setRemoteOverride_explicitEmptyArray_disablesCategory() {
        ErrorClassificationConfig.setRemoteOverride("""{"highDemandSubstrings": []}""")
        assertFalse(ErrorClassificationConfig.current().isHighDemandError("overloaded"))
    }

    @Test
    fun setRemoteOverride_malformedJson_isIgnored() {
        val before = ErrorClassificationConfig.current()
        val applied = ErrorClassificationConfig.setRemoteOverride("{not json")
        assertEquals(0, applied)
        assertEquals(before, ErrorClassificationConfig.current())
    }
}
