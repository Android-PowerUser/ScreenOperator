package com.google.ai.sample.feature.multimodal

import com.google.ai.sample.util.Command
import com.google.ai.sample.util.CommandParser

internal data class ParsedCommandBatch(
    val commands: List<Command>,
    val hasTakeScreenshotCommand: Boolean,
    val commandDescriptions: String
)

internal object PhotoReasoningCommandProcessing {
    fun parseForStreaming(accumulatedText: String): List<Command> {
        return CommandParser.parseCommands(accumulatedText, clearBuffer = true)
    }

    fun parseForFinalExecution(text: String): ParsedCommandBatch {
        val commands = CommandParser.parseCommands(text)
        return ParsedCommandBatch(
            commands = commands,
            hasTakeScreenshotCommand = commands.any { it is Command.TakeScreenshot },
            commandDescriptions = commands.joinToString("; ") { it.toString() }
        )
    }
}
