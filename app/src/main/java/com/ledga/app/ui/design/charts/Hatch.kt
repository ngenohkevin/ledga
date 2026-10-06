package com.ledga.app.ui.design.charts

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

/** A column with [top]-radius top corners and [bottom]-radius bottom corners. */
internal fun barPath(rect: Rect, top: Float, bottom: Float): Path = Path().apply {
    addRoundRect(
        RoundRect(
            rect,
            topLeft = CornerRadius(top),
            topRight = CornerRadius(top),
            bottomRight = CornerRadius(bottom),
            bottomLeft = CornerRadius(bottom),
        ),
    )
}

/**
 * The in-progress period (spec §10.4, mockup `.prog`): 45° stripes, 4 dp on and 3 dp off, inside a 1.5 dp outline.
 */
internal fun DrawScope.hatchedBar(rect: Rect, top: Float, bottom: Float, color: Color) {
    if (rect.width <= 0f || rect.height <= 0f) return
    val path = barPath(rect, top, bottom)
    val stripe = 4.dp.toPx()
    val step = (stripe + 3.dp.toPx()) * 1.41421356f // horizontal spacing that leaves 3 dp between 45° stripes
    clipPath(path) {
        var x = rect.left - rect.height
        while (x < rect.right) {
            drawLine(color, Offset(x, rect.bottom), Offset(x + rect.height, rect.top), strokeWidth = stripe)
            x += step
        }
    }
    drawPath(path, color, style = Stroke(1.5.dp.toPx()))
}
