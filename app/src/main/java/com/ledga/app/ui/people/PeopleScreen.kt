package com.ledga.app.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledga.app.data.db.dao.TopMerchant
import com.ledga.app.data.parser.TransactionDirection
import com.ledga.app.ui.components.parseColor
import com.ledga.app.ui.components.v2.Avatar
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
fun PeopleScreen(viewModel: PeopleViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    if (state.selectedPerson != null) {
        PersonSheet(
            name = state.selectedPerson!!,
            modeLabel = state.mode.label,
            transactions = state.selectedPersonTransactions,
            onDismiss = { viewModel.clearSelection() },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.Screen),
    ) {
        // Sent / Received toggle
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
            PeopleMode.entries.forEach { mode ->
                LedgaChip(
                    label = mode.label,
                    selected = state.mode == mode,
                    onClick = { viewModel.setMode(mode) },
                )
            }
        }

        Spacer(modifier = Modifier.height(Space.s4))

        LedgaSearchField(
            value = state.query,
            onValueChange = { viewModel.setQuery(it) },
            placeholder = "Search people by name…",
        )

        MinTotalSlider(
            minTotal = state.minTotal,
            onChange = { viewModel.setMinTotal(it) },
        )

        Spacer(modifier = Modifier.height(Space.s4))

        if (state.people.isEmpty()) {
            BentoCard {
                Text(
                    text = if (state.query.isNotBlank() || state.minTotal > 0)
                        "No people match these filters."
                    else "No ${state.mode.label.lowercase()} people yet.",
                    style = LedgaText.BodyM,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Space.s4),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 140.dp),
            ) {
                item {
                    Text(
                        text = "${state.people.size} ${if (state.people.size == 1) "person" else "people"}",
                        style = LedgaText.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.people, key = { it.recipientName }) { person ->
                    PersonRow(
                        person = person,
                        isInflow = state.mode == PeopleMode.RECEIVED,
                        onClick = { viewModel.selectPerson(person.recipientName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonRow(person: TopMerchant, isInflow: Boolean, onClick: () -> Unit) {
    BentoCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                initials = initialsOf(person.recipientName),
                color = LedgaAccent,
                size = 40.dp,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Space.s4),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = person.recipientName,
                    style = LedgaText.BodyL,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${person.transactionCount} ${if (person.transactionCount == 1) "transaction" else "transactions"}",
                    style = LedgaText.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = CurrencyFormatter.formatKshCompact(person.totalAmount),
                style = LedgaText.TitleS,
                color = if (isInflow) LedgaInflow else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MinTotalSlider(minTotal: Double, onChange: (Double) -> Unit) {
    var value by remember(minTotal) { mutableStateOf(minTotal.toFloat()) }
    Column(modifier = Modifier.padding(top = Space.s4)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Total at least",
                style = LedgaText.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (value <= 0f) "Any amount"
                else CurrencyFormatter.formatKshCompact(value.toDouble()),
                style = LedgaText.TitleS,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.toDouble()) },
            valueRange = 0f..100_000f,
            steps = 99, // 1K increments
            colors = SliderDefaults.colors(
                thumbColor = LedgaAccent,
                activeTrackColor = LedgaAccent,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonSheet(
    name: String,
    modeLabel: String,
    transactions: List<com.ledga.app.data.db.entity.TransactionWithCategory>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val total = transactions.sumOf { it.transaction.amount }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(text = name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = "${modeLabel.lowercase()} · ${CurrencyFormatter.formatKsh(total)} over ${transactions.size} " +
                    if (transactions.size == 1) "transaction" else "transactions",
                style = LedgaText.BodyM,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(transactions, key = { it.transaction.id }) { twc ->
                    TransactionRowV2(
                        recipient = twc.transaction.recipientName ?: name,
                        amount = twc.transaction.amount,
                        isInflow = twc.transaction.direction == TransactionDirection.INFLOW,
                        category = twc.category?.name,
                        color = twc.category?.color?.let(::parseColor)
                            ?: androidx.compose.ui.graphics.Color.Gray,
                        icon = twc.category?.icon ?: "person",
                        balance = twc.transaction.balance,
                        timestamp = twc.transaction.timestamp,
                        onClick = {},
                    )
                }
            }
        }
    }
}

private fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+"))
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .uppercase()
        .ifEmpty { "?" }
