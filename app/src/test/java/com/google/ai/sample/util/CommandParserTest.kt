package com.google.ai.sample.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommandParserTest {

    @Before
    fun setUp() {
        CommandParser.clearBuffer()
    }

    @Test
    fun parseCommands_extractsClickAndWriteTextCommands() {
        val input = """click("Login") writeText("hello")"""

        val commands = CommandParser.parseCommands(input, clearBuffer = true)

        assertEquals(2, commands.size)
        assertTrue(commands[0] is Command.ClickButton)
        assertTrue(commands[1] is Command.WriteText)
    }

    @Test
    fun clearBuffer_resetsParserBufferState() {
        CommandParser.parseCommands("""click("OK")""")
        assertTrue(CommandParser.getBufferContent().isNotBlank())

        CommandParser.clearBuffer()

        assertEquals("", CommandParser.getBufferContent())
    }

    @Test
    fun parseCommands_setsBufferClearFlagAfterMatch() {
        val first = CommandParser.parseCommands("""takeScreenshot()""", clearBuffer = true)
        assertEquals(1, first.size)

        val second = CommandParser.parseCommands("", clearBuffer = false)

        assertEquals(0, second.size)
        assertEquals("", CommandParser.getBufferContent())
    }

    @Test
    fun parseCommands_keepsSingleScreenshotCommandInstance() {
        val commands = CommandParser.parseCommands(
            """takeScreenshot() takeScreenshot()""",
            clearBuffer = true
        )

        assertEquals(1, commands.count { it is Command.TakeScreenshot })
    }

    @Test
    fun parseCommands_extractsEnterCommand() {
        val commands = CommandParser.parseCommands("enter()", clearBuffer = true)
        assertEquals(1, commands.size)
        assertTrue(commands.first() is Command.PressEnterKey)
    }

    @Test
    fun parseCommands_extractsRetrieveCommand() {
        val commands = CommandParser.parseCommands("retrieve(\"Termux\")", clearBuffer = true)
        assertEquals(1, commands.size)
        assertTrue(commands.first() is Command.Retrieve)
    }
}
