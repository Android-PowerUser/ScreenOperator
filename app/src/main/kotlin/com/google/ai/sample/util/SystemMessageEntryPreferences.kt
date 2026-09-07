package com.google.ai.sample.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Pure storage proxy for skill-set (database) entries.
 *
 * All business logic — default entries, titles, migration, upsert — now lives
 * in the WebView (index.html → _ensureDefaultDbEntries). This class only
 * reads and writes the persisted JSON list so the native/WebView boundary
 * stays a thin I/O layer.
 */
object SystemMessageEntryPreferences {
    private const val TAG = "SystemMessageEntryPrefs"
    private const val PREFS_NAME = "system_message_entry_prefs"
    private const val KEY_SYSTEM_MESSAGE_ENTRIES = "system_message_entries"
    private val entryListSerializer = ListSerializer(SystemMessageEntry.serializer())

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ── Raw JSON access (used by WebViewBridge.getRawDatabaseJson / setRawDatabaseJson) ──

    fun loadRawJson(context: Context): String {
        return getSharedPreferences(context).getString(KEY_SYSTEM_MESSAGE_ENTRIES, null) ?: "[]"
    }

    fun saveRawJson(context: Context, json: String) {
        try {
            getSharedPreferences(context).edit {
                putString(KEY_SYSTEM_MESSAGE_ENTRIES, json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveRawJson error: ${e.message}", e)
        }
    }

    // ── Typed helpers (used by WebViewBridge.getDatabaseEntries / addDatabaseEntry / …) ──

    fun saveEntries(context: Context, entries: List<SystemMessageEntry>) {
        try {
            val jsonString = Json.encodeToString(entryListSerializer, entries)
            Log.d(TAG, "Saving ${entries.size} entries.")
            getSharedPreferences(context).edit {
                putString(KEY_SYSTEM_MESSAGE_ENTRIES, jsonString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving entries: ${e.message}", e)
        }
    }

    fun loadEntries(context: Context): List<SystemMessageEntry> {
        return try {
            val jsonString = getSharedPreferences(context).getString(KEY_SYSTEM_MESSAGE_ENTRIES, null)
            if (jsonString == null) {
                Log.d(TAG, "No entries found, returning empty list.")
                return emptyList()
            }
            val loaded = Json.decodeFromString(entryListSerializer, jsonString)
            Log.d(TAG, "Loaded ${loaded.size} entries.")
            loaded
        } catch (e: Exception) {
            Log.e(TAG, "Error loading entries: ${e.message}", e)
            emptyList()
        }
    }

    fun addEntry(context: Context, entry: SystemMessageEntry) {
        Log.d(TAG, "Adding entry: Title='${entry.title}'")
        val entries = loadEntries(context).toMutableList()
        entries.add(entry)
        saveEntries(context, entries)
    }

    fun updateEntry(context: Context, oldEntry: SystemMessageEntry, newEntry: SystemMessageEntry) {
        Log.d(TAG, "Updating entry: OldTitle='${oldEntry.title}', NewTitle='${newEntry.title}'")
        val entries = loadEntries(context).toMutableList()
        val index = entries.indexOfFirst { it.title == oldEntry.title }
        if (index != -1) {
            entries[index] = newEntry
            saveEntries(context, entries)
            Log.i(TAG, "Entry updated successfully: NewTitle='${newEntry.title}'")
        } else {
            Log.w(TAG, "Entry with old title '${oldEntry.title}' not found for update.")
        }
    }

    fun deleteEntry(context: Context, entryToDelete: SystemMessageEntry) {
        val entries = loadEntries(context).toMutableList()
        val removed = entries.removeAll { it.title == entryToDelete.title && it.guide == entryToDelete.guide }
        if (removed) {
            saveEntries(context, entries)
            Log.d(TAG, "Deleted entry: ${entryToDelete.title}")
        } else {
            Log.w(TAG, "Entry not found for deletion: ${entryToDelete.title}")
        }
    }
}
