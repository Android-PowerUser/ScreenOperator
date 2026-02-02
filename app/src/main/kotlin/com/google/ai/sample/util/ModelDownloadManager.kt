package com.google.ai.sample.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import java.io.File

object ModelDownloadManager {
    // Placeholder URL - in a real app this would be a valid link to the model file
    const val MODEL_URL = "https://storage.googleapis.com/mediapipe-models/llm/gemma-2b-it-gpu.bin"
    const val MODEL_FILE_NAME = "gemma-3n-e4b-it-offline.bin"
    const val MODEL_SIZE_BYTES = 4_700_000_000L // 4.7 GB as requested

    fun isModelDownloaded(context: Context): Boolean {
        val file = File(context.getExternalFilesDir(null), MODEL_FILE_NAME)
        return file.exists() && file.length() > 1_000_000 // Simple check if it's at least 1MB
    }

    fun getModelFile(context: Context): File {
        return File(context.getExternalFilesDir(null), MODEL_FILE_NAME)
    }

    fun getAvailableStorageGB(context: Context): Double {
        val path = context.getExternalFilesDir(null) ?: return 0.0
        val stat = StatFs(path.path)
        val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
        return bytesAvailable / (1024.0 * 1024.0 * 1024.0)
    }

    fun downloadModel(context: Context): Long {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("Downloading Gemma 3n E4B")
            .setDescription("Downloading offline AI model (4.7 GB)")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, MODEL_FILE_NAME)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        return downloadManager.enqueue(request)
    }
}
