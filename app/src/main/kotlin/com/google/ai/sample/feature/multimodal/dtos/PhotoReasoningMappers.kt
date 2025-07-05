package com.google.ai.sample.feature.multimodal.dtos

import android.content.Context // Required for SDK ImagePart
import android.graphics.Bitmap // Required for SDK ImagePart
import com.google.ai.sample.util.ImageUtils // Our Base64<->Bitmap utils
import com.google.ai.client.generativeai.type.Content // SDK Content
import com.google.ai.client.generativeai.type.Part // SDK Part
import com.google.ai.client.generativeai.type.TextPart // SDK TextPart
import com.google.ai.client.generativeai.type.ImagePart // SDK ImagePart
import com.google.ai.client.generativeai.type.BlobPart // New SDK Part Type
import com.google.ai.client.generativeai.type.FunctionCallPart // New SDK Part Type
import com.google.ai.client.generativeai.type.FunctionResponsePart // New SDK Part Type
import com.google.ai.client.generativeai.type.content // SDK content builder
import org.json.JSONObject // For FunctionResponsePart
import android.util.Log // For logging errors

// --- SDK to DTO Mappers ---

fun Part.toDto(context: Context): PartDto { // Added context parameter
    return when (this) {
        is TextPart -> TextPartDto(text = this.text)
        is ImagePart -> {
            // Save the Bitmap to a temp file and get its path
            val filePath = ImageUtils.saveBitmapToTempFile(context, this.image)
            // Handle case where filePath might be null (saving failed)
            filePath?.let { ImagePartDto(imageFilePath = it) }
                ?: throw RuntimeException("Failed to save bitmap to temporary file for ImagePart.") // Or handle more gracefully
        }
        is BlobPart -> BlobPartDto(mimeType = this.mimeType, data = this.blob)
        is FunctionCallPart -> FunctionCallPartDto(name = this.name, args = this.args)
        is FunctionResponsePart -> {
            // Assuming this.response is org.json.JSONObject
            val responseJsonString = this.response.toString() // Convert JSONObject to String
            FunctionResponsePartDto(name = this.name, responseJson = responseJsonString)
        }
        else -> throw IllegalArgumentException("Unsupported SDK Part type for DTO conversion: ${this.javaClass.name}")
    }
}

fun Content.toDto(context: Context): ContentDto { // Added context parameter
    return ContentDto(
        role = this.role,
        parts = this.parts.map { it.toDto(context) } // Pass context to Part.toDto()
    )
}

// --- DTO to SDK Mappers ---

fun PartDto.toSdk(): Part { // No context needed here as path is absolute
    return when (this) {
        is TextPartDto -> TextPart(text = this.text)
        is ImagePartDto -> {
            val bitmap: Bitmap? = ImageUtils.loadBitmapFromFile(this.imageFilePath)
            bitmap?.let { ImagePart(it) }
                ?: throw IllegalArgumentException("Failed to load Bitmap from file path: ${this.imageFilePath}, or file was invalid.")
        }
        is BlobPartDto -> BlobPart(mimeType = this.mimeType, blob = this.data)
        is FunctionCallPartDto -> FunctionCallPart(name = this.name, args = this.args as Map<String, String?>?)
        is FunctionResponsePartDto -> {
            // Convert responseJson String back to org.json.JSONObject
            val jsonObject = try {
                JSONObject(this.responseJson)
            } catch (e: org.json.JSONException) {
                // Log the error and decide on fallback.
                // For now, throw an exception as the data is likely corrupted or not what's expected.
                Log.e("PhotoReasoningMappers", "Error parsing responseJson for FunctionResponsePartDto: ${e.message}")
                throw IllegalArgumentException("Invalid JSON string for FunctionResponsePart response: ${this.responseJson}", e)
            }
            FunctionResponsePart(name = this.name, response = jsonObject)
        }
        // else -> throw IllegalArgumentException("Unsupported PartDto type for SDK conversion: ${this.javaClass.name}") // Retain if other PartDto types are not expected
    }
}

fun ContentDto.toSdk(): Content {
    // The SDK's `content` builder is convenient here.
    // It takes a role (optional) and a block to define parts.
    val sdkParts = this.parts.map { it.toSdk() }
    return content(this.role) {
        sdkParts.forEach { sdkPart ->
            when (sdkPart) {
                is TextPart -> text(sdkPart.text)
                is ImagePart -> image(sdkPart.image) // sdkPart.image is the Bitmap
                // Add other SDK Part types here if they become relevant
                else -> throw IllegalArgumentException("Unsupported SDK Part type during ContentDto.toSdk() parts mapping: ${sdkPart.javaClass.name}")
            }
        }
    }
}
