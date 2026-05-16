package com.ledga.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ledga.app.util.CurrencyFormatter

data class DonutSegment(
    val label: String,
    val value: Float,
    val color: Color
)

@Composable
fun DonutChart(
    segments: List<DonutSegment>,
    totalAmount: Double,
    modifier: Modifier = Modifier
) {
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(segments) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(800))
    }

    val total = segments.sumOf { it.value.toDouble() }.toFloat()

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Force square so the donut stays circular even when the parent
            // is narrower than the historical 180dp fixed size (e.g. inside
            // a 50/50 Row in Home's "Where it went" card).
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Stroke scales with the box so the ring looks right at any size.
            val strokeWidth = (size.minDimension * 0.18f).coerceAtMost(32.dp.toPx())
            val radius = (size.minDimension - strokeWidth) / 2
            var startAngle = -90f

            if (total == 0f) {
                drawArc(
                    color = Color.LightGray,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                return@Canvas
            }

            segments.forEach { segment ->
                val sweep = (segment.value / total) * 360f * animationProgress.value
                drawArc(
                    color = segment.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
                startAngle += (segment.value / total) * 360f * animationProgress.value
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = CurrencyFormatter.formatKshCompact(totalAmount),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Total Spent",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
