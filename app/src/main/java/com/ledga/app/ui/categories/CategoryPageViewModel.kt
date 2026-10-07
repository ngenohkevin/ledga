package com.ledga.app.ui.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.edit.CategoryLooks
import com.ledga.app.data.edit.CategoryRules
import com.ledga.app.data.edit.RemovedRule
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.lines.LineChoice
import com.ledga.app.data.lines.SelectedLine
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.RuleRow
import com.ledga.app.data.room.TxRow
import com.ledga.app.data.trackers.CategoryDetail
import com.ledga.app.data.trackers.CategoryMeasure
import com.ledga.app.data.trackers.Trackers
import com.ledga.app.time.LiveClock
import com.ledga.app.ui.activity.ActivityLink
import com.ledga.app.ui.activity.ActivityLinks
import com.ledga.app.ui.design.charts.Bar
import com.ledga.app.ui.design.format.NameFormat
import com.ledga.app.ui.rules.RuleDraft
import com.ledga.app.ui.rules.ShownPreview
import com.ledga.app.ui.trackers.TrackerText
import com.ledga.core.chart.Bucket
import com.ledga.core.chart.Bucketing
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.model.Categories
import com.ledga.core.time.Nairobi
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** The page's chart ranges (spec §10.4, R54). */
enum class DetailRange(val label: String) {
    SIX_MONTHS("6M"),
    TWELVE_MONTHS("12M"),
    ALL("All"),
}

/** One rule on a category's page (R73, R94). */
data class RuleUi(val id: Long, val label: String, val builtIn: Boolean, val enabled: Boolean)

/** One of a category's top places (D4, R96): who, how much in the category's measure, how many payments. */
data class TopPlaceUi(val counterpartyKey: String, val name: String, val totalCents: Long, val count: Int)

/** A category's page (4e spec §3.2). */
data class CategoryPageUi(
    val loaded: Boolean = false,
    /** The category no longer exists (or never did): the page offers Back. */
    val missing: Boolean = false,
    val category: CategoryRow? = null,
    val measure: CategoryMeasure = CategoryMeasure.SPENT,
    val line: LineChoice = LineChoice(),
    val range: DetailRange = DetailRange.TWELVE_MONTHS,
    val buckets: List<Bucket> = emptyList(),
    val bars: List<Bar> = emptyList(),
    val selectedIndex: Int? = null,
    /** The dashed line: per month, or per completed year once All shows years (R54). */
    val averageCents: Long? = null,
    val thisMonthCents: Long = 0,
    val lastMonthCents: Long = 0,
    /** "Avg / month" (R52). */
    val monthlyAverageCents: Long? = null,
    val year: Int = 0,
    val yearSoFarCents: Long = 0,
    val topPlaces: List<TopPlaceUi> = emptyList(),
    val payments: List<TxRow> = emptyList(),
    /** Its payments that show, on every line: none at all is the empty state (§3.2.8) and R72's archive question. */
    val paymentCount: Int = 0,
    val rules: List<RuleUi> = emptyList(),
    val today: LocalDate? = null,
) {
    /** Archive is for the person's own categories (R72). */
    val own: Boolean get() = category?.origin == CategoryOrigin.USER

    /** R50: spending groups only, and not while archived. */
    val canTrack: Boolean get() = category != null && !category.archived && CategoryMeasure.of(category.groupKey) == CategoryMeasure.SPENT

    /** Own-account rules come from a payment's "My own account" switch (R37). */
    val canAddRule: Boolean get() = category != null && category.key != Categories.OWN_ACCOUNTS

    /** D6: a built-in category whose icon or colour is no longer its seed's. */
    val canReset: Boolean get() = category?.let(CategoryLooks::differsFromSeed) == true

    /** §3.2.8: no payment in it shows, on any line. */
    val empty: Boolean get() = loaded && !missing && paymentCount == 0
}

