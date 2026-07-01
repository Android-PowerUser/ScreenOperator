package com.google.ai.sample.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommandParserTest {

    @Before
    fun setUp() {
        CommandParser.clearBuffer()
        CommandParser.clearRemotePatternOverrides()
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
    fun parseCommands_extractsCopyToClipboardCommand() {
        val input = """copyToClipboard("hello world")"""

        val commands = CommandParser.parseCommands(input, clearBuffer = true)

        assertEquals(1, commands.size)
        val command = commands[0]
        assertTrue(command is Command.CopyToClipboard)
        assertEquals("hello world", (command as Command.CopyToClipboard).text)
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

    @Test
    fun parseCommands_extractsWaitCommand() {
        val commands = CommandParser.parseCommands("Wait(7) takeScreenshot()", clearBuffer = true)

        assertEquals(2, commands.size)
        val wait = commands.first()
        assertTrue(wait is Command.Wait)
        assertEquals(7L, (wait as Command.Wait).seconds)
        assertTrue(commands[1] is Command.TakeScreenshot)
    }

    @Test
    fun parseCommands_extractsCompletedCommand() {
        val commands = CommandParser.parseCommands("completed()", clearBuffer = true)

        assertEquals(1, commands.size)
        assertTrue(commands.first() is Command.Completed)
    }

    @Test
    fun parseCommands_keepsSingleCompletedCommandInstance() {
        val commands = CommandParser.parseCommands(
            "completed() completed()",
            clearBuffer = true
        )

        assertEquals(1, commands.count { it is Command.Completed })
    }

    @Test
    fun parseCommands_extractsTermuxCommandWithNestedSingleQuotes() {
        val commands = CommandParser.parseCommands(
            """Termux("su -c 'ifconfig'")""",
            clearBuffer = true
        )

        assertEquals(1, commands.size)
        val command = commands.first()
        assertTrue(command is Command.TermuxCommand)
        assertEquals("su -c 'ifconfig'", (command as Command.TermuxCommand).command)
    }

    @Test
    fun parseCommands_extractsTermuxCommandWithNestedDoubleQuotes() {
        val commands = CommandParser.parseCommands(
            """Termux('su -c "ifconfig"')""",
            clearBuffer = true
        )

        assertEquals(1, commands.size)
        val command = commands.first()
        assertTrue(command is Command.TermuxCommand)
        assertEquals("su -c \"ifconfig\"", (command as Command.TermuxCommand).command)
    }

    @Test
    fun setRemotePatternOverrides_recognizesAlternateSyntaxForExistingCommandType() {
        // A hypothetical new model emits "Click('...')" (capitalized, single quotes) instead
        // of the built-in "click(\"...\")" syntax. Without an override, this is NOT recognized:
        val before = CommandParser.parseCommands("Click('Login')", clearBuffer = true)
        assertEquals(0, before.size)

        val applied = CommandParser.setRemotePatternOverrides(
            """[{"id":"clickAlt","commandType":"CLICK_BUTTON","regex":"(?i)\\bClick\\([\"']([^\"']+)[\"']"}]"""
        )
        assertEquals(1, applied)

        val after = CommandParser.parseCommands("Click('Login')", clearBuffer = true)
        assertEquals(1, after.size)
        assertTrue(after.first() is Command.ClickButton)
        assertEquals("Login", (after.first() as Command.ClickButton).buttonText)
    }

    @Test
    fun setRemotePatternOverrides_skipsUnknownCommandType() {
        val applied = CommandParser.setRemotePatternOverrides(
            """[{"id":"bogus","commandType":"DOES_NOT_EXIST","regex":"(?i)\\bfoo\\(\\)"}]"""
        )
        assertEquals(0, applied)
    }

    @Test
    fun setRemotePatternOverrides_skipsInvalidRegexWithoutCrashing() {
        val applied = CommandParser.setRemotePatternOverrides(
            """[{"id":"badRegex","commandType":"CLICK_BUTTON","regex":"("}]"""
        )
        assertEquals(0, applied)
    }

    @Test
    fun clearRemotePatternOverrides_revertsToBuiltInPatternsOnly() {
        CommandParser.setRemotePatternOverrides(
            """[{"id":"clickAlt","commandType":"CLICK_BUTTON","regex":"(?i)\\bClick\\([\"']([^\"']+)[\"']"}]"""
        )
        assertEquals(1, CommandParser.parseCommands("Click('Login')", clearBuffer = true).size)

        CommandParser.clearRemotePatternOverrides()

        assertEquals(0, CommandParser.parseCommands("Click('Login')", clearBuffer = true).size)
    }

}
