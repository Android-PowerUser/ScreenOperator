package com.google.ai.sample.util

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloadManager {
    private const val TAG = "ModelDownloadManager"
    private const val MODEL_FILENAME = "gemma-3n-e4b-it-gpu.bin"
    private const val DOWNLOAD_URL = "https://www.kaggle.com/api/v1/models/google/gemma-3/mediapipe/gemma-3-4b-it-gpu/1/download"
    const val MODEL_SIZE_BYTES = 4_700_000_000L // 4.7 GB

    fun getModelFile(context: Context): File {
        return File(context.getExternalFilesDir(null), MODEL_FILENAME)
    }

    fun isModelDownloaded(context: Context): Boolean {
        val file = getModelFile(context)
        return file.exists() && file.length() >= MODEL_SIZE_BYTES * 0.9 // Basic check
    }

    fun getAvailableInternalStorage(): Long {
        val stat = StatFs(Environment.getDataDirectory().path)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    fun getAvailableExternalStorage(context: Context): Long {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return 0L
        val stat = StatFs(externalFilesDir.path)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    suspend fun downloadModel(context: Context, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val targetFile = getModelFile(context)
        val tempFile = File(context.getExternalFilesDir(null), "$MODEL_FILENAME.tmp")

        try {
            val url = URL(DOWNLOAD_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Server returned HTTP ${connection.responseCode}")
                return@withContext false
            }

            val fileLength = connection.contentLengthLong
            val input = connection.inputStream
            val output = FileOutputStream(tempFile)

            val data = ByteArray(1024 * 64)
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
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model", e)
            if (tempFile.exists()) tempFile.delete()
            false
        }
    }
}
