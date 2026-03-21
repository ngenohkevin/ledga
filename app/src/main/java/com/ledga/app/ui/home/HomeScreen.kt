package com.ledga.app.ui.home

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledga.app.ui.components.DonutChart
import com.ledga.app.ui.components.PeriodSelector
import com.ledga.app.ui.components.StatCard
import com.ledga.app.ui.components.TransactionCard
import com.ledga.app.util.CurrencyFormatter

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Greeting
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = state.greeting,
                style = MaterialTheme.typography.headlineSmall
            )
        }

        // Balance card
        item {
            StatCard(
                label = "M-PESA Balance",
                value = state.balance?.let { CurrencyFormatter.formatKsh(it) } ?: "—",
                subtitle = "from last transaction",
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Spending summary
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = state.monthLabel,
                    value = CurrencyFormatter.formatKshCompact(state.totalSpending),
                    subtitle = "Spent",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Fees",
                    value = CurrencyFormatter.formatKsh(state.totalFees),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Period selector
        item {
            PeriodSelector(
                selected = state.selectedPeriod,
                onSelect = { viewModel.selectPeriod(it) }
            )
        }

        // Donut chart
        item {
            if (state.donutSegments.isNotEmpty()) {
                DonutChart(
                    segments = state.donutSegments,
                    totalAmount = state.totalSpending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
        }

        // Recent transactions header
        item {
            Text(
                text = "Recent Transactions",
                style = MaterialTheme.typography.titleMedium
            )
        }

        // Transaction list
        if (state.recentTransactions.isEmpty()) {
            item {
                Text(
                    text = "No transactions yet. Your M-Pesa transactions will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        } else {
            items(
                items = state.recentTransactions,
                key = { it.transaction.id }
            ) { twc ->
                TransactionCard(
                    transaction = twc.transaction,
                    category = twc.category
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
