package com.ledga.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ledga.app.data.db.entity.Category
import com.ledga.app.data.db.entity.TransactionEntity
import com.ledga.app.data.parser.TransactionDirection
import com.ledga.app.ui.theme.LedgaInflow
import com.ledga.app.util.CurrencyFormatter
import com.ledga.app.util.DateUtils

@Composable
fun TransactionCard(
    transaction: TransactionEntity,
    category: Category?,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isInflow = transaction.direction == TransactionDirection.INFLOW
    val amountColor = if (isInflow) LedgaInflow else MaterialTheme.colorScheme.onSurface
    val displayName = transaction.recipientName ?: transaction.type.name.replace("_", " ")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category icon with colored background
            val iconColor = category?.color?.let { parseColor(it) }
                ?: MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = iconColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon(category?.icon ?: "category"),
                    contentDescription = category?.name,
                    modifier = Modifier.size(22.dp),
                    tint = iconColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name and time
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
                Text(
                    text = DateUtils.formatTime(transaction.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Amount
            Text(
                text = CurrencyFormatter.formatKshSigned(transaction.amount, isInflow),
                style = MaterialTheme.typography.titleSmall,
                color = amountColor
            )
        }
    }
}

fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color.Gray
    }
}
