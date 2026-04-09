package com.google.ai.sample.util

object CoordinateParser {
    fun parse(coordinateString: String, screenSize: Int): Float {
        return try {
            parseInternal(coordinateString, screenSize)
        } catch (e: NumberFormatException) {
            throw NumberFormatException("Invalid coordinate string: '$coordinateString'. Expected a number or percentage.")
        }
    }

    private fun parseInternal(coordinateString: String, screenSize: Int): Float {
        if (!coordinateString.endsWith("%")) return coordinateString.toFloat()
        val numericValue = coordinateString.removeSuffix("%").toFloat()
        return (numericValue / 100.0f) * screenSize
    }
}
