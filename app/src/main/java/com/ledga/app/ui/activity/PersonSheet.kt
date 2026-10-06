package com.ledga.app.ui.activity

import com.ledga.app.data.lines.SelectedLine
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.TxRow
import com.ledga.app.data.room.dao.PersonSummary
import com.ledga.app.time.LiveClock
import com.ledga.app.ui.design.components.InitialAvatar
import com.ledga.app.ui.design.components.Leading
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
import com.ledga.core.money.Decimals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject
import com.ledga.app.ui.design.components.TxRow as TransactionRow

/** The person sheet (spec §10.4): who, what was sent and received, and every payment between you. */
data class PersonSheetUi(
    val person: PersonRowUi? = null,
    val summary: PersonSummary = PersonSummary(0, 0, 0, 0),
    val categories: Map<String, CategoryRow> = emptyMap(),
    val today: LocalDate? = null,
    /** "Business ··78" while a line is chosen (R47): the totals and payments are that line's only. */
    val lineLabel: String? = null,
)

@HiltViewModel
class PersonSheetViewModel @Inject constructor(
    private val ledger: LedgerQueries,
    db: LedgaDatabase,
    live: LiveClock,
    line: SelectedLine,
) : ViewModel() {
    private val person = MutableStateFlow<PersonRowUi?>(null)
    private val lineId = line.choice.map { it.lineId }.distinctUntilChanged()
    private val chosen = line.choice.map { it.selected }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<PersonSheetUi> = combine(person, chosen) { p, l -> p to l }.flatMapLatest { (p, l) ->
        if (p == null) {
            flowOf(PersonSheetUi())
        } else {
            combine(ledger.personSummary(p.key, l?.id), db.categoriesDao().observeAll(), live.today) { s, cats, today ->
                PersonSheetUi(p, s, cats.associateBy { it.key }, today, l?.let(TxText::lineLabel))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonSheetUi())

    /** Every payment with this person on the chosen line (R47), newest first, through the list's own filter. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val items: Flow<PagingData<TxRow>> =
        combine(person.filterNotNull().map { it.key }.distinctUntilChanged(), lineId) { key, id -> key to id }.flatMapLatest { (key, id) ->
            Pager(PagingConfig(pageSize = ActivityViewModel.PAGE, enablePlaceholders = false)) {
                ledger.transactions(TransactionFilter(counterpartyKey = key, lineId = id))
            }.flow
        }.cachedIn(viewModelScope)

    fun open(p: PersonRowUi) {
        person.value = p
    }
}

/** The person sheet's body: one lazy list (it scrolls itself inside the sheet, `app/DESIGN.md`). */
@Composable
fun PersonSheetContent(ui: PersonSheetUi, items: LazyPagingItems<TxRow>, onOpenTx: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    val p = ui.person ?: return
    LazyColumn(modifier.fillMaxWidth()) {
        item(key = "header") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                InitialAvatar(p.name, inflow = false, size = WellSize.XLarge)
                Text(p.name, Modifier.padding(top = Spacing.m).semantics { heading() }, style = LedgaType.section, color = c.ink, textAlign = TextAlign.Center)
                p.phone?.let { Text(it, style = LedgaType.caption, color = c.muted) }
                // People's line chip sits under the sheet, so the sheet says which line its totals are for (R47).
                ui.lineLabel?.let { Text("On $it", style = LedgaType.caption, color = c.muted) }
                Row(Modifier.fillMaxWidth().padding(top = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    StatTile("Sent · ${ui.summary.sentCount}", ksh(ui.summary.sentCents), Modifier.weight(1f))
                    StatTile("Received · ${ui.summary.receivedCount}", ksh(ui.summary.receivedCents), Modifier.weight(1f))
                }
                SectionHeader("Payments", Modifier.padding(top = Spacing.l, bottom = Spacing.s))
            }
        }
        items(count = items.itemCount, key = items.itemKey { it.code }) { i ->
            val row = items[i] ?: return@items
            val category = ui.categories[row.categoryKey]
            val day = DateLabels.nairobiDate(row.occurredAt)
            if (i > 0) RowDivider()
            TransactionRow(
                leading = Leading.Icon(category?.icon3d ?: "fluent_package"),
                title = TxText.subtitle(row, category?.name ?: "Other"),
                subtitle = DateLabels.date(day),
                subtitleTail = DateLabels.clock(row.occurredAt),
                amountCents = row.amountCents,
                inflow = TxText.isInflow(row.flow),
                speech = TxText.speech(row, ui.today ?: day),
                onClick = { onOpenTx(row.code) },
                onClickLabel = "Open payment",
            )
        }
    }
}

private fun ksh(cents: Long): String = "${AmountFormat.CURRENCY} ${AmountFormat.plain(cents, Decimals.NEVER)}"

/** Opens the person sheet for [person] (null = closed); a payment opens the transaction sheet on top. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonSheetHost(
    person: PersonRowUi?,
    onDismiss: () -> Unit,
    onOpenTx: (String) -> Unit,
    vm: PersonSheetViewModel = hiltViewModel(key = "person-sheet"),
) {
    if (person == null) return
    LaunchedEffect(person.key) { vm.open(person) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val items = vm.items.collectAsLazyPagingItems()
    LedgaModalSheet(onDismiss = onDismiss, title = null) {
        PersonSheetContent(ui.takeIf { it.person?.key == person.key } ?: PersonSheetUi(person), items, onOpenTx)
    }
}
