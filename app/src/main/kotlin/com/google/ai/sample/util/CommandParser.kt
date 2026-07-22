package com.google.ai.sample.util

import android.util.Log

/**
 * Fully JSON-driven command parser.
 *
 * All command patterns are loaded from JSON (command-builtins.json for built-in commands,
 * command-patterns.json for overrides, custom-action-types.json for JS-handled actions).
 * No command regexes are hardcoded in Kotlin.
 *
 * Each CommandType has a factory function that builds the corresponding Command from
 * MatchResult capture groups. This is the ONLY place where Command subclasses are
 * instantiated — it acts as a sealed registry that cannot be extended by remote JSON.
 */
object CommandParser {
    private const val TAG = "CommandParser"

    private val SINGLE_INSTANCE_COMMAND_TYPES = setOf(
        CommandType.TAKE_SCREENSHOT,
        CommandType.COMPLETED
    )

    enum class CommandType {
        CLICK_BUTTON, LONG_CLICK_BUTTON, TAP_COORDINATES, TAKE_SCREENSHOT, COMPLETED, WAIT,
        PRESS_HOME, PRESS_BACK, SHOW_RECENT_APPS,
        SCROLL_DOWN, SCROLL_UP, SCROLL_LEFT, SCROLL_RIGHT,
        SCROLL_DOWN_FROM_COORDINATES, SCROLL_UP_FROM_COORDINATES,
        SCROLL_LEFT_FROM_COORDINATES, SCROLL_RIGHT_FROM_COORDINATES,
        OPEN_APP, WRITE_TEXT, PRESS_ENTER_KEY, TERMUX_COMMAND, PINCH_GESTURE,
        COPY_TO_CLIPBOARD, LAUNCH_INTENT,
        // JS-handled: Retrieve, PopUp, model switching
        SHOW_POPUP,
        /** Container type for all action types defined remotely via custom-action-types.json. */
        WEBVIEW_CUSTOM_ACTION;

        companion object {
            fun safeValueOf(name: String): CommandType? =
                try { valueOf(name) } catch (_: IllegalArgumentException) { null }
        }
    }

    // ── Command factory registry (sealed, cannot be extended by remote JSON) ──────────

