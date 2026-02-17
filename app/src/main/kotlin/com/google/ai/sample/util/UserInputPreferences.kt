package com.google.ai.sample.util

import android.content.Context

object UserInputPreferences {
    private const val PREFS_NAME = "UserInputPrefs"
    private const val KEY_USER_INPUT = "user_input"

    fun saveUserInput(context: Context, text: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USER_INPUT, text).apply()
    }

    fun loadUserInput(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_INPUT, "") ?: ""
    }
}
