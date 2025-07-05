package com.google.ai.sample.feature.multimodal.dtos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Note: The Gemini SDK's Content object can have a nullable 'role'.
// Parts are non-empty.

@Serializable
data class ContentDto(
    val role: String? = null, // e.g., "user", "model", "function"
    val parts: List<PartDto>
)

@Serializable
sealed interface PartDto

@Serializable
@SerialName("text") // This helps ensure the type is clearly identified in JSON if needed, good practice for sealed types
data class TextPartDto(val text: String) : PartDto

@Serializable
@SerialName("image")
data class ImagePartDto(
    val imageFilePath: String // Path to a temporary file holding the image
) : PartDto

@Serializable
@SerialName("blob")
data class BlobPartDto(
    val mimeType: String,
    val data: ByteArray // kotlinx.serialization handles ByteArray directly
) : PartDto {
    // Custom equals/hashCode for ByteArray comparison if needed, though not strictly for serialization
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BlobPartDto
        if (mimeType != other.mimeType) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }
    override fun hashCode(): Int {
        var result = mimeType.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

@Serializable
@SerialName("functionCall")
data class FunctionCallPartDto(
    val name: String,
    val args: Map<String, String?>? // Changed to allow nullable String values in the map
) : PartDto

@Serializable
@SerialName("functionResponse")
data class FunctionResponsePartDto(
    val name: String,
    val responseJson: String // To store org.json.JSONObject.toString()
) : PartDto
