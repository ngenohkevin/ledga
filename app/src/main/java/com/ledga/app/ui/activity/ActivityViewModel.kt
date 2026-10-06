package com.ledga.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.ledga.app.data.derive.DateFilter
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
import com.ledga.core.time.InstantRange
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Activity's three segments (spec §10.4). */
enum class ActivitySegment(val label: String) {
    TRANSACTIONS("Transactions"),
    SPENDING("Spending"),
    PEOPLE("People"),
}

/** The filter sheet's date presets (R39). "Last 3 months" is this month and the two before it. */
enum class DatePreset(val label: String) {
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    LAST_3_MONTHS("Last 3 months"),
    THIS_YEAR("This year"),
    ;

    fun filter(now: Instant): DateFilter {
        val month = Periods.current(PeriodType.MONTH, now)
        val range = when (this) {
            THIS_MONTH -> Periods.liveRange(month, now)
            LAST_MONTH -> month.previous().range()
            LAST_3_MONTHS -> InstantRange(month.previous().previous().startInstant, null)
            THIS_YEAR -> Periods.liveRange(Periods.current(PeriodType.YEAR, now), now)
        }
        return DateFilter(label, range)
    }
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
)

/** Activity's segment and its Transactions list (spec §10.4); Spending and People have their own ViewModels. */
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val ledger: LedgerQueries,
    db: LedgaDatabase,
    lines: LinesRepository,
    private val live: LiveClock,
    private val edits: TransactionEdits,
) : ViewModel() {
    private val _segment = MutableStateFlow(ActivitySegment.TRANSACTIONS)
    val segment: StateFlow<ActivitySegment> = _segment

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(TransactionFilter())

    /** What the list runs: chips and sheet at once, the typed search once typing settles (cleared at once). */
    @OptIn(FlowPreview::class)
    private val settled: Flow<TransactionFilter> =
        combine(filter, query.debounce { if (it.isEmpty()) 0L else QUERY_SETTLE_MS }) { f, q -> f.copy(query = q) }
            .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: Flow<PagingData<ActivityItem>> = settled
        .flatMapLatest { f -> Pager(PagingConfig(pageSize = PAGE, enablePlaceholders = false)) { ledger.transactions(f) }.flow }
        .map { it.toActivityItems() }
        .cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<TransactionsUi> = combine(
        filter,
        query,
        settled.flatMapLatest { ledger.dayTotals(it) },
        db.categoriesDao().observeAll().map { rows -> rows.associateBy { it.key } },
        combine(lines.observe(), live.today, db.transactionsDao().observeSpan()) { ls, today, span -> Triple(ls, today, span.count > 0) },
    ) { f, q, totals, categories, (ls, today, history) ->
        TransactionsUi(q, f, totals, categories, ls, today, history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUi())

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

    fun now(): Instant = live.now()

    companion object {
        const val PAGE = 50
        const val QUERY_SETTLE_MS = 250L
    }
}