    /** Maps CommandType → command builder. This is the security boundary. */
    private val COMMAND_FACTORY: Map<CommandType, (MatchResult, List<Int>) -> Command> = mapOf(
        CommandType.CLICK_BUTTON to { m, g -> Command.ClickButton(m.groupValues[g[0] + 1]) },
        CommandType.LONG_CLICK_BUTTON to { m, g -> Command.LongClickButton(m.groupValues[g[0] + 1]) },
        CommandType.TAP_COORDINATES to { m, g -> Command.TapCoordinates(m.groupValues[g[0] + 1], m.groupValues[g[1] + 1]) },
        CommandType.TAKE_SCREENSHOT to { _, _ -> Command.TakeScreenshot },
        CommandType.COMPLETED to { _, _ -> Command.Completed },
        CommandType.WAIT to { m, g -> Command.Wait(m.groupValues[g[0] + 1].toLong()) },
        CommandType.PRESS_HOME to { _, _ -> Command.PressHomeButton },
        CommandType.PRESS_BACK to { _, _ -> Command.PressBackButton },
        CommandType.SHOW_RECENT_APPS to { _, _ -> Command.ShowRecentApps },
        CommandType.SCROLL_DOWN to { _, _ -> Command.ScrollDown },
        CommandType.SCROLL_UP to { _, _ -> Command.ScrollUp },
        CommandType.SCROLL_LEFT to { _, _ -> Command.ScrollLeft },
        CommandType.SCROLL_RIGHT to { _, _ -> Command.ScrollRight },
        CommandType.SCROLL_DOWN_FROM_COORDINATES to { m, g ->
            Command.ScrollDownFromCoordinates(m.groupValues[g[0] + 1], m.groupValues[g[1] + 1], m.groupValues[g[2] + 1], m.groupValues[g[3] + 1].toLong())
        },
        CommandType.SCROLL_UP_FROM_COORDINATES to { m, g ->
            Command.ScrollUpFromCoordinates(m.groupValues[g[0] + 1], m.groupValues[g[1] + 1], m.groupValues[g[2] + 1], m.groupValues[g[3] + 1].toLong())
        },
        CommandType.SCROLL_LEFT_FROM_COORDINATES to { m, g ->
            Command.ScrollLeftFromCoordinates(m.groupValues[g[0] + 1], m.groupValues[g[1] + 1], m.groupValues[g[2] + 1], m.groupValues[g[3] + 1].toLong())
        },
        CommandType.SCROLL_RIGHT_FROM_COORDINATES to { m, g ->
            Command.ScrollRightFromCoordinates(m.groupValues[g[0] + 1], m.groupValues[g[1] + 1], m.groupValues[g[2] + 1], m.groupValues[g[3] + 1].toLong())
        },
        CommandType.OPEN_APP to { m, g -> Command.OpenApp(m.groupValues[g[0] + 1]) },
        CommandType.WRITE_TEXT to { m, g -> Command.WriteText(m.groupValues[g[0] + 1]) },
        CommandType.PRESS_ENTER_KEY to { _, _ -> Command.PressEnterKey },
        CommandType.TERMUX_COMMAND to { m, g -> Command.TermuxCommand(m.groupValues[g[0] + 1]) },
        CommandType.PINCH_GESTURE to { m, g ->
            Command.PinchGesture(m.groupValues[g[0] + 1], m.groupValues[g[1] + 1], m.groupValues[g[2] + 1], m.groupValues[g[3] + 1], m.groupValues[g[4] + 1].toLong())
        },
        CommandType.COPY_TO_CLIPBOARD to { m, g -> Command.CopyToClipboard(m.groupValues[g[0] + 1]) },
        CommandType.LAUNCH_INTENT to { m, g ->
            Command.LaunchIntent(m.groupValues[g[0] + 1], m.groupValues[g[1] + 1].ifBlank { "{}" }, m.groupValues[g[2] + 1])
        },
        // JS-handled types: emit as WebViewCustomAction so JS handles the logic
        CommandType.SHOW_POPUP to { m, g ->
            val answers = g.drop(1).mapNotNull { idx ->
                val v = m.groupValues.getOrNull(idx + 1)
                if (v.isNullOrBlank()) null else v
            }
            Command.WebViewCustomAction("SHOW_POPUP", listOf(m.groupValues[g[0] + 1]) + answers)
        },
        CommandType.WEBVIEW_CUSTOM_ACTION to { _, _ ->
            error("WEBVIEW_CUSTOM_ACTION should not be built by the factory — it's built inline for custom-action-types.json entries")
        }
    )

    // ── Internal data structures ──────────────────────────────────────────────────

    data class JsonPatternEntry(
        val id: String,
        val type: String,
        val regex: String,
        val groups: List<Int> = emptyList()
    )

    private data class PatternInfo(
        val id: String,
        val regex: Regex,
        val commandType: CommandType,
        val groupIndices: List<Int>,
        val commandBuilder: (MatchResult) -> Command
    )
    private data class ProcessedMatch(
        val startIndex: Int,
        val endIndex: Int,
        val command: Command,
        val commandType: CommandType
    )

    // ── Built-in patterns (loaded from JSON at init, never hardcoded as regexes) ──

