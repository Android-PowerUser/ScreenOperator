package com.google.ai.sample

import android.content.Context
import android.util.Log
import android.widget.Toast

internal object MainActivityStatusNotifier {
    fun showStatusMessage(context: Context, tag: String, message: String, isError: Boolean) {
        Toast.makeText(context, message, if (isError) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        if (isError) {
            Log.e(tag, "updateStatusMessage (Error): $message")
        } else {
            Log.d(tag, "updateStatusMessage (Info): $message")
        }
    }
}
