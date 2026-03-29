package com.google.ai.sample

import android.content.Intent
import android.net.Uri

internal object MainActivityScreenshotIntents {
    fun extractScreenInfo(intent: Intent): String? {
        return intent.getStringExtra(MainActivity.EXTRA_SCREEN_INFO)
    }

    fun extractScreenshotUri(intent: Intent): Uri? {
        val uriString = intent.getStringExtra(MainActivity.EXTRA_SCREENSHOT_URI)
        return uriString?.let(Uri::parse)
    }
}
