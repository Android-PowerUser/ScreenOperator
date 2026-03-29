package com.google.ai.sample

import android.content.Context
import android.content.Intent

internal object MainActivityMediaProjectionIntents {
    fun startCapture(
        context: Context,
        resultCode: Int,
        resultData: Intent,
        takeScreenshotOnStart: Boolean
    ): Intent {
        return Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START_CAPTURE
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, resultData)
            putExtra(ScreenCaptureService.EXTRA_TAKE_SCREENSHOT_ON_START, takeScreenshotOnStart)
        }
    }

    fun keepAliveForWebRtc(context: Context): Intent {
        return Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_KEEP_ALIVE_FOR_WEBRTC
        }
    }

    fun takeScreenshot(context: Context): Intent {
        return Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_TAKE_SCREENSHOT
        }
    }

    fun stopCapture(context: Context): Intent {
        return Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP_CAPTURE
        }
    }
}
