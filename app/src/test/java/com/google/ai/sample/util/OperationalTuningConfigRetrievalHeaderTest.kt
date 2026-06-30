package com.google.ai.sample.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class OperationalTuningConfigRetrievalHeaderTest {

    @After
    fun tearDown() {
        OperationalTuningConfig.clearRemoteOverride()
    }

    @Test
    fun default_matchesOriginalHardcodedPrefix() {
        assertEquals("Retrieved information [", OperationalTuningConfig.current().retrievalHeaderPrefix)
    }

    @Test
    fun setRemoteOverride_appliesCustomPrefix() {
        OperationalTuningConfig.setRemoteOverride("""{"retrievalHeaderPrefix": "Custom info ["}""")
        assertEquals("Custom info [", OperationalTuningConfig.current().retrievalHeaderPrefix)
    }

    @Test
    fun setRemoteOverride_blankPrefix_fallsBackToCurrent() {
        OperationalTuningConfig.setRemoteOverride("""{"retrievalHeaderPrefix": "   "}""")
        assertEquals("Retrieved information [", OperationalTuningConfig.current().retrievalHeaderPrefix)
    }

    @Test
    fun setRemoteOverride_doesNotAffectUnrelatedFields() {
        OperationalTuningConfig.setRemoteOverride("""{"retrievalHeaderPrefix": "Custom ["}""")
        val policy = OperationalTuningConfig.current()
        assertEquals(1500L, policy.mistralMinIntervalMsDefault)
        assertEquals("[Process completed - press Enter]", policy.termuxProcessCompletedPrompt)
    }
}
