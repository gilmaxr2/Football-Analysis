package com.footballanalysis.app

import android.graphics.Color
import android.graphics.PointF
import java.util.UUID

data class TacticalClip(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val playerNumber: Int? = null
) {
    init {
        require(startTimeMs >= 0)
        require(endTimeMs > startTimeMs)
    }
}

enum class DrawingTool {
    FREEHAND,
    ARROW,
    LINE,
    DASHED,
    CIRCLE,
    RECTANGLE,
    ZONE,
    OFFSIDE,
    ERASER,
    NONE
}

data class TacticalDrawing(
    val id: String = UUID.randomUUID().toString(),
    val tool: DrawingTool,
    val points: List<PointF>,
    val color: Int = Color.YELLOW,
    val strokeWidth: Float = 6f,
    val timestampMs: Long,
    val durationMs: Long = 0L
)
