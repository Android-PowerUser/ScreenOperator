package com.google.ai.sample.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class TrialDurationOverrideConfigTest {

    @After
    fun tearDown() {
        TrialDurationOverrideConfig.clearRemoteOverride()
    }

    @Test
    fun default_isSevenDays() {
        assertEquals(7L * 24 * 60 * 60 * 1000L, TrialDurationOverrideConfig.current())
        assertEquals(TrialDurationOverrideConfig.DEFAULT_TRIAL_DURATION_MS, TrialDurationOverrideConfig.current())
    }

    @Test
    fun setRemoteOverride_appliesPositiveDuration() {
        val fourteenDaysMs = 14L * 24 * 60 * 60 * 1000L
        val applied = TrialDurationOverrideConfig.setRemoteOverride("""{"trialDurationMs": $fourteenDaysMs}""")

        assertEquals(1, applied)
        assertEquals(fourteenDaysMs, TrialDurationOverrideConfig.current())
    }

    @Test
    fun setRemoteOverride_zeroOrNegativeDuration_isIgnored() {
        TrialDurationOverrideConfig.setRemoteOverride("""{"trialDurationMs": 0}""")
        assertEquals(TrialDurationOverrideConfig.DEFAULT_TRIAL_DURATION_MS, TrialDurationOverrideConfig.current())

        TrialDurationOverrideConfig.setRemoteOverride("""{"trialDurationMs": -1000}""")
        assertEquals(TrialDurationOverrideConfig.DEFAULT_TRIAL_DURATION_MS, TrialDurationOverrideConfig.current())
    }

    @Test
    fun setRemoteOverride_missingField_isIgnored() {
        val applied = TrialDurationOverrideConfig.setRemoteOverride("""{"somethingElse": 1}""")
        assertEquals(0, applied)
        assertEquals(TrialDurationOverrideConfig.DEFAULT_TRIAL_DURATION_MS, TrialDurationOverrideConfig.current())
    }

    @Test
    fun setRemoteOverride_blankJson_resetsToDefault() {
        TrialDurationOverrideConfig.setRemoteOverride("""{"trialDurationMs": 99999}""")
        TrialDurationOverrideConfig.setRemoteOverride("")
        assertEquals(TrialDurationOverrideConfig.DEFAULT_TRIAL_DURATION_MS, TrialDurationOverrideConfig.current())
    }

    @Test
    fun setRemoteOverride_malformedJson_isIgnored() {
        val before = TrialDurationOverrideConfig.current()
        val applied = TrialDurationOverrideConfig.setRemoteOverride("not json")
        assertEquals(0, applied)
        assertEquals(before, TrialDurationOverrideConfig.current())
    }
}
