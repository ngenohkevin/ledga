package com.ledga.app.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.startup.SmsAccess
import com.ledga.app.ui.design.components.Banner
import com.ledga.app.ui.design.components.BannerTone
import com.ledga.app.ui.design.components.EmptyState
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.StatTile
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.app.work.BackgroundWork
import com.ledga.app.work.HistoryProgress
import com.ledga.core.time.InstantRange
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant
import java.util.Locale

data class InterimHomeState(
    val smsGranted: Boolean = true,
    val count: Int = 0,
    val first: Instant? = null,
    val last: Instant? = null,
    val spentThisMonthCents: Long = 0L,
    val history: HistoryProgress? = null,
)

/**
 * R32: Home until 4c builds the real one. It shows enough to see that an import worked (how many transactions, from
 * when to when, spent this month), plus the two states 4c keeps: "Updating your history…" and SMS access missing.
 */
@Composable
fun InterimHome(state: InterimHomeState, onAllowSms: () -> Unit, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).verticalScroll(rememberScrollState())) {
        ScreenTitle("Home")
        Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            state.history?.let { Banner("Updating your history…", BannerTone.Progress, progress = it.fraction) }
            when {
                !state.smsGranted -> EmptyState(
                    "fluent_incoming_envelope",
                    "Ledga can't see your M-Pesa messages",
                    "Allow SMS access and every payment shows up here on its own.",
                    actionLabel = "Allow SMS access",
                    onAction = onAllowSms,
                )
                state.count == 0 -> if (state.history == null) {
                    EmptyState(
                        "fluent_magnifying_glass_tilted_left",
                        "No M-Pesa payments yet",
                        "New payments appear here as their messages arrive.",
                    )
                }
                else -> LedgaCard {
                    Text("Your history", style = LedgaType.cardTitle, color = c.ink)
                    Row(Modifier.padding(top = Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        StatTile("Transactions", grouped(state.count), Modifier.weight(1f))
                        StatTile("Spent this month", "${AmountFormat.CURRENCY} ${AmountFormat.plain(state.spentThisMonthCents)}", Modifier.weight(1f))
                    }
                    val first = state.first
                    val last = state.last
                    if (first != null && last != null) {
                        Text(
                            "From ${DateLabels.date(DateLabels.nairobiDate(first))} to ${DateLabels.date(DateLabels.nairobiDate(last))}",
                            Modifier.padding(top = Spacing.m),
                            style = LedgaType.caption,
                            color = c.muted,
                        )
                    }
                }
            }
            Text("The full Home screen arrives in the next build.", style = LedgaType.caption, color = c.muted)
        }
    }
}

/** "1,111". */
internal fun grouped(n: Int): String = String.format(Locale.ENGLISH, "%,d", n)

/** Task 8 makes this a @HiltViewModel (Hilt would check it against v1's graph before then). */
class InterimHomeViewModel(
    db: LedgaDatabase,
    ledger: LedgerQueries,
    private val work: BackgroundWork,
    private val sms: SmsAccess,
    clock: Clock,
) : ViewModel() {
    private val smsGranted = MutableStateFlow(sms.granted())

    /** R34: fixed when Home opens. 4b's live-period ticker moves it at midnight. */
    private val month: InstantRange = clock.instant().let { now -> Periods.liveRange(Periods.current(PeriodType.MONTH, now), now) }

    val state: StateFlow<InterimHomeState> = combine(
        smsGranted,
        db.transactionsDao().observeSpan(),
        ledger.spent(month),
        work.history,
    ) { granted, span, spent, history ->
        InterimHomeState(granted, span.count, span.firstAt, span.lastAt, spent.cents, history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InterimHomeState())

    /** On resume: the user may have granted or revoked access in Settings meanwhile. */
    fun refreshAccess() {
        smsGranted.value = sms.granted()
    }

    fun onSmsGranted() {
        refreshAccess()
        work.importInbox()
    }
}
