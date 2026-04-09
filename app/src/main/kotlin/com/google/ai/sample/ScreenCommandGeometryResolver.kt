package com.google.ai.sample

internal object ScreenCommandGeometryResolver {
    data class ResolvedPoint(val xPx: Float, val yPx: Float)

    data class ResolvedScrollGesture(
        val xPx: Float,
        val yPx: Float,
        val distancePx: Float,
        val durationMs: Long
    )

    enum class ScrollAxis { HORIZONTAL, VERTICAL }

    fun resolvePoint(
        x: String,
        y: String,
        screenWidth: Int,
        screenHeight: Int,
        coordinateResolver: (String, Int) -> Float
    ): ResolvedPoint {
        return ResolvedPoint(
            xPx = coordinateResolver(x, screenWidth),
            yPx = coordinateResolver(y, screenHeight)
        )
    }

    fun resolveScrollGesture(
        x: String,
        y: String,
        distance: String,
        durationMs: Long,
        axis: ScrollAxis,
        screenWidth: Int,
        screenHeight: Int,
        coordinateResolver: (String, Int) -> Float
    ): ResolvedScrollGesture {
        val point = resolvePoint(
            x = x,
            y = y,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            coordinateResolver = coordinateResolver
        )
        val distanceBasis = if (axis == ScrollAxis.HORIZONTAL) screenWidth else screenHeight
        val distancePx = coordinateResolver(distance, distanceBasis)
        return ResolvedScrollGesture(point.xPx, point.yPx, distancePx, durationMs)
    }
}
