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
import com.google.ai.sample.network.AiCallCancellationHandle
import com.google.ai.sample.network.MistralRequestCoordinator
import kotlinx.coroutines.CancellationException
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
data class ServiceMistralImageContent(@SerialName("image_url") val imageUrl: String) : ServiceMistralContent()

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

internal suspend fun callMistralApi(
    modelName: String,
    apiKey: String,
    chatHistory: List<Content>,
    inputContent: Content,
    availableApiKeys: List<String> = listOf(apiKey),
    cancellationHandle: AiCallCancellationHandle? = null
): Pair<String?, String?> {
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
                            ServiceMistralImageContent(imageUrl = "data:image/jpeg;base64,${com.google.ai.sample.util.ImageUtils.bitmapToBase64(part.image)}")
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
            model = currentModelOption?.let { com.google.ai.sample.util.ModelIdentifierOverrides.resolve(it) } ?: modelName,
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

        val keysForCoordinator = availableApiKeys.filter { it.isNotBlank() }.distinct().ifEmpty { listOf(apiKey) }
        val minIntervalMs = if (
            modelName == com.google.ai.sample.ModelOption.MISTRAL_MEDIUM_3_1.modelName ||
            modelName == com.google.ai.sample.ModelOption.MISTRAL_MEDIUM_3_5.modelName
        ) {
            com.google.ai.sample.util.OperationalTuningConfig.current().mistralMinIntervalMsFastModels
        } else {
            com.google.ai.sample.util.OperationalTuningConfig.current().mistralMinIntervalMsDefault
        }
        val maxAttempts = if (
            modelName == com.google.ai.sample.ModelOption.MISTRAL_LARGE_3.modelName ||
            modelName == com.google.ai.sample.ModelOption.MISTRAL_MEDIUM_3_1.modelName ||
            modelName == com.google.ai.sample.ModelOption.MISTRAL_MEDIUM_3_5.modelName
        ) {
            3
        } else {
            maxOf(4, keysForCoordinator.size * 3)
        }
        try {
            val coordinated = MistralRequestCoordinator.execute(
                apiKeys = keysForCoordinator,
                maxAttempts = maxAttempts,
                minIntervalMs = minIntervalMs,
                shouldCancel = { cancellationHandle?.isCancellationRequested == true }
            ) { key ->
                val call = client.newCall(
                    request.newBuilder()
                        .header("Authorization", "Bearer $key")
                        .build()
                )
                cancellationHandle?.register(call)
                try {
                    call.execute()
                } catch (e: IOException) {
                    if (call.isCanceled() || cancellationHandle?.isCancellationRequested == true) {
                        throw CancellationException("Mistral API call cancelled by user").also { it.initCause(e) }
                    }
                    throw e
                }
            }

            coordinated.response.use { response ->
                val responseBody = response.body?.string()
                if (cancellationHandle?.isCancellationRequested == true) {
                    throw CancellationException("Mistral API call cancelled by user")
                }
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
        } finally {
            cancellationHandle?.clearCurrentCall()
        }
    } catch (e: CancellationException) {
        if (cancellationHandle?.isCancellationRequested == true) {
            Log.d("ScreenCaptureService", "Mistral API call cancelled by user", e)
        } else {
            throw e
        }
    } catch (e: IOException) {
        if (cancellationHandle?.isCancellationRequested == true) {
            Log.d("ScreenCaptureService", "Mistral API network call cancelled by user", e)
        } else {
            errorMessage = e.localizedMessage ?: "Mistral API network call failed"
            Log.e("ScreenCaptureService", "Mistral API network failure", e)
        }
    } catch (e: SerializationException) {
        errorMessage = e.localizedMessage ?: "Mistral API response parse failed"
        Log.e("ScreenCaptureService", "Mistral API parse failure", e)
    } catch (e: IllegalStateException) {
        errorMessage = e.localizedMessage ?: "Mistral API call failed"
        Log.e("ScreenCaptureService", "Mistral API state failure", e)
    }

    return Pair(responseText, errorMessage)
}

internal suspend fun callPuterApi(modelName: String, apiKey: String, chatHistory: List<Content>, inputContent: Content, cancellationHandle: AiCallCancellationHandle? = null): Pair<String?, String?> {
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

        // Set max_tokens to 65536 for Qwen models to comply with Puter API requirements
        val maxTokens = if (modelName.contains("qwen", ignoreCase = true)) {
            65535
        } else {
            null
        }

        val requestBody = com.google.ai.sample.network.PuterRequest(
            model = currentModelOption?.let { com.google.ai.sample.util.ModelIdentifierOverrides.resolve(it) } ?: modelName,
            messages = apiMessages,
            max_tokens = maxTokens
        )

        responseText = com.google.ai.sample.network.PuterApiClient.call(apiKey, requestBody, cancellationHandle)
        
    } catch (e: CancellationException) {
        if (cancellationHandle?.isCancellationRequested == true) {
            Log.d("ScreenCaptureService", "Puter API call cancelled by user", e)
        } else {
            throw e
        }
    } catch (e: IOException) {
        if (cancellationHandle?.isCancellationRequested == true) {
            Log.d("ScreenCaptureService", "Puter API network call cancelled by user", e)
        } else {
            errorMessage = e.localizedMessage ?: "Puter API network call failed"
            Log.e("ScreenCaptureService", "Puter API network failure", e)
        }
    } catch (e: IllegalStateException) {
        errorMessage = e.localizedMessage ?: "Puter API call failed"
        Log.e("ScreenCaptureService", "Puter API state failure", e)
    }

    return Pair(responseText, errorMessage)
}


