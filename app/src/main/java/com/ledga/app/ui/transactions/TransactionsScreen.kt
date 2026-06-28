package com.ledga.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledga.app.data.db.entity.TransactionWithCategory
import com.ledga.app.data.parser.TransactionDirection
import com.ledga.app.ui.components.parseColor
import com.ledga.app.ui.components.v2.BentoCard
import com.ledga.app.ui.components.v2.LedgaChip
import com.ledga.app.ui.components.v2.LedgaSearchField
import com.ledga.app.ui.components.v2.TransactionRowV2
import com.ledga.app.ui.theme.LedgaAccent
import com.ledga.app.ui.theme.LedgaInflow
import com.ledga.app.ui.theme.LedgaText
import com.ledga.app.ui.theme.Space
import com.ledga.app.util.CurrencyFormatter

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    if (state.selectedTransaction != null) {
        TransactionDetailSheet(
            transaction = state.selectedTransaction!!.transaction,
            category = state.selectedTransaction!!.category,
            categories = state.categories,
            onDismiss = { viewModel.clearSelection() },
            onCategoryChange = { categoryId ->
                viewModel.changeCategory(
                    state.selectedTransaction!!.transaction.id,
                    categoryId
                )
            },
            accounts = state.accounts,
            onAccountChange = { accountId ->
                viewModel.changeAccount(
                    state.selectedTransaction!!.transaction.id,
                    accountId
                )
            },
            manualGoals = state.manualGoals,
            goalIdsForTransaction = state.selectedTxGoalIds,
            onToggleGoal = { goalId, currentlyIn ->
                viewModel.toggleGoalContribution(
                    transactionId = state.selectedTransaction!!.transaction.id,
                    goalId = goalId,
                    currentlyIn = currentlyIn,
                )
            },
            isOwnAccount = state.isOwnAccount(state.selectedTransaction!!.transaction.recipientName),
            onToggleOwnAccount = state.selectedTransaction!!.transaction.recipientName?.let { name ->
                {
                    viewModel.toggleOwnAccount(
                        recipientName = name,
                        currentlyMarked = state.isOwnAccount(name),
                    )
                }
            },
            onCarTagChange = { tag ->
                viewModel.changeCarTag(
                    state.selectedTransaction!!.transaction.id,
                    tag,
                )
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.Screen),
    ) {
        LedgaSearchField(
            value = state.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = "Search merchants, codes…",
            modifier = Modifier.padding(top = Space.s4),
        )

        Spacer(modifier = Modifier.height(Space.s4))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
            items(TransactionsViewModel.FILTERS.keys.toList()) { filter ->
                LedgaChip(
                    label = filter,
                    selected = state.activeFilter == filter,
                    onClick = { viewModel.setFilter(filter) },
                )
            }
        }

        // When the Large tab is active, a slider sets the "large" threshold —
        // drag right for only the biggest transactions, left to include smaller.
        if (state.activeFilter == TransactionsViewModel.LARGE_FILTER) {
            val threshold by viewModel.largeThreshold.collectAsState()
            LargeThresholdSlider(
                threshold = threshold,
                onThresholdChange = { viewModel.setLargeThreshold(it) },
            )
        }

        Spacer(modifier = Modifier.height(Space.s5))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Space.s5),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp),
        ) {
            if (state.groupedTransactions.isEmpty()) {
                item {
                    BentoCard {
                        Text(
                            text = if (state.searchQuery.isNotBlank()) {
                                "No transactions match \"${state.searchQuery}\"."
                            } else {
                                "No transactions in this filter yet."
                            },
                            style = LedgaText.BodyM,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                state.groupedTransactions.forEach { (day, transactions) ->
                    item(key = "day-$day") {
                        DayGroupCard(day = day, transactions = transactions) { twc ->
                            viewModel.selectTransaction(twc)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayGroupCard(
    day: String,
    transactions: List<TransactionWithCategory>,
    onSelect: (TransactionWithCategory) -> Unit,
) {
    val totals = remember(transactions) { computeDayTotals(transactions) }

    BentoCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.Card, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = day.uppercase(),
                style = LedgaText.Overline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (totals.outflow > 0) {
                Text(
                    text = "Out ${CurrencyFormatter.formatKshCompact(totals.outflow)}",
                    style = LedgaText.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (totals.inflow > 0) {
                if (totals.outflow > 0) {
                    Text(
                        text = "  ·  ",
                        style = LedgaText.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "In ${CurrencyFormatter.formatKshCompact(totals.inflow)}",
                    style = LedgaText.Caption,
                    color = LedgaInflow,
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = Space.Card),
        )
        transactions.forEachIndexed { index, twc ->
            TransactionRowV2(
                recipient = twc.transaction.recipientName
                    ?: twc.transaction.type.name.replace("_", " "),
                amount = twc.transaction.amount,
                isInflow = twc.transaction.direction == TransactionDirection.INFLOW,
                category = twc.category?.name,
                color = twc.category?.color?.let(::parseColor)
                    ?: androidx.compose.ui.graphics.Color.Gray,
                icon = twc.category?.icon ?: "category",
                balance = twc.transaction.balance,
                timestamp = twc.transaction.timestamp,
                onClick = { onSelect(twc) },
            )
            if (index < transactions.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = Space.Card),
                )
            }
        }
    }
}

/**
 * Slider that picks the "large" threshold (Ksh 1K–100K, in 1K steps). Drags
 * update the label live; the list re-filters once on release to avoid a DB
 * query on every tick. Resets to the stored value if it changes elsewhere.
 */
@Composable
private fun LargeThresholdSlider(
    threshold: Double,
    onThresholdChange: (Double) -> Unit,
) {
    var sliderValue by remember(threshold) { mutableStateOf(threshold.toFloat()) }
    Column(modifier = Modifier.padding(top = Space.s4)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Transactions of at least",
                style = LedgaText.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = CurrencyFormatter.formatKshCompact(sliderValue.toDouble()),
                style = LedgaText.TitleS,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onThresholdChange(sliderValue.toDouble()) },
            valueRange = 1_000f..100_000f,
            steps = 98, // 1K increments across 1K–100K
            colors = SliderDefaults.colors(
                thumbColor = LedgaAccent,
                activeTrackColor = LedgaAccent,
            ),
        )
    }
}

private data class DayTotals(val inflow: Double, val outflow: Double)

private fun computeDayTotals(transactions: List<TransactionWithCategory>): DayTotals {
    var inflow = 0.0
    var outflow = 0.0
    for (twc in transactions) {
        when (twc.transaction.direction) {
            TransactionDirection.INFLOW -> inflow += twc.transaction.amount
            TransactionDirection.OUTFLOW -> outflow += twc.transaction.amount
        }
    }
    return DayTotals(inflow, outflow)
}

