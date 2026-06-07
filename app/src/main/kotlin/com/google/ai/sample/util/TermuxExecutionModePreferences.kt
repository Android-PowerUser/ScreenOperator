package com.google.ai.sample.util

import android.content.Context

object TermuxExecutionModePreferences {
    private const val PREF_NAME = "termux_execution_mode_prefs"
    private const val KEY_EXECUTE_IN_BACKGROUND = "execute_in_background"

    fun executeInBackground(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_EXECUTE_IN_BACKGROUND, false)

    fun setExecuteInBackground(context: Context, executeInBackground: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EXECUTE_IN_BACKGROUND, executeInBackground)
            .apply()
    }
}
