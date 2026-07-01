package com.google.ai.sample.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserPinchTest {

    private fun parse(text: String): Command? {
        return CommandParser.extractCommandsFromText(text).firstOrNull()
    }

    @Test
    fun parsePinchWithPixels() {
        val cmd = parse("""pinch(540, 960, 50, 300, 400)""") as? Command.PinchGesture
        assertEquals("540", cmd?.centerX)
        assertEquals("960", cmd?.centerY)
        assertEquals("50", cmd?.startDistance)
        assertEquals("300", cmd?.endDistance)
        assertEquals(400L, cmd?.durationMs)
    }

    @Test
    fun parsePinchWithPercentages() {
        val cmd = parse("""pinch("50%", "50%", "10%", "60%", 300)""") as? Command.PinchGesture
        assertEquals("50%", cmd?.centerX)
        assertEquals("50%", cmd?.centerY)
        assertEquals("10%", cmd?.startDistance)
        assertEquals("60%", cmd?.endDistance)
        assertEquals(300L, cmd?.durationMs)
    }

    @Test
    fun parsePinchCaseInsensitive() {
        val cmd = parse("""Pinch(540, 960, 200, 50, 500)""")
        assertTrue(cmd is Command.PinchGesture)
    }

    @Test
    fun parsePinchWithSpaces() {
        val cmd = parse("""pinch( 540 , 960 , 50 , 300 , 400 )""") as? Command.PinchGesture
        assertEquals("540", cmd?.centerX)
        assertEquals(400L, cmd?.durationMs)
    }

    @Test
    fun noParsePinchWithMissingArgs() {
        // Only 4 args instead of 5 - should not match
        assertNull(parse("pinch(540, 960, 50, 300)"))
    }
}
