package com.google.ai.sample.feature.multimodal

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.util.Log
import android.widget.Toast
import java.io.File

object ModelDownloadManager {
    private const val TAG = "ModelDownloadManager"
    const val MODEL_FILENAME = "gemma-3n-e4b-it-int4.litertlm"
    private var downloadId: Long = -1

    fun isModelDownloaded(context: Context): Boolean {
        val file = getModelFile(context)
        return file.exists() && file.length() > 0
    }

    fun getModelFile(context: Context): File {
        return File(context.getExternalFilesDir(null), MODEL_FILENAME)
    }

    fun downloadModel(context: Context, url: String) {
        if (isModelDownloaded(context)) {
            Toast.makeText(context, "Model already downloaded.", Toast.LENGTH_SHORT).show()
            return
        }

        val file = getModelFile(context)
        if (file.exists()) {
            file.delete() // Clean up partial or old file
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading Gemma Model")
            .setDescription("Downloading offline AI model (4.92 GB)...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, MODEL_FILENAME)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)

        Toast.makeText(context, "Download started. Check notifications.", Toast.LENGTH_LONG).show()
        Log.d(TAG, "Download started with ID: $downloadId")
    }

    fun cancelDownload(context: Context) {
        if (downloadId != -1L) {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.remove(downloadId)
            downloadId = -1
            Toast.makeText(context, "Download cancelled.", Toast.LENGTH_SHORT).show()
        }
    }
}
