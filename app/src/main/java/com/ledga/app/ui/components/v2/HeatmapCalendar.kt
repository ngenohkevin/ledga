package com.ledga.app.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.theme.LedgaAccent
import com.ledga.app.ui.theme.LedgaText
import java.util.Calendar

/**
 * GitHub-style heatmap calendar — one cell per day, columns are weeks.
 *
 * [dailyAmounts] maps a day-start timestamp (epoch ms at LOCAL midnight) to a
 * total spend amount for that day. The DAO buckets by local midnight too, so
 * the keys line up. Days that don't appear render as empty cells.
 *
 * Cell size adapts to the available width so wide ranges (e.g. a 24-week year
 * view) never overflow the card and clip the earliest weeks.
 */

// Alpha ramp shared by cells + legend so they always agree. Index 0 = empty.
private val HEATMAP_ALPHAS = listOf(0.06f, 0.18f, 0.34f, 0.60f, 0.92f)
private val CELL_GAP = 4.dp

@Composable
fun HeatmapCalendar(
    dailyAmounts: Map<Long, Double>,
    weeks: Int = 12,
    modifier: Modifier = Modifier,
) {
    val maxAmount = (dailyAmounts.values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    // Anchor on Monday of the current week — then walk backwards `weeks` weeks.
    val mondayThisWeek = Calendar.getInstance().apply {
        timeInMillis = today.timeInMillis
        val daysFromMonday = ((get(Calendar.DAY_OF_WEEK) + 5) % 7)
        add(Calendar.DAY_OF_YEAR, -daysFromMonday)
    }
    val firstDay = Calendar.getInstance().apply {
        timeInMillis = mondayThisWeek.timeInMillis
        add(Calendar.WEEK_OF_YEAR, -(weeks - 1))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Reserve the day-label column (~16dp) + the gap before the grid,
            // then split the rest across the week columns so they always fit.
            val labelReserve = 20.dp
            val cellSize: Dp = ((maxWidth - labelReserve - CELL_GAP * (weeks - 1)) / weeks)
                .coerceIn(6.dp, 14.dp)

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Day labels column — only show M/W/F to avoid clutter.
                Column(
                    modifier = Modifier.padding(end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(CELL_GAP),
                ) {
                    listOf("M", "", "W", "", "F", "", "").forEach { letter ->
                        Box(
                            modifier = Modifier.size(width = 12.dp, height = cellSize),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (letter.isNotEmpty()) {
                                Text(
                                    text = letter,
                                    style = LedgaText.Caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Week columns
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(CELL_GAP, alignment = Alignment.End),
                ) {
                    repeat(weeks) { weekIndex ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(CELL_GAP),
                        ) {
                            repeat(7) { dayIndex ->
                                val cellDay = Calendar.getInstance().apply {
                                    timeInMillis = firstDay.timeInMillis
                                    add(Calendar.WEEK_OF_YEAR, weekIndex)
                                    add(Calendar.DAY_OF_YEAR, dayIndex)
                                }
                                val isFuture = cellDay.timeInMillis > today.timeInMillis
                                val amount = dailyAmounts[cellDay.timeInMillis] ?: 0.0
                                HeatmapCell(
                                    amount = amount,
                                    maxAmount = maxAmount,
                                    isFuture = isFuture,
                                    cellSize = cellSize,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Legend row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "low",
                style = LedgaText.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                HEATMAP_ALPHAS.forEach { alpha ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(LedgaAccent.copy(alpha = alpha)),
                    )
                }
            }
            Text(
                text = "high",
                style = LedgaText.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Map a spend amount to one of the non-empty alpha steps (index 1..4). */
private fun alphaFor(amount: Double, maxAmount: Double): Float {
    if (amount <= 0.0) return HEATMAP_ALPHAS[0]
    val intensity = (amount / maxAmount).coerceIn(0.0, 1.0)
    val step = when {
        intensity < 0.25 -> 1
        intensity < 0.50 -> 2
        intensity < 0.75 -> 3
        else -> 4
    }
    return HEATMAP_ALPHAS[step]
}

@Composable
private fun HeatmapCell(amount: Double, maxAmount: Double, isFuture: Boolean, cellSize: Dp) {
    val color = if (isFuture) Color.Transparent
    else LedgaAccent.copy(alpha = alphaFor(amount, maxAmount))
    Box(
        modifier = Modifier
            .size(cellSize)
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}
