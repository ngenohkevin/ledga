package com.ledga.app.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.theme.LedgaAccent
import com.ledga.app.ui.theme.LedgaText
import java.util.Calendar

/**
 * GitHub-style heatmap calendar — one cell per day, columns are weeks.
 *
 * Spec from LEDGA_REDESIGN.md §4.4: "low ▢▢▢▢ high" intensity legend
 * using --accent at 8/24/56/88% opacity (plus an empty step at ~6% for
 * days with zero spend).
 *
 * [dailyAmounts] maps a day-start timestamp (epoch ms, normalized to
 * 00:00:00) to a total spend amount for that day. Days that don't appear
 * in the map render as empty cells.
 */
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
        // Day-of-week labels row + cell grid as one block, with the day labels on the left
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Day labels column — only show M/W/F to avoid clutter.
            Column(
                modifier = Modifier.padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("M", "", "W", "", "F", "", "").forEach { letter ->
                    Box(
                        modifier = Modifier.size(width = 12.dp, height = 14.dp),
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
                horizontalArrangement = Arrangement.spacedBy(4.dp, alignment = Alignment.End),
            ) {
                val cellsPerWeek = 7
                repeat(weeks) { weekIndex ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        repeat(cellsPerWeek) { dayIndex ->
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
                            )
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
                listOf(0.0, 0.2, 0.5, 0.8, 1.0).forEach { intensity ->
                    LegendCell(intensity)
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

@Composable
private fun HeatmapCell(amount: Double, maxAmount: Double, isFuture: Boolean) {
    val color = when {
        isFuture -> Color.Transparent
        amount <= 0.0 -> LedgaAccent.copy(alpha = 0.06f)
        else -> {
            val intensity = (amount / maxAmount).coerceIn(0.0, 1.0)
            val alpha = when {
                intensity < 0.25 -> 0.18f
                intensity < 0.50 -> 0.34f
                intensity < 0.75 -> 0.60f
                else -> 0.92f
            }
            LedgaAccent.copy(alpha = alpha)
        }
    }
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}

@Composable
private fun LegendCell(intensity: Double) {
    val alpha = when {
        intensity <= 0.0 -> 0.06f
        intensity < 0.25 -> 0.18f
        intensity < 0.50 -> 0.34f
        intensity < 0.75 -> 0.60f
        else -> 0.92f
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(LedgaAccent.copy(alpha = alpha)),
    )
}
