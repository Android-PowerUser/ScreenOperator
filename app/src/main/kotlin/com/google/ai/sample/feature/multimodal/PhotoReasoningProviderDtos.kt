package com.google.ai.sample.feature.multimodal

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

// Data classes for Cerebras API
@Serializable
internal data class CerebrasRequest(
    val model: String,
    val messages: List<CerebrasMessage>,
    val max_completion_tokens: Int = 1024,
    val temperature: Double = 0.2,
    val top_p: Double = 1.0,
    val stream: Boolean = false
)

@Serializable
internal data class CerebrasMessage(
    val role: String,
    val content: String
)

@Serializable
internal data class CerebrasResponse(
    val choices: List<CerebrasChoice>
)

@Serializable
internal data class CerebrasChoice(
    val message: CerebrasResponseMessage
)

@Serializable
internal data class CerebrasResponseMessage(
    val role: String,
    val content: String
)

// Data classes for Mistral API
@Serializable
internal data class MistralRequest(
    val model: String,
    val messages: List<MistralMessage>,
    val max_tokens: Int = 4096,
    val temperature: Double = 0.7,
    val top_p: Double = 1.0,
    val stream: Boolean = false
)

@Serializable
internal data class MistralMessage(
    val role: String,
    val content: List<MistralContent>
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
internal sealed class MistralContent

@Serializable
@kotlinx.serialization.SerialName("text")
internal data class MistralTextContent(val text: String) : MistralContent()

@Serializable
@kotlinx.serialization.SerialName("image_url")
internal data class MistralImageContent(
    @kotlinx.serialization.SerialName("image_url") val imageUrl: String
) : MistralContent()

@Serializable
internal data class MistralResponse(
    val choices: List<MistralChoice>
)

@Serializable
internal data class MistralChoice(
    val message: MistralResponseMessage
)

@Serializable
internal data class MistralResponseMessage(
    val role: String,
    val content: String
)