    private val BUILTIN_PATTERNS_JSON = """
[
  {"id":"enterKey1","type":"PRESS_ENTER_KEY","regex":"(?i)\\benter\\(\\)"},
  {"id":"writeText1","type":"WRITE_TEXT","regex":"(?i)\\bwriteText\\([\"']([^\"']+)[\"']\\)","groups":[1]},
  {"id":"termux1","type":"TERMUX_COMMAND","regex":"(?i)\\bTermux\\(\\s*([\"'])((?:\\\\.|(?!\\1\\s*\\)).)*)\\1\\s*\\)","groups":[2]},
  {"id":"clickBtn1","type":"CLICK_BUTTON","regex":"(?i)\\bclick\\([\"']([^\"']+)[\"']\\)","groups":[1]},
  {"id":"longClickBtn1","type":"LONG_CLICK_BUTTON","regex":"(?i)\\blongClick\\([\"']([^\"']+)[\"']\\)","groups":[1]},
  {"id":"tapCoords1","type":"TAP_COORDINATES","regex":"(?i)\\btapAtCoordinates\\(\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*\\)","groups":[1,2]},
  {"id":"screenshot1","type":"TAKE_SCREENSHOT","regex":"(?i)\\btakeScreenshot\\(\\)"},
  {"id":"completed1","type":"COMPLETED","regex":"(?i)\\bcompleted\\(\\)"},
  {"id":"wait1","type":"WAIT","regex":"(?i)\\bWait\\(\\s*(\\d+)\\s*\\)","groups":[1]},
  {"id":"home1","type":"PRESS_HOME","regex":"(?i)\\bhome\\(\\)"},
  {"id":"back1","type":"PRESS_BACK","regex":"(?i)\\bback\\(\\)"},
  {"id":"recentApps1","type":"SHOW_RECENT_APPS","regex":"(?i)\\brecentApps\\(\\)"},
  {"id":"scrollDown1","type":"SCROLL_DOWN","regex":"(?i)\\bscrollDown\\(\\)"},
  {"id":"scrollUp1","type":"SCROLL_UP","regex":"(?i)\\bscrollUp\\(\\)"},
  {"id":"scrollLeft1","type":"SCROLL_LEFT","regex":"(?i)\\bscrollLeft\\(\\)"},
  {"id":"scrollRight1","type":"SCROLL_RIGHT","regex":"(?i)\\bscrollRight\\(\\)"},
  {"id":"scrollDownCoords","type":"SCROLL_DOWN_FROM_COORDINATES","regex":"(?i)\\bscrollDown\\s*\\(\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*,\\s*(\\d+)\\s*\\)","groups":[1,2,3,4]},
  {"id":"scrollUpCoords","type":"SCROLL_UP_FROM_COORDINATES","regex":"(?i)\\bscrollUp\\s*\\(\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*,\\s*(\\d+)\\s*\\)","groups":[1,2,3,4]},
  {"id":"scrollLeftCoords","type":"SCROLL_LEFT_FROM_COORDINATES","regex":"(?i)\\bscrollLeft\\s*\\(\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*,\\s*(\\d+)\\s*\\)","groups":[1,2,3,4]},
  {"id":"scrollRightCoords","type":"SCROLL_RIGHT_FROM_COORDINATES","regex":"(?i)\\bscrollRight\\s*\\(\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*,\\s*(\\d+)\\s*\\)","groups":[1,2,3,4]},
  {"id":"openApp1","type":"OPEN_APP","regex":"(?i)\\bopenApp\\([\"']([^\"']+)[\"']\\)","groups":[1]},
  {"id":"pinch1","type":"PINCH_GESTURE","regex":"(?i)\\bpinch\\(\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*,\\s*([\\d\\.%]+)\\s*,\\s*(\\d+)\\s*\\)","groups":[1,2,3,4,5]},
  {"id":"launchIntent1","type":"LAUNCH_INTENT","regex":"(?i)\\blaunchIntent\\([\"']([^\"']+)[\"']\\s*,\\s*[\"']([^\"']*)[\"']\\s*,\\s*[\"']([^\"']*)[\"']\\)","groups":[1,2,3]},
  {"id":"copyToClipboard1","type":"COPY_TO_CLIPBOARD","regex":"(?i)\\bcopyToClipboard\\([\"']([^\"']*)[\"']\\)","groups":[1]},
  {"id":"popUp1","type":"SHOW_POPUP","regex":"(?i)\\bpopUp\\([\"']([^\"']+)[\"'](?:\\s*,\\s*[\"']([^\"']*)[\"'])?(?:\\s*,\\s*[\"']([^\"']*)[\"'])?(?:\\s*,\\s*[\"']([^\"']*)[\"'])?(?:\\s*,\\s*[\"']([^\"']*)[\"'])?(?:\\s*,\\s*[\"']([^\"']*)[\"'])?\\)","groups":[1,2,3,4,5,6]}
]
    """.trimIndent()

