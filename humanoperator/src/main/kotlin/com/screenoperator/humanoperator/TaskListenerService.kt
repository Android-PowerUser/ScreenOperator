package com.screenoperator.humanoperator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class TaskListenerService : Service(), SignalingClient.SignalingListener {

    companion object {
        private const val TAG = "TaskListenerService"
        private const val CHANNEL_ID = "task_listener_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private var signalingClient: SignalingClient? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Starting TaskListenerService")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createForegroundNotification())

        signalingClient = SignalingClient(this)
        signalingClient?.startListeningForTasks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Stopping TaskListenerService")
        signalingClient?.disconnect()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Task Listener",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the app running to listen for new tasks"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Human Operator Active")
            .setContentText("Listening for new tasks in the background")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onNewTask(taskId: String, text: String, supportId: String?) {
        Log.d(TAG, "onNewTask: Showing notification for task $taskId")
        try {
            // Intent to open MainActivity on click
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, "human_operator_tasks")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("New Task Available!")
                .setContentText(if (text.isNotBlank()) text.take(100) else "A new task is waiting")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            
            // Re-create the high priority channel if it doesn't exist
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "human_operator_tasks",
                    "Incoming Tasks",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for new tasks from Screen Operator"
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }

            NotificationManagerCompat.from(this).notify(taskId.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "No notification permission", e)
        }
    }

    override fun onTaskRemoved(taskId: String) {
        NotificationManagerCompat.from(this).cancel(taskId.hashCode())
    }

    override fun onClaimed(taskId: String) {}
    override fun onClaimFailed(reason: String) {}
    override fun onSDPOffer(sdp: String) {}
    override fun onICECandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {}
    override fun onPeerDisconnected() {}
    override fun onError(message: String) {}
}
