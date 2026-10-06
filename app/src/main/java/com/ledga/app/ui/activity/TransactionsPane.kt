package com.ledga.app.ui.activity

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.ledga.app.data.derive.FlowFilter
import com.ledga.app.data.room.TxRow
import com.ledga.app.ui.design.components.ChoiceChip
import com.ledga.app.ui.design.components.DayHeader
import com.ledga.app.ui.design.components.EmptyState
import com.ledga.app.ui.design.components.ErrorState
import com.ledga.app.ui.design.components.SearchField
import com.ledga.app.ui.design.components.Segment
import com.ledga.app.ui.design.components.SkeletonRow
import com.ledga.app.ui.design.components.cardSegment
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.tokens.Radii
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.tx.TxText
import com.ledga.app.ui.design.components.TxRow as TransactionRow

/** Everything the Transactions segment can do; `ActivityTab` wires it to the ViewModel and the sheets. */
data class TransactionsActions(
    val onQuery: (String) -> Unit = {},
    val onFlow: (FlowFilter) -> Unit = {},
    val onLine: (Long) -> Unit = {},
    val onOpen: (String) -> Unit = {},
    val onPickCategory: (String) -> Unit = {},
    val onClearFilters: () -> Unit = {},
)

/**
 * Activity › Transactions (spec §10.4, mockup `activity`):
 * - search;
 * - the flow and line chips;
 * - the paged list in day cards with Out/In totals.
 *
 * Rows leave the balance out (dev-e2: it squeezes the category at 1.3×); the transaction sheet shows it.
 */
@Composable
fun TransactionsPane(ui: TransactionsUi, items: LazyPagingItems<ActivityItem>, actions: TransactionsActions, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Owner ruling M5: in a short pane (a phone in landscape) the search and chips scroll away with the list, so the
        // payments get the height; in a normal one they stay put above it (mockup `activity`).
        val controlsScroll = maxHeight < SHORT_PANE
        Column(Modifier.fillMaxSize()) {
            if (!controlsScroll) Controls(ui, actions)
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = if (controlsScroll) 0.dp else Spacing.s)) {
                TransactionList(ui, items, actions, header = if (controlsScroll) ({ Controls(ui, actions) }) else null)
            }
        }
    }
}

/** Below this height the controls scroll with the list (M5): fixed, they would leave a phone in landscape one row. */
private val SHORT_PANE = 400.dp

@Composable
private fun Controls(ui: TransactionsUi, actions: TransactionsActions) {
    Column(Modifier.fillMaxWidth()) {
        SearchField(ui.query, actions.onQuery, "Name, phone, code or amount", Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s))
        FilterChips(ui, actions)
    }
}

private val FlowFilter.label: String
    get() = when (this) {
        FlowFilter.ALL -> "All"
        FlowFilter.OUT -> "Money out"
        FlowFilter.IN -> "Money in"
        FlowFilter.FULIZA -> "Fuliza"
    }

private val FlowFilter.icon: ImageVector?
    get() = when (this) {
        FlowFilter.OUT -> Ph.ArrowUpRight
        FlowFilter.IN -> Ph.ArrowDownLeft
        else -> null
    }

/** R38: the flow chips are one choice (radio buttons); a line chip toggles on its own. */
@Composable
private fun FilterChips(ui: TransactionsUi, actions: TransactionsActions) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            FlowFilter.entries.forEach { f ->
                ChoiceChip(f.label, ui.filter.flow == f, { actions.onFlow(f) }, icon = f.icon, role = Role.RadioButton)
            }
        }
        if (ui.lines.size >= 2) {
            ui.lines.forEach { line ->
                ChoiceChip(TxText.lineLabel(line), ui.filter.lineId == line.id, { actions.onLine(line.id) }, icon = Ph.SimCard)
            }
        }
    }
}

