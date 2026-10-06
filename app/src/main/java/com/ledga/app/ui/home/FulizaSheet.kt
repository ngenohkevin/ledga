package com.ledga.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.ledga.app.data.derive.FlowFilter
import com.ledga.app.data.derive.FulizaStatus
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.lines.SelectedLine
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.TxRow
import com.ledga.app.time.LiveClock
import com.ledga.app.ui.activity.ActivityViewModel
import com.ledga.app.ui.design.components.CategoryIcon
import com.ledga.app.ui.design.components.EmptyState
import com.ledga.app.ui.design.components.LedgaModalSheet
import com.ledga.app.ui.design.components.RowDivider
import com.ledga.app.ui.design.components.SectionHeader
import com.ledga.app.ui.design.components.StatTile
import com.ledga.app.ui.design.components.WellSize
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.app.ui.tx.TxText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject
import com.ledga.app.ui.design.components.TxRow as TransactionRow

/** The Fuliza sheet (spec §10.4, R58): the line's status, then its draws and repayments. */
data class FulizaSheetUi(
    val loaded: Boolean = false,
    val status: FulizaStatus? = null,
    /** "Business ··78" while a line is chosen (R47). */
    val lineLabel: String? = null,
    val categories: Map<String, CategoryRow> = emptyMap(),
    val today: LocalDate? = null,
)

@HiltViewModel
class FulizaSheetViewModel @Inject constructor(
    private val ledger: LedgerQueries,
    db: LedgaDatabase,
    line: SelectedLine,
    live: LiveClock,
) : ViewModel() {
    val ui: StateFlow<FulizaSheetUi> = combine(line.choice, ledger.fulizaReadings(), db.categoriesDao().observeAll(), live.today) { ch, readings, cats, today ->
        FulizaSheetUi(true, FulizaStatus.forLine(readings, ch.lineId), ch.selected?.let(TxText::lineLabel), cats.associateBy { it.key }, today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FulizaSheetUi())

    /** Every payment Fuliza touched on the chosen line: Transactions' own Fuliza filter (R38), newest first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val items: Flow<PagingData<TxRow>> = line.choice.map { it.lineId }.distinctUntilChanged().flatMapLatest { id ->
        Pager(PagingConfig(pageSize = ActivityViewModel.PAGE, enablePlaceholders = false)) {
            ledger.transactions(TransactionFilter(flow = FlowFilter.FULIZA, lineId = id))
        }.flow
    }.cachedIn(viewModelScope)
}

/** The sheet's body: one lazy list, which scrolls itself inside the sheet (`app/DESIGN.md`). */
@Composable
fun FulizaSheetContent(ui: FulizaSheetUi, items: LazyPagingItems<TxRow>, onOpenTx: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    LazyColumn(modifier.fillMaxWidth()) {
        item(key = "header") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CategoryIcon("fluent_credit_card", contentDescription = null, size = WellSize.XLarge)
                Text(
                    listOfNotNull("Fuliza", ui.lineLabel).joinToString(" · "),
                    Modifier.padding(top = Spacing.m).semantics { heading() },
                    style = LedgaType.section,
                    color = c.ink,
                    textAlign = TextAlign.Center,
                )
                val s = ui.status
                if (s != null) {
                    val owed = s.outstanding.cents > 0
                    Row(Modifier.fillMaxWidth().padding(top = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        StatTile("Owed", if (owed) "${AmountFormat.CURRENCY} ${AmountFormat.plain(s.outstanding.cents)}" else "Nothing", Modifier.weight(1f))
                        StatTile(if (owed) "Still available" else "Available", s.available?.let { HomeText.ksh(it.cents) } ?: "Not known yet", Modifier.weight(1f))
                    }
                    s.dueDate?.let { due ->
                        val late = ui.today != null && due < ui.today
                        Text(HomeText.due(due, ui.today), Modifier.padding(top = Spacing.s), style = LedgaType.caption, color = if (late) c.danger else c.muted)
                    }
                }
                SectionHeader("Draws and repayments", Modifier.padding(top = Spacing.l, bottom = Spacing.s))
            }
        }
        if (items.loadState.refresh is LoadState.NotLoading && items.itemCount == 0) {
            item(key = "empty") { EmptyState("fluent_credit_card", "No Fuliza yet", "Payments Fuliza helps with, and their repayments, show up here.") }
        }
        items(count = items.itemCount, key = items.itemKey { it.code }) { i ->
            val row = items[i] ?: return@items
            val category = ui.categories[row.categoryKey]
            val day = DateLabels.nairobiDate(row.occurredAt)
            if (i > 0) RowDivider()
            val drawn = row.fulizaDrawnCents?.takeIf { it > 0 }
            TransactionRow(
                leading = TxText.leading(row, category?.icon3d ?: "fluent_package"),
                title = TxText.title(row),
                subtitle = if (ui.today != null && day.year != ui.today.year) DateLabels.date(day) else DateLabels.dayMonth(day),
                amountCents = row.amountCents,
                // The draw sits under the amount, which never gives way: as the subtitle it was cut by a long date.
                balanceText = drawn?.let { "Fuliza ${AmountFormat.CURRENCY} ${AmountFormat.plain(it)}" },
                inflow = TxText.isInflow(row.flow),
                speech = TxText.speech(row, ui.today ?: day),
                onClick = { onOpenTx(row.code) },
                onClickLabel = "Open payment",
            )
        }
    }
}

/** Opens the Fuliza sheet; a payment opens the transaction sheet on top, and Hide closes this one too (R58). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FulizaSheetHost(open: Boolean, onDismiss: () -> Unit, onOpenTx: (String) -> Unit, vm: FulizaSheetViewModel = hiltViewModel(key = "fuliza-sheet")) {
    if (!open) return
    val ui by vm.ui.collectAsStateWithLifecycle()
    val items = vm.items.collectAsLazyPagingItems()
    LedgaModalSheet(onDismiss = onDismiss, title = null) { FulizaSheetContent(ui, items, onOpenTx) }
}
