package com.ledga.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledga.app.ui.components.DonutChart
import com.ledga.app.ui.components.Period
import com.ledga.app.ui.components.parseColor
import com.ledga.app.ui.components.v2.AccountChip
import com.ledga.app.ui.components.v2.AccountSwitcherSheet
import com.ledga.app.ui.components.v2.Avatar
import com.ledga.app.ui.components.v2.BentoCard
import com.ledga.app.ui.components.v2.LedgaChip
import com.ledga.app.ui.components.v2.LedgaTopBar
import com.ledga.app.ui.components.v2.StatCard
import com.ledga.app.ui.components.v2.TopBarIconButton
import com.ledga.app.ui.components.v2.TransactionRowV2
import com.ledga.app.ui.components.v2.onTonal
import com.ledga.app.ui.theme.LedgaAccent
import com.ledga.app.ui.theme.LedgaAccentDeep
import com.ledga.app.ui.theme.LedgaAccentSoft
import com.ledga.app.ui.theme.LedgaDanger
import com.ledga.app.ui.theme.LedgaInflow
import com.ledga.app.ui.theme.LedgaInk
import com.ledga.app.ui.theme.LedgaText
import com.ledga.app.ui.theme.Radius
import com.ledga.app.ui.theme.Space
import com.ledga.app.util.CurrencyFormatter
import com.ledga.app.util.DateUtils

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToActivity: () -> Unit = {},
    onNavigateToInsights: () -> Unit = {},
    onNavigateToYou: () -> Unit = {},
    onManageAccounts: () -> Unit = {},
    onOpenUpdate: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    var switcherOpen by remember { mutableStateOf(false) }

    if (switcherOpen) {
        AccountSwitcherSheet(
            accounts = state.accounts,
            selectedAccountId = state.selectedAccountId,
            onSelectAccount = { viewModel.selectAccount(it) },
            onDismiss = { switcherOpen = false },
            onManage = onManageAccounts,
        )
    }

    val activeAccount = state.accounts.firstOrNull { it.id == state.selectedAccountId }
    val accountLabel = activeAccount?.displayName
        ?: if (state.accounts.size > 1) "Combined" else state.accounts.firstOrNull()?.displayName
    val accountColor = activeAccount?.colorHex?.let(::parseColor) ?: LedgaAccent
    val accountInitials = accountLabel?.split(" ")
        ?.mapNotNull { it.firstOrNull()?.toString() }
        ?.joinToString("")
        ?.take(2)
        ?.uppercase()
        ?: "ME"

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- Top bar: account chip + greeting + actions ----
        LedgaTopBar(
            leading = {
                if (state.accounts.size > 1) {
                    AccountChip(
                        initials = accountInitials,
                        label = accountLabel ?: "Combined",
                        accentColor = accountColor,
                        onClick = { switcherOpen = true },
                    )
                } else {
                    Avatar(initials = accountInitials, color = accountColor)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Text(
                            text = state.greeting.takeIf { it.isNotBlank() } ?: "Hi",
                            style = LedgaText.BodyM,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = accountLabel ?: "Your tracker",
                            style = LedgaText.TitleS,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            },
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                    TopBarIconButton(
                        icon = Icons.Filled.Notifications,
                        contentDescription = "Notifications",
                        onClick = {},
                    )
                    TopBarIconButton(
                        icon = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        onClick = onNavigateToYou,
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.Screen)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(Space.Section),
        ) {
            // ---- Update banner (conditional) ----
            state.updateAvailable?.let { release ->
                UpdateBanner(
                    versionLabel = release.tag_name,
                    prefetched = state.updatePrefetched,
                    onSeeWhatsNew = onOpenUpdate,
                    onUpdate = onOpenUpdate,
                )
            }

            // ---- Period selector ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s3),
            ) {
                Period.entries.forEach { period ->
                    LedgaChip(
                        label = period.label,
                        selected = state.selectedPeriod == period,
                        onClick = { viewModel.selectPeriod(period) },
                    )
                }
            }

            // ---- HERO: spent for selected period ----
            BentoCard(onClick = onNavigateToActivity) {
                val overlineLabel = when (state.selectedPeriod) {
                    Period.TODAY -> "SPENT TODAY"
                    Period.THIS_WEEK -> "SPENT THIS WEEK"
                    Period.THIS_MONTH -> "SPENT THIS MONTH"
                }
                Text(
                    text = overlineLabel,
                    style = LedgaText.Overline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = CurrencyFormatter.formatKsh(state.totalSpending),
                    style = LedgaText.DisplayL,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = state.monthLabel +
                            if (state.totalFees > 0) "  ·  ${CurrencyFormatter.formatKsh(state.totalFees)} in fees" else "",
                    style = LedgaText.BodyM,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- 2-up: Balance · Top category ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s4),
            ) {
                StatCard(
                    overline = "Balance",
                    value = state.balance?.let { CurrencyFormatter.formatKshCompact(it) } ?: "—",
                    caption = "from last tx",
                    modifier = Modifier.weight(1f),
                )
                val top = state.categoryBreakdown.firstOrNull()
                if (top != null) {
                    StatCard(
                        overline = "Top category",
                        value = CurrencyFormatter.formatKshCompact(top.amount),
                        caption = top.name,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    StatCard(
                        overline = "Top category",
                        value = "—",
                        caption = "no spending yet",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ---- Fuliza status (only when the SMS history has ever carried it) ----
            if (state.fulizaOutstanding != null || state.fulizaAvailable != null) {
                FulizaStatusCard(
                    outstanding = state.fulizaOutstanding,
                    available = state.fulizaAvailable,
                    asOf = state.fulizaOutstandingAt,
                )
            }

            // ---- Donut breakdown ----
            if (state.donutSegments.isNotEmpty()) {
                BentoCard(title = "Where it went", onClick = onNavigateToActivity) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.s5),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = Space.s3),
                            contentAlignment = Alignment.Center,
                        ) {
                            DonutChart(
                                segments = state.donutSegments,
                                totalAmount = state.totalSpending,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Space.s3),
                        ) {
                            state.categoryBreakdown.take(5).forEach { item ->
                                LegendRow(name = item.name, color = parseColor(item.color))
                            }
                        }
                    }
                }
            }

            // ---- Insights teaser ----
            // Use the theme-adaptive accent-container so the card stays
            // readable in both light + dark.
            val insightBg = MaterialTheme.colorScheme.primaryContainer
            val insightInk = onTonal(insightBg)
            val topInsight = state.topInsight
            if (topInsight != null) {
                BentoCard(
                    overline = topInsight.typeLabel,
                    title = topInsight.headline,
                    icon = Icons.Filled.AutoAwesome,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    tonal = true,
                    tonalColor = insightBg,
                    onClick = onNavigateToInsights,
                ) {
                    if (!topInsight.body.isNullOrBlank()) {
                        Text(
                            text = topInsight.body,
                            style = LedgaText.BodyM,
                            color = insightInk,
                        )
                    }
                }
            } else {
                BentoCard(
                    overline = "Insights",
                    title = "All quiet — nothing unusual",
                    icon = Icons.Filled.AutoAwesome,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    tonal = true,
                    tonalColor = insightBg,
                    onClick = onNavigateToInsights,
                ) {
                    Text(
                        text = "Ledga watches your activity daily and surfaces anything " +
                                "worth attention. Tap to see how it works.",
                        style = LedgaText.BodyM,
                        color = insightInk,
                    )
                }
            }

            // ---- Recent activity ----
            Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recent activity",
                        style = LedgaText.TitleM,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "See all",
                        style = LedgaText.BodyM,
                        color = LedgaAccentDeep,
                        modifier = Modifier.clickable { onNavigateToActivity() },
                    )
                }

                if (state.recentTransactions.isEmpty()) {
                    BentoCard {
                        Text(
                            text = "No transactions yet. M-Pesa SMS will land here automatically.",
                            style = LedgaText.BodyM,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    BentoCard(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        state.recentTransactions.take(5).forEachIndexed { index, twc ->
                            TransactionRowV2(
                                recipient = twc.transaction.recipientName
                                    ?: twc.transaction.type.name.replace("_", " "),
                                amount = twc.transaction.amount,
                                isInflow = twc.transaction.direction.name == "INFLOW",
                                category = twc.category?.name,
                                color = twc.category?.color?.let(::parseColor) ?: Color.Gray,
                                icon = twc.category?.icon ?: "category",
                                balance = twc.transaction.balance,
                            )
                            if (index < state.recentTransactions.lastIndex.coerceAtMost(4)) {
                                androidx.compose.material3.HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = Space.Card),
                                )
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.padding(bottom = Space.s7))
        }
    }
}

