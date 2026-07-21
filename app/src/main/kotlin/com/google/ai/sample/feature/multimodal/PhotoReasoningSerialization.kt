package com.google.ai.sample.feature.multimodal

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

internal object PhotoReasoningSerialization {
    fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