@HiltViewModel
class CategoryPageViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val trackers: Trackers,
    private val db: LedgaDatabase,
    private val line: SelectedLine,
    private val live: LiveClock,
    private val edits: TransactionEdits,
    private val links: ActivityLinks,
) : ViewModel() {
    /** `CategoryRoute.categoryKey`: navigation stores a route's arguments under their property names. */
    val categoryKey: String = checkNotNull(handle.get<String>("categoryKey")) { "CategoryRoute needs a categoryKey" }

    /** `CategoryRoute.month` ("2026-09"), the month Spending showed (D3); let go once the person picks a bar or a range. */
    private val focus = MutableStateFlow(handle.get<String>("month")?.let(YearMonth::parse))

    /** A month older than the last twelve opens on All, where it can be seen. */
    private val range = MutableStateFlow(
        focus.value?.takeIf { it < YearMonth.from(Periods.dateOf(live.now())).minusMonths(11) }?.let { DetailRange.ALL } ?: DetailRange.TWELVE_MONTHS,
    )

    /** The bar the person tapped; null selects [focus]'s, else the running one. */
    private val chosen = MutableStateFlow<Int?>(null)

    /** "+ Add rule" (R48): the count for exactly the text typed, and a save that refuses text it hasn't counted. */
    private val draft = RuleDraft(viewModelScope, edits, categoryKey)
    val preview: StateFlow<ShownPreview?> = draft.preview

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<CategoryPageUi> = combine(live.today, line.choice) { today, choice -> today to choice }
        .distinctUntilChanged()
        .flatMapLatest { (today, choice) ->
            val now = live.now()
            combine(
                trackers.category(categoryKey, choice.lineId, now),
                db.rulesDao().observeAll(),
                db.transactionsDao().observeCountInCategory(categoryKey),
                range,
                combine(chosen, focus) { pick, month -> pick to month },
            ) { d, rules, count, r, (pick, month) -> build(d, rules, count, today, now, choice, r, pick, month) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryPageUi())

    fun setRange(r: DetailRange) {
        range.value = r
        chosen.value = null
        focus.value = null
    }

    fun select(index: Int) {
        chosen.value = index
        focus.value = null
    }

    /** R47: the chip on the page changes the one choice every summary follows. */
    fun selectLine(id: Long?) {
        viewModelScope.launch { line.select(id) }
    }

    /** R89: tracking is a setting of the page; it never closes it. */
    fun setTracked(tracked: Boolean) {
        viewModelScope.launch { edits.setTracked(categoryKey, tracked) }
    }

    /** R51: [onResult] is false for a blank name or one another category in the group has. */
    fun rename(name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(edits.renameCategory(categoryKey, name)) }
    }

    fun setIcon(icon: String) {
        viewModelScope.launch { edits.setCategoryIcon(categoryKey, icon) }
    }

    fun setColor(swatch: CategoryLooks.Swatch) {
        viewModelScope.launch { edits.setCategoryColor(categoryKey, swatch) }
    }

    fun resetLooks() {
        viewModelScope.launch { edits.resetCategoryLooks(categoryKey) }
    }

    fun setArchived(archived: Boolean) {
        viewModelScope.launch { edits.setArchived(categoryKey, archived) }
    }

    fun setRuleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { edits.setRuleEnabled(id, enabled) }
    }

    /** R49/R73: deletes a rule of the person's own; [onDeleted] gets what Undo needs. */
    fun deleteRule(id: Long, onDeleted: (RemovedRule) -> Unit) {
        viewModelScope.launch { edits.removeRule(id)?.let(onDeleted) }
    }

    fun restoreRule(removed: RemovedRule) {
        viewModelScope.launch { edits.restoreRule(removed) }
    }

    fun previewRule(name: String, account: String) = draft.count(name, account)

    fun clearPreview() = draft.clear()

    fun addRule(name: String, account: String, onDone: () -> Unit) = draft.save(name, account, onDone)

    /** The snackbar's Undo after Hide (spec §10.4). */
    fun undoHide(code: String): Job = viewModelScope.launch { edits.setHidden(code, false) }

    /** R61: "See all" opens Transactions narrowed to this category on the chosen line. */
    fun openAll() = links.open(ActivityLink.Transactions(TransactionFilter(categoryKeys = setOf(categoryKey), lineId = ui.value.line.lineId)))

    /** D4: a top place opens its payments in this category, on the chosen line. */
    fun openPlace(counterpartyKey: String) = links.open(
        ActivityLink.Transactions(TransactionFilter(categoryKeys = setOf(categoryKey), counterpartyKey = counterpartyKey, lineId = ui.value.line.lineId)),
    )

    private fun build(
        d: CategoryDetail?,
        rules: List<RuleRow>,
        count: Int,
        today: LocalDate,
        now: Instant,
        choice: LineChoice,
        r: DetailRange,
        pick: Int?,
        month: YearMonth?,
    ): CategoryPageUi {
        if (d == null) return CategoryPageUi(loaded = true, missing = true, line = choice, today = today)
        val s = d.summary
        val buckets = when (r) {
            DetailRange.SIX_MONTHS -> s.months.takeLast(6)
            DetailRange.TWELVE_MONTHS -> s.months.takeLast(12)
            DetailRange.ALL -> Bucketing.allTime(d.allMonths)
        }
        val running = buckets.lastIndex
        val years = buckets.firstOrNull()?.period?.type == PeriodType.YEAR
        val focused = month?.let { m -> m.atDay(1).atStartOfDay(Nairobi.ZONE).toInstant().let { at -> buckets.indexOfFirst { at in it.period } } }?.takeIf { it >= 0 }
        return CategoryPageUi(
            loaded = true,
            category = s.category,
            measure = d.measure,
            line = choice,
            range = r,
            buckets = buckets,
            bars = buckets.mapIndexed { i, b -> bar(b, i == running) },
            selectedIndex = (pick ?: focused ?: running).takeIf { it in buckets.indices },
            averageCents = if (years) Bucketing.averagePerYear(d.allMonths, now)?.cents else s.averageCents,
            thisMonthCents = s.thisMonth.total.cents,
            lastMonthCents = s.lastMonth.total.cents,
            monthlyAverageCents = s.averageCents,
            year = today.year,
            yearSoFarCents = d.yearSoFarCents,
            topPlaces = d.topPlaces.map { TopPlaceUi(it.counterpartyKey, it.name?.let(NameFormat::display) ?: it.phone ?: "Unknown", it.totalCents, it.count) },
            payments = d.payments,
            paymentCount = count,
            rules = CategoryRules.forCategory(rules, s.category.key).map { RuleUi(it.id, TrackerText.ruleLabel(it), it.origin == RuleOrigin.SYSTEM, it.enabled) },
            today = today,
        )
    }

    private fun bar(b: Bucket, running: Boolean): Bar {
        val p = b.period
        val label = if (p.type == PeriodType.YEAR) p.start.year.toString() else p.start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH)
        val (title, caption) = TrackerText.tooltip(b, running)
        return Bar(label, listOf(b.total.cents), "$title, $caption", inProgress = running)
    }
}
