package com.google.ai.sample.feature.multimodal

import android.content.Context
import android.util.Log
import android.widget.Toast
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
 * Custom download manager for the Gemma 3n model.
 * Uses HttpURLConnection with Range-Request support for resume capability.
 */
object ModelDownloadManager {
    private const val TAG = "ModelDownloadManager"
    const val MODEL_FILENAME = "gemma-3n-e4b-it-int4.litertlm"
    private const val TEMP_SUFFIX = ".downloading"
    private const val BUFFER_SIZE = 8192
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY_MS = 3000L
    private const val PROGRESS_UPDATE_INTERVAL_MS = 500L

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

    fun isModelDownloaded(context: Context): Boolean {
        val file = getModelFile(context)
        return file != null && file.exists() && file.length() > 0
    }

    fun getModelFile(context: Context): File? {
        val externalFilesDir = context.getExternalFilesDir(null)
        return if (externalFilesDir != null) {
            File(externalFilesDir, MODEL_FILENAME)
        } else {
            Log.e(TAG, "External files directory is not available.")
            null
        }
    }

    private fun getTempFile(context: Context): File? {
        val externalFilesDir = context.getExternalFilesDir(null)
        return if (externalFilesDir != null) {
            File(externalFilesDir, MODEL_FILENAME + TEMP_SUFFIX)
        } else {
            null
        }
    }

    fun downloadModel(context: Context, url: String) {
        if (isModelDownloaded(context)) {
            Toast.makeText(context, "Model already downloaded.", Toast.LENGTH_SHORT).show()
            return
        }

        if (downloadJob?.isActive == true) {
            Log.d(TAG, "Download already in progress.")
            return
        }

        isPaused = false
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            downloadWithResume(context, url)
        }
    }

    fun pauseDownload() {
        Log.d(TAG, "Pausing download...")
        isPaused = true
    }

    fun resumeDownload(context: Context, url: String) {
        if (downloadJob?.isActive == true) {
            Log.d(TAG, "Download is still active, not resuming.")
            return
        }

        isPaused = false
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            downloadWithResume(context, url)
        }
    }

    fun cancelDownload(context: Context) {
        Log.d(TAG, "Cancelling download...")
        isPaused = false
        downloadJob?.cancel()
        downloadJob = null

        // Delete temp file
        val tempFile = getTempFile(context)
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete()
            Log.d(TAG, "Temp file deleted.")
        }

        _downloadState.value = DownloadState.Idle
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, "Download cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun downloadWithResume(context: Context, url: String) {
        val tempFile = getTempFile(context) ?: run {
            _downloadState.value = DownloadState.Error("Storage not available.")
            return
        }
        val finalFile = getModelFile(context) ?: run {
            _downloadState.value = DownloadState.Error("Storage not available.")
            return
        }

        var retryCount = 0
        var bytesDownloaded = if (tempFile.exists()) tempFile.length() else 0L

        while (retryCount <= MAX_RETRIES) {
            if (!coroutineContext.isActive) return // Coroutine was cancelled

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
                        _downloadState.value = DownloadState.Error("Server error: $responseCode")
                        return
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
                                return
                            }

                            if (isPaused) {
                                Log.d(TAG, "Download paused at $bytesDownloaded bytes.")
                                _downloadState.value = DownloadState.Paused(
                                    bytesDownloaded = bytesDownloaded,
                                    totalBytes = totalBytes
                                )
                                return
                            }

                            output.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead

                            // Rate-limit progress updates
                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL_MS) {
                                lastProgressUpdate = now
                                _downloadState.value = DownloadState.Downloading(
                                    progress = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f,
                                    bytesDownloaded = bytesDownloaded,
                                    totalBytes = totalBytes
                                )
                            }
                        }
                    }
                }

                // Download complete - rename temp to final
                if (tempFile.exists()) {
                    finalFile.delete()
                    if (tempFile.renameTo(finalFile)) {
                        Log.i(TAG, "Download complete! File: ${finalFile.absolutePath} (${finalFile.length()} bytes)")
                        _downloadState.value = DownloadState.Completed
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Model download complete!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        _downloadState.value = DownloadState.Error("Failed to save model file.")
                    }
                }
                return // Success, exit retry loop

            } catch (e: IOException) {
                Log.e(TAG, "Download error (attempt ${retryCount + 1}): ${e.message}")
                retryCount++
                if (retryCount > MAX_RETRIES) {
                    _downloadState.value = DownloadState.Error("Download failed after $MAX_RETRIES retries: ${e.message}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    _downloadState.value = DownloadState.Downloading(
                        progress = if (bytesDownloaded > 0) 0f else 0f,
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
