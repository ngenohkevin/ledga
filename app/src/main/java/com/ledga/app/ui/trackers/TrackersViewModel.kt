package com.ledga.app.ui.trackers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.lines.LineChoice
import com.ledga.app.data.lines.SelectedLine
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.trackers.TrackerSummary
import com.ledga.app.data.trackers.Trackers
import com.ledga.app.time.LiveClock
import com.ledga.app.ui.design.charts.Bar
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.core.chart.Bucket
import com.ledga.core.chart.Bucketing
import com.ledga.core.model.CategoryGroup
import com.ledga.core.money.Money
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** The Trackers chart's ranges (R53). */
enum class TrackerRange(val label: String) {
    SIX_MONTHS("6M"),
    TWELVE_MONTHS("12M"),
    YEAR("Year"),
}

/** The Trackers tab (spec §10.4, mockup `trackers`). */
data class TrackersUi(
    val loaded: Boolean = false,
    val range: TrackerRange = TrackerRange.SIX_MONTHS,
    val line: LineChoice = LineChoice(),
    val trackers: List<TrackerSummary> = emptyList(),
    /** The stacked chart: a bar per month of the range, a segment per tracker in list order (bottom first). */
    val bars: List<Bar> = emptyList(),
    /** The average per completed month in the range (R53); null while the range has none. */
    val averageCents: Long? = null,
    val thisMonthCents: Long = 0,
    /** "All trackers · last 6 months", "All trackers · 2026". */
    val heading: String = "",
    /** What "Track a category" offers (R50): untracked spending categories, in picker order. */
    val untracked: List<CategoryRow> = emptyList(),
    val today: LocalDate? = null,
)

@HiltViewModel
class TrackersViewModel @Inject constructor(
    private val trackers: Trackers,
    private val db: LedgaDatabase,
    private val line: SelectedLine,
    private val live: LiveClock,
    private val edits: TransactionEdits,
) : ViewModel() {
    private val range = MutableStateFlow(TrackerRange.SIX_MONTHS)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<TrackersUi> = combine(live.today, line.choice) { today, choice -> today to choice }
        .distinctUntilChanged()
        .flatMapLatest { (today, choice) ->
            val now = live.now()
            combine(trackers.summaries(choice.lineId, now), range, db.categoriesDao().observeAll()) { list, r, categories ->
                build(today, now, choice, list, r, categories)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackersUi())

    fun setRange(r: TrackerRange) {
        range.value = r
    }

    /** R47: the chip on Trackers changes the one choice every summary follows. */
    fun selectLine(id: Long?) {
        viewModelScope.launch { line.select(id) }
    }

    /** R50: tracks a category; its tracker appears at once. */
    fun track(key: String) {
        viewModelScope.launch { edits.setTracked(key, true) }
    }

    private fun build(today: LocalDate, now: Instant, choice: LineChoice, list: List<TrackerSummary>, r: TrackerRange, categories: List<CategoryRow>): TrackersUi {
        val count = when (r) {
            TrackerRange.SIX_MONTHS -> 6
            TrackerRange.TWELVE_MONTHS -> 12
            TrackerRange.YEAR -> today.monthValue
        }
        val periods = Periods.lastN(PeriodType.MONTH, now, count)
        // Every summary's months are the same 13 (`Trackers.MONTHS`), oldest first: the range is their tail.
        val offset = Trackers.MONTHS - count
        val stacked = periods.mapIndexed { i, p ->
            Bucket(p, Money(list.sumOf { it.months[offset + i].total.cents }), list.sumOf { it.months[offset + i].count })
        }
        val bars = periods.mapIndexed { i, p ->
            val month = YearMonth.from(p.start)
            val running = i == periods.lastIndex
            Bar(
                label = month.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH),
                segments = list.map { it.months[offset + i].total.cents },
                speech = "${DateLabels.monthYear(month)}${if (running) " so far" else ""}: ${TrackerText.ksh(stacked[i].total.cents)}",
                inProgress = running,
            )
        }
        return TrackersUi(
            loaded = true,
            range = r,
            line = choice,
            trackers = list,
            bars = bars,
            averageCents = Bucketing.averageOfCompleted(stacked, now)?.cents,
            thisMonthCents = stacked.last().total.cents,
            heading = if (r == TrackerRange.YEAR) "All trackers · ${today.year}" else "All trackers · last $count months",
            untracked = categories.filter { !it.tracked && !it.archived && it.groupKey in SPENDING_GROUPS },
            today = today,
        )
    }

    private companion object {
        /** R50: a category outside these always spends 0 (B1), so it can't be tracked. */
        val SPENDING_GROUPS = setOf(CategoryGroup.BILLS_UTILITIES, CategoryGroup.CAR, CategoryGroup.EVERYDAY, CategoryGroup.MONEY)
    }
}
