package com.google.ai.sample.feature.multimodal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.ai.sample.GenerativeAiViewModelFactory
import com.google.ai.sample.ModelOption
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Custom download manager for offline LiteRT models.
 * Uses HttpURLConnection with Range-Request support for resume capability.
 * Point 18: Includes Android notification for download progress.
 */
object ModelDownloadManager {
    private const val TAG = "ModelDownloadManager"
    private const val TEMP_SUFFIX = ".downloading"
    private const val BUFFER_SIZE = 8192
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY_MS = 3000L
    private const val PROGRESS_UPDATE_INTERVAL_MS = 500L
    
    // Notification constants
    private const val DOWNLOAD_CHANNEL_ID = "model_download_channel"
    private const val DOWNLOAD_NOTIFICATION_ID = 3001

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(
            val progress: Float,           // 0.0 - 1.0
            val bytesDownloaded: Long,
            val totalBytes: Long
        ) : DownloadState()
        object Completed : DownloadState()
        data class Error(val message: String) : DownloadState()
        data class Paused(
            val bytesDownloaded: Long,
            val totalBytes: Long
        ) : DownloadState()
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var downloadJob: Job? = null
    private var isPaused = false

    private data class DownloadTarget(
        val finalFile: File,
        val tempFile: File,
        val url: String,
        val label: String
    )

    fun isModelDownloaded(context: Context, model: ModelOption = GenerativeAiViewModelFactory.getCurrentModel()): Boolean {
        val modelFile = getModelFile(context, model)
        return modelFile != null && modelFile.exists() && modelFile.length() > 0
    }

    fun getModelFile(context: Context, model: ModelOption = GenerativeAiViewModelFactory.getCurrentModel()): File? {
        val modelFilename = resolveInstalledModelFilename(context, model) ?: model.offlineModelFilename ?: return null
        val externalFilesDir = context.getExternalFilesDir(null)
        return if (externalFilesDir != null) {
            File(externalFilesDir, modelFilename)
        } else {
            Log.e(TAG, "External files directory is not available.")
            null
        }
    }

    private fun getRequiredFiles(context: Context, model: ModelOption): List<File> {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return emptyList()
        val activeModelFilename = resolveInstalledModelFilename(context, model)
        val requiredNames = if (model == ModelOption.QWEN3_5_4B_OFFLINE && activeModelFilename == "model_quantized.litertlm") {
            listOf("model_quantized.litertlm", "sentencepiece.model")
        } else if (model.offlineRequiredFilenames.isNotEmpty()) {
            model.offlineRequiredFilenames
        } else {
            listOfNotNull(model.offlineModelFilename)
        }
        return requiredNames.map { File(externalFilesDir, it) }
    }

