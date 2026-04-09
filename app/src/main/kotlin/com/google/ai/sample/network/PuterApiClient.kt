package com.google.ai.sample.network

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

// unified DTOs
@Serializable
data class PuterRequest(
    val model: String,
    val messages: List<PuterMessage>,
    val temperature: Double = 0.7,
    val top_p: Double = 1.0,
    val max_tokens: Int = 4096,
    val stream: Boolean = false
)

@Serializable
data class PuterMessage(
    val role: String,
    val content: List<PuterContent>
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
sealed class PuterContent

@Serializable
@SerialName("text")
data class PuterTextContent(val text: String) : PuterContent()

@Serializable
@SerialName("image_url")
data class PuterImageContent(val image_url: PuterImageUrl) : PuterContent()

@Serializable
data class PuterImageUrl(val url: String)

@Serializable
data class PuterResponse(
    val id: String = "",
    val `object`: String = "",
    val created: Long = 0,
    val model: String = "",
    val choices: List<PuterChoice> = emptyList()
)

@Serializable
data class PuterChoice(
    val index: Int,
    val message: PuterResponseMessage,
    val finish_reason: String? = null
)

@Serializable
data class PuterResponseMessage(
    val role: String,
    val content: String? = ""
)

object PuterApiClient {
    val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(100, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val jsonConfig = Json {
        ignoreUnknownKeys = true
    }

    fun bitmapToBase64DataUri(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    suspend fun call(apiKey: String, requestBody: PuterRequest): String {
        val mediaType = "application/json".toMediaType()
        val jsonBody = jsonConfig.encodeToString(PuterRequest.serializer(), requestBody)

        val request = Request.Builder()
            .url("https://api.puter.com/puterai/openai/v1/chat/completions")
            .post(jsonBody.toRequestBody(mediaType))
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        return client.newCall(request).execute().use { response ->
            val responseBodyString = response.body?.string()
            if (!response.isSuccessful) {
                throw IOException("Puter API Error (${response.code}): $responseBodyString")
            }
            if (responseBodyString == null) {
                throw IOException("Empty response body from Puter")
            }
            
            val puterResponse = jsonConfig.decodeFromString(PuterResponse.serializer(), responseBodyString)
            puterResponse.choices.firstOrNull()?.message?.content ?: throw IOException("No response from model")
        }
    }
}