@Composable
private fun LegendRow(name: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Text(
            text = name,
            style = LedgaText.BodyM,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun UpdateBanner(
    versionLabel: String,
    prefetched: Boolean,
    onSeeWhatsNew: () -> Unit,
    onUpdate: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.Card)),
        shape = RoundedCornerShape(Radius.Card),
        color = LedgaAccentSoft,
        border = androidx.compose.foundation.BorderStroke(1.dp, LedgaAccent),
    ) {
        Column(
            modifier = Modifier.padding(Space.Card),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = LedgaAccentDeep,
                    modifier = Modifier.size(20.dp),
                )
                val title = if (prefetched) "  Ledga $versionLabel is ready to install"
                else "  Ledga $versionLabel is available"
                Text(
                    text = title,
                    style = LedgaText.TitleS,
                    color = LedgaInk,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                Text(
                    text = "See what's new",
                    style = LedgaText.BodyM,
                    color = LedgaAccentDeep,
                    modifier = Modifier.clickable { onSeeWhatsNew() },
                )
                Text(
                    text = if (prefetched) "Install →" else "Update →",
                    style = LedgaText.BodyM,
                    color = LedgaAccentDeep,
                    modifier = Modifier.clickable { onUpdate() },
                )
            }
        }
    }
}

@Composable
private fun FulizaStatusCard(outstanding: Double?, available: Double?, asOf: Long?) {
    BentoCard(title = "Fuliza") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s5),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "OUTSTANDING",
                    style = LedgaText.Overline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = outstanding?.let { CurrencyFormatter.formatKshCompact(it) } ?: "—",
                    style = LedgaText.TitleM,
                    color = if ((outstanding ?: 0.0) > 0) LedgaDanger
                    else MaterialTheme.colorScheme.onSurface,
                )
                asOf?.let {
                    Text(
                        text = "as of ${DateUtils.formatRelativeDate(it).lowercase()}",
                        style = LedgaText.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AVAILABLE TO BORROW",
                    style = LedgaText.Overline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = available?.let { CurrencyFormatter.formatKshCompact(it) } ?: "—",
                    style = LedgaText.TitleM,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        // Usage bar — how deep into the limit the overdraft currently is.
        if (outstanding != null && available != null && outstanding + available > 0) {
            val usedFraction = (outstanding / (outstanding + available)).toFloat()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.s4)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outline),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(usedFraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (usedFraction > 0.5f) LedgaDanger else LedgaAccent),
                )
            }
        }
        // If the latest Fuliza SMS we have is more than a day old, the figure
        // may be behind reality (Safaricom now sends Fuliza from a separate
        // sender; older messages need a one-time re-import to be read in).
        val stale = asOf != null &&
            System.currentTimeMillis() - asOf > java.util.concurrent.TimeUnit.DAYS.toMillis(1)
        if (stale) {
            Text(
                text = "No recent Fuliza SMS read. Run “Import SMS history” in You → Data " +
                        "to pull in newer Fuliza messages.",
                style = LedgaText.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.s3),
            )
        }
    }
}
