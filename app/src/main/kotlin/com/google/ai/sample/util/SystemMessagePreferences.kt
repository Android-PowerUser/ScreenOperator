package com.google.ai.sample.util

import android.content.Context
import android.util.Log
import androidx.core.content.edit

/**
 * Utility class to manage system message persistence.
 *
 * The DEFAULT system message is intentionally NOT stored here – it lives in
 * index.html (DEFAULT_SYSTEM_MSG) so it can be updated via a web bundle change
 * without an app release.  When [loadSystemMessage] returns an empty string the
 * caller (WebViewBridge / JS) falls back to the HTML-defined default.
 */
object SystemMessagePreferences {
    private const val TAG = "SystemMessagePrefs"
    private const val PREFS_NAME = "system_message_prefs"
    private const val KEY_SYSTEM_MESSAGE = "system_message"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Save system message to SharedPreferences.
     */
    fun saveSystemMessage(context: Context, message: String) {
        try {
            Log.d(TAG, "Saving system message (length=${message.length})")
            prefs(context).edit { putString(KEY_SYSTEM_MESSAGE, message) }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving system message: ${e.message}", e)
        }
    }

    /**
     * Load system message from SharedPreferences.
     *
     * Returns the user-saved message, or an empty string when nothing has been
     * saved yet.  An empty string is the signal for the WebView layer to use
     * its own DEFAULT_SYSTEM_MSG constant (defined in index.html), keeping the
     * authoritative default in one place – the HTML bundle.
     */
    fun loadSystemMessage(context: Context): String {
        return try {
            val message = prefs(context).getString(KEY_SYSTEM_MESSAGE, "") ?: ""
            Log.d(TAG, "Loaded system message from prefs (length=${message.length})")
            message
        } catch (e: Exception) {
            Log.e(TAG, "Error loading system message: ${e.message}", e)
            ""
        }
    }

    /**
     * Returns an empty string – the default is now owned by the WebView's DEFAULT_SYSTEM_MSG
     * in index.html.  Kept for source compatibility with callers that may still reference it.
     */
    fun getDefaultSystemMessage(): String = ""
}