/** The list, or its loading, error or empty state. [header] (the controls in a short pane) scrolls above either. */
@Composable
private fun TransactionList(
    ui: TransactionsUi,
    items: LazyPagingItems<ActivityItem>,
    actions: TransactionsActions,
    header: (@Composable () -> Unit)?,
) {
    val refresh = items.loadState.refresh
    when {
        items.itemCount == 0 && refresh is LoadState.Loading -> WithHeader(header) { LoadingCard() }
        items.itemCount == 0 && refresh is LoadState.Error -> WithHeader(header) {
            ErrorState(
                title = "Couldn't read your payments",
                body = "Something went wrong reading this phone's history.",
                onRetry = { items.retry() },
            )
        }
        items.itemCount == 0 && !ui.hasHistory && ui.filter.isEverything && ui.query.isBlank() -> WithHeader(header) {
            EmptyState(
                iconKey = "fluent_memo",
                title = "No payments yet",
                body = "M-Pesa payments show up here as soon as Ledga reads them.",
            )
        }
        items.itemCount == 0 -> WithHeader(header) {
            EmptyState(
                iconKey = "fluent_magnifying_glass_tilted_left",
                title = "Nothing matches",
                body = "Try another name, code or amount, or clear the filters.",
                actionLabel = "Clear filters",
                onAction = actions.onClearFilters,
            )
        }
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.xxl)) {
            if (header != null) {
                item(key = "controls", contentType = "controls") { Column(Modifier.padding(bottom = Spacing.s)) { header() } }
            }
            items(count = items.itemCount, key = items.itemKey { it.key }, contentType = items.itemContentType { it::class.simpleName }) { i ->
                Box(Modifier.padding(horizontal = Spacing.screen)) {
                    when (val item = items[i]) {
                        is ActivityItem.Day -> DayStart(item, ui)
                        is ActivityItem.Tx -> TxListRow(item.row, dividerAbove = i > 0 && items.peek(i - 1) is ActivityItem.Tx, ui, actions)
                        ActivityItem.End -> CardCap()
                        null -> SkeletonRow()
                    }
                }
            }
        }
    }
}

/** A state screen under the scrolling controls of a short pane, or on its own. */
@Composable
private fun WithHeader(header: (@Composable () -> Unit)?, content: @Composable () -> Unit) {
    if (header == null) {
        content()
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            header()
            Spacer(Modifier.height(Spacing.s))
            content()
        }
    }
}

@Composable
private fun LoadingCard() {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).cardSegment(Segment.Single)) {
        repeat(6) { SkeletonRow() }
    }
}

/** A day's header (R40), closing the previous day's card first when there is one. */
@Composable
private fun DayStart(item: ActivityItem.Day, ui: TransactionsUi) {
    val totals = ui.dayTotals[item.day]
    Column(Modifier.fillMaxWidth()) {
        if (item.closesPrevious) {
            CardCap()
            Spacer(Modifier.height(Spacing.m))
        }
        DayHeader(
            // Until the live clock answers (a frame at most), no day is called TODAY or YESTERDAY.
            label = DateLabels.dayHeader(item.day, ui.today ?: item.day.plusDays(2)),
            outCents = totals?.outCents ?: 0L,
            inCents = totals?.inCents ?: 0L,
            modifier = Modifier.cardSegment(Segment.Top),
        )
    }
}

/** The day card's rounded bottom (R40): as tall as the corner, so the arc starts where the last row ends. */
@Composable
private fun CardCap() = Box(Modifier.fillMaxWidth().height(Radii.card).cardSegment(Segment.Bottom))

@Composable
private fun TxListRow(row: TxRow, dividerAbove: Boolean, ui: TransactionsUi, actions: TransactionsActions) {
    val category = ui.categories[row.categoryKey]
    val subtitle = TxText.subtitle(row, category?.name ?: "Other")
    TransactionRow(
        leading = TxText.leading(row, category?.icon3d ?: "fluent_package"),
        title = TxText.title(row),
        subtitle = if (row.isHidden) "Hidden · $subtitle" else subtitle,
        subtitleTail = DateLabels.clock(row.occurredAt),
        amountCents = row.amountCents,
        inflow = TxText.isInflow(row.flow),
        speech = TxText.speech(row, ui.today ?: DateLabels.nairobiDate(row.occurredAt)),
        modifier = Modifier.cardSegment(Segment.Middle, dividerAbove),
        onClick = { actions.onOpen(row.code) },
        onClickLabel = "Open payment",
        onLongClick = { actions.onPickCategory(row.code) },
        onLongClickLabel = "Change category",
    )
}
