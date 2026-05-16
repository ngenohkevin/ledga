package com.ledga.app.ui.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledga.app.ui.components.parseColor
import com.ledga.app.ui.components.v2.BentoCard
import com.ledga.app.ui.components.v2.HeatmapCalendar
import com.ledga.app.ui.components.v2.LedgaChip
import com.ledga.app.ui.components.v2.StatCard
import com.ledga.app.ui.theme.LedgaAccentDeep
import com.ledga.app.ui.theme.LedgaText
import com.ledga.app.ui.theme.Space
import com.ledga.app.util.CurrencyFormatter
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries

@Composable
fun TrendsScreen(
    viewModel: TrendsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.Screen)
            .padding(top = Space.s4, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(Space.Section),
    ) {
        // ---- Period selector ----
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
            items(TrendsPeriod.entries.toList()) { period ->
                LedgaChip(
                    label = period.label,
                    selected = state.selectedPeriod == period,
                    onClick = { viewModel.selectPeriod(period) },
                )
            }
        }

        // ---- Total spent hero ----
        StatCard(
            overline = "Total spent · ${state.selectedPeriod.label}",
            value = CurrencyFormatter.formatKsh(state.totalSpending),
            caption = "${state.dailySpending.size} active days",
        )

        // ---- Daily bars ----
        BentoCard(title = "Daily spending") {
            if (state.dailySpending.isNotEmpty()) {
                DailySpendingChart(
                    dailySpending = state.dailySpending.map { it.totalAmount },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
            } else {
                EmptyHint("No spending in this period yet.")
            }
        }

        // ---- Heatmap (new) ----
        BentoCard(title = "Spending heatmap") {
            val heatmapData = remember(state.dailySpending) {
                state.dailySpending.associate { it.dayTimestamp to it.totalAmount }
            }
            HeatmapCalendar(
                dailyAmounts = heatmapData,
                weeks = when (state.selectedPeriod) {
                    TrendsPeriod.WEEK -> 4
                    TrendsPeriod.MONTH -> 12
                    TrendsPeriod.QUARTER -> 16
                    TrendsPeriod.YEAR -> 24
                },
            )
        }

        // ---- Top merchants ----
        if (state.topMerchants.isNotEmpty()) {
            BentoCard(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(
                    text = "Top merchants".uppercase(),
                    style = LedgaText.Overline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.Card, vertical = 14.dp),
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = Space.Card),
                )
                state.topMerchants.forEachIndexed { index, merchant ->
                    MerchantRow(
                        name = merchant.recipientName,
                        count = merchant.transactionCount,
                        amount = merchant.totalAmount,
                    )
                    if (index < state.topMerchants.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = Space.Card),
                        )
                    }
                }
            }
        }

        // ---- Category breakdown ----
        if (state.categorySpending.isNotEmpty()) {
            BentoCard(title = "By category") {
                state.categorySpending.take(8).forEach { cs ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(parseColor(cs.color))
                        )
                        Text(
                            text = cs.name,
                            style = LedgaText.BodyL,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = Space.s4),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = CurrencyFormatter.formatKshCompact(cs.totalAmount),
                            style = LedgaText.BodyL,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // ---- Fees summary ----
        BentoCard(
            overline = "Fees paid",
            title = CurrencyFormatter.formatKsh(state.totalFees),
            icon = Icons.Filled.LocalAtm,
            iconTint = LedgaAccentDeep,
        ) {
            Text(
                text = if (state.totalFees == 0.0) {
                    "Nice — zero fees in this period."
                } else {
                    "Withdrawals and Fuliza usage drive most of these. Insights " +
                            "will suggest ways to cut them down."
                },
                style = LedgaText.BodyM,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MerchantRow(name: String, count: Int, amount: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.Card, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = LedgaText.BodyL,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$count ${if (count == 1) "transaction" else "transactions"}",
                style = LedgaText.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = CurrencyFormatter.formatKshCompact(amount),
            style = LedgaText.TitleS,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = LedgaText.BodyM,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Space.s5),
    )
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

