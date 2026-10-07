package com.ledga.app.ui.activity

import kotlinx.coroutines.launch
import com.ledga.app.data.lines.SelectedLine
import com.ledga.app.data.lines.LineChoice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.derive.DateFilter
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.trackers.CategoryMeasure
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.dao.CategoryTotal
import com.ledga.app.data.room.dao.MonthTotal
import com.ledga.app.data.room.dao.PeriodTotals
import com.ledga.app.time.LiveClock
import com.ledga.app.ui.design.charts.Bar
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.core.chart.Bucketing
import com.ledga.core.model.Categories
import com.ledga.core.model.CategoryGroup
import com.ledga.core.money.Decimals
import com.ledga.core.money.Money
import com.ledga.core.time.InstantRange
import com.ledga.core.time.Nairobi
import com.ledga.core.time.Period
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

/** One "Where it went" row (spec §10.4): a category, or a group of them. */
data class ShareRow(
    val id: String,
    val name: String,
    val icon3d: String,
    /** The category whose colour the bar takes; a group takes its first category's. */
    val colorKey: String,
    val color: String?,
    val colorDark: String?,
    val cents: Long,
    val count: Int,
    val fraction: Float,
    /** What a tap filters Transactions to. */
    val categoryKeys: Set<String>,
    /**
     * D3: a category row opens its page only when the category counts as spending; a Money in or Not spending category
     * here is only its fees, which its page (received or moved) wouldn't show, so it opens Transactions (final review I1).
     */
    val opensPage: Boolean = false,
)

/** D3: what a "Where it went" row opens — a category's page at the month shown, or (by group) Transactions. */
sealed interface ShareTap {
    data class Page(val categoryKey: String, val month: String?) : ShareTap
    data class Transactions(val filter: TransactionFilter) : ShareTap
}

/** Activity › Spending (spec §10.4, mockup `spending`). Every number reads the `ledger` view. */
data class SpendingUi(
    val loaded: Boolean = false,
    val month: YearMonth? = null,
    val current: YearMonth? = null,
    val earliest: YearMonth? = null,
    val totals: PeriodTotals = PeriodTotals(0, 0, 0),
    val deltaPercent: Int? = null,
    /** "Aug" for a whole month, "same days Sep" for the current one (spec §7.6). */
    val comparedWith: String = "",
    val months: List<YearMonth> = emptyList(),
    val bars: List<Bar> = emptyList(),
    val byGroup: Boolean = false,
    val shares: List<ShareRow> = emptyList(),
    val line: LineChoice = LineChoice(),
) {
    val isCurrent: Boolean get() = month != null && month == current
    val canGoBack: Boolean get() = month != null && earliest != null && month > earliest
    val canGoForward: Boolean get() = month != null && current != null && month < current
    val selectedIndex: Int? get() = months.indexOf(month).takeIf { it >= 0 }
}

