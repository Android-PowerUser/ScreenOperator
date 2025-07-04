package com.google.ai.sample.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

object SystemMessageEntryPreferences {
    private const val TAG = "SystemMessageEntryPrefs"
    private const val PREFS_NAME = "system_message_entry_prefs"
    private const val KEY_SYSTEM_MESSAGE_ENTRIES = "system_message_entries"
    private const val KEY_DEFAULT_DB_ENTRIES_POPULATED = "default_db_entries_populated" // Added constant

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveEntries(context: Context, entries: List<SystemMessageEntry>) {
        try {
            val jsonString = Json.encodeToString(ListSerializer(SystemMessageEntry.serializer()), entries)
            Log.d(TAG, "Saving ${entries.size} entries. First entry title if exists: ${entries.firstOrNull()?.title}.")
            // Log.v(TAG, "Saving JSON: $jsonString") // Verbose, uncomment if needed for deep debugging
            val editor = getSharedPreferences(context).edit()
            editor.putString(KEY_SYSTEM_MESSAGE_ENTRIES, jsonString)
            editor.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving entries: ${e.message}", e)
        }
    }

    fun loadEntries(context: Context): List<SystemMessageEntry> {
        try {
            val prefs = getSharedPreferences(context)
            val defaultsPopulated = prefs.getBoolean(KEY_DEFAULT_DB_ENTRIES_POPULATED, false)

            if (!defaultsPopulated) {
                Log.d(TAG, "Default entries not populated. Populating now.")
                val defaultEntries = listOf(
                    SystemMessageEntry(
                        title = "Termux",
                        guide = "To write something in Termux you must be sure the ESC HOME banner is away. If not: `back()` `scrollRight(75%, 99%, 50%, 50)` `tapAtCoordinates(50%, 99%)` this in one message. Check if the banner has disappeared also at the bottom. To show the keyboard in Termux if the banner is gone: `tapAtCoordinates(50%, 99%)` And you must always `Enter()` twice.\""
                    ),
                    SystemMessageEntry(
                        title = "Chromium-based Browser",
                        guide = "To see more in a screenshot, you may want to consider zooming out. To do this, tap the three vertical dots, select the appropriate location in the menu, and then tap the 'minus' symbol (multiple times). You can only zoom out to 50%.\""
                    )
                )
                saveEntries(context, defaultEntries) // This saves them to KEY_SYSTEM_MESSAGE_ENTRIES
                prefs.edit().putBoolean(KEY_DEFAULT_DB_ENTRIES_POPULATED, true).apply()
                Log.d(TAG, "Populated and saved default database entries.")
                // The logic will now fall through to load these just-saved entries.
            }

            // Existing logic to load entries from KEY_SYSTEM_MESSAGE_ENTRIES:
            val jsonString = prefs.getString(KEY_SYSTEM_MESSAGE_ENTRIES, null)
            if (jsonString != null) {
                // Log.v(TAG, "Loaded JSON: $jsonString") // Verbose
                val loadedEntries = Json.decodeFromString(ListSerializer(SystemMessageEntry.serializer()), jsonString)
                Log.d(TAG, "Loaded ${loadedEntries.size} entries. First entry title if exists: ${loadedEntries.firstOrNull()?.title}.")
                return loadedEntries
            }
            Log.d(TAG, "No entries found, returning empty list.")
            return emptyList()
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
            // Optionally, add the new entry if the old one is not found
            // addEntry(context, newEntry)
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
}
