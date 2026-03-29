package com.google.ai.sample.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageUtils {
    private const val TAG = "ImageUtils"
    private const val TEMP_IMAGE_DIR = "image_parts"
    private const val TEMP_IMAGE_PREFIX = "temp_image_"
    private const val IMAGE_EXTENSION = ".png"
    private const val PNG_QUALITY = 100

    fun bitmapToBase64(bitmap: Bitmap, @Suppress("UNUSED_PARAMETER") quality: Int = 80): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    fun base64ToBitmap(base64String: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error decoding Base64 string to Bitmap: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error decoding Base64 string to Bitmap: ${e.message}")
            null
        }
    }

    fun saveBitmapToTempFile(context: Context, bitmap: Bitmap): String? {
        return try {
            val cacheDir = File(context.cacheDir, TEMP_IMAGE_DIR)
            cacheDir.mkdirs()

            val fileName = "$TEMP_IMAGE_PREFIX${System.currentTimeMillis()}$IMAGE_EXTENSION"
            val tempFile = File(cacheDir, fileName)

            FileOutputStream(tempFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, fos)
            }
            Log.d(TAG, "Bitmap saved to temp file: ${tempFile.absolutePath}")
            tempFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap to temp file: ${e.message}", e)
            null
        }
    }

    fun loadBitmapFromFile(filePath: String): Bitmap? {
        return try {
            if (!File(filePath).exists()) {
                Log.e(TAG, "File not found for loading bitmap: $filePath")
                return null
            }
            BitmapFactory.decodeFile(filePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from file: $filePath", e)
            null
        }
    }

    fun deleteFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    Log.d(TAG, "Successfully deleted file: $filePath")
                } else {
                    Log.w(TAG, "Failed to delete file: $filePath")
                }
                deleted
            } else {
                Log.w(TAG, "File not found for deletion: $filePath")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: $filePath", e)
            false
        }
    }
}
