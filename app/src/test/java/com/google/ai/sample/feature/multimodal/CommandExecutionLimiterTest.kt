package com.google.ai.sample.feature.multimodal

import com.google.ai.sample.util.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandExecutionLimiterTest {

    private val fiveClicks = listOf(
        Command.ClickButton("one"),
        Command.ClickButton("two"),
        Command.ClickButton("three"),
        Command.ClickButton("four"),
        Command.ClickButton("five")
    )

    @Test
    fun truncate_unlimitedWhenMaxIsZeroOrNegative() {
        val zero = CommandExecutionLimiter.truncate(fiveClicks, maxCommandsPerMessage = 0)
        val negative = CommandExecutionLimiter.truncate(fiveClicks, maxCommandsPerMessage = -1)

        assertFalse(zero.wasTruncated)
        assertEquals(5, zero.commandsToExecute.size)
        assertFalse(negative.wasTruncated)
        assertEquals(5, negative.commandsToExecute.size)
    }

    @Test
    fun truncate_keepsAllWhenUnderOrAtLimit() {
        val result = CommandExecutionLimiter.truncate(fiveClicks, maxCommandsPerMessage = 5)

        assertFalse(result.wasTruncated)
        assertEquals(5, result.executedCount)
        assertEquals(5, result.totalCount)
    }

    @Test
    fun truncate_keepsOnlyFirstNInOriginalOrderWhenOverLimit() {
        val result = CommandExecutionLimiter.truncate(fiveClicks, maxCommandsPerMessage = 2)

        assertTrue(result.wasTruncated)
        assertEquals(5, result.totalCount)
        assertEquals(2, result.executedCount)
        assertEquals(
            listOf(Command.ClickButton("one"), Command.ClickButton("two")),
            result.commandsToExecute
        )
    }

    @Test
    fun isWithinLimit_unlimitedWhenMaxIsZeroOrNegative() {
        assertTrue(CommandExecutionLimiter.isWithinLimit(index = 99, maxCommandsPerMessage = 0))
        assertTrue(CommandExecutionLimiter.isWithinLimit(index = 99, maxCommandsPerMessage = -5))
    }

    @Test
    fun isWithinLimit_respectsBoundary() {
        assertTrue(CommandExecutionLimiter.isWithinLimit(index = 0, maxCommandsPerMessage = 2))
        assertTrue(CommandExecutionLimiter.isWithinLimit(index = 1, maxCommandsPerMessage = 2))
        assertFalse(CommandExecutionLimiter.isWithinLimit(index = 2, maxCommandsPerMessage = 2))
        assertFalse(CommandExecutionLimiter.isWithinLimit(index = 3, maxCommandsPerMessage = 2))
    }
}
