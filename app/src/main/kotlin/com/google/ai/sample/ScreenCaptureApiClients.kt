package com.google.ai.sample

import android.util.Log
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.ImagePart
import com.google.ai.client.generativeai.type.TextPart
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

// Data classes for Mistral API in Service
@Serializable
data class ServiceMistralRequest(
    val model: String,
    val messages: List<ServiceMistralMessage>,
    val max_tokens: Int = 4096,
    val temperature: Double = 0.7,
    val top_p: Double = 1.0,
    val stream: Boolean = false
)

@Serializable
data class ServiceMistralMessage(
    val role: String,
    val content: List<ServiceMistralContent>
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
sealed class ServiceMistralContent

@Serializable
@SerialName("text")
data class ServiceMistralTextContent(@SerialName("text") val text: String) : ServiceMistralContent()

@Serializable
@SerialName("image_url")
data class ServiceMistralImageContent(@SerialName("image_url") val imageUrl: ServiceMistralImageUrl) : ServiceMistralContent()

@Serializable
data class ServiceMistralImageUrl(val url: String)

@Serializable
data class ServiceMistralResponse(
    val choices: List<ServiceMistralChoice>
)

@Serializable
data class ServiceMistralChoice(
    val message: ServiceMistralResponseMessage
)

@Serializable
data class ServiceMistralResponseMessage(
    val role: String,
    val content: String
)

internal suspend fun callMistralApi(modelName: String, apiKey: String, chatHistory: List<Content>, inputContent: Content): Pair<String?, String?> {
    var responseText: String? = null
    var errorMessage: String? = null

    val json = Json {
        serializersModule = SerializersModule {
            polymorphic(ServiceMistralContent::class) {
                subclass(ServiceMistralTextContent::class, ServiceMistralTextContent.serializer())
                subclass(ServiceMistralImageContent::class, ServiceMistralImageContent.serializer())
            }
        }
        ignoreUnknownKeys = true
    }

    val currentModelOption = com.google.ai.sample.ModelOption.values().find { it.modelName == modelName }
    val supportsScreenshot = currentModelOption?.supportsScreenshot ?: true

    try {
        val apiMessages = mutableListOf<ServiceMistralMessage>()

        // Combine history and input, but handle system role if needed
        (chatHistory + inputContent).forEach { content ->
            val parts = content.parts.mapNotNull { part ->
                when (part) {
                    is TextPart -> if (part.text.isNotBlank()) ServiceMistralTextContent(text = part.text) else null
                    is ImagePart -> {
                        if (supportsScreenshot) {
                            ServiceMistralImageContent(imageUrl = ServiceMistralImageUrl(url = "data:image/jpeg;base64,${com.google.ai.sample.util.ImageUtils.bitmapToBase64(part.image)}"))
                        } else null
                    }
                    else -> null
                }
            }
            if (parts.isNotEmpty()) {
                val role = when (content.role) {
                    "user" -> "user"
                    "system" -> "system"
                    else -> "assistant"
                }
                apiMessages.add(ServiceMistralMessage(role = role, content = parts))
            }
        }

        val requestBody = ServiceMistralRequest(
            model = modelName,
            messages = apiMessages
        )

        val client = OkHttpClient()
        val mediaType = "application/json".toMediaType()
        val jsonBody = json.encodeToString(ServiceMistralRequest.serializer(), requestBody)

        val request = Request.Builder()
            .url("https://api.mistral.ai/v1/chat/completions")
            .post(jsonBody.toRequestBody(mediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            if (!response.isSuccessful) {
                Log.e("ScreenCaptureService", "Mistral API Error ($response.code): $responseBody")
                errorMessage = "Mistral Error ${response.code}: $responseBody"
            } else {
                if (responseBody != null) {
                    val mistralResponse = json.decodeFromString(ServiceMistralResponse.serializer(), responseBody)
                    responseText = mistralResponse.choices.firstOrNull()?.message?.content ?: "No response from model"
                } else {
                    errorMessage = "Empty response body from Mistral"
                }
            }
        }
    } catch (e: IOException) {
        errorMessage = e.localizedMessage ?: "Mistral API network call failed"
        Log.e("ScreenCaptureService", "Mistral API network failure", e)
    } catch (e: SerializationException) {
        errorMessage = e.localizedMessage ?: "Mistral API response parse failed"
        Log.e("ScreenCaptureService", "Mistral API parse failure", e)
    } catch (e: IllegalStateException) {
        errorMessage = e.localizedMessage ?: "Mistral API call failed"
        Log.e("ScreenCaptureService", "Mistral API state failure", e)
    }

    return Pair(responseText, errorMessage)
}

internal suspend fun callPuterApi(modelName: String, apiKey: String, chatHistory: List<Content>, inputContent: Content): Pair<String?, String?> {
    var responseText: String? = null
    var errorMessage: String? = null
    
    val currentModelOption = com.google.ai.sample.ModelOption.values().find { it.modelName == modelName }
    val supportsScreenshot = currentModelOption?.supportsScreenshot ?: true

    try {
        val apiMessages = mutableListOf<com.google.ai.sample.network.PuterMessage>()

        // Combine history and input, but handle system role if needed
        (chatHistory + inputContent).forEach { content ->
            val parts = content.parts.mapNotNull { part ->
                when (part) {
                    is TextPart -> if (part.text.isNotBlank()) com.google.ai.sample.network.PuterTextContent(text = part.text) else null
                    is ImagePart -> {
                        if (supportsScreenshot) {
                            val base64Uri = com.google.ai.sample.network.PuterApiClient.bitmapToBase64DataUri(part.image)
                            com.google.ai.sample.network.PuterImageContent(image_url = com.google.ai.sample.network.PuterImageUrl(url = base64Uri))
                        } else null
                    }
                    else -> null
                }
            }
            if (parts.isNotEmpty()) {
                val role = when (content.role) {
                    "user" -> "user"
                    "system" -> "system"
                    else -> "assistant"
                }
                apiMessages.add(com.google.ai.sample.network.PuterMessage(role = role, content = parts))
            }
        }

        val requestBody = com.google.ai.sample.network.PuterRequest(
            model = modelName,
            messages = apiMessages
        )

        responseText = com.google.ai.sample.network.PuterApiClient.call(apiKey, requestBody)
        
    } catch (e: IOException) {
        errorMessage = e.localizedMessage ?: "Puter API network call failed"
        Log.e("ScreenCaptureService", "Puter API network failure", e)
    } catch (e: IllegalStateException) {
        errorMessage = e.localizedMessage ?: "Puter API call failed"
        Log.e("ScreenCaptureService", "Puter API state failure", e)
    }

    return Pair(responseText, errorMessage)
}
