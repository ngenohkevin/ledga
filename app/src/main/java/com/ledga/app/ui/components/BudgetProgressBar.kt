package com.ledga.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.theme.LedgaAccent
import com.ledga.app.ui.theme.LedgaDanger
import com.ledga.app.ui.theme.LedgaWarning
import com.ledga.app.util.CurrencyFormatter

@Composable
fun BudgetProgressBar(
    label: String,
    spent: Double,
    limit: Double,
    modifier: Modifier = Modifier
) {
    val progress = if (limit > 0) (spent / limit).toFloat().coerceIn(0f, 1f) else 0f
    val percentage = if (limit > 0) ((spent / limit) * 100).toInt() else 0
    val color = when {
        percentage >= 100 -> LedgaDanger
        percentage >= 80 -> LedgaWarning
        else -> LedgaAccent
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${CurrencyFormatter.formatKshCompact(spent)} / ${CurrencyFormatter.formatKshCompact(limit)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
