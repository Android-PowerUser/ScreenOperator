package com.google.ai.sample.util

import android.util.Log

/**
 * Command parser for extracting commands from AI responses
 */
object CommandParser {
    private const val TAG = "CommandParser"

    // Enum to represent different command types
    private enum class CommandTypeEnum {
        CLICK_BUTTON, LONG_CLICK_BUTTON, TAP_COORDINATES, TAKE_SCREENSHOT, PRESS_HOME, PRESS_BACK,
        SHOW_RECENT_APPS, SCROLL_DOWN, SCROLL_UP, SCROLL_LEFT, SCROLL_RIGHT,
        SCROLL_DOWN_FROM_COORDINATES, SCROLL_UP_FROM_COORDINATES,
        SCROLL_LEFT_FROM_COORDINATES, SCROLL_RIGHT_FROM_COORDINATES,
        OPEN_APP, WRITE_TEXT, USE_HIGH_REASONING_MODEL, USE_LOW_REASONING_MODEL,
        PRESS_ENTER_KEY
    }

    // Data class to hold pattern information
    private data class PatternInfo(
        val id: String, // For debugging
        val regex: Regex,
        val commandBuilder: (MatchResult) -> Command,
        val commandType: CommandTypeEnum // Used for single-instance command check
    )

    // Master list of all patterns
    private val ALL_PATTERNS: List<PatternInfo> = listOf(
        // Enter key patterns
        PatternInfo("enterKey1", Regex("(?i)\\benter\\(\\)"), { Command.PressEnterKey }, CommandTypeEnum.PRESS_ENTER_KEY),

        // Model selection patterns
        PatternInfo("highReasoning1", Regex("(?i)\\bhighReasoningModel\\(\\)"), { Command.UseHighReasoningModel }, CommandTypeEnum.USE_HIGH_REASONING_MODEL),
        PatternInfo("lowReasoning1", Regex("(?i)\\blowReasoningModel\\(\\)"), { Command.UseLowReasoningModel }, CommandTypeEnum.USE_LOW_REASONING_MODEL),

        // Write text patterns
        PatternInfo("writeText1", Regex("(?i)\\bwriteText\\([\"']([^\"']+)[\"']\\)"), { match -> Command.WriteText(match.groupValues[1]) }, CommandTypeEnum.WRITE_TEXT),

        // Click (long) button patterns
        PatternInfo("clickBtn1", Regex("(?i)\\bclick\\([\"']([^\"']+)[\"']"), { match -> Command.ClickButton(match.groupValues[1]) }, CommandTypeEnum.CLICK_BUTTON),
        PatternInfo("longClickBtn1", Regex("(?i)\\blongClick\\([\"']([^\"']+)[\"']"), { match -> Command.LongClickButton(match.groupValues[1]) }, CommandTypeEnum.LONG_CLICK_BUTTON),

        // Tap coordinates patterns
        PatternInfo("tapCoords1", Regex("(?i)\\btapAtCoordinates\\(\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*\\)"), { match -> Command.TapCoordinates(match.groupValues[1], match.groupValues[2]) }, CommandTypeEnum.TAP_COORDINATES),

        // Screenshot patterns
        PatternInfo("screenshot1", Regex("(?i)\\btakeScreenshot\\(\\)"), { Command.TakeScreenshot }, CommandTypeEnum.TAKE_SCREENSHOT),

        // Home button patterns
        PatternInfo("home1", Regex("(?i)\\bhome\\(\\)"), { Command.PressHomeButton }, CommandTypeEnum.PRESS_HOME),

        // Back button patterns
        PatternInfo("back1", Regex("(?i)\\bback\\(\\)"), { Command.PressBackButton }, CommandTypeEnum.PRESS_BACK),

        // Recent apps patterns
        PatternInfo("recentApps1", Regex("(?i)\\brecentApps\\(\\)"), { Command.ShowRecentApps }, CommandTypeEnum.SHOW_RECENT_APPS),

        // Scroll patterns (simple)
        PatternInfo("scrollDown1", Regex("(?i)\\bscrollDown\\(\\)"), { Command.ScrollDown }, CommandTypeEnum.SCROLL_DOWN),
        PatternInfo("scrollUp1", Regex("(?i)\\bscrollUp\\(\\)"), { Command.ScrollUp }, CommandTypeEnum.SCROLL_UP),
        PatternInfo("scrollLeft1", Regex("(?i)\\bscrollLeft\\(\\)"), { Command.ScrollLeft }, CommandTypeEnum.SCROLL_LEFT),
        PatternInfo("scrollRight1", Regex("(?i)\\bscrollRight\\(\\)"), { Command.ScrollRight }, CommandTypeEnum.SCROLL_RIGHT),

        // Scroll from coordinates patterns
        PatternInfo("scrollDownCoords", Regex("(?i)\\bscrollDown\\s*\\(\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*,\\s*(\\d+)\\s*\\)"),
            { match -> Command.ScrollDownFromCoordinates(match.groupValues[1], match.groupValues[2], match.groupValues[3], match.groupValues[4].toLong()) }, CommandTypeEnum.SCROLL_DOWN_FROM_COORDINATES),
        PatternInfo("scrollUpCoords", Regex("(?i)\\bscrollUp\\s*\\(\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*,\\s*(\\d+)\\s*\\)"),
            { match -> Command.ScrollUpFromCoordinates(match.groupValues[1], match.groupValues[2], match.groupValues[3], match.groupValues[4].toLong()) }, CommandTypeEnum.SCROLL_UP_FROM_COORDINATES),
        PatternInfo("scrollLeftCoords", Regex("(?i)\\bscrollLeft\\s*\\(\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*,\\s*(\\d+)\\s*\\)"),
            { match -> Command.ScrollLeftFromCoordinates(match.groupValues[1], match.groupValues[2], match.groupValues[3], match.groupValues[4].toLong()) }, CommandTypeEnum.SCROLL_LEFT_FROM_COORDINATES),
        PatternInfo("scrollRightCoords", Regex("(?i)\\bscrollRight\\s*\\(\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*,\\s*(\\d+)\\s*\\)"),
            { match -> Command.ScrollRightFromCoordinates(match.groupValues[1], match.groupValues[2], match.groupValues[3], match.groupValues[4].toLong()) }, CommandTypeEnum.SCROLL_RIGHT_FROM_COORDINATES),

        // Open app patterns
        PatternInfo("openApp1", Regex("(?i)\\bopenApp\\([\"']([^\"']+)[\"']\\)"), { match -> Command.OpenApp(match.groupValues[1]) }, CommandTypeEnum.OPEN_APP)
    )

