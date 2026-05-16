package com.ledga.app.ui.components.v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.components.categoryIcon
import com.ledga.app.ui.theme.LedgaInflow
import com.ledga.app.ui.theme.LedgaText
import com.ledga.app.ui.theme.Space
import com.ledga.app.util.CurrencyFormatter
import com.ledga.app.util.DateUtils

/**
 * Canonical transaction row used inside bento cards (recent activity, day
 * groups, search results). Designed to sit edge-to-edge inside a card —
 * the card supplies the rounded background and horizontal padding.
 */
@Composable
fun TransactionRowV2(
    recipient: String,
    amount: Double,
    isInflow: Boolean,
    category: String?,
    color: Color,
    icon: String,
    balance: Double,
    timestamp: Long? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = Space.Card, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        CategoryIconTile(color = color, icon = categoryIcon(icon))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recipient,
                style = LedgaText.BodyL,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(category ?: "Uncategorized")
                    if (timestamp != null) {
                        append(" · ")
                        append(DateUtils.formatTime(timestamp))
                    }
                },
                style = LedgaText.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = CurrencyFormatter.formatKshSigned(amount, isInflow),
                style = LedgaText.TitleS,
                color = if (isInflow) LedgaInflow else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Bal ${CurrencyFormatter.formatKshCompact(balance)}",
                style = LedgaText.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
