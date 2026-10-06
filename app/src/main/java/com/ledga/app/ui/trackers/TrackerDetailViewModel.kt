package com.ledga.app.ui.trackers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.derive.TransactionFilter
import com.ledga.app.data.edit.RemovedRule
import com.ledga.app.data.edit.RulePreview
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.lines.LineChoice
import com.ledga.app.data.lines.SelectedLine
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.TxRow
import com.ledga.app.data.trackers.TrackerDetail
import com.ledga.app.data.trackers.Trackers
import com.ledga.app.time.LiveClock
import com.ledga.app.ui.activity.ActivityLink
import com.ledga.app.ui.activity.ActivityLinks
import com.ledga.app.ui.design.charts.Bar
import com.ledga.core.chart.Bucket
import com.ledga.core.chart.Bucketing
import com.ledga.core.time.PeriodType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** Tracker detail's chart ranges (spec §10.4, R54). */
enum class DetailRange(val label: String) {
    SIX_MONTHS("6M"),
    TWELVE_MONTHS("12M"),
    ALL("All"),
}

/** One "Matched by" chip (R49). */
data class RuleChipUi(val id: Long, val label: String)

/** Tracker detail (spec §10.4). */
data class TrackerDetailUi(
    val loaded: Boolean = false,
    /** The category no longer exists (or never did): the screen offers Back. */
    val missing: Boolean = false,
    val category: CategoryRow? = null,
    val line: LineChoice = LineChoice(),
    val range: DetailRange = DetailRange.TWELVE_MONTHS,
    val buckets: List<Bucket> = emptyList(),
    val bars: List<Bar> = emptyList(),
    val selectedIndex: Int? = null,
    /** The dashed line: per month, or per completed year once All shows years (R54). */
    val averageCents: Long? = null,
    val thisMonthCents: Long = 0,
    val lastMonthCents: Long = 0,
    /** "Avg / month" (R52): the same number as the tile and the row. */
    val monthlyAverageCents: Long? = null,
    val year: Int = 0,
    val yearSoFarCents: Long = 0,
    val rules: List<RuleChipUi> = emptyList(),
    val editing: Boolean = false,
    val payments: List<TxRow> = emptyList(),
    val today: LocalDate? = null,
)

/** A rule count (R48) and the text it was worked out for; null [preview] for a name too short to use. */
data class ShownPreview(val name: String, val account: String, val preview: RulePreview?) {
    fun isFor(name: String, account: String) = this.name == name && this.account == account
}

