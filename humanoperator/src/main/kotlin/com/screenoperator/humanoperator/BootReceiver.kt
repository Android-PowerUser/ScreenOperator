package com.screenoperator.humanoperator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "onReceive: BOOT_COMPLETED received. Starting TaskListenerService...")
            val serviceIntent = Intent(context, TaskListenerService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "Service started successfully on boot.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start TaskListenerService on boot", e)
            }
        }
    }
}
