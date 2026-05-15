package com.google.ai.sample

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCaptureApiClientsTest {
    @Test
    fun serviceGroqRequest_serializesImageUrlAsObject() {
        val json = Json {
            serializersModule = SerializersModule {
                polymorphic(ServiceGroqContent::class) {
                    subclass(ServiceGroqTextContent::class)
                    subclass(ServiceGroqImageContent::class)
                }
            }
        }
        val request = ServiceGroqRequest(
            model = "meta-llama/llama-4-scout-17b-16e-instruct",
            messages = listOf(
                ServiceGroqMessage(
                    role = "user",
                    content = listOf(
                        ServiceGroqTextContent("look"),
                        ServiceGroqImageContent(ServiceGroqImageUrl("data:image/jpeg;base64,abc"))
                    )
                )
            )
        )

        val encoded = json.encodeToString(ServiceGroqRequest.serializer(), request)

        assertTrue(encoded.contains("\"type\":\"image_url\""))
        assertTrue(encoded.contains("\"image_url\":{\"url\":\"data:image/jpeg;base64,abc\"}"))
    }
}