@Serializable
data class ServiceGroqRequest(
    val model: String,
    val messages: List<ServiceGroqMessage>,
    val max_tokens: Int = 4096,
    val temperature: Double = 0.7,
    val top_p: Double = 1.0,
    val stream: Boolean = false
)

@Serializable
data class ServiceGroqMessage(
    val role: String,
    val content: List<ServiceGroqContent>
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
sealed class ServiceGroqContent

@Serializable
@SerialName("text")
data class ServiceGroqTextContent(@SerialName("text") val text: String) : ServiceGroqContent()

@Serializable
@SerialName("image_url")
data class ServiceGroqImageContent(@SerialName("image_url") val imageUrl: ServiceGroqImageUrl) : ServiceGroqContent()

@Serializable
data class ServiceGroqImageUrl(val url: String)

internal suspend fun callGroqApi(modelName: String, apiKey: String, chatHistory: List<Content>, inputContent: Content, cancellationHandle: AiCallCancellationHandle? = null): Pair<String?, String?> {
    var responseText: String? = null
    var errorMessage: String? = null

    val currentModelOption = com.google.ai.sample.ModelOption.values().find { it.modelName == modelName }
    val supportsScreenshot = currentModelOption?.supportsScreenshot ?: true

    try {
        val apiMessages = mutableListOf<ServiceGroqMessage>()
        (chatHistory + inputContent).forEach { content ->
            val parts = content.parts.mapNotNull { part ->
                when (part) {
                    is TextPart -> if (part.text.isNotBlank()) ServiceGroqTextContent(text = part.text) else null
                    is ImagePart -> {
                        if (supportsScreenshot) {
                            ServiceGroqImageContent(
                                imageUrl = ServiceGroqImageUrl(
                                    url = "data:image/jpeg;base64,${com.google.ai.sample.util.ImageUtils.bitmapToBase64(part.image)}"
                                )
                            )
                        } else {
                            null
                        }
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
                apiMessages.add(ServiceGroqMessage(role = role, content = parts))
            }
        }

        val requestBody = ServiceGroqRequest(
            model = currentModelOption?.let { com.google.ai.sample.util.ModelIdentifierOverrides.resolve(it) } ?: modelName,
            messages = apiMessages
        )
        val json = Json {
            ignoreUnknownKeys = true
            serializersModule = SerializersModule {
                polymorphic(ServiceGroqContent::class) {
                    subclass(ServiceGroqTextContent::class)
                    subclass(ServiceGroqImageContent::class)
                }
            }
        }
        val mediaType = "application/json".toMediaType()
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .post(json.encodeToString(ServiceGroqRequest.serializer(), requestBody).toRequestBody(mediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        val call = client.newCall(request)
        cancellationHandle?.register(call)
        try {
            val response = try {
                call.execute()
            } catch (e: IOException) {
                if (call.isCanceled() || cancellationHandle?.isCancellationRequested == true) {
                    throw CancellationException("Groq API call cancelled by user").also { it.initCause(e) }
                }
                throw e
            }
            if (cancellationHandle?.isCancellationRequested == true) {
                response.close()
                throw CancellationException("Groq API call cancelled by user")
            }
            response.use { response ->
                val responseBody = response.body?.string()
                if (cancellationHandle?.isCancellationRequested == true) {
                    throw CancellationException("Groq API call cancelled by user")
                }
                if (!response.isSuccessful) {
                    errorMessage = "Groq Error ${response.code}: $responseBody"
                } else if (!responseBody.isNullOrBlank()) {
                    val parsed = json.decodeFromString(ServiceMistralResponse.serializer(), responseBody)
                    responseText = parsed.choices.firstOrNull()?.message?.content ?: "No response from model"
                } else {
                    errorMessage = "Empty response body from Groq"
                }
            }
        } finally {
            cancellationHandle?.clearCurrentCall()
        }
    } catch (e: CancellationException) {
        if (cancellationHandle?.isCancellationRequested == true) {
            Log.d("ScreenCaptureService", "Groq API call cancelled by user", e)
        } else {
            throw e
        }
    } catch (e: Exception) {
        if (cancellationHandle?.isCancellationRequested == true && e is IOException) {
            Log.d("ScreenCaptureService", "Groq API network call cancelled by user", e)
        } else {
            errorMessage = e.localizedMessage ?: "Groq API call failed"
            Log.e("ScreenCaptureService", "Groq API failure", e)
        }
    }

    return Pair(responseText, errorMessage)
}
