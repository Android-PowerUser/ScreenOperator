package com.google.ai.sample.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class GenerationDefaultsConfigTest {

    @After
    fun tearDown() {
        GenerationDefaultsConfig.clearRemoteOverride()
    }

    @Test
    fun defaults_matchOriginalHardcodedValues() {
        val policy = GenerationDefaultsConfig.current()
        assertEquals(0.0f, policy.temperature)
        assertEquals(0.0f, policy.topP)
        assertEquals(1, policy.topK)
    }

    @Test
    fun setRemoteOverride_appliesValidValues() {
        val applied = GenerationDefaultsConfig.setRemoteOverride("""{"temperature": 0.2, "topP": 0.9, "topK": 32}""")

        assertEquals(1, applied)
        val policy = GenerationDefaultsConfig.current()
        assertEquals(0.2f, policy.temperature)
        assertEquals(0.9f, policy.topP)
        assertEquals(32, policy.topK)
    }

    @Test
    fun setRemoteOverride_outOfRangeValues_fallBackToCurrentPerField() {
        GenerationDefaultsConfig.setRemoteOverride("""{"temperature": 5.0, "topP": -1.0, "topK": 0}""")

        val policy = GenerationDefaultsConfig.current()
        assertEquals(0.0f, policy.temperature)
        assertEquals(0.0f, policy.topP)
        assertEquals(1, policy.topK)
    }

    @Test
    fun setRemoteOverride_partialPayload_onlyChangesProvidedFields() {
        GenerationDefaultsConfig.setRemoteOverride("""{"topK": 16}""")

        val policy = GenerationDefaultsConfig.current()
        assertEquals(16, policy.topK)
        assertEquals(0.0f, policy.temperature)
        assertEquals(0.0f, policy.topP)
    }

    @Test
    fun setRemoteOverride_blankJson_resetsToDefaults() {
        GenerationDefaultsConfig.setRemoteOverride("""{"topK": 16}""")
        GenerationDefaultsConfig.setRemoteOverride("")
        assertEquals(1, GenerationDefaultsConfig.current().topK)
    }

    @Test
    fun setRemoteOverride_malformedJson_isIgnored() {
        val before = GenerationDefaultsConfig.current()
        val applied = GenerationDefaultsConfig.setRemoteOverride("not json")
        assertEquals(0, applied)
        assertEquals(before, GenerationDefaultsConfig.current())
    }
}