@HiltViewModel
class TrackerDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val trackers: Trackers,
    private val line: SelectedLine,
    private val live: LiveClock,
    private val edits: TransactionEdits,
    private val links: ActivityLinks,
    private val stopped: StoppedTrackers,
) : ViewModel() {
    /** `TrackerRoute.categoryKey`: navigation stores a route's arguments under their property names. */
    val categoryKey: String = checkNotNull(handle.get<String>("categoryKey")) { "TrackerRoute needs a categoryKey" }

    private val range = MutableStateFlow(DetailRange.TWELVE_MONTHS)

    /** The bar the person tapped; null selects the running one. */
    private val chosen = MutableStateFlow<Int?>(null)
    private val editing = MutableStateFlow(false)
    private val ruleText = MutableStateFlow<Pair<String, String>?>(null)

    /**
     * What "+ Add rule" would do (R48), with the text it was counted for. A newer text cancels an older count, so a
     * slow count for a shorter name can never land last.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val preview: StateFlow<ShownPreview?> = ruleText
        .mapLatest { text -> text?.let { (name, account) -> ShownPreview(name, account, edits.rulePreview(categoryKey, name, account.ifBlank { null })) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<TrackerDetailUi> = combine(live.today, line.choice) { today, choice -> today to choice }
        .distinctUntilChanged()
        .flatMapLatest { (today, choice) ->
            val now = live.now()
            combine(trackers.detail(categoryKey, choice.lineId, now), range, chosen, editing) { d, r, pick, edit ->
                build(d, today, now, choice, r, pick, edit)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackerDetailUi())

    fun setRange(r: DetailRange) {
        range.value = r
        chosen.value = null
    }

    fun select(index: Int) {
        chosen.value = index
    }

    fun toggleEditing() = editing.update { !it }

    /** R47: the chip on Tracker detail changes the one choice every summary follows. */
    fun selectLine(id: Long?) {
        viewModelScope.launch { line.select(id) }
    }

    /** R49: removes a rule; [onRemoved] gets what Undo needs. Removing the last one ends edit mode. */
    fun removeRule(id: Long, onRemoved: (RemovedRule) -> Unit) {
        val last = ui.value.rules.none { it.id != id }
        viewModelScope.launch {
            val removed = edits.removeRule(id) ?: return@launch
            if (last) editing.value = false
            onRemoved(removed)
        }
    }

    fun restoreRule(removed: RemovedRule) {
        viewModelScope.launch { edits.restoreRule(removed) }
    }

    /** R48: recounts what "+ Add rule" would do with this text. */
    fun previewRule(name: String, account: String) {
        ruleText.value = name to account
    }

    fun clearPreview() {
        ruleText.value = null
    }

    /**
     * R48: saves only what the person saw counted. Text typed since, or still being counted, does nothing: the rule
     * clears hand-filed choices, and removing it later doesn't bring them back.
     */
    fun addRule(name: String, account: String, onDone: () -> Unit) {
        if (preview.value?.isFor(name, account) != true || preview.value?.preview == null) return
        viewModelScope.launch { if (edits.addRule(categoryKey, name, account.ifBlank { null })) onDone() }
    }

    /** R51: [onResult] is false for a blank name or one another category in the group has. */
    fun rename(name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(edits.renameCategory(categoryKey, name)) }
    }

    /** R51: stops tracking; the screen closes in [onDone], and the screen below offers Undo ([StoppedTrackers]). */
    fun stopTracking(onDone: () -> Unit) {
        val name = ui.value.category?.name
        viewModelScope.launch {
            edits.setTracked(categoryKey, false)
            if (name != null) stopped.post(StoppedTracking(categoryKey, name))
            onDone()
        }
    }

    /** The snackbar's Undo after Hide (spec §10.4). */
    fun undoHide(code: String): Job = viewModelScope.launch { edits.setHidden(code, false) }

    /** R61: "See all" opens Transactions filtered to this category on the chosen line. */
    fun openAll() = links.open(ActivityLink.Transactions(TransactionFilter(categoryKeys = setOf(categoryKey), lineId = ui.value.line.lineId)))

    private fun build(d: TrackerDetail?, today: LocalDate, now: Instant, choice: LineChoice, r: DetailRange, pick: Int?, edit: Boolean): TrackerDetailUi {
        if (d == null) return TrackerDetailUi(loaded = true, missing = true, line = choice, today = today)
        val s = d.summary
        val buckets = when (r) {
            DetailRange.SIX_MONTHS -> s.months.takeLast(6)
            DetailRange.TWELVE_MONTHS -> s.months.takeLast(12)
            DetailRange.ALL -> Bucketing.allTime(d.allMonths)
        }
        val running = buckets.lastIndex
        val years = buckets.firstOrNull()?.period?.type == PeriodType.YEAR
        return TrackerDetailUi(
            loaded = true,
            category = s.category,
            line = choice,
            range = r,
            buckets = buckets,
            bars = buckets.mapIndexed { i, b -> bar(b, i == running) },
            selectedIndex = (pick ?: running).takeIf { it in buckets.indices },
            averageCents = if (years) Bucketing.averageOfCompleted(buckets, now)?.cents else s.averageCents,
            thisMonthCents = s.thisMonth.total.cents,
            lastMonthCents = s.lastMonth.total.cents,
            monthlyAverageCents = s.averageCents,
            year = today.year,
            yearSoFarCents = d.yearSoFarCents,
            rules = d.rules.map { RuleChipUi(it.id, TrackerText.ruleLabel(it)) },
            editing = edit && d.rules.isNotEmpty(),
            payments = d.payments,
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
