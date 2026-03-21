package com.ledga.app.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledga.app.data.db.entity.Category
import com.ledga.app.data.db.entity.TransactionEntity
import com.ledga.app.data.parser.TransactionDirection
import com.ledga.app.ui.components.parseColor
import com.ledga.app.ui.theme.InflowGreen
import com.ledga.app.ui.theme.OutflowRed
import com.ledga.app.util.CurrencyFormatter
import com.ledga.app.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    transaction: TransactionEntity,
    category: Category?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onCategoryChange: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isInflow = transaction.direction == TransactionDirection.INFLOW

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Amount
            Text(
                text = CurrencyFormatter.formatKshSigned(transaction.amount, isInflow),
                style = MaterialTheme.typography.headlineMedium,
                color = if (isInflow) InflowGreen else OutflowRed,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Recipient
            Text(
                text = transaction.recipientName ?: transaction.type.name.replace("_", " "),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Details
            DetailRow("Date", DateUtils.formatDate(transaction.timestamp))
            DetailRow("Time", DateUtils.formatTime(transaction.timestamp))
            DetailRow("Type", transaction.type.name.replace("_", " "))
            DetailRow("Code", transaction.transactionCode)
            DetailRow("Balance After", CurrencyFormatter.formatKsh(transaction.balance))

            if (transaction.transactionCost > 0) {
                DetailRow("Fee", CurrencyFormatter.formatKsh(transaction.transactionCost))
            }
            if (transaction.recipientPhone != null) {
                DetailRow("Phone", transaction.recipientPhone)
            }
            if (transaction.accountNumber != null) {
                DetailRow("Account", transaction.accountNumber)
            }
            if (transaction.destinationCountry != null) {
                DetailRow("Country", transaction.destinationCountry)
            }
            if (transaction.fulizaAmount != null) {
                DetailRow("Fuliza Amount", CurrencyFormatter.formatKsh(transaction.fulizaAmount))
            }
            if (transaction.fulizaOutstanding != null) {
                DetailRow("Fuliza Outstanding", CurrencyFormatter.formatKsh(transaction.fulizaOutstanding))
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Category selector
            Text(
                text = "Category",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    val isSelected = cat.id == transaction.categoryId
                    Surface(
                        modifier = Modifier.clickable { onCategoryChange(cat.id) },
                        shape = MaterialTheme.shapes.small,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(10.dp),
                                shape = CircleShape,
                                color = parseColor(cat.color)
                            ) {}
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
