package com.google.ai.sample.util

import android.content.Context

object UserInputPreferences {
    private const val PREFS_NAME = "UserInputPrefs"
    private const val KEY_USER_INPUT = "user_input"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveUserInput(context: Context, text: String) {
        prefs(context).edit().putString(KEY_USER_INPUT, text).apply()
    }

    fun loadUserInput(context: Context): String {
        return prefs(context).getString(KEY_USER_INPUT, "") ?: ""
    }
}
