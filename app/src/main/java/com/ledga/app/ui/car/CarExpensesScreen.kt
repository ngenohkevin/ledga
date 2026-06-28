package com.ledga.app.ui.car

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledga.app.data.db.entity.CarTag
import com.ledga.app.data.db.entity.TransactionEntity
import com.ledga.app.data.parser.TransactionDirection
import com.ledga.app.ui.components.parseColor
import com.ledga.app.ui.components.v2.BackLeading
import com.ledga.app.ui.components.v2.BentoCard
import com.ledga.app.ui.components.v2.LedgaChip
import com.ledga.app.ui.components.v2.LedgaTopBar
import com.ledga.app.ui.components.v2.TransactionRowV2
import com.ledga.app.ui.components.v2.onTonal
import com.ledga.app.ui.components.v2.onTonalMuted
import com.ledga.app.ui.theme.LedgaAccentSoft
import com.ledga.app.ui.theme.LedgaText
import com.ledga.app.ui.theme.Space
import com.ledga.app.util.CurrencyFormatter
import com.ledga.app.util.DateUtils

/**
 * Car expenses — fuel & service spend, summed across every line.
 *
 * A car's running cost doesn't care which SIM paid, so every figure here is
 * account-agnostic (see [CarExpensesViewModel]). The hero shows the lifetime
 * total; the toggle picks Fuel or Service, and the three stat cards break that
 * down into this week / this month / all time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarExpensesScreen(
    onBack: () -> Unit,
    viewModel: CarExpensesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    state.selected?.let { txn ->
        RetagSheet(
            transaction = txn,
            onDismiss = { viewModel.clearSelection() },
            onSetTag = { tag -> viewModel.setTag(txn.id, tag) },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LedgaTopBar(title = "Car", leading = { BackLeading(onBack) })

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.Screen)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(Space.Section),
        ) {
            // ---- Hero: lifetime cost of the car ----
            BentoCard(tonal = true, tonalColor = LedgaAccentSoft) {
                Text(
                    text = "What your car has cost you".uppercase(),
                    style = LedgaText.Overline,
                    color = onTonalMuted(LedgaAccentSoft),
                )
                Text(
                    text = CurrencyFormatter.formatKsh(state.totalAllTime),
                    style = LedgaText.DisplayL,
                    color = onTonal(LedgaAccentSoft),
                )
                Text(
                    text = "Fuel ${CurrencyFormatter.formatKshCompact(state.fuelAllTime)}" +
                        "   ·   Service ${CurrencyFormatter.formatKshCompact(state.serviceAllTime)}",
                    style = LedgaText.BodyM,
                    color = onTonalMuted(LedgaAccentSoft),
                )
            }

            // ---- Fuel / Service toggle ----
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                CarTag.entries.forEach { tag ->
                    LedgaChip(
                        label = tag.label,
                        selected = state.selectedTag == tag,
                        onClick = { viewModel.selectTag(tag) },
                    )
                }
            }

            // ---- This week / This month / All time (for the selected tag) ----
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                StatCard(modifier = Modifier.weight(1f), label = "This week", value = state.week)
                StatCard(modifier = Modifier.weight(1f), label = "This month", value = state.month)
                StatCard(modifier = Modifier.weight(1f), label = "All time", value = state.allTime)
            }

            // ---- Tagged transactions ----
            val label = state.selectedTag.label
            if (state.transactions.isEmpty()) {
                BentoCard {
                    Text(
                        text = "No ${label.lowercase()} payments tagged yet.",
                        style = LedgaText.BodyL,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Open any transaction, scroll to “Car expense” and mark " +
                            "it as $label. It’ll show up here.",
                        style = LedgaText.BodyM,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                BentoCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.Card, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "$label payments".uppercase(),
                            style = LedgaText.Overline,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${state.transactions.size}",
                            style = LedgaText.Caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = Space.Card),
                    )
                    state.transactions.forEachIndexed { index, twc ->
                        TransactionRowV2(
                            recipient = twc.transaction.recipientName
                                ?: twc.transaction.type.name.replace("_", " "),
                            amount = twc.transaction.amount,
                            isInflow = twc.transaction.direction == TransactionDirection.INFLOW,
                            category = DateUtils.formatDate(twc.transaction.timestamp),
                            color = twc.category?.color?.let(::parseColor)
                                ?: androidx.compose.ui.graphics.Color.Gray,
                            icon = twc.category?.icon ?: "category",
                            balance = twc.transaction.balance,
                            timestamp = twc.transaction.timestamp,
                            onClick = { viewModel.selectTransaction(twc.transaction) },
                        )
                        if (index < state.transactions.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = Space.Card),
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.padding(bottom = Space.s7))
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, label: String, value: Double) {
    BentoCard(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = LedgaText.Overline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = CurrencyFormatter.formatKshCompact(value),
            style = LedgaText.TitleM,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Lightweight sheet for re-tagging or removing a car tag straight from this
 * screen — so a mis-tagged row can be fixed without hunting for it in Activity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RetagSheet(
    transaction: TransactionEntity,
    onDismiss: () -> Unit,
    onSetTag: (CarTag?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = CurrencyFormatter.formatKsh(transaction.amount),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = transaction.recipientName ?: transaction.type.name.replace("_", " "),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = DateUtils.formatDate(transaction.timestamp),
                style = LedgaText.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Space.s5)

            Text(
                text = "Car expense",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Space.s3)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                LedgaChip(
                    label = "Remove",
                    selected = transaction.carTag == null,
                    onClick = { onSetTag(null) },
                )
                CarTag.entries.forEach { tag ->
                    LedgaChip(
                        label = tag.label,
                        selected = transaction.carTag == tag,
                        onClick = { onSetTag(tag) },
                    )
                }
            }

            Spacer(Space.s8)
        }
    }
}

@Composable
private fun Spacer(height: androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.height(height))
}
