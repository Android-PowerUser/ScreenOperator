package com.google.ai.sample

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

internal class ScreenCaptureNotificationFactory(
    private val context: Context,
    private val channelId: String
) {
    fun createAiOperationNotification(): Notification {
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle("Screen Operator")
            .setContentText("Processing AI request...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false)
            .build()
    }

    fun createNotification(): Notification {
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle("Screen Capture Active")
            .setContentText("Ready to take screenshots")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    fun createNotificationChannel(channelName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            serviceChannel.description = "Notifications for screen capture service"
            serviceChannel.setShowBadge(false)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}
