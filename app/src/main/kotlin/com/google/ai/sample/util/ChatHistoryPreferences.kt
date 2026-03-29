package com.google.ai.sample.util

import android.content.Context
import com.google.ai.sample.feature.multimodal.PhotoReasoningMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * Utility class for persisting chat history across app restarts
 */
object ChatHistoryPreferences {
    private const val PREFS_NAME = "chat_history_prefs"
    private const val KEY_CHAT_MESSAGES = "chat_messages"
    private val gson = Gson()
    private val messageListType: Type = object : TypeToken<List<PhotoReasoningMessage>>() {}.type

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Save chat messages to SharedPreferences
     */
    fun saveChatMessages(context: Context, messages: List<PhotoReasoningMessage>) {
        val json = gson.toJson(messages)
        prefs(context).edit().putString(KEY_CHAT_MESSAGES, json).apply()
    }
    
    /**
     * Load chat messages from SharedPreferences
     */
    fun loadChatMessages(context: Context): List<PhotoReasoningMessage> {
        val json = prefs(context).getString(KEY_CHAT_MESSAGES, null) ?: return emptyList()
        return try {
            gson.fromJson(json, messageListType)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Clear all chat messages from SharedPreferences
     */
    fun clearChatMessages(context: Context) {
        prefs(context).edit().remove(KEY_CHAT_MESSAGES).apply()
    }
}