/** Spending, month by month (spec §10.4): the selected month follows the current one until the person steps away. */
@HiltViewModel
class SpendingViewModel @Inject constructor(
    private val ledger: LedgerQueries,
    private val db: LedgaDatabase,
    private val live: LiveClock,
    private val line: SelectedLine,
    private val links: ActivityLinks,
) : ViewModel() {
    /** The month the person stepped to; null follows the current month, so the view moves on when a month ends. */
    private val chosen = MutableStateFlow<YearMonth?>(null)
    private val byGroup = MutableStateFlow(false)

    init {
        // R57: Home's spending card opens Spending at the current month, however far back the person stepped before.
        viewModelScope.launch { links.spendingHops.drop(1).collect { chosen.value = null } }
    }

    private data class Selection(
        val today: LocalDate,
        val current: YearMonth,
        val earliest: YearMonth,
        val month: YearMonth,
        val byGroup: Boolean,
        val line: LineChoice,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<SpendingUi> = combine(live.today, chosen, byGroup, db.transactionsDao().observeSpan(), line.choice) { today, picked, group, span, choice ->
        val current = YearMonth.from(today)
        val earliest = span.firstAt?.let { YearMonth.from(Periods.dateOf(it)) }?.coerceAtMost(current) ?: current
        Selection(today, current, earliest, (picked ?: current).coerceIn(earliest, current), group, choice)
    }
        .distinctUntilChanged()
        .flatMapLatest(::load)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpendingUi())

    fun previous() {
        val u = ui.value
        val m = u.month ?: return
        if (u.canGoBack) chosen.value = m.minusMonths(1)
    }

    fun next() {
        val u = ui.value
        val m = u.month ?: return
        if (u.canGoForward) chosen.value = m.plusMonths(1).takeIf { it != u.current }
    }

    /** A tap on a bar (padding months before the first payment are ignored). */
    fun select(index: Int) {
        val u = ui.value
        val m = u.months.getOrNull(index) ?: return
        if (u.earliest != null && m < u.earliest) return
        chosen.value = m.takeIf { it != u.current }
    }

    fun setByGroup(on: Boolean) {
        byGroup.value = on
    }

    /** R47: the line chip on Spending changes the one choice every summary follows. */
    fun selectLine(id: Long?) {
        viewModelScope.launch { line.select(id) }
    }

    /** "Tap → filtered transactions" (spec §10.4): that category or group, in the selected month (R69: live while current). */
    fun transactionsFor(row: ShareRow): TransactionFilter {
        val m = ui.value.month ?: return TransactionFilter(categoryKeys = row.categoryKeys, lineId = ui.value.line.lineId)
        return TransactionFilter(categoryKeys = row.categoryKeys, dates = DateFilter.Month(m), lineId = ui.value.line.lineId)
    }

    /**
     * D3: a spending category's row opens its page at this month; a group row, or a category that is only fees here
     * (final review I1), opens Transactions narrowed to it.
     */
    fun tap(row: ShareRow): ShareTap =
        if (row.opensPage) ShareTap.Page(row.id, ui.value.month?.toString()) else ShareTap.Transactions(transactionsFor(row))

    private fun load(s: Selection): Flow<SpendingUi> {
        val now = live.now()
        val period = Period(PeriodType.MONTH, s.month.atDay(1))
        val isCurrent = s.month == s.current
        val range = Periods.liveRange(period, now)
        // Spec §7.6: the current month against the same elapsed time of the last; a whole month against the whole last one.
        val compare = if (isCurrent) Periods.comparisonWindow(PeriodType.MONTH, now) else period.previous().range()
        val previousName = s.month.minusMonths(1).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        val months = window(s)
        val chartFrom = months.first().atDay(1).atStartOfDay(Nairobi.ZONE).toInstant()
        val lineId = s.line.lineId
        return combine(
            ledger.totals(range, lineId),
            ledger.spent(compare, lineId),
            ledger.spentByMonth(InstantRange(chartFrom, null), lineId),
            ledger.spentByCategory(range, lineId),
            db.categoriesDao().observeAll(),
        ) { totals, before, monthly, byCategory, categories ->
            SpendingUi(
                loaded = true,
                month = s.month,
                current = s.current,
                earliest = s.earliest,
                totals = totals,
                deltaPercent = Bucketing.deltaPercent(Money(totals.spentCents), before),
                comparedWith = if (isCurrent) "same days $previousName" else previousName,
                months = months,
                bars = bars(months, monthly, s.current),
                byGroup = s.byGroup,
                shares = shares(byCategory, categories, s.byGroup),
                line = s.line,
            )
        }
    }

    /** 8–12 bars (spec §10.4): up to 12 months ending at the current month, or at the chosen month when it is older. */
    private fun window(s: Selection): List<YearMonth> {
        val end = if (s.month >= s.current.minusMonths(MAX_BARS - 1L)) s.current else s.month
        val count = (ChronoUnit.MONTHS.between(s.earliest, end) + 1).toInt().coerceIn(MIN_BARS, MAX_BARS)
        return (count - 1 downTo 0).map { end.minusMonths(it.toLong()) }
    }

    private fun bars(months: List<YearMonth>, monthly: Map<String, MonthTotal>, current: YearMonth): List<Bar> = months.map { m ->
        val cents = monthly[m.toString()]?.cents ?: 0L
        val so = if (m == current) " so far" else ""
        Bar(
            label = m.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH),
            segments = listOf(cents),
            speech = "${DateLabels.monthYear(m)}$so: ${AmountFormat.CURRENCY} ${AmountFormat.plain(cents, Decimals.NEVER)}",
            inProgress = m == current,
        )
    }

    private fun shares(rows: List<CategoryTotal>, categories: List<CategoryRow>, byGroup: Boolean): List<ShareRow> {
        val total = rows.sumOf { it.cents }
        if (total <= 0) return emptyList()
        val byKey = categories.associateBy { it.key }
        val entries = if (!byGroup) {
            rows.map { r ->
                val cat = byKey[r.categoryKey]
                ShareRow(
                    r.categoryKey, cat?.name ?: "Other", cat?.icon3d ?: FALLBACK_ICON, r.categoryKey, cat?.color, cat?.colorDark, r.cents, r.count, 0f,
                    setOf(r.categoryKey), opensPage = cat != null && CategoryMeasure.of(cat.groupKey) == CategoryMeasure.SPENT,
                )
            }
        } else {
            rows.groupBy { byKey[it.categoryKey]?.groupKey ?: CategoryGroup.MONEY }.map { (group, list) ->
                val lead = categories.filter { it.groupKey == group }.minByOrNull { it.sortOrder }
                ShareRow(
                    group.name, group.displayName, lead?.icon3d ?: FALLBACK_ICON, lead?.key ?: Categories.OTHER, lead?.color, lead?.colorDark,
                    list.sumOf { it.cents }, list.sumOf { it.count }, 0f, list.map { it.categoryKey }.toSet(),
                )
            }
        }
        return entries.sortedByDescending { it.cents }.map { it.copy(fraction = it.cents.toFloat() / total) }
    }

    private companion object {
        const val MIN_BARS = 8
        const val MAX_BARS = 12
        const val FALLBACK_ICON = "fluent_package"
    }
}
