package com.google.ai.sample.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMappingOverridesConfigTest {

    @After
    fun tearDown() {
        // These tests mutate process-wide state; never leak an override into other test classes.
        AppMappingOverridesConfig.clearRemoteOverride()
    }

    @Test
    fun setRemoteOverride_blankJson_resetsToDefaults() {
        AppMappingOverridesConfig.setRemoteOverride("""{"matchThreshold": 50}""")
        AppMappingOverridesConfig.setRemoteOverride("")

        val policy = AppMappingOverridesConfig.current()
        assertEquals(AppMappingOverridesConfig.DEFAULT_MATCH_THRESHOLD, policy.matchThreshold)
        assertTrue(policy.apps.isEmpty())
    }

    @Test
    fun setRemoteOverride_parsesValidAppEntry() {
        val json = """
            {
              "matchThreshold": 80,
              "apps": [
                {
                  "canonicalName": "myneatapp",
                  "packageName": "com.example.myneatapp",
                  "variations": ["my neat app", "neat app"],
                  "aliasesForPackageLookup": ["mna"]
                }
              ]
            }
        """.trimIndent()

        val count = AppMappingOverridesConfig.setRemoteOverride(json)

        assertEquals(1, count)
        val policy = AppMappingOverridesConfig.current()
        assertEquals(80, policy.matchThreshold)
        assertEquals(1, policy.apps.size)
        val app = policy.apps.first()
        assertEquals("myneatapp", app.canonicalName)
        assertEquals("com.example.myneatapp", app.packageName)
        assertEquals(listOf("my neat app", "neat app"), app.variations)
        assertEquals(listOf("mna"), app.aliasesForPackageLookup)
    }

    @Test
    fun setRemoteOverride_skipsEntriesMissingRequiredFields() {
        val json = """
            {
              "apps": [
                { "canonicalName": "noPackage" },
                { "packageName": "com.example.nocanonical" },
                { "canonicalName": "valid", "packageName": "com.example.valid" }
              ]
            }
        """.trimIndent()

        val count = AppMappingOverridesConfig.setRemoteOverride(json)

        assertEquals(1, count)
        assertEquals("valid", AppMappingOverridesConfig.current().apps.single().canonicalName)
    }

    @Test
    fun setRemoteOverride_outOfRangeThreshold_fallsBackToDefault() {
        AppMappingOverridesConfig.setRemoteOverride("""{"matchThreshold": 150}""")
        assertEquals(AppMappingOverridesConfig.DEFAULT_MATCH_THRESHOLD, AppMappingOverridesConfig.current().matchThreshold)

        AppMappingOverridesConfig.setRemoteOverride("""{"matchThreshold": -5}""")
        assertEquals(AppMappingOverridesConfig.DEFAULT_MATCH_THRESHOLD, AppMappingOverridesConfig.current().matchThreshold)
    }

    @Test
    fun setRemoteOverride_malformedJson_isIgnored() {
        val before = AppMappingOverridesConfig.current()
        val count = AppMappingOverridesConfig.setRemoteOverride("{not valid json")
        assertEquals(0, count)
        assertEquals(before, AppMappingOverridesConfig.current())
    }

    @Test
    fun manualMappings_mergesOverridesOnTopOfBuiltIns() {
        assertNull(AppMappings.manualMappings["myneatapp"])

        AppMappingOverridesConfig.setRemoteOverride(
            """{"apps":[{"canonicalName":"myneatapp","packageName":"com.example.myneatapp"}]}"""
        )

        assertEquals("com.example.myneatapp", AppMappings.manualMappings["myneatapp"])
        // Built-ins are still present alongside the new override.
        assertEquals("com.whatsapp", AppMappings.manualMappings["whatsapp"])
    }

    @Test
    fun manualMappings_overrideWinsOnCanonicalNameClash() {
        AppMappingOverridesConfig.setRemoteOverride(
            """{"apps":[{"canonicalName":"whatsapp","packageName":"com.example.whatsapp.fork"}]}"""
        )

        assertEquals("com.example.whatsapp.fork", AppMappings.manualMappings["whatsapp"])
    }

    @Test
    fun appNameVariations_includesOverrideVariations() {
        AppMappingOverridesConfig.setRemoteOverride(
            """{"apps":[{"canonicalName":"myneatapp","packageName":"com.example.myneatapp","variations":["my neat app"]}]}"""
        )

        assertEquals(listOf("my neat app"), AppMappings.appNameVariations["myneatapp"])
        // Built-in variations are still present alongside the new override.
        assertTrue(AppMappings.appNameVariations["whatsapp"]?.contains("wa") == true)
    }
}
