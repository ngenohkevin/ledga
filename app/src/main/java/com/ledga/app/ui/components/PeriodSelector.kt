package com.ledga.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class Period(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("Month")
}

@Composable
fun PeriodSelector(
    selected: Period,
    onSelect: (Period) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Period.entries.forEach { period ->
            CategoryChip(
                label = period.label,
                isSelected = selected == period,
                onClick = { onSelect(period) }
            )
        }
    }
}
