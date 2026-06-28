package com.google.ai.sample.feature.multimodal

import com.google.ai.sample.util.Command

/**
 * Caps how many commands from a single parsed AI response are allowed to execute, per the
 * remotely configurable [com.google.ai.sample.util.ExecutionPolicyConfig.Policy].
 *
 * Kept as a small, pure unit with no Android/ViewModel dependencies (mirrors the existing
 * [PhotoReasoningCommandExecutionGuard] pattern) so the truncation boundary itself can be unit
 * tested without spinning up the whole app.
 */
internal object CommandExecutionLimiter {

    data class Result(
        /** The prefix of [commands] (in original order) that is still allowed to execute. */
        val commandsToExecute: List<Command>,
        val wasTruncated: Boolean,
        val totalCount: Int,
        val executedCount: Int
    )

    /**
     * @param commands the full ordered list of commands parsed from one AI response.
     * @param maxCommandsPerMessage commands beyond this many (counting from the start, in the
     *   order the AI wrote them) are dropped. A value <= 0 means "unlimited" - [commands] is
     *   returned unchanged, matching the existing (pre-limit) behavior.
     */
    fun truncate(commands: List<Command>, maxCommandsPerMessage: Int): Result {
        if (maxCommandsPerMessage <= 0 || commands.size <= maxCommandsPerMessage) {
            return Result(
                commandsToExecute = commands,
                wasTruncated = false,
                totalCount = commands.size,
                executedCount = commands.size
            )
        }
        return Result(
            commandsToExecute = commands.take(maxCommandsPerMessage),
            wasTruncated = true,
            totalCount = commands.size,
            executedCount = maxCommandsPerMessage
        )
    }

    /**
     * True if the command at [index] (0-based, in the original parsed order for the current
     * message) is still allowed to execute under [maxCommandsPerMessage]. Used by incremental/
     * streaming execution, which processes commands one at a time as they arrive rather than as
     * a single pre-built list, so it needs an index-based check instead of [truncate].
     */
    fun isWithinLimit(index: Int, maxCommandsPerMessage: Int): Boolean {
        return maxCommandsPerMessage <= 0 || index < maxCommandsPerMessage
    }
}
