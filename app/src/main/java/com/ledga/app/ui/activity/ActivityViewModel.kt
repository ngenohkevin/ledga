package com.ledga.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.ledga.app.data.derive.FlowFilter
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.LineRow
import com.ledga.app.data.room.dao.DayTotal
import com.ledga.app.time.LiveClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Activity's three segments (spec §10.4). */
enum class ActivitySegment(val label: String) {
    TRANSACTIONS("Transactions"),
    SPENDING("Spending"),
    PEOPLE("People"),
}

/** Everything the Transactions segment shows besides the paged rows. */
data class TransactionsUi(
    /** The search as typed; the list follows it once typing settles. */
    val query: String = "",
    val filter: TransactionFilter = TransactionFilter(),
    val dayTotals: Map<LocalDate, DayTotal> = emptyMap(),
    val categories: Map<String, CategoryRow> = emptyMap(),
    val lines: List<LineRow> = emptyList(),
    val today: LocalDate? = null,
    /** False when this phone has no payments at all yet (not merely none matching). */
    val hasHistory: Boolean = true,
    /** Counts the requests to focus the search field (R61); the pane focuses it when this goes up. */
    val searchFocus: Int = 0,
)

/** Activity's segment and its Transactions list (spec §10.4); Spending and People have their own ViewModels. */
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val ledger: LedgerQueries,
    db: LedgaDatabase,
    lines: LinesRepository,
    private val live: LiveClock,
    private val edits: TransactionEdits,
    private val links: ActivityLinks,
) : ViewModel() {
    private val _segment = MutableStateFlow(ActivitySegment.TRANSACTIONS)
    val segment: StateFlow<ActivitySegment> = _segment

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(TransactionFilter())
    private val searchFocus = MutableStateFlow(0)

    /** One timer for the list and the day headers (4c testing note: `LiveClock` runs one timer per collector). */
    private val today = live.today.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** What a load runs: the filter, and the day its dates are turned into a range against (R69; null without dates). */
    private data class Load(val filter: TransactionFilter, val today: LocalDate?)

    /** Chips and sheet at once, the typed search once typing settles (cleared at once), and a new range each day. */
    @OptIn(FlowPreview::class)
    private val settled: Flow<Load> =
        combine(filter, query.debounce { if (it.isEmpty()) 0L else QUERY_SETTLE_MS }, today) { f, q, t ->
            val run = f.copy(query = q)
            Load(run, t.takeIf { run.dates != null })
        }.distinctUntilChanged()

    /** The paged list; each settled filter and search is a new numbered load (R184: a new one starts at the top). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val items: Flow<PagingData<ActivityItem>> = settled.withIndex()
        .flatMapLatest { (n, l) -> Pager(PAGING) { ledger.transactions(l.filter, l.today) }.flow.map { it.toActivityItems(load = n) } }
        .cachedIn(viewModelScope)

    private data class Extras(val lines: List<LineRow>, val today: LocalDate, val hasHistory: Boolean, val searchFocus: Int)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<TransactionsUi> = combine(
        filter,
        query,
        settled.flatMapLatest { ledger.dayTotals(it.filter, it.today) },
        db.categoriesDao().observeAll().map { rows -> rows.associateBy { it.key } },
        combine(lines.observe(), today, db.transactionsDao().observeSpan(), searchFocus) { ls, day, span, focus ->
            Extras(ls, day, span.count > 0, focus)
        },
    ) { f, q, totals, categories, x ->
        TransactionsUi(q, f, totals, categories, x.lines, x.today, x.hasHistory, x.searchFocus)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUi())

    /** R61, R93: the hand-off waiting for Activity; the screen takes it while it is shown ([TakeLinks]). */
    val pending: StateFlow<ActivityLink?> = links.requests

    /** R61: applies [link] once; a newer request stays waiting. */
    fun take(link: ActivityLink) {
        when (link) {
            is ActivityLink.Transactions -> {
                showTransactions(link.filter)
                if (link.focusSearch) searchFocus.update { it + 1 }
            }
            ActivityLink.Spending -> _segment.value = ActivitySegment.SPENDING
            ActivityLink.People -> _segment.value = ActivitySegment.PEOPLE
        }
        links.taken(link)
    }

    fun select(segment: ActivitySegment) {
        _segment.value = segment
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun setFlow(flow: FlowFilter) = filter.update { it.copy(flow = flow) }

    /** A line chip toggles (R38): tap it again for every line. */
    fun toggleLine(lineId: Long) = filter.update { it.copy(lineId = if (it.lineId == lineId) null else lineId) }

    /** The filter sheet's "Show results": its four filters replace the current ones; the chips and line stay. */
    fun applySheet(sheet: TransactionFilter) = filter.update {
        it.copy(categoryKeys = sheet.categoryKeys, dates = sheet.dates, minAmountCents = sheet.minAmountCents, includeHidden = sheet.includeHidden)
    }

    /** R61: the pane focused the search field; the request is done. */
    fun searchFocused() {
        searchFocus.value = 0
    }

    fun clearFilters() {
        filter.value = TransactionFilter()
        query.value = ""
    }

    /** Spending's "tap → filtered transactions" (spec §10.4): Transactions, narrowed to [f]. */
    fun showTransactions(f: TransactionFilter) {
        filter.value = f
        query.value = ""
        _segment.value = ActivitySegment.TRANSACTIONS
    }

    /** The snackbar's Undo after Hide (spec §10.4). */
    fun undoHide(code: String): Job = viewModelScope.launch { edits.setHidden(code, false) }

    companion object {
        const val PAGE = 50
        const val QUERY_SETTLE_MS = 250L

        /**
         * Every payments list's paging: Transactions, the person sheet, the Fuliza sheet. Placeholders stay on. Each edit
         * reloads the list from the position paging reports, and without placeholders that position counts only the
         * rows loaded since the last reload, so the second edit deep in a list reloaded it near the top (owner report,
         * 2026-10-08).
         */
        val PAGING = PagingConfig(pageSize = PAGE, enablePlaceholders = true)
    }
}