    private val builtinPatterns: List<PatternInfo> by lazy { parsePatternsJson(BUILTIN_PATTERNS_JSON) }

    // ── Runtime state ───────────────────────────────────────────────────────────

    /** All active patterns: builtins + remote overrides */
    @Volatile private var activePatterns: List<PatternInfo> = emptyList()

    /** Custom action types from custom-action-types.json */
    @Volatile private var customActionPatterns: List<CustomActionTypeConfig.ParsedEntry> = emptyList()

    /** Ensure builtins are loaded before first use */
    private fun ensureBuiltinsLoaded() {
        if (activePatterns.isEmpty()) {
            activePatterns = builtinPatterns
        }
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /** Installs additional command-recognition patterns from remote JSON. */
    @Synchronized
    fun setRemotePatternOverrides(json: String): Int {
        ensureBuiltinsLoaded()
        val parsed = CommandPatternConfig.parse(json)
        val overrides = parsed.mapNotNull { override ->
            val type = CommandType.safeValueOf(override.commandType.name) ?: return@mapNotNull null
            val factory = COMMAND_FACTORY[type] ?: return@mapNotNull null
            try {
                val regex = try { Regex(override.regex.pattern) } catch (_: Exception) { null } ?: return@mapNotNull null
                PatternInfo(override.id, regex, type, emptyList()) { match ->
                    factory(match, (0 until match.groupValues.size - 1).toList())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping remote pattern '${override.id}': ${e.message}")
                null
            }
        }
        activePatterns = builtinPatterns + overrides
        Log.d(TAG, "Installed ${overrides.size} remote pattern override(s), total=${activePatterns.size}")
        return overrides.size
    }

    @Synchronized
    fun clearRemotePatternOverrides() {
        activePatterns = builtinPatterns
    }

    @Synchronized
    fun setCustomActionTypes(json: String): Int {
        customActionPatterns = CustomActionTypeConfig.parse(json)
        Log.d(TAG, "Installed ${customActionPatterns.size} custom action type(s)")
        return customActionPatterns.size
    }

    @Synchronized
    fun clearCustomActionTypes() {
        customActionPatterns = emptyList()
    }

    // ── Parsing ─────────────────────────────────────────────────────────────────

    private var textBuffer = ""
    private var shouldClearBuffer = false

    @Synchronized
    fun parseCommands(text: String, clearBuffer: Boolean = false): List<Command> {
        ensureBuiltinsLoaded()
        var commands: List<Command> = emptyList()
        try {
            resetBufferIfNeeded(clearBuffer)
            val normalizedText = normalizeText(text)
            textBuffer += normalizedText
            Log.d(TAG, "Current buffer for command parsing: $textBuffer")
            commands = processTextInternal(textBuffer)
            if (commands.isNotEmpty()) {
                shouldClearBuffer = true
                Log.d(TAG, "Commands found, buffer will be cleared on next call")
            }
            Log.d(TAG, "Found ${commands.size} commands in text")
            commands.forEach(::logCommandDetails)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing commands: ${e.message}", e)
        }
        return commands
    }

    private fun resetBufferIfNeeded(clearBuffer: Boolean) {
        if (clearBuffer || shouldClearBuffer) {
            textBuffer = ""
            shouldClearBuffer = false
            Log.d(TAG, "Buffer cleared")
        }
    }

    private fun logCommandDetails(command: Command) {
        when (command) {
            is Command.ClickButton -> Log.d(TAG, "Command details: ClickButton(\"${command.buttonText}\")")
            is Command.LongClickButton -> Log.d(TAG, "Command details: LongClickButton(\"${command.buttonText}\")")
            is Command.TapCoordinates -> Log.d(TAG, "Command details: TapCoordinates(${command.x}, ${command.y})")
            is Command.TakeScreenshot -> Log.d(TAG, "Command details: TakeScreenshot")
            is Command.Completed -> Log.d(TAG, "Command details: Completed")
            is Command.Wait -> Log.d(TAG, "Command details: Wait(${command.seconds})")
            is Command.PressHomeButton -> Log.d(TAG, "Command details: PressHomeButton")
            is Command.PressBackButton -> Log.d(TAG, "Command details: PressBackButton")
            is Command.ShowRecentApps -> Log.d(TAG, "Command details: ShowRecentApps")
            is Command.ScrollDown -> Log.d(TAG, "Command details: ScrollDown")
            is Command.ScrollUp -> Log.d(TAG, "Command details: ScrollUp")
            is Command.ScrollLeft -> Log.d(TAG, "Command details: ScrollLeft")
            is Command.ScrollRight -> Log.d(TAG, "Command details: ScrollRight")
            is Command.ScrollDownFromCoordinates -> Log.d(TAG, "Command details: ScrollDownFromCoordinates(${command.x}, ${command.y}, ${command.distance}, ${command.duration})")
            is Command.ScrollUpFromCoordinates -> Log.d(TAG, "Command details: ScrollUpFromCoordinates(${command.x}, ${command.y}, ${command.distance}, ${command.duration})")
            is Command.ScrollLeftFromCoordinates -> Log.d(TAG, "Command details: ScrollLeftFromCoordinates(${command.x}, ${command.y}, ${command.distance}, ${command.duration})")
            is Command.ScrollRightFromCoordinates -> Log.d(TAG, "Command details: ScrollRightFromCoordinates(${command.x}, ${command.y}, ${command.distance}, ${command.duration})")
            is Command.OpenApp -> Log.d(TAG, "Command details: OpenApp(\"${command.packageName}\")")
            is Command.PinchGesture -> Log.d(TAG, "Command details: PinchGesture(${command.centerX}, ${command.centerY}, ...)")
            is Command.WriteText -> Log.d(TAG, "Command details: WriteText(\"${command.text}\")")
            is Command.PressEnterKey -> Log.d(TAG, "Command details: PressEnterKey")
            is Command.TermuxCommand -> Log.d(TAG, "Command details: TermuxCommand(\"${command.command}\")")
            is Command.CopyToClipboard -> Log.d(TAG, "Command details: CopyToClipboard(\"${command.text}\")")
            is Command.WebViewCustomAction -> Log.d(TAG, "Command details: WebViewCustomAction(id=\"${command.id}\", groups=${command.groups})")
            is Command.LaunchIntent -> Log.d(TAG, "Command details: LaunchIntent(action=\"${command.action}\")")
        }
    }

    private fun processTextInternal(text: String): List<Command> {
        val foundRawMatches = collectRawMatches(text)
        val finalCommands = mutableListOf<Command>()
        val addedSingleInstanceCommands = mutableSetOf<CommandType>()
        foundRawMatches.sortBy { it.startIndex }
        Log.d(TAG, "Sorted raw matches (${foundRawMatches.size}): $foundRawMatches")
        var currentPosition = 0
        for (processedMatch in foundRawMatches) {
            val (startIndex, endIndex, command, commandType) = processedMatch
            if (startIndex >= currentPosition) {
                val isSingleInstanceDuplicate = commandType in SINGLE_INSTANCE_COMMAND_TYPES &&
                    !addedSingleInstanceCommands.add(commandType)
                if (isSingleInstanceDuplicate) {
                    Log.d(TAG, "Skipping duplicate single-instance command: $command")
                } else {
                    finalCommands.add(command)
                    currentPosition = endIndex + 1
                }
            } else {
                Log.d(TAG, "Skipping overlapping command: $command")
            }
        }
        return finalCommands
    }

    private fun collectRawMatches(text: String): MutableList<ProcessedMatch> {
        val foundRawMatches = mutableListOf<ProcessedMatch>()
        for (patternInfo in activePatterns) {
            try {
                patternInfo.regex.findAll(text).forEach { matchResult ->
                    try {
                        val command = patternInfo.commandBuilder(matchResult)
                        foundRawMatches.add(
                            ProcessedMatch(matchResult.range.first, matchResult.range.last, command, patternInfo.commandType)
                        )
                        Log.d(TAG, "Found match: pattern=${patternInfo.id}, command=$command")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error building command for ${patternInfo.id}: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error matching pattern ${patternInfo.id}: ${e.message}", e)
            }
        }
        // Custom action types (from custom-action-types.json)
        for (entry in customActionPatterns) {
            try {
                entry.regex.findAll(text).forEach { matchResult ->
                    try {
                        val groups = matchResult.groupValues.drop(1)
                        val command = Command.WebViewCustomAction(entry.id, groups)
                        foundRawMatches.add(
                            ProcessedMatch(matchResult.range.first, matchResult.range.last, command, CommandType.WEBVIEW_CUSTOM_ACTION)
                        )
                        Log.d(TAG, "Found custom action match: id=${entry.id}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error building WebViewCustomAction for ${entry.id}: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error matching custom action ${entry.id}: ${e.message}", e)
            }
        }
        return foundRawMatches
    }

    private fun normalizeText(text: String): String {
        var normalized = text.replace(Regex("\\s+"), " ")
        normalized = normalized.replace(Regex("\\r\\n|\\r"), "\n")
        return normalized.trim()
    }

    @Synchronized
    fun clearBuffer() {
        textBuffer = ""
        shouldClearBuffer = false
    }

    fun testCommandRecognition(commandText: String): List<Command> {
        clearBuffer()
        return parseCommands(commandText)
    }

    @Synchronized
    fun getBufferContent(): String = textBuffer

    // ── JSON parsing ────────────────────────────────────────────────────────────

    private fun parsePatternsJson(json: String): List<PatternInfo> {
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val obj = arr.getJSONObject(i)
                    val id = obj.getString("id")
                    val typeName = obj.getString("type")
                    val regexStr = obj.getString("regex")
                    val groupsJson = obj.optJSONArray("groups")
                    val groups = if (groupsJson != null) {
                        (0 until groupsJson.length()).map { groupsJson.getInt(it) - 1 }
                    } else emptyList()

                    val type = CommandType.safeValueOf(typeName)
                        ?: return@mapNotNull null.also { Log.w(TAG, "Unknown CommandType: $typeName") }

                    val factory = COMMAND_FACTORY[type]
                        ?: return@mapNotNull null.also { Log.w(TAG, "No factory for CommandType: $typeName") }

                    val regex = try { Regex(regexStr) } catch (e: Exception) {
                        Log.w(TAG, "Invalid regex for $id: ${e.message}")
                        return@mapNotNull null
                    }

                    PatternInfo(id, regex, type, groups) { match ->
                        if (type == CommandType.SHOW_POPUP) {
                            // Special handling: collect non-blank answer groups
                            val answers = groups.mapNotNull { idx ->
                                val v = match.groupValues.getOrNull(idx + 1)
                                if (v.isNullOrBlank()) null else v
                            }
                            Command.WebViewCustomAction("SHOW_POPUP", listOf(match.groupValues[groups.getOrElse(0) { 0 } + 1]) + answers)
                        } else {
                            factory(match, groups)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping malformed pattern entry: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse builtin patterns JSON: ${e.message}", e)
            emptyList()
        }
    }
}
