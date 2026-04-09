package com.google.ai.sample

internal object MainActivityScreenshotFlowDecider {
    enum class Action {
        TAKE_ADDITIONAL_SCREENSHOT,
        REQUEST_PERMISSION
    }

    fun decide(isScreenCaptureServiceRunning: Boolean): Action {
        return if (isScreenCaptureServiceRunning) {
            Action.TAKE_ADDITIONAL_SCREENSHOT
        } else {
            Action.REQUEST_PERMISSION
        }
    }
}
