package com.ledga.app.ui.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.ledga.app.ui.components.CategoryChip
import com.ledga.app.ui.components.StatCard
import com.ledga.app.ui.components.parseColor
import com.ledga.app.util.CurrencyFormatter

@Composable
fun TrendsScreen(
    viewModel: TrendsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Text("Trends", style = MaterialTheme.typography.headlineSmall)
        }

        // Period selector
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrendsPeriod.entries.forEach { period ->
                    CategoryChip(
                        label = period.label,
                        isSelected = state.selectedPeriod == period,
                        onClick = { viewModel.selectPeriod(period) }
                    )
                }
            }
        }

        // Summary cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = "Total Spent",
                    value = CurrencyFormatter.formatKshCompact(state.totalSpending),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Total Fees",
                    value = CurrencyFormatter.formatKsh(state.totalFees),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Daily spending bar chart
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Daily Spending",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (state.dailySpending.isNotEmpty()) {
                        DailySpendingChart(
                            dailySpending = state.dailySpending.map { it.totalAmount },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    } else {
                        Text(
                            text = "No spending data for this period.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                }
            }
        }

        // Category breakdown
        if (state.categorySpending.isNotEmpty()) {
            item {
                Text(
                    text = "By Category",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(state.categorySpending) { cs ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(12.dp),
                        shape = CircleShape,
                        color = parseColor(cs.color)
                    ) {}
                    Text(
                        text = cs.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    )
                    Text(
                        text = CurrencyFormatter.formatKsh(cs.totalAmount),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Top merchants
        if (state.topMerchants.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Top Merchants",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(state.topMerchants) { merchant ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = merchant.recipientName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "${merchant.transactionCount} transactions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = CurrencyFormatter.formatKsh(merchant.totalAmount),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun DailySpendingChart(
    dailySpending: List<Double>,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(dailySpending) {
        if (dailySpending.isNotEmpty()) {
            modelProducer.runTransaction {
                columnSeries {
                    series(dailySpending.map { it.toFloat() })
                }
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(),
        ),
        modelProducer = modelProducer,
        modifier = modifier,
    )
}
