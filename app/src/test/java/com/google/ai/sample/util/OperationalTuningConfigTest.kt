package com.google.ai.sample.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class OperationalTuningConfigTest {

    @After
    fun tearDown() {
        OperationalTuningConfig.clearRemoteOverride()
    }

    @Test
    fun defaults_matchOriginalHardcodedValues() {
        val policy = OperationalTuningConfig.current()
        assertEquals(1500L, policy.mistralMinIntervalMsDefault)
        assertEquals(420L, policy.mistralMinIntervalMsFastModels)
        assertEquals(5_000L, policy.mistralMaxServerDelayMs)
        assertEquals(100L, policy.mistralCancelCheckIntervalMs)
        assertEquals(3, policy.modelDownloadMaxRetries)
        assertEquals(3_000L, policy.modelDownloadRetryDelayMs)
        assertEquals(500L, policy.modelDownloadProgressUpdateIntervalMs)
        assertEquals("[Process completed - press Enter]", policy.termuxProcessCompletedPrompt)
    }

    @Test
    fun setRemoteOverride_overridesOnlyProvidedFields() {
        OperationalTuningConfig.setRemoteOverride("""{"modelDownloadMaxRetries": 5}""")

        val policy = OperationalTuningConfig.current()
        assertEquals(5, policy.modelDownloadMaxRetries)
        // Untouched fields keep their built-in default.
        assertEquals(1500L, policy.mistralMinIntervalMsDefault)
        assertEquals(3_000L, policy.modelDownloadRetryDelayMs)
    }

    @Test
    fun setRemoteOverride_negativeValues_fallBackToCurrent() {
        OperationalTuningConfig.setRemoteOverride("""{"modelDownloadMaxRetries": -1, "mistralMinIntervalMsDefault": -100}""")

        val policy = OperationalTuningConfig.current()
        assertEquals(3, policy.modelDownloadMaxRetries)
        assertEquals(1500L, policy.mistralMinIntervalMsDefault)
    }

    @Test
    fun setRemoteOverride_blankTermuxPrompt_fallsBackToCurrent() {
        OperationalTuningConfig.setRemoteOverride("""{"termuxProcessCompletedPrompt": "   "}""")
        assertEquals("[Process completed - press Enter]", OperationalTuningConfig.current().termuxProcessCompletedPrompt)
    }

    @Test
    fun setRemoteOverride_blankJson_resetsToDefaults() {
        OperationalTuningConfig.setRemoteOverride("""{"modelDownloadMaxRetries": 9}""")
        OperationalTuningConfig.setRemoteOverride("")
        assertEquals(3, OperationalTuningConfig.current().modelDownloadMaxRetries)
    }

    @Test
    fun setRemoteOverride_malformedJson_isIgnored() {
        val before = OperationalTuningConfig.current()
        val applied = OperationalTuningConfig.setRemoteOverride("{not valid")
        assertEquals(0, applied)
        assertEquals(before, OperationalTuningConfig.current())
    }
}
