package com.ledga.app.ui.design.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.ChartTones
import androidx.compose.runtime.remember

/**
 * A small bar strip with no axes (spec §10.6): Home's 6-period spending bars and the tracker tiles' spark bars.
 * Bars draw in [color]'s soft tone and the [highlightIndex] bar in its strong tone; the [inProgressIndex] bar is hatched in
 * the strong tone. Every bar is at least 3:1 against the card (R27). Zero bars aren't drawn.
 */
@Composable
fun MiniBars(
    values: List<Long>,
    modifier: Modifier = Modifier,
    highlightIndex: Int? = null,
    inProgressIndex: Int? = null,
    color: Color = LedgaTheme.colors.chartPrimary,
    height: Dp = 64.dp,
    gap: Dp = 8.dp,
    cornerTop: Dp = 7.dp,
    cornerBottom: Dp = 3.dp,
    minBar: Dp = 3.dp,
    contentDescription: String? = null,
) {
    val surface = LedgaTheme.colors.surface
    val strong = remember(color, surface) { ChartTones.strong(color, surface) }
    val soft = remember(color, surface) { ChartTones.soft(color, surface) }
    val max = ChartMath.scaleMax(values, null)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        val n = values.size
        if (n == 0) return@Canvas
        val g = gap.toPx()
        val w = (size.width - g * (n - 1)) / n
        values.forEachIndexed { i, v ->
            val f = ChartMath.fraction(v, max)
            if (f <= 0f) return@forEachIndexed
            val h = maxOf(f * size.height, minBar.toPx())
            val left = i * (w + g)
            val rect = Rect(left, size.height - h, left + w, size.height)
            when (i) {
                inProgressIndex -> hatchedBar(rect, cornerTop.toPx(), cornerBottom.toPx(), strong)
                highlightIndex -> drawPath(barPath(rect, cornerTop.toPx(), cornerBottom.toPx()), strong)
                else -> drawPath(barPath(rect, cornerTop.toPx(), cornerBottom.toPx()), soft)
            }
        }
    }
}

/** A tracker tile's 6-month spark bars: soft bars, with the current month in the category's strong tone. */
@Composable
fun SparkBars(values: List<Long>, color: Color, modifier: Modifier = Modifier) = MiniBars(
    values = values,
    modifier = modifier,
    highlightIndex = values.lastIndex,
    color = color,
    height = 20.dp,
    gap = 3.dp,
    cornerTop = 2.dp,
    cornerBottom = 2.dp,
    minBar = 2.dp,
)
