package com.google.ai.sample

import android.content.Context
import android.content.Intent
import android.os.Build

internal class MediaProjectionServiceStarter(private val context: Context) {
    fun start(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
