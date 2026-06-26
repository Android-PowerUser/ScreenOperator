package com.google.ai.sample.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomModelRegistryTest {

    private val sampleJson = """
        [
          {"id":"MODEL_A","endpoint":"https://api.example.com/a","modelName":"a"},
          {"id":"MODEL_B","endpoint":"https://api.example.com/b","modelName":"b"}
        ]
    """.trimIndent()

    @After
    fun tearDown() {
        // CustomModelRegistry is a singleton object - reset its state so tests don't bleed
        // into each other.
        CustomModelRegistry.setModels("[]")
        CustomModelRegistry.clearActiveModel()
    }

    @Test
    fun setModels_installsAllValidEntries() {
        val count = CustomModelRegistry.setModels(sampleJson)
        assertEquals(2, count)
        assertEquals(listOf("MODEL_A", "MODEL_B"), CustomModelRegistry.getModels().map { it.id })
    }

    @Test
    fun findById_returnsDefinitionOrNull() {
        CustomModelRegistry.setModels(sampleJson)
        assertEquals("MODEL_A", CustomModelRegistry.findById("MODEL_A")?.id)
        assertNull(CustomModelRegistry.findById("DOES_NOT_EXIST"))
    }

    @Test
    fun setActiveModelId_succeedsForKnownModel() {
        CustomModelRegistry.setModels(sampleJson)
        val activated = CustomModelRegistry.setActiveModelId("MODEL_B")
        assertTrue(activated)
        assertEquals("MODEL_B", CustomModelRegistry.getActiveModelId())
        assertEquals("MODEL_B", CustomModelRegistry.getActiveModel()?.id)
    }

    @Test
    fun setActiveModelId_failsForUnknownModel() {
        CustomModelRegistry.setModels(sampleJson)
        val activated = CustomModelRegistry.setActiveModelId("NOT_A_CUSTOM_MODEL")
        assertFalse(activated)
        assertNull(CustomModelRegistry.getActiveModelId())
    }

    @Test
    fun clearActiveModel_deactivates() {
        CustomModelRegistry.setModels(sampleJson)
        CustomModelRegistry.setActiveModelId("MODEL_A")
        CustomModelRegistry.clearActiveModel()
        assertNull(CustomModelRegistry.getActiveModel())
        assertNull(CustomModelRegistry.getActiveModelId())
    }

    @Test
    fun setModels_deactivatesActiveModelIfRemovedFromNewConfig() {
        CustomModelRegistry.setModels(sampleJson)
        CustomModelRegistry.setActiveModelId("MODEL_A")
        assertEquals("MODEL_A", CustomModelRegistry.getActiveModelId())

        // New config no longer contains MODEL_A
        CustomModelRegistry.setModels("""[{"id":"MODEL_C","endpoint":"https://api.example.com/c","modelName":"c"}]""")

        assertNull(CustomModelRegistry.getActiveModelId())
    }

    @Test
    fun setModels_keepsActiveModelIfStillPresentInNewConfig() {
        CustomModelRegistry.setModels(sampleJson)
        CustomModelRegistry.setActiveModelId("MODEL_A")

        // Re-apply a config that still contains MODEL_A (e.g. unrelated entry added)
        CustomModelRegistry.setModels(
            """[
                {"id":"MODEL_A","endpoint":"https://api.example.com/a","modelName":"a"},
                {"id":"MODEL_D","endpoint":"https://api.example.com/d","modelName":"d"}
            ]"""
        )

        assertEquals("MODEL_A", CustomModelRegistry.getActiveModelId())
    }
}
