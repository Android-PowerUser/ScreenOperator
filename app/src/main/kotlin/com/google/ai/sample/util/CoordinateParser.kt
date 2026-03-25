package com.google.ai.sample.util

object CoordinateParser {
    fun parse(coordinateString: String, screenSize: Int): Float {
        return try {
            if (coordinateString.endsWith("%")) {
                val numericValue = coordinateString.removeSuffix("%").toFloat()
                (numericValue / 100.0f) * screenSize
            } else {
                coordinateString.toFloat()
            }
        } catch (e: NumberFormatException) {
            throw NumberFormatException("Invalid coordinate string: '$coordinateString'. Expected a number or percentage.")
        }
    }
}
