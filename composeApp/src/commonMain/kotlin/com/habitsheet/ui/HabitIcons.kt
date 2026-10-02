package com.habitsheet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LocalContentColor

internal enum class HabitIconGlyph {
    Tracker,
    Plan,
    Manage,
    Settings,
    Add,
    Back,
    Forward,
    Share,
    Edit,
    Up,
    Down,
    Categories,
}

/** A small shared vector set so Android and iOS render identical, non-emoji icons. */
@Composable
internal fun HabitIcon(
    glyph: HabitIconGlyph,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier) {
        val unit = minOf(size.width, size.height) / 24f
        val strokeWidth = 1.8f * unit
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, point(x1, y1), point(x2, y2), strokeWidth, StrokeCap.Round)

        when (glyph) {
            HabitIconGlyph.Plan -> {
                drawRoundRect(tint, topLeft = point(3f, 5f), size = Size(18f * unit, 16f * unit), cornerRadius = CornerRadius(2f * unit), style = stroke)
                line(3f, 10f, 21f, 10f)
                line(8f, 3f, 8f, 7f)
                line(16f, 3f, 16f, 7f)
                drawCircle(tint, 1.2f * unit, point(8f, 14f))
                drawCircle(tint, 1.2f * unit, point(13f, 14f))
            }
            HabitIconGlyph.Tracker -> {
                drawRoundRect(
                    tint,
                    topLeft = point(3f, 3f),
                    size = Size(18f * unit, 18f * unit),
                    cornerRadius = CornerRadius(4f * unit),
                    style = stroke,
                )
                line(7f, 12f, 10.2f, 15.2f)
                line(10.2f, 15.2f, 17.2f, 8.4f)
            }
            HabitIconGlyph.Manage -> {
                line(4f, 7f, 20f, 7f)
                line(4f, 12f, 20f, 12f)
                line(4f, 17f, 20f, 17f)
                drawCircle(tint, 2.2f * unit, point(9f, 7f))
                drawCircle(tint, 2.2f * unit, point(15f, 12f))
                drawCircle(tint, 2.2f * unit, point(11f, 17f))
            }
            HabitIconGlyph.Settings -> {
                drawCircle(tint, 4.2f * unit, point(12f, 12f), style = stroke)
                drawCircle(tint, 1.3f * unit, point(12f, 12f))
                listOf(
                    12f to 2.5f, 12f to 21.5f, 2.5f to 12f, 21.5f to 12f,
                    5.3f to 5.3f, 18.7f to 18.7f, 18.7f to 5.3f, 5.3f to 18.7f,
                ).forEach { (x, y) ->
                    val dx = x - 12f
                    val dy = y - 12f
                    val length = kotlin.math.sqrt(dx * dx + dy * dy)
                    line(12f + dx * 6.2f / length, 12f + dy * 6.2f / length, x, y)
                }
            }
            HabitIconGlyph.Add -> {
                line(12f, 5f, 12f, 19f)
                line(5f, 12f, 19f, 12f)
            }
            HabitIconGlyph.Back, HabitIconGlyph.Forward -> {
                val path = Path()
                if (glyph == HabitIconGlyph.Back) {
                    path.moveTo(15.5f * unit, 4.5f * unit)
                    path.lineTo(8f * unit, 12f * unit)
                    path.lineTo(15.5f * unit, 19.5f * unit)
                } else {
                    path.moveTo(8.5f * unit, 4.5f * unit)
                    path.lineTo(16f * unit, 12f * unit)
                    path.lineTo(8.5f * unit, 19.5f * unit)
                }
                drawPath(path, tint, style = stroke)
            }
            HabitIconGlyph.Share -> {
                drawRoundRect(
                    tint,
                    topLeft = point(4f, 9f),
                    size = Size(16f * unit, 12f * unit),
                    cornerRadius = CornerRadius(2.5f * unit),
                    style = stroke,
                )
                line(12f, 15f, 12f, 3f)
                line(12f, 3f, 7.8f, 7.2f)
                line(12f, 3f, 16.2f, 7.2f)
            }
            HabitIconGlyph.Edit -> {
                line(5f, 19f, 8.5f, 18.3f)
                line(8.5f, 18.3f, 19f, 7.8f)
                line(16.2f, 5f, 19f, 7.8f)
                line(5f, 19f, 5.8f, 15.5f)
                line(5.8f, 15.5f, 16.2f, 5f)
            }
            HabitIconGlyph.Up -> {
                line(6f, 15f, 12f, 9f)
                line(12f, 9f, 18f, 15f)
            }
            HabitIconGlyph.Down -> {
                line(6f, 9f, 12f, 15f)
                line(12f, 15f, 18f, 9f)
            }
            HabitIconGlyph.Categories -> {
                listOf(4f to 4f, 13f to 4f, 4f to 13f, 13f to 13f).forEach { (x, y) ->
                    drawRoundRect(
                        tint,
                        topLeft = point(x, y),
                        size = Size(7f * unit, 7f * unit),
                        cornerRadius = CornerRadius(1.5f * unit),
                        style = stroke,
                    )
                }
            }
        }
    }
}