    // Buffer for storing partial text between calls
    private var textBuffer = ""

    // Flag to indicate if we should clear the buffer on next call
    private var shouldClearBuffer = false

    /**
     * Parse commands from the given text
     *
     * @param text The text to parse for commands
     * @param clearBuffer Whether to clear the buffer before parsing (default: false)
     * @return A list of commands found in the text
     */
    fun parseCommands(text: String, clearBuffer: Boolean = false): List<Command> {
        val commands = mutableListOf<Command>()

        try {
            // Clear buffer if requested or if flag is set
            if (clearBuffer || shouldClearBuffer) {
                textBuffer = ""
                shouldClearBuffer = false
                Log.d(TAG, "Buffer cleared")
            }

            // Normalize the text (trim whitespace, normalize line breaks)
            val normalizedText = normalizeText(text)

            // Append to buffer
            textBuffer += normalizedText

            // Debug the buffer
            Log.d(TAG, "Current buffer for command parsing: $textBuffer")

            // Process the buffer line by line
            val lines = textBuffer.split("\n")

            // Process each line and the combined buffer
            processText(textBuffer, commands)

            // If we found commands, clear the buffer for next time
            if (commands.isNotEmpty()) {
                shouldClearBuffer = true
                Log.d(TAG, "Commands found, buffer will be cleared on next call")
            }

            Log.d(TAG, "Found ${commands.size} commands in text") // This log remains

            // Debug each found command
            commands.forEach { command ->
                when (command) {
                    is Command.ClickButton -> Log.d(TAG, "Command details: ClickButton(\"${command.buttonText}\")")
                    is Command.LongClickButton -> Log.d(TAG, "Command details: LongClickButton(\"${command.buttonText}\")")
                    is Command.TapCoordinates -> Log.d(TAG, "Command details: TapCoordinates(${command.x}, ${command.y})")
                    is Command.TakeScreenshot -> Log.d(TAG, "Command details: TakeScreenshot")
                    is Command.PressHomeButton -> Log.d(TAG, "Command details: PressHomeButton")
                    is Command.PressBackButton -> Log.d(TAG, "Command details: PressBackButton")
                    is Command.ShowRecentApps -> Log.d(TAG, "Command details: ShowRecentApps")
                    is Command.ScrollDown -> Log.d(TAG, "Command details: ScrollDown")
                    is Command.ScrollUp -> Log.d(TAG, "Command details: ScrollUp")
                    is Command.ScrollLeft -> Log.d(TAG, "Command details: ScrollLeft")
                    is Command.ScrollRight -> Log.d(TAG, "Command details: ScrollRight")
                    is Command.ScrollDownFromCoordinates -> Log.d(TAG, "Command details: ScrollDownFromCoordinates(${command.x}, ${command.y}, ${command.distance}, ${command.duration})")
                    is Command.UseHighReasoningModel -> Log.d(TAG, "Command details: UseHighReasoningModel")
                    is Command.UseLowReasoningModel -> Log.d(TAG, "Command details: UseLowReasoningModel")
                    is Command.ScrollUpFromCoordinates -> Log.d(TAG, "Command details: ScrollUpFromCoordinates(${command.x}, ${command.y}, ${command.distance}, ${command.duration})")
                    is Command.ScrollLeftFromCoordinates -> Log.d(TAG, "Command details: ScrollLeftFromCoordinates(${command.x}, ${command.y}, ${command.distance}, ${command.duration})")
                    is Command.ScrollRightFromCoordinates -> Log.d(TAG, "Command details: ScrollRightFromCoordinates(${command.x}, ${command.y}, ${command.distance}, ${command.duration})")
                    is Command.OpenApp -> Log.d(TAG, "Command details: OpenApp(\"${command.packageName}\")")
                    is Command.WriteText -> Log.d(TAG, "Command details: WriteText(\"${command.text}\")")
                    is Command.PressEnterKey -> Log.d(TAG, "Command details: PressEnterKey")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing commands: ${e.message}", e)
        }

        return commands
    }

    /**
     * Process text to find commands
     */
    private fun processTextInternal(text: String): List<Command> {
        data class ProcessedMatch(val startIndex: Int, val endIndex: Int, val command: Command, val type: CommandTypeEnum)
        val foundRawMatches = mutableListOf<ProcessedMatch>()
        val finalCommands = mutableListOf<Command>()
        val addedSingleInstanceCommands = mutableSetOf<CommandTypeEnum>()

        for (patternInfo in ALL_PATTERNS) {
            try {
                patternInfo.regex.findAll(text).forEach { matchResult ->
                    try {
                        val command = patternInfo.commandBuilder(matchResult)
                        // Store the commandType from the patternInfo that generated this command
                        foundRawMatches.add(ProcessedMatch(matchResult.range.first, matchResult.range.last, command, patternInfo.commandType))
                        Log.d(TAG, "Found raw match: Start=${matchResult.range.first}, End=${matchResult.range.last}, Command=${command}, Type=${patternInfo.commandType}, Pattern=${patternInfo.id}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error building command for pattern ${patternInfo.id} with match ${matchResult.value}: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error finding matches for pattern ${patternInfo.id}: ${e.message}", e)
            }
        }

        // Sort matches by start index
        foundRawMatches.sortBy { it.startIndex }
        Log.d(TAG, "Sorted raw matches (${foundRawMatches.size}): $foundRawMatches")

        var currentPosition = 0
        for (processedMatch in foundRawMatches) {
            val (startIndex, endIndex, command, commandTypeFromMatch) = processedMatch // Destructure
            if (startIndex >= currentPosition) {
                var canAdd = true
                // Use commandTypeFromMatch directly here
                val isSingleInstanceType = when (commandTypeFromMatch) {
                    CommandTypeEnum.TAKE_SCREENSHOT -> true // Only TakeScreenshot is single-instance
                    else -> false
                }
                if (isSingleInstanceType) {
                    if (addedSingleInstanceCommands.contains(commandTypeFromMatch)) {
                        canAdd = false
                        Log.d(TAG, "Skipping duplicate single-instance command: $command (Type: $commandTypeFromMatch)")
                    } else {
                        addedSingleInstanceCommands.add(commandTypeFromMatch)
                    }
                }

                if (canAdd) {
                    // Simplified duplicate check: if it's not a single instance type, allow it.
                    // More sophisticated duplicate checks for parameterized commands can be added here if needed.
                    // For now, only single-instance types are strictly controlled for duplication.
                    // The overlap filter (startIndex >= currentPosition) already prevents identical commands
                    // from the exact same text span.
                    finalCommands.add(command)
                    currentPosition = endIndex + 1
                    Log.d(TAG, "Added command: $command. New currentPosition: $currentPosition")
                }
            } else {
                Log.d(TAG, "Skipping overlapping command: $command (startIndex $startIndex < currentPosition $currentPosition)")
            }
        }
        Log.d(TAG, "Final commands list (${finalCommands.size}): $finalCommands")
        return finalCommands
    }

    /**
     * Process text to find commands
     */
    private fun processText(text: String, commands: MutableList<Command>) {
        val extractedCommands = processTextInternal(text)
        commands.addAll(extractedCommands)
    }

    /**
     * Normalize text by trimming whitespace and normalizing line breaks
     */
    private fun normalizeText(text: String): String {
        // Replace multiple spaces with a single space
        var normalized = text.replace(Regex("\\s+"), " ")

        // Ensure consistent line breaks
        normalized = normalized.replace(Regex("\\r\\n|\\r"), "\n")

        return normalized.trim() // Added trim() here as well for good measure
    }

    /**
     * Clear the text buffer
     */
    fun clearBuffer() {
        textBuffer = ""
        shouldClearBuffer = false
        Log.d(TAG, "Buffer manually cleared")
    }

    /**
     * Debug method to test if a specific command would be recognized
     */
    fun testCommandRecognition(commandText: String): List<Command> {
        Log.d(TAG, "Testing command recognition for: \"$commandText\"")

        // Clear buffer for testing
        clearBuffer()

        val commands = parseCommands(commandText)
        Log.d(TAG, "Recognition test result: ${commands.size} commands found")
        return commands
    }

    /**
     * Get the current buffer content (for debugging)
     */
    fun getBufferContent(): String {
        return textBuffer
    }
}

/**
 * Sealed class representing different types of commands
 */
sealed class Command {
    /**
     * Command to click a button with the specified text
     */
    data class ClickButton(val buttonText: String) : Command()

    /**
     * Command to long click a button with the specified text
     */
    data class LongClickButton(val buttonText: String) : Command()

    /**
     * Command to tap at the specified coordinates
     */
    data class TapCoordinates(val x: String, val y: String) : Command()

    /**
     * Command to take a screenshot
     */
    object TakeScreenshot : Command()

    /**
     * Command to press the home button
     */
    object PressHomeButton : Command()

    /**
     * Command to press the back button
     */
    object PressBackButton : Command()

    /**
     * Command to show recent apps
     */
    object ShowRecentApps : Command()

    /**
     * Command to scroll down
     */
    object ScrollDown : Command()

    /**
     * Command to scroll up
     */
    object ScrollUp : Command()

    /**
     * Command to scroll left
     */
    object ScrollLeft : Command()
    
    /**
     * Command to press the Enter key
     */
    object PressEnterKey : Command()

    /**
     * Command to scroll right
     */
    object ScrollRight : Command()

    /**
     * Command to scroll down from specific coordinates with custom distance and duration
     */
    data class ScrollDownFromCoordinates(val x: String, val y: String, val distance: String, val duration: Long) : Command()

    /**
     * Command to scroll up from specific coordinates with custom distance and duration
     */
    data class ScrollUpFromCoordinates(val x: String, val y: String, val distance: String, val duration: Long) : Command()

    /**
     * Command to scroll left from specific coordinates with custom distance and duration
     */
    data class ScrollLeftFromCoordinates(val x: String, val y: String, val distance: String, val duration: Long) : Command()

    /**
     * Command to scroll right from specific coordinates with custom distance and duration
     */
    data class ScrollRightFromCoordinates(val x: String, val y: String, val distance: String, val duration: Long) : Command()

    /**
     * Command to open an app by package name
     */
    data class OpenApp(val packageName: String) : Command()

    /**
     * Command to write text into the currently focused text field
     */
    data class WriteText(val text: String) : Command()

    /**
     * Command to switch to high reasoning model (gemini-2.5-pro-preview-03-25)
     */
    object UseHighReasoningModel : Command()

    /**
     * Command to switch to low reasoning model (gemini-2.0-flash-lite)
     */
    object UseLowReasoningModel : Command()
}
