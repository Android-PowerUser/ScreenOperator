package com.google.ai.sample.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.util.Log

object BroadcastReceiverCompat {
    fun register(context: Context, receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    fun unregister(context: Context, receiver: BroadcastReceiver, receiverName: String, tag: String) {
        Log.d(tag, "Unregistering $receiverName.")
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Log.w(tag, "$receiverName was not registered or already unregistered.", e)
        }
    }
}
