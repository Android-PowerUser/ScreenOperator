package com.google.ai.sample

import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.ai.sample.feature.multimodal.dtos.ContentDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

// Data classes for Vercel API
@Serializable
data class VercelRequest(
    val model: String,
    val messages: List<VercelMessage>,
    val stream: Boolean = true
)

@Serializable
data class VercelStreamChunk(
    val choices: List<VercelStreamChoice>
)

@Serializable
data class VercelStreamChoice(
    val delta: VercelStreamDelta
)

@Serializable
data class VercelStreamDelta(
    val content: String? = null
)

@Serializable
data class VercelMessage(
    val role: String,
    val content: String
)

internal suspend fun callVercelApi(
    context: android.content.Context,
    modelName: String,
    apiKey: String,
    chatHistory: List<ContentDto>,
    inputContent: ContentDto
): String {
    val messages = mutableListOf<VercelMessage>()
    
    // Add Chat History
    chatHistory.forEach { contentDto ->
        val role = if (contentDto.role == "user") "user" else "assistant"
        val text = contentDto.parts.filterIsInstance<com.google.ai.sample.feature.multimodal.dtos.TextPartDto>()
            .joinToString("\n") { it.text }
        if (text.isNotBlank()) messages.add(VercelMessage(role = role, content = text))
    }

    // Add current input
    val inputText = inputContent.parts.filterIsInstance<com.google.ai.sample.feature.multimodal.dtos.TextPartDto>()
        .joinToString("\n") { it.text }
    if (inputText.isNotBlank()) messages.add(VercelMessage(role = "user", content = inputText))

    val requestBodyJson = Json.encodeToString(VercelRequest.serializer(), VercelRequest(model = modelName, messages = messages, stream = true))
    val mediaType = "application/json".toMediaType()

    val httpRequest = Request.Builder()
        .url("https://v0-screen-operator-clon-pi.vercel.app/api/chat")
        .post(requestBodyJson.toRequestBody(mediaType))
        .addHeader("Content-Type", "application/json")
        .addHeader("x-api-key", apiKey)
        .build()

    val client = OkHttpClient()
    val response = client.newCall(httpRequest).execute()

    if (!response.isSuccessful) {
        val err = response.body?.string()
        response.close()
        throw IOException("Vercel API error ${response.code}: $err")
    }

    val body = response.body ?: throw IOException("Empty response from Vercel")
    val reader = body.charStream().buffered()
    val accumulated = StringBuilder()
    val sseJson = Json { ignoreUnknownKeys = true }

    try {
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val l = line ?: break
            if (l.startsWith("data: ")) {
                val data = l.removePrefix("data: ").trim()
                if (data == "[DONE]") break
                if (data.isEmpty()) continue
                
                try {
                    val chunk = sseJson.decodeFromString<VercelStreamChunk>(data)
                    val delta = chunk.choices.firstOrNull()?.delta?.content
                    if (!delta.isNullOrEmpty()) {
                        accumulated.append(delta)
                        // Broadcast update to ViewModel
                        val intent = Intent(ScreenCaptureService.ACTION_AI_STREAM_UPDATE).apply {
                            putExtra(ScreenCaptureService.EXTRA_AI_STREAM_CHUNK, accumulated.toString())
                        }
                        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                    }
                } catch (e: SerializationException) {
                    // Skip malformed chunks
                }
            }
        }
    } finally {
        reader.close()
        response.close()
    }

    return accumulated.toString()
}