    private fun resolveInstalledModelFilename(context: Context, model: ModelOption): String? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        val candidates = listOfNotNull(model.offlineModelFilename) + model.offlineAlternateModelFilenames
        return candidates.firstOrNull { name ->
            val f = File(externalFilesDir, name)
            f.exists() && f.length() > 0
        }
    }

    fun getMissingRequiredFiles(context: Context, model: ModelOption): List<String> {
        val requiredFiles = getRequiredFiles(context, model)
        return requiredFiles.filter { !it.exists() || it.length() <= 0 }.map { it.name }
    }
    
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                "Model Download",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of model downloads"
                setShowBadge(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun showDownloadNotification(context: Context, progress: Float, bytesDownloaded: Long, totalBytes: Long) {
        createNotificationChannel(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val progressPercent = (progress * 100).toInt()
        val notification = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Downloading Model")
            .setContentText("${formatBytes(bytesDownloaded)} / ${if (totalBytes > 0) formatBytes(totalBytes) else "?"} ($progressPercent%)")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, progressPercent, totalBytes <= 0)
            .setOnlyAlertOnce(true)
            .build()
        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, notification)
    }
    
    private fun showDownloadCompleteNotification(context: Context) {
        createNotificationChannel(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Model Download Complete")
            .setContentText("The model is ready to use.")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, notification)
    }
    
    private fun cancelDownloadNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(DOWNLOAD_NOTIFICATION_ID)
    }

    fun downloadModel(context: Context, model: ModelOption, url: String) {
        if (isModelDownloaded(context, model)) {
            Toast.makeText(context, "Model already downloaded.", Toast.LENGTH_SHORT).show()
            return
        }

        if (downloadJob?.isActive == true) {
            Log.d(TAG, "Download already in progress.")
            return
        }

        isPaused = false
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            downloadModelPackage(context, model, url)
        }
    }

    fun pauseDownload() {
        Log.d(TAG, "Pausing download...")
        isPaused = true
    }

    fun resumeDownload(context: Context, model: ModelOption, url: String) {
        if (downloadJob?.isActive == true) {
            Log.d(TAG, "Download is still active, not resuming.")
            return
        }

        isPaused = false
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            downloadModelPackage(context, model, url)
        }
    }

    fun cancelDownload(context: Context, model: ModelOption) {
        Log.d(TAG, "Cancelling download...")
        isPaused = false
        downloadJob?.cancel()
        downloadJob = null

        // Delete temp files for full package
        val externalFilesDir = context.getExternalFilesDir(null)
        if (externalFilesDir != null) {
            val targets = buildDownloadTargets(context, model, model.downloadUrl ?: "")
            targets.forEach { target ->
                if (target.tempFile.exists()) {
                    target.tempFile.delete()
                }
            }
            Log.d(TAG, "Temporary package files deleted.")
        }

        _downloadState.value = DownloadState.Idle
        cancelDownloadNotification(context)
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, "Download cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun downloadModelPackage(context: Context, model: ModelOption, primaryUrl: String) {
        val targets = buildDownloadTargets(context, model, primaryUrl)
        if (targets.isEmpty()) {
            _downloadState.value = DownloadState.Error("Storage not available.")
            return
        }

        for ((index, target) in targets.withIndex()) {
            if (!coroutineContext.isActive) return
            Log.i(TAG, "Downloading package file ${index + 1}/${targets.size}: ${target.label}")
            val error = downloadSingleFileWithResume(context, target, index, targets.size)
            if (error != null) {
                _downloadState.value = DownloadState.Error(error)
                cancelDownloadNotification(context)
                return
            }
        }

        _downloadState.value = DownloadState.Completed
        showDownloadCompleteNotification(context)
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Model download complete!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildDownloadTargets(context: Context, model: ModelOption, primaryUrl: String): List<DownloadTarget> {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return emptyList()
        val primaryFilename = model.offlineModelFilename ?: return emptyList()
        val urls = listOf(primaryUrl) + model.additionalDownloadUrls
        val filenames = urls.mapIndexedNotNull { idx, url ->
            if (idx == 0) primaryFilename else filenameFromUrl(url)
        }
        if (urls.size != filenames.size) {
            Log.e(TAG, "Could not resolve filename for at least one download URL.")
            return emptyList()
        }
        return urls.zip(filenames).map { (url, filename) ->
            val finalFile = File(externalFilesDir, filename)
            DownloadTarget(
                finalFile = finalFile,
                tempFile = File(externalFilesDir, "$filename$TEMP_SUFFIX"),
                url = url,
                label = filename
            )
        }
    }

    private fun filenameFromUrl(url: String): String? {
        val clean = url.substringBefore('?')
        val slash = clean.lastIndexOf('/')
        return if (slash >= 0 && slash + 1 < clean.length) clean.substring(slash + 1) else null
    }

    private suspend fun downloadSingleFileWithResume(
        context: Context,
        target: DownloadTarget,
        fileIndex: Int,
        fileCount: Int
    ): String? {
        val tempFile = target.tempFile
        val finalFile = target.finalFile
        val url = target.url

        if (finalFile.exists() && finalFile.length() > 0L) {
            Log.d(TAG, "Skipping already downloaded file: ${target.label}")
            return null
        }

        var retryCount = 0
        var bytesDownloaded = if (tempFile.exists()) tempFile.length() else 0L

        while (retryCount <= MAX_RETRIES) {
            if (!coroutineContext.isActive) return null // Coroutine was cancelled

            var connection: HttpURLConnection? = null
            try {
                Log.d(TAG, "Starting download (attempt ${retryCount + 1}), resuming from byte $bytesDownloaded")

                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "ScreenOperator/1.0")
                    if (bytesDownloaded > 0) {
                        setRequestProperty("Range", "bytes=$bytesDownloaded-")
                    }
                }

                val responseCode = connection.responseCode
                Log.d(TAG, "Response code: $responseCode")

                val totalBytes: Long
                val inputStream = connection.inputStream

                when (responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        // Server doesn't support range, restart from beginning
                        totalBytes = connection.contentLengthLong
                        bytesDownloaded = 0
                        if (tempFile.exists()) tempFile.delete()
                    }
                    HttpURLConnection.HTTP_PARTIAL -> {
                        // Server supports range, resume
                        val contentRange = connection.getHeaderField("Content-Range")
                        totalBytes = if (contentRange != null && contentRange.contains("/")) {
                            contentRange.substringAfter("/").toLongOrNull() ?: -1L
                        } else {
                            bytesDownloaded + connection.contentLengthLong
                        }
                    }
                    else -> {
                        return "Server error for ${target.label}: $responseCode"
                    }
                }

                Log.d(TAG, "Total bytes: $totalBytes, already downloaded: $bytesDownloaded")
                _downloadState.value = DownloadState.Downloading(
                    progress = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f,
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = totalBytes
                )

                val fos = FileOutputStream(tempFile, bytesDownloaded > 0) // append if resuming
                val buffer = ByteArray(BUFFER_SIZE)
                var lastProgressUpdate = System.currentTimeMillis()

                inputStream.use { input ->
                    fos.use { output ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (!coroutineContext.isActive) {
                                Log.d(TAG, "Download cancelled during read.")
                                cancelDownloadNotification(context)
                                return null
                            }

                            if (isPaused) {
                                Log.d(TAG, "Download paused at $bytesDownloaded bytes.")
                                _downloadState.value = DownloadState.Paused(
                                    bytesDownloaded = bytesDownloaded,
                                    totalBytes = totalBytes
                                )
                                // Keep notification showing paused state
                                showDownloadNotification(context, bytesDownloaded.toFloat() / totalBytes, bytesDownloaded, totalBytes)
                                return null
                            }

                            output.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead

                            // Rate-limit progress updates
                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL_MS) {
                                lastProgressUpdate = now
                                val progress = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
                                val aggregateProgress = (fileIndex + progress) / fileCount.toFloat()
                                _downloadState.value = DownloadState.Downloading(
                                    progress = aggregateProgress,
                                    bytesDownloaded = bytesDownloaded,
                                    totalBytes = totalBytes
                                )
                                // Point 18: Update notification with progress
                                showDownloadNotification(context, aggregateProgress, bytesDownloaded, totalBytes)
                            }
                        }
                    }
                }

                // Download complete - rename temp to final
                if (tempFile.exists()) {
                    finalFile.delete()
                    if (tempFile.renameTo(finalFile)) {
                        Log.i(TAG, "Download complete! File: ${finalFile.absolutePath} (${finalFile.length()} bytes)")
                    } else {
                        return "Failed to save ${target.label}."
                    }
                }
                return null // Success, exit retry loop

            } catch (e: IOException) {
                Log.e(TAG, "Download error (attempt ${retryCount + 1}): ${e.message}")
                retryCount++
                if (retryCount > MAX_RETRIES) {
                    return "Download failed for ${target.label} after $MAX_RETRIES retries: ${e.message}"
                } else {
                    _downloadState.value = DownloadState.Downloading(
                        progress = fileIndex.toFloat() / fileCount.toFloat(),
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = -1
                    )
                    Log.d(TAG, "Retrying in ${RETRY_DELAY_MS}ms...")
                    delay(RETRY_DELAY_MS)
                }
            } finally {
                connection?.disconnect()
            }
        }

        return "Download failed for ${target.label}."
    }

    /**
     * Format bytes to human-readable string (e.g. "1.23 GB")
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.2f GB".format(bytes.toDouble() / 1_073_741_824)
            bytes >= 1_048_576 -> "%.1f MB".format(bytes.toDouble() / 1_048_576)
            bytes >= 1024 -> "%.0f KB".format(bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }
}
