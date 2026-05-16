package com.ledga.app.ui.components.v2

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.theme.LedgaDanger
import com.ledga.app.ui.theme.LedgaText
import kotlin.math.min

/**
 * Circular progress ring used by Goal cards and Budget cards.
 *
 * - [progress] is clamped to 0f..1f, but values > 1 still render as an
 *   "over" state in [LedgaDanger] (over-budget).
 * - Sizes from the spec: 56 / 80 / 120; stroke 6 fixed.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    strokeWidth: Dp = 6.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    overColor: Color = LedgaDanger,
    label: String? = null,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceAtLeast(0f),
        label = "ring-progress",
    )
    val clamped = min(animated, 1f)
    val isOver = animated > 1f
    val fillColor = if (isOver) overColor else progressColor

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            val topLeft = Offset(sw / 2f, sw / 2f)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
            drawArc(
                color = fillColor,
                startAngle = -90f,
                sweepAngle = 360f * clamped,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
        }
        if (label != null) {
            Text(
                text = label,
                style = LedgaText.TitleS,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
