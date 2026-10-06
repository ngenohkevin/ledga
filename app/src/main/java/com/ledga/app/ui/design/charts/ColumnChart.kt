package com.ledga.app.ui.design.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.ChartTones
import com.ledga.app.ui.design.tokens.Radii
import com.ledga.app.ui.design.type.LedgaType

/**
 * The full column chart (spec §10.6, refinement R17), used for:
 * - Spending's 8–12 months;
 * - a tracker's months, where [dimUnselected] draws every bar but the selected one in its soft tone and [average] draws the dashed line;
 * - the stacked Trackers chart, with one colour per segment.
 *
 * Bars are layout children, so each is a TalkBack node reading its [Bar.speech], and is tap-to-select when
 * [onSelect] is set. [summary] describes the whole chart. The selected bar's [tooltip] floats above it, kept inside
 * the chart's width.
 */
@Composable
fun ColumnChart(
    bars: List<Bar>,
    colors: List<Color>,
    summary: String,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
    dimUnselected: Boolean = false,
    average: Long? = null,
    averageLabel: String = "AVG",
    showGrid: Boolean = false,
    height: Dp = 150.dp,
    tooltip: (@Composable (Int) -> Unit)? = null,
) {
    val c = LedgaTheme.colors
    // A stacked Trackers chart with nothing tracked passes no colours: draw in chartPrimary rather than crash.
    val colors = colors.ifEmpty { listOf(c.chartPrimary) }
    // R27 (owner decision 2026-10-06): bars meet WCAG 1.4.11 on the card. Strong = selected, current or undimmed.
    val strong = remember(colors, c.surface) { colors.map { ChartTones.strong(it, c.surface) } }
    val soft = remember(colors, c.surface) { colors.map { ChartTones.soft(it, c.surface) } }
    val measurer = rememberTextMeasurer()
    val top = ChartMath.scaleMax(bars.map { it.total }, average)
    val headroom = if (tooltip != null) 34.dp else 8.dp
    val gap = 6.dp
    val selected = selectedIndex?.takeIf { it in bars.indices }
    val avg = average?.takeIf { it > 0L }
    // Where the tooltip landed: a gridline value it would cover is left out rather than half hidden.
    var tipBox by remember { mutableStateOf<Rect?>(null) }
    val shownTip = if (selected != null && tooltip != null) tipBox else null

    Column(modifier.fillMaxWidth().semantics { contentDescription = summary }) {
        Box(Modifier.fillMaxWidth().height(height)) {
            Canvas(Modifier.fillMaxSize()) {
                val plot = size.height - headroom.toPx()
                fun yOf(v: Long) = size.height - ChartMath.fraction(v, top) * plot
                if (showGrid) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                    ChartMath.gridLines(top).forEach { v ->
                        val y = yOf(v)
                        drawLine(c.line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dash)
                    }
                }
                if (avg != null) {
                    val y = yOf(avg)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                    drawLine(c.warning, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = dash)
                }
            }
            Row(Modifier.fillMaxSize().padding(top = headroom), horizontalArrangement = Arrangement.spacedBy(gap)) {
                bars.forEachIndexed { index, bar ->
                    val isSelected = index == selected
                    val select = if (onSelect != null) Modifier.selectable(selected = isSelected, onClick = { onSelect(index) }) else Modifier
                    Canvas(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(select)
                            .semantics {
                                contentDescription = bar.speech
                                this.selected = isSelected
                            },
                    ) {
                        drawBar(bar, top, if (dimUnselected && !isSelected) soft else strong, strong)
                    }
                }
            }
            // Over the bars: gridline values (right) and the average's label (left), on the card colour so they stay readable.
            if (avg != null || showGrid) {
                Canvas(Modifier.fillMaxSize()) {
                    val plot = size.height - headroom.toPx()
                    fun yOf(v: Long) = size.height - ChartMath.fraction(v, top) * plot
                    if (showGrid) {
                        ChartMath.gridLines(top).forEach { v ->
                            val label = measurer.measure(AmountFormat.compact(v), LedgaType.overline.copy(color = c.muted))
                            val at = Offset(size.width - label.size.width - 2.dp.toPx(), (yOf(v) - label.size.height - 1.dp.toPx()).coerceAtLeast(0f))
                            val plate = Rect(Offset(at.x - 2.dp.toPx(), at.y), Size(label.size.width + 4.dp.toPx(), label.size.height.toFloat()))
                            if (shownTip?.overlaps(plate) != true) {
                                drawRect(c.surface, plate.topLeft, plate.size)
                                drawText(label, topLeft = at)
                            }
                        }
                    }
                    if (avg != null) {
                        val label = measurer.measure(averageLabel, LedgaType.overline.copy(color = c.warning))
                        val at = Offset(0f, (yOf(avg) - label.size.height - 2.dp.toPx()).coerceAtLeast(0f))
                        drawRect(c.surface, at, Size(label.size.width + 4.dp.toPx(), label.size.height.toFloat()))
                        drawText(label, topLeft = at)
                    }
                }
            }
            if (selected != null && tooltip != null) {
                TooltipAt(selected, bars.map { ChartMath.fraction(it.total, top) }, headroom, gap, onPlaced = { tipBox = it }) { tooltip(selected) }
            }
        }
        // One shared size for every label: full names while they fit (down to 8 sp), else first letters ("J F M A …").
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            val fitted = rememberFittedLabels(bars.map { it.label }, maxWidth, gap)
            Row(Modifier.fillMaxWidth().testTag(ChartTags.LABELS), horizontalArrangement = Arrangement.spacedBy(gap)) {
                fitted.texts.forEachIndexed { index, text ->
                    val isSelected = index == selected
                    Text(
                        text,
                        // Hidden from TalkBack: each bar already reads its label in its speech.
                        Modifier.weight(1f).semantics { hideFromAccessibility() },
                        style = fitted.style.copy(fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium),
                        color = if (isSelected) c.ink else c.muted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/**
 * Stacked segments, bottom up. Each takes its exact share of the height, with a 2 dp gap between segments. The
 * top segment gets 6 dp corners and the base 2 dp. An in-progress bar is hatched in the strong colours.
 */
private fun DrawScope.drawBar(bar: Bar, top: Long, fills: List<Color>, strong: List<Color>) {
    val gap = 2.dp.toPx()
    val radiusTop = 6.dp.toPx()
    val radiusInner = 3.dp.toPx()
    val radiusBase = 2.dp.toPx()
    val minBar = 2.dp.toPx()
    val positive = bar.segments.indices.filter { bar.segments[it] > 0L }
    var bottom = size.height
    positive.forEachIndexed { k, j ->
        val h = maxOf(ChartMath.fraction(bar.segments[j], top) * size.height, minBar)
        val isBase = k == 0
        val isTop = k == positive.lastIndex
        val rect = Rect(0f, bottom - h + if (isTop) 0f else gap / 2, size.width, bottom - if (isBase) 0f else gap / 2)
        val upper = if (isTop) radiusTop else radiusInner
        val lower = if (isBase) radiusBase else radiusInner
        if (bar.inProgress) {
            hatchedBar(rect, upper, lower, strong.getOrElse(j) { strong.last() })
        } else {
            drawPath(barPath(rect, upper, lower), fills.getOrElse(j) { fills.last() })
        }
        bottom -= h
    }
}

/** Places [content] where [ChartMath.tooltipPosition] says, over bar [index] of bars standing at [fractions]. */
@Composable
private fun TooltipAt(index: Int, fractions: List<Float>, headroom: Dp, gap: Dp, onPlaced: (Rect) -> Unit, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val tip = measurables.first().measure(Constraints())
        layout(constraints.maxWidth, constraints.maxHeight) {
            val (x, y) = ChartMath.tooltipPosition(
                index, fractions, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), headroom.toPx(), gap.toPx(),
                tip.width, tip.height, 4.dp.toPx(),
            )
            tip.place(x, y)
            onPlaced(Rect(x.toFloat(), y.toFloat(), (x + tip.width).toFloat(), (y + tip.height).toFloat()))
        }
    }
}

/** The selected bar's tooltip: "Ksh 1,850 · Sep" over "3 payments", in canvas on ink (mockup `.tip`). */
@Composable
fun ChartTooltip(title: String, caption: String? = null) {
    val c = LedgaTheme.colors
    Column(Modifier.clip(RoundedCornerShape(Radii.tooltip)).background(c.ink).padding(horizontal = 8.dp, vertical = 5.dp)) {
        Text(title, style = LedgaType.label, color = c.canvas)
        if (caption != null) Text(caption, style = LedgaType.caption, color = c.canvas)
    }
}

/** Swatch + name for each series of a stacked chart. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartLegend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (name, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(ChartTones.strong(color, c.surface)))
                Spacer(Modifier.width(5.dp))
                Text(name, style = LedgaType.caption, color = c.muted)
            }
        }
    }
}

private const val MIN_LABEL_SP = 8f

private class FittedLabels(val texts: List<String>, val style: TextStyle)

/** The largest shared caption size at which every label fits its column, trying full labels before first letters. */
@Composable
private fun rememberFittedLabels(labels: List<String>, width: Dp, gap: Dp): FittedLabels {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, width, gap, density) {
        val base = LedgaType.caption
        if (labels.isEmpty()) return@remember FittedLabels(labels, base)
        val slot = with(density) { (width.toPx() - gap.toPx() * (labels.size - 1)) / labels.size }
        fun widest(texts: List<String>, size: Float) = texts.maxOf {
            measurer.measure(
                it, base.copy(fontSize = size.sp, fontWeight = FontWeight.ExtraBold),
                maxLines = 1, softWrap = false, density = density,
            ).size.width
        }
        for (texts in listOf(labels, labels.map { it.take(1) })) {
            var size = base.fontSize.value
            while (size >= MIN_LABEL_SP) {
                if (widest(texts, size) <= slot) return@remember FittedLabels(texts, base.copy(fontSize = size.sp))
                size -= 0.5f
            }
        }
        FittedLabels(labels.map { it.take(1) }, base.copy(fontSize = MIN_LABEL_SP.sp))
    }
}

/** Test hooks for chart parts that TalkBack doesn't see. */
object ChartTags {
    const val LABELS = "chart-labels"
}
