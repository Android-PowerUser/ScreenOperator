package com.google.ai.sample.util

object CoordinateParser {
    fun parse(coordinateString: String, screenSize: Int): Float {
        return if (coordinateString.endsWith("%")) {
            val numericValue = coordinateString.removeSuffix("%").toFloat()
            (numericValue / 100.0f) * screenSize
        } else {
            coordinateString.toFloat()
        }
    }
}
