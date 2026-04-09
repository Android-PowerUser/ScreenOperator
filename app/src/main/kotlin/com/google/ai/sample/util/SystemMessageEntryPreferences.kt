package com.google.ai.sample.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object SystemMessageEntryPreferences {
    private const val TAG = "SystemMessageEntryPrefs"
    private const val PREFS_NAME = "system_message_entry_prefs"
    private const val KEY_SYSTEM_MESSAGE_ENTRIES = "system_message_entries"
    private const val KEY_DEFAULT_DB_ENTRIES_POPULATED = "default_db_entries_populated"
    private val entryListSerializer = ListSerializer(SystemMessageEntry.serializer())

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val defaultEntries: List<SystemMessageEntry> = listOf(
        SystemMessageEntry(
            title = "Termux",
            guide = "To write something in Termux you must be sure the ESC HOME banner is away. If not: back() scrollRight(75%, 99%, 50%, 50) tapAtCoordinates(50%, 99%) this in one message. Check if the banner has disappeared also at the bottom. To show the keyboard in Termux if the banner is gone: tapAtCoordinates(50%, 99%) And you must always Enter() twice.\""
        ),
        SystemMessageEntry(
            title = "Chromium-based Browser",
            guide = "To see more in a screenshot, you may want to consider zooming out. To do this, tap the three vertical dots, select the appropriate location in the menu, and then tap the 'minus' symbol (multiple times). It only works approximately in 10% increments. Press the button that many times in a message to zoom out until 50%. More isn't possible.\""
        ),
        SystemMessageEntry(
            title = "Miscellaneous",
            guide = "Some fields will not be spelled out, such as \"Nach Apps und Spielen su...\". In such a case, you must always enter the full version: clickOnButton(\"Nach Apps und Spielen suchen\")\""
        ),
        SystemMessageEntry(
            title = "File operations",
            guide = "As a VLM, Termux is the fastest way to perform file operations. If it's not installed, use existing file manager.\""
        )
    )

    fun saveEntries(context: Context, entries: List<SystemMessageEntry>) {
        try {
            val jsonString = Json.encodeToString(entryListSerializer, entries)
            Log.d(TAG, "Saving ${entries.size} entries. First entry title if exists: ${entries.firstOrNull()?.title}.")
            getSharedPreferences(context).edit {
                putString(KEY_SYSTEM_MESSAGE_ENTRIES, jsonString)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving entries: ${e.message}", e)
        }
    }

    fun loadEntries(context: Context): List<SystemMessageEntry> {
        try {
            val prefs = getSharedPreferences(context)
            ensureDefaultEntriesIfNeeded(context, prefs)
            return loadPersistedEntries(prefs)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading entries: ${e.message}", e)
            return emptyList()
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
        // Assuming title is unique for deletion. A more robust approach might use a unique ID.
        val removed = entries.removeAll { it.title == entryToDelete.title && it.guide == entryToDelete.guide }
        if (removed) {
            saveEntries(context, entries)
            Log.d(TAG, "Deleted entry: ${entryToDelete.title}")
        } else {
            Log.w(TAG, "Entry not found for deletion: ${entryToDelete.title}")
        }
    }

    private fun ensureDefaultEntriesIfNeeded(context: Context, prefs: SharedPreferences) {
        val defaultsPopulated = prefs.getBoolean(KEY_DEFAULT_DB_ENTRIES_POPULATED, false)
        if (defaultsPopulated) return

        Log.d(TAG, "Default entries not populated. Populating now.")
        saveEntries(context, defaultEntries)
        prefs.edit { putBoolean(KEY_DEFAULT_DB_ENTRIES_POPULATED, true) }
        Log.d(TAG, "Populated and saved default database entries.")
    }

    private fun loadPersistedEntries(prefs: SharedPreferences): List<SystemMessageEntry> {
        val jsonString = prefs.getString(KEY_SYSTEM_MESSAGE_ENTRIES, null)
        if (jsonString == null) {
            Log.d(TAG, "No entries found, returning empty list.")
            return emptyList()
        }

        val loadedEntries = Json.decodeFromString(entryListSerializer, jsonString)
        Log.d(TAG, "Loaded ${loadedEntries.size} entries. First entry title if exists: ${loadedEntries.firstOrNull()?.title}.")
        return loadedEntries
    }
}
