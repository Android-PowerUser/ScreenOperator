package com.google.ai.sample.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomModelConfigTest {

    @Test
    fun parse_validEntry_appliesDefaultsAndFields() {
        val json = """
            [{"id":"MY_MODEL","displayName":"My Model","endpoint":"https://api.example.com/v1/chat/completions","modelName":"example/model"}]
        """.trimIndent()

        val result = CustomModelConfig.parse(json)

        assertEquals(1, result.size)
        val def = result.first()
        assertEquals("MY_MODEL", def.id)
        assertEquals("My Model", def.displayName)
        assertEquals("https://api.example.com/v1/chat/completions", def.endpoint)
        assertEquals("example/model", def.modelName)
        // Defaults
        assertEquals("Authorization", def.apiKeyHeader)
        assertEquals("Bearer ", def.apiKeyPrefix)
        assertEquals(false, def.supportsScreenshot)
        assertEquals(false, def.supportsTopK)
        assertEquals(true, def.stream)
    }

    @Test
    fun parse_explicitFieldsOverrideDefaults() {
        val json = """
            [{
              "id":"M2","endpoint":"https://api.example.com/x","modelName":"m",
              "apiKeyHeader":"x-api-key","apiKeyPrefix":"",
              "supportsScreenshot":true,"supportsTopK":true,"stream":false
            }]
        """.trimIndent()

        val def = CustomModelConfig.parse(json).first()

        assertEquals("x-api-key", def.apiKeyHeader)
        assertEquals("", def.apiKeyPrefix)
        assertTrue(def.supportsScreenshot)
        assertTrue(def.supportsTopK)
        assertEquals(false, def.stream)
        // displayName defaults to id when omitted
        assertEquals("M2", def.displayName)
    }

    @Test
    fun parse_skipsEntryMissingRequiredFields() {
        val json = """[{"id":"NO_ENDPOINT","modelName":"m"}]"""
        assertEquals(0, CustomModelConfig.parse(json).size)
    }

    @Test
    fun parse_skipsNonHttpsEndpoint() {
        val json = """[{"id":"INSECURE","endpoint":"http://api.example.com/x","modelName":"m"}]"""
        assertEquals(0, CustomModelConfig.parse(json).size)
    }

    @Test
    fun parse_skipsMalformedEntryWithoutCrashing() {
        assertEquals(0, CustomModelConfig.parse("[1, 2, 3]").size)
        assertEquals(0, CustomModelConfig.parse("not json at all").size)
        assertEquals(0, CustomModelConfig.parse("").size)
    }

    @Test
    fun parse_multipleEntriesOnlyValidOnesKept() {
        val json = """
            [
              {"id":"GOOD","endpoint":"https://api.example.com/a","modelName":"m1"},
              {"id":"BAD"},
              {"id":"GOOD2","endpoint":"https://api.example.com/b","modelName":"m2"}
            ]
        """.trimIndent()

        val result = CustomModelConfig.parse(json)
        assertEquals(2, result.size)
        assertEquals(listOf("GOOD", "GOOD2"), result.map { it.id })
    }
}
