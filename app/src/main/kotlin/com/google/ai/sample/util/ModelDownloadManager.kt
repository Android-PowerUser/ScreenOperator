package com.google.ai.sample.util

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request

object ModelDownloadManager {
    private const val TAG = "ModelDownloadManager"
    private const val MODEL_FILENAME = "gemma-3n-E4B-it-int4.task"
    private const val DOWNLOAD_URL = "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview/resolve/20250520/gemma-3n-E4B-it-int4.task?download=true"
    const val MODEL_SIZE_BYTES = 4_700_000_000L // 4.7 GB

    fun getModelFile(context: Context): File {
        return File(context.getExternalFilesDir(null), MODEL_FILENAME)
    }

    fun isModelDownloaded(context: Context): Boolean {
        val file = getModelFile(context)
        // More robust check: check existence and a minimum size threshold
        return file.exists() && file.length() > 4_000_000_000L
    }

    fun getAvailableExternalStorage(context: Context): Long {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return 0L
        val stat = StatFs(externalFilesDir.path)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    suspend fun downloadModel(context: Context, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val targetFile = getModelFile(context)
        val tempFile = File(context.getExternalFilesDir(null), "$MODEL_FILENAME.tmp")

        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val request = Request.Builder()
            .url(DOWNLOAD_URL)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Server returned ${response.code}: ${response.message}")
                    return@withContext false
                }

                val body = response.body ?: return@withContext false
                val fileLength = body.contentLength()
                val input = body.byteStream()
                val output = FileOutputStream(tempFile)

                val data = ByteArray(1024 * 128)
                var total: Long = 0
                var count: Int
                while (input.read(data).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        onProgress(total.toFloat() / fileLength)
                    }
                    output.write(data, 0, count)
                }

                output.flush()
                output.close()
                input.close()

                if (tempFile.renameTo(targetFile)) {
                    Log.i(TAG, "Model downloaded successfully to ${targetFile.absolutePath}")
                    true
                } else {
                    Log.e(TAG, "Failed to rename temp file to target file")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model", e)
            if (tempFile.exists()) tempFile.delete()
            false
        }
    }
}
