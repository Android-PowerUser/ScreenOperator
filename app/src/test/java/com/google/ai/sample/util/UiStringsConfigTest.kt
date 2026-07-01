package com.google.ai.sample.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class UiStringsConfigTest {

    @After
    fun tearDown() {
        UiStringsConfig.clearRemoteOverride()
    }

    @Test
    fun get_returnsDefaultWhenNoOverrideInstalled() {
        assertEquals("Stop", UiStringsConfig.get("chat_stop_button", "Stop"))
    }

    @Test
    fun setRemoteOverride_overridesOnlyMatchingIds() {
        UiStringsConfig.setRemoteOverride("""{"chat_stop_button": "Stopp"}""")

        assertEquals("Stopp", UiStringsConfig.get("chat_stop_button", "Stop"))
        // An ID with no override entry still falls back to its default.
        assertEquals("Save", UiStringsConfig.get("entry_editor_save_button", "Save"))
    }

    @Test
    fun setRemoteOverride_blankValue_fallsBackToDefault() {
        UiStringsConfig.setRemoteOverride("""{"chat_stop_button": "   "}""")
        assertEquals("Stop", UiStringsConfig.get("chat_stop_button", "Stop"))
    }

    @Test
    fun setRemoteOverride_nonStringValue_isSkipped() {
        val applied = UiStringsConfig.setRemoteOverride("""{"chat_stop_button": 42}""")
        assertEquals(0, applied)
        assertEquals("Stop", UiStringsConfig.get("chat_stop_button", "Stop"))
    }

    @Test
    fun setRemoteOverride_blankJson_resetsToDefaults() {
        UiStringsConfig.setRemoteOverride("""{"chat_stop_button": "Stopp"}""")
        UiStringsConfig.setRemoteOverride("")
        assertEquals("Stop", UiStringsConfig.get("chat_stop_button", "Stop"))
    }

    @Test
    fun setRemoteOverride_malformedJson_isIgnored() {
        UiStringsConfig.setRemoteOverride("""{"chat_stop_button": "Stopp"}""")
        val applied = UiStringsConfig.setRemoteOverride("not json")
        assertEquals(0, applied)
        // Previous override is untouched by the malformed call.
        assertEquals("Stopp", UiStringsConfig.get("chat_stop_button", "Stop"))
    }

    @Test
    fun get_withPlaceholders_substitutesPositionalArgs() {
        assertEquals(
            "Error sharing file: disk full",
            UiStringsConfig.get("toast_file_share_error", "Error sharing file: {0}", "disk full")
        )
    }

    @Test
    fun get_withPlaceholders_overrideCanReorderOrOmitArgs() {
        UiStringsConfig.setRemoteOverride("""{"toast_entry_overwritten": "Eintrag '{0}' wurde ersetzt."}""")
        assertEquals(
            "Eintrag 'MyEntry' wurde ersetzt.",
            UiStringsConfig.get("toast_entry_overwritten", "Entry '{0}' overwritten.", "MyEntry")
        )
    }
}
