package com.google.ai.sample.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrialUiConfigTest {

    @After
    fun tearDown() {
        TrialUiConfig.clearRemoteOverride()
    }

    @Test
    fun defaults_matchOriginalHardcodedDialogText() {
        val policy = TrialUiConfig.current()
        assertEquals("Trial Information", policy.firstLaunchDialogTitle)
        assertEquals("Trial period expired", policy.trialExpiredDialogTitle)
        assertEquals("Choose Payment Method", policy.paymentMethodDialogTitle)
        assertEquals("Information", policy.infoDialogTitle)
        // The resolver's message defaults to the same text as the expired dialog body.
        assertEquals(policy.trialExpiredDialogBody, policy.resolvedExpiredStateInfoMessage())
    }

    @Test
    fun setRemoteOverride_overridesOnlyProvidedFields() {
        TrialUiConfig.setRemoteOverride("""{"trialExpiredDialogSubscribeButton": "Jetzt abonnieren"}""")

        val policy = TrialUiConfig.current()
        assertEquals("Jetzt abonnieren", policy.trialExpiredDialogSubscribeButton)
        // Untouched fields keep their built-in default.
        assertEquals("Trial period expired", policy.trialExpiredDialogTitle)
        assertEquals("Choose Payment Method", policy.paymentMethodDialogTitle)
    }

    @Test
    fun setRemoteOverride_subsequentCallsAreCumulative() {
        TrialUiConfig.setRemoteOverride("""{"firstLaunchDialogTitle": "Willkommen"}""")
        TrialUiConfig.setRemoteOverride("""{"firstLaunchDialogButton": "Verstanden"}""")

        val policy = TrialUiConfig.current()
        assertEquals("Willkommen", policy.firstLaunchDialogTitle)
        assertEquals("Verstanden", policy.firstLaunchDialogButton)
    }

    @Test
    fun setRemoteOverride_expiredStateInfoMessage_independentWhenSet() {
        TrialUiConfig.setRemoteOverride(
            """{"trialExpiredDialogBody": "Dialog text", "expiredStateInfoMessage": "Resolver text"}"""
        )

        val policy = TrialUiConfig.current()
        assertEquals("Dialog text", policy.trialExpiredDialogBody)
        assertEquals("Resolver text", policy.resolvedExpiredStateInfoMessage())
    }

    @Test
    fun setRemoteOverride_blankJson_resetsToDefaults() {
        TrialUiConfig.setRemoteOverride("""{"firstLaunchDialogTitle": "Custom"}""")
        TrialUiConfig.setRemoteOverride("")

        assertEquals("Trial Information", TrialUiConfig.current().firstLaunchDialogTitle)
        assertNull(TrialUiConfig.current().expiredStateInfoMessage)
    }

    @Test
    fun setRemoteOverride_malformedJson_isIgnored() {
        val before = TrialUiConfig.current()
        val applied = TrialUiConfig.setRemoteOverride("not json")
        assertEquals(0, applied)
        assertEquals(before, TrialUiConfig.current())
    }
}
