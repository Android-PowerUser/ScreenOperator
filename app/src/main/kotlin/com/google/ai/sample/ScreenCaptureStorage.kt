package com.google.ai.sample

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class ScreenCaptureStorage(
    private val context: Context,
    private val loggerTag: String
) {
    fun saveScreenshot(
        bitmap: Bitmap,
        onSaved: (Uri) -> Unit,
        onSuccessMessage: (String) -> Unit,
        onErrorMessage: (String) -> Unit
    ) {
        try {
            val picturesDir = ensureScreenshotDirectory()
            val maxScreenshots = 100
            pruneOldScreenshots(picturesDir, maxScreenshots)

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(picturesDir, "screenshot_$timestamp.png")
            writeBitmapToPngFile(bitmap, file)

            Log.i(loggerTag, "Screenshot saved to: ${file.absolutePath}")
            onSuccessMessage("Screenshot saved to: Android/data/com.google.ai.sample/files/Pictures/Screenshots/")
            onSaved(Uri.fromFile(file))
        } catch (e: IOException) {
            Log.e(loggerTag, "Failed to save screenshot (I/O)", e)
            onErrorMessage("Failed to save screenshot: ${e.message}")
        } catch (e: SecurityException) {
            Log.e(loggerTag, "Failed to save screenshot (security)", e)
            onErrorMessage("Failed to save screenshot: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.e(loggerTag, "Failed to save screenshot", e)
            onErrorMessage("Failed to save screenshot: ${e.message}")
        }
    }

    private fun ensureScreenshotDirectory(): File {
        val picturesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Screenshots")
        if (!picturesDir.exists()) {
            picturesDir.mkdirs()
        }
        return picturesDir
    }

    private fun pruneOldScreenshots(picturesDir: File, maxScreenshots: Int) {
        val screenshotFiles = picturesDir.listFiles { _, name ->
            name.startsWith("screenshot_") && name.endsWith(".png")
        }?.toMutableList() ?: mutableListOf()

        screenshotFiles.sortBy { it.name }

        val screenshotsToDelete = screenshotFiles.size - (maxScreenshots - 1)
        if (screenshotsToDelete <= 0) return

        Log.i(
            loggerTag,
            "Max screenshots reached. Current count: ${screenshotFiles.size}. Attempting to delete $screenshotsToDelete oldest screenshot(s)."
        )

        for (i in 0 until screenshotsToDelete) {
            if (i < screenshotFiles.size) {
                val oldestFile = screenshotFiles[i]
                if (oldestFile.delete()) {
                    Log.i(loggerTag, "Deleted oldest screenshot: ${oldestFile.absolutePath}")
                } else {
                    Log.e(loggerTag, "Failed to delete oldest screenshot: ${oldestFile.absolutePath}")
                }
            }
        }
    }

    private fun writeBitmapToPngFile(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
        }
    }
}
