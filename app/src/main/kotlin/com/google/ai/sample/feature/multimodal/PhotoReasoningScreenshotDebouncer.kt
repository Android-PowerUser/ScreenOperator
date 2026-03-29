package com.google.ai.sample.feature.multimodal

import android.net.Uri

internal class PhotoReasoningScreenshotDebouncer(
    private val debounceWindowMs: Long = 2000L
) {
    private var lastProcessedScreenshotUri: Uri? = null
    private var lastProcessedScreenshotTimeMs: Long = 0L

    fun shouldProcess(screenshotUri: Uri, nowMs: Long): Boolean {
        val isDuplicateWithinWindow = screenshotUri == lastProcessedScreenshotUri &&
            (nowMs - lastProcessedScreenshotTimeMs) < debounceWindowMs
        if (isDuplicateWithinWindow) {
            return false
        }

        lastProcessedScreenshotUri = screenshotUri
        lastProcessedScreenshotTimeMs = nowMs
        return true
    }
}
