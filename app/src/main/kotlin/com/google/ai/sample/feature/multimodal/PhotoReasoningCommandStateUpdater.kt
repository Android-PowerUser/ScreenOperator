package com.google.ai.sample.feature.multimodal

import com.google.ai.sample.util.Command

internal object PhotoReasoningCommandStateUpdater {
    fun appendCommand(existing: List<Command>, command: Command): List<Command> {
        return existing.toMutableList().also { it.add(command) }
    }

    fun appendCommands(existing: List<Command>, commands: List<Command>): List<Command> {
        return existing.toMutableList().also { it.addAll(commands) }
    }

    fun buildDetectedStatus(commandDescriptions: String): String {
        return "Commands detected: $commandDescriptions"
    }
}
