package com.google.ai.sample.feature.multimodal

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.IOException

internal class PhotoReasoningOpenAiStreamParser(
    private val json: Json
) {
    /**
     * Liest einen OpenAI-kompatiblen SSE-Stream zeilenweise.
     * Ruft [onChunk] mit dem jeweils akkumulierten Text auf.
     * Gibt den vollständigen Text zurück.
     */
    suspend fun parse(
        body: okhttp3.ResponseBody,
        onChunk: suspend (accumulatedText: String) -> Unit
    ): String {
        val accumulated = StringBuilder()
        val reader = body.charStream().buffered()
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: break
                if (currentLine.startsWith("data: ")) {
                    val data = currentLine.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    if (data.isEmpty()) continue
                    try {
                        val chunk = json.decodeFromString<OpenAIStreamChunk>(data)
                        val delta = chunk.choices.firstOrNull()?.delta?.content
                        if (!delta.isNullOrEmpty()) {
                            accumulated.append(delta)
                            onChunk(accumulated.toString())
                        }
                    } catch (_: SerializationException) {
                        // Fehlerhafte Chunk überspringen
                    }
                }
            }
        } finally {
            reader.close()
        }
        return accumulated.toString()
    }
}

/** Thrown when Mistral returns HTTP 429 (rate limit). */
internal class MistralRateLimitException(message: String) : IOException(message)

@Serializable
internal data class OpenAIStreamChunk(
    val choices: List<OpenAIStreamChoice>
)

@Serializable
internal data class OpenAIStreamChoice(
    val delta: OpenAIStreamDelta
)

@Serializable
internal data class OpenAIStreamDelta(
    val content: String? = null
)
