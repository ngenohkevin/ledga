package com.ledga.app.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.ledga.app.data.parser.TransactionDirection
import com.ledga.app.ui.components.parseColor
import com.ledga.app.ui.components.v2.BackLeading
import com.ledga.app.ui.components.v2.BentoCard
import com.ledga.app.ui.components.v2.LedgaTopBar
import com.ledga.app.ui.components.v2.ProgressRing
import com.ledga.app.ui.components.v2.TopBarIconButton
import com.ledga.app.ui.components.v2.TransactionRowV2
import com.ledga.app.ui.theme.LedgaText
import com.ledga.app.ui.theme.Space
import com.ledga.app.util.CurrencyFormatter
import com.ledga.app.util.DateUtils

@Composable
fun GoalDetailScreen(
    onBack: () -> Unit,
    viewModel: GoalDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var confirmingDelete by remember { mutableStateOf(false) }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete goal?") },
            text = { Text("This removes the goal but keeps all transactions intact.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    viewModel.delete(onDeleted = onBack)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LedgaTopBar(
            title = state.goal?.goal?.name ?: "Goal",
            leading = { BackLeading(onBack) },
            trailing = {
                TopBarIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "Delete goal",
                    onClick = { confirmingDelete = true },
                )
            },
        )

        val gp = state.goal
        if (gp == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(Space.Screen),
            ) {
                Text(
                    text = if (state.isLoading) "Loading…" else "Goal not found.",
                    style = LedgaText.BodyL,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.Screen)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(Space.Section),
        ) {
            // Hero
            BentoCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s7),
                ) {
                    ProgressRing(
                        progress = gp.percent,
                        size = 120.dp,
                        strokeWidth = 8.dp,
                        progressColor = parseColor(gp.goal.colorHex),
                        label = "${(gp.percent * 100).toInt()}%",
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = CurrencyFormatter.formatKsh(gp.currentAmount),
                            style = LedgaText.TitleL,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "of ${CurrencyFormatter.formatKsh(gp.goal.targetAmount)}",
                            style = LedgaText.BodyM,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        gp.goal.targetDate?.let {
                            Text(
                                text = "Target ${DateUtils.formatDate(it)}",
                                style = LedgaText.Caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        gp.eta?.let {
                            Text(
                                text = "Projected ${DateUtils.formatDate(it)}",
                                style = LedgaText.Caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Contributions
            BentoCard(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(
                    text = "Contributions (${state.contributions.size})".uppercase(),
                    style = LedgaText.Overline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.Card, vertical = 14.dp),
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = Space.Card),
                )

                if (state.contributions.isEmpty()) {
                    Text(
                        text = "No contributing transactions yet. New matching " +
                                "transactions will appear here automatically.",
                        style = LedgaText.BodyM,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Space.Card),
                    )
                } else {
                    state.contributions.forEachIndexed { index, twc ->
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
                        )
                        if (index < state.contributions.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = Space.Card),
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.padding(bottom = Space.s5))
        }
    }
}
