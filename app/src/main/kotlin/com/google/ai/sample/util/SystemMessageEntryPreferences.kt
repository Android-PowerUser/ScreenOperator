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
    private const val KEY_LEGACY_TERMUX_ENTRY_MIGRATED = "legacy_termux_entry_migrated"
    private val entryListSerializer = ListSerializer(SystemMessageEntry.serializer())

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private const val OLD_TERMUX_TITLE = "Termux"
    private const val TERMUX_TITLE = "If \"Termux(\"command\")\" doesn't work"
    private const val OLD_FIELD_TITLE = "If a field is not spelled out"
    private const val FIELD_TITLE = "If a field to click is not spelled out"

    private val termuxEntry = SystemMessageEntry(
        title = TERMUX_TITLE,
        guide = """1. Open Termux; if that has no effect, it is not installed and you will need to download the GitHub version using a browser.

2. Else go ~/.termux/termux.properties uncomment 
allow-external-apps = true in Termux
Confirm Android Termux access permissions request.

3. The "Run commands in Termux environment" permission must be approved in the Screen Operator app info in the Android settings. But this should be already turned on so try Termux("Your command") again."""
    )

    private val fieldEntry = SystemMessageEntry(
        title = FIELD_TITLE,
        guide = "Some fields will not be spelled out, such as \"Nach Apps und Spielen su...\". In such a case, you must always enter the full version: clickOnButton(\"Nach Apps und Spielen suchen\")\""
    )

    private val defaultEntries: List<SystemMessageEntry> = listOf(
        termuxEntry,
        SystemMessageEntry(
            title = "Chromium-based Browser",
            guide = "To see more in a screenshot, you may want to consider zooming out. To do this, tap the three vertical dots, select the appropriate location in the menu, and then tap the 'minus' symbol (multiple times). It only works approximately in 10% increments. Press the button that many times in a message to zoom out until 50%. More isn't possible.\""
        ),
        fieldEntry,
        SystemMessageEntry(
            title = "File operations",
            guide = "As a VLM, Termux is the fastest way to perform file operations. If it's not installed, use existing file manager.\""
        ),
        SystemMessageEntry(
            title = "Root availability",
            guide = """Anyone with root access also has a root app like Magisk, KernelSU, or something similar. Try opening all these apps simultaneously. If then one is actually running, you'll see there whether root access is active."""
        ),
        SystemMessageEntry(
            title = "Set thumbnail for videos",
            guide = """Set thumbnails won't work with WMV files.
Use Termux and retrieve the manual for that and install FFMPEG there if it's not already present. Specify the video, image, and output paths. Use: -map 0 -map 1 -c copy -c:v:1 mjpeg -disposition:v:1 attached_pic 

Alternate way: Open FFMPEG App. (If it's not installed, download the app from GitHub, not the Play Store, because Google restrictions prevent FFMPEG from accessing most files.) 
Click 2 input files and the second one is for the picture. Write -map 0 -map 1 -c copy -c:v:1 mjpeg -disposition:v:1 attached_pic in the text field on the bottom"""
        ),
        SystemMessageEntry(
            title = "Codex",
            guide = """Go to https://chatgpt.com/codex/cloud and log in if necessary. Select the repository, and the most up-to-date branch.

First time with Codex: Yes


If "Yes": Write Codex a task and click submit. It will take 3 seconds to start. Find the task field and stop it. The environment for the repository is now created. Tap Menu (top left) and Environments, tap the relevant one, click Edit, and set the setup script to manual. In the new text field, enter the following, if you task is to develop an Android app:

#!/bin/bash

# Exit on any error
set -e

# 1. Set up the Android SDK directory
echo "Setting up Android SDK directory..."

mkdir -p android_sdk

echo "sdk.dir=android_sdk" > local.properties

# Add android_sdk to .gitignore if it's not already there
if !  grep -q "android_sdk/" .gitignore;  then
   echo "android_sdk/" >> .gitignore
 fi

 # 2. Download and unzip the Android SDK command-line tools
 echo "Downloading and unzipping Android SDK..."
 wget -q https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
 unzip -q commandlinetools-linux-13114758_latest.zip -d android_sdk
 rm commandlinetools-linux-13114758_latest.zip

 #3. Restructure cmdline-tools for sdkmanager
 # FIX: Rename the extracted folder to a temp name FIRST (outside of itself),
 # then create the required nested structure and move the contents in.
 # Doing `mv cmdline-tools/* cmdline-tools/latest` would try to move
 #  `latest` into itself, which fails.
 echo "Restructuring cmdline-tools..."
 mv android_sdk/cmdline-tools android_sdk/cmdline-tools-tmp
 mkdir -p android_sdk/cmdline-tools/latest
 mv android_sdk/cmdline-tools-tmp/* android_sdk/cmdline-tools/latest/
 rm -rf android_sdk/cmdline-tools-tmp

 #4. Install required SDK packages
 echo "Installing SDK packages..."
 # FIX: Append `||  true` because `yes` receives a SIGPIPE (exit code 141)
 # once sdkmanager closes its stdin after all licenses are accepted.
 # With `set -e` active, that would abort the script — but it's harmless here.
 yes |  android_sdk/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null || true

android_sdk/cmdline-tools/latest/bin/sdkmanager \

"platforms;android-35" \

"build-tools;35.0.0" \

"platform-tools" 

Enable internet access for agents and select "All (unrestricted)" for the domain allowlist and save. Go back (several times) to the Codex input field. 

Then Open your own app, tap on Database, Developing Android apps, set "First time with Codex" to "No" and press "save".


If "No": Write Codex the task and click submit. It will take 3 seconds to start. Find the new task field and click on it. You can click on protocol to see what's happening right now.

Use the "Wait(seconds)" function to save tokens. After a longer period of time, the session or container must first be created. This takes approximately 120 seconds. The container is used across multiple tasks.
If the session is already created and Codex only asked questions, the response takes 40 seconds.
The task itself takes approximately 70 seconds and the build to test takes 135 seconds.

Proceed normally."""
        ),
        SystemMessageEntry(
            title = "Developing Android apps",
            guide = """1. Only if it's an app from scratch create a Github repo with Termux if installed else with a browser and log in or sign up if necessary. Ask the user whether the repo should be public or private and the name of the app and the repo.


thereafter:
retrieve("Codex")"""
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
        val currentEntries = runCatching { loadPersistedEntries(prefs) }.getOrElse { error ->
            Log.e(TAG, "Error loading persisted entries for default synchronization: ${error.message}", error)
            emptyList()
        }

        val shouldMigrateLegacyTermux = shouldMigrateLegacyTermuxEntry(currentEntries, prefs)
        val synchronizedEntries = synchronizeDefaultEntries(currentEntries, shouldMigrateLegacyTermux)
        if (synchronizedEntries != currentEntries) {
            Log.d(TAG, "Synchronizing default database entries. Before=${currentEntries.size}, After=${synchronizedEntries.size}.")
            saveEntries(context, synchronizedEntries)
        }

        prefs.edit {
            if (!prefs.getBoolean(KEY_DEFAULT_DB_ENTRIES_POPULATED, false)) {
                putBoolean(KEY_DEFAULT_DB_ENTRIES_POPULATED, true)
                Log.d(TAG, "Marked default database entries as populated.")
            }
            if (!prefs.getBoolean(KEY_LEGACY_TERMUX_ENTRY_MIGRATED, false)) {
                putBoolean(KEY_LEGACY_TERMUX_ENTRY_MIGRATED, true)
                Log.d(TAG, "Marked legacy Termux database entry migration as completed.")
            }
        }
    }

    private fun shouldMigrateLegacyTermuxEntry(entries: List<SystemMessageEntry>, prefs: SharedPreferences): Boolean {
        if (prefs.getBoolean(KEY_LEGACY_TERMUX_ENTRY_MIGRATED, false)) return false
        return entries.none { it.title.equals(TERMUX_TITLE, ignoreCase = true) }
    }

    private fun synchronizeDefaultEntries(
        entries: List<SystemMessageEntry>,
        shouldMigrateLegacyTermux: Boolean
    ): List<SystemMessageEntry> {
        val synchronizedEntries = entries.toMutableList()
        val legacyTermuxTitles = if (shouldMigrateLegacyTermux) listOf(OLD_TERMUX_TITLE) else emptyList()
        upsertRequiredEntry(synchronizedEntries, termuxEntry, legacyTermuxTitles)
        upsertRequiredEntry(synchronizedEntries, fieldEntry, listOf(OLD_FIELD_TITLE, "Miscellaneous"))
        defaultEntries
            .filterNot { it.title == termuxEntry.title || it.title == fieldEntry.title }
            .forEach { upsertRequiredEntry(synchronizedEntries, it) }
        return synchronizedEntries
    }

    private fun upsertRequiredEntry(
        entries: MutableList<SystemMessageEntry>,
        requiredEntry: SystemMessageEntry,
        legacyTitles: List<String> = emptyList()
    ) {
        val matchingTitles = legacyTitles + requiredEntry.title
        val firstMatchingIndex = entries.indexOfFirst { entry ->
            matchingTitles.any { title -> entry.title.equals(title, ignoreCase = true) }
        }

        if (firstMatchingIndex == -1) {
            entries.add(requiredEntry)
            return
        }

        entries[firstMatchingIndex] = requiredEntry
        for (index in entries.indices.reversed()) {
            if (index != firstMatchingIndex && matchingTitles.any { entries[index].title.equals(it, ignoreCase = true) }) {
                entries.removeAt(index)
            }
        }
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
