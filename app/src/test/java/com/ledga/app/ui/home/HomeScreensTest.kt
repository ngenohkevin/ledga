package com.ledga.app.ui.home

import com.ledga.app.data.derive.FulizaStatus
import com.ledga.app.data.derive.HomeBalance
import com.ledga.app.data.lines.LineChoice
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.dao.PeriodSum
import com.ledga.app.data.room.dao.PeriodTotals
import com.ledga.app.data.trackers.TrackerSummary
import com.ledga.app.testing.BUSINESS
import com.ledga.app.testing.PERSONAL
import com.ledga.app.testing.fulizaTxRow
import com.ledga.app.testing.snapScreen
import com.ledga.app.testing.snapScreenLandscape
import com.ledga.app.testing.txRow
import com.ledga.app.ui.app.ShellFrame
import com.ledga.app.ui.app.Tab
import com.ledga.core.chart.Bucket
import com.ledga.core.model.Categories
import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
import java.time.Instant
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spec §15.2: Home (mockup `home`), light/dark × 1.0/1.3, plus landscape. Synthetic values only (never the mockup's). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class HomeScreensTest {
    private val now = Instant.parse("2026-10-06T06:00:00Z") // Tue 6 Oct 2026, 09:00 in Nairobi
    private val today = LocalDate.parse("2026-10-06")
    private val categories = Categories.SEED.associate {
        it.key to CategoryRow(it.key, it.name, it.group, it.icon3d, null, null, it.tracked, it.sortOrder, CategoryOrigin.SYSTEM, false)
    }
    private val months = Periods.lastN(PeriodType.MONTH, now, 13)

    private fun tracker(key: String, last6: List<Long>, average: Long?, usual: Int? = null): TrackerSummary {
        val values = List(7) { 0L } + last6
        return TrackerSummary(categories.getValue(key), months.mapIndexed { i, p -> Bucket(p, Money(values[i]), if (values[i] > 0) 1 else 0) }, average, usual, null)
    }

    private val trackers = listOf(
        tracker(Categories.ELECTRICITY, listOf(171_000, 182_000, 168_000, 190_000, 176_000, 95_000), 178_000),
        tracker(Categories.WATER, listOf(64_000, 58_000, 61_000, 66_000, 59_000, 0), 61_000, usual = 12),
        tracker(Categories.FUEL, listOf(740_000, 815_000, 690_000, 760_000, 705_000, 230_000), 742_000),
        tracker(Categories.CAR_SERVICE, listOf(0, 520_000, 0, 0, 0, 0), 87_000),
    )

    private val month = HomeSpending.card(
        PeriodType.MONTH, now, PeriodTotals(873_500, 6_300, 1_250_000), Money(970_000),
        mapOf(
            "2026-05" to PeriodSum("2026-05", 3_210_000, 20), "2026-06" to PeriodSum("2026-06", 3_475_000, 22),
            "2026-07" to PeriodSum("2026-07", 3_390_000, 21), "2026-08" to PeriodSum("2026-08", 3_820_000, 25),
            "2026-09" to PeriodSum("2026-09", 3_655_000, 30), "2026-10" to PeriodSum("2026-10", 873_500, 9),
        ),
    )

    private val recent = listOf(
        txRow(code = "TJK4AB12SA", at = Instant.parse("2026-10-06T05:40:00Z")), // KPLC Prepaid, 8:40 AM
        fulizaTxRow(), // yesterday
        txRow(
            code = "TJK4AB12SB", kind = TxKind.RECEIVE, name = "JANE TESTER", phone = "0712345111", account = null,
            categoryKey = Categories.RECEIVED, amountCents = 500_000, at = Instant.parse("2026-10-02T09:00:00Z"),
        ),
    )

    private val home = HomeUi(
        loaded = true,
        unreadAlerts = 3,
        greeting = "Good morning",
        name = "Amani",
        line = LineChoice(listOf(PERSONAL)),
        balance = HomeBalance(2_315_075, Instant.parse("2026-10-06T05:40:00Z"), 1),
        fuliza = FulizaStatus(Money.ZERO, Money(1_000_000), Money(1_000_000), null),
        spending = month,
        trackers = trackers,
        recent = recent,
        categories = categories,
        today = today,
        hasHistory = true,
    )

    @Test
    fun home() = snapScreen("home") { ShellFrame(Tab.HOME, onSelect = {}) { HomeContent(home, HomeActions()) } }

    @Test
    fun owedOnBusiness() = snapScreen("home_owed") {
        val week = HomeSpending.card(PeriodType.WEEK, now, PeriodTotals(145_000, 0, 0), Money(300_000), mapOf("2026-10-05" to PeriodSum("2026-10-05", 145_000, 2)))
        val ui = home.copy(
            name = null,
            line = LineChoice(listOf(PERSONAL, BUSINESS), selectedId = 2),
            balance = HomeBalance(96_350, Instant.parse("2026-10-05T11:15:00Z"), 2),
            fuliza = FulizaStatus(Money(641_836), Money(991_836), Money(350_000), LocalDate.parse("2026-11-02")),
            spending = week,
            notificationsNudge = true,
        )
        ShellFrame(Tab.HOME, onSelect = {}) { HomeContent(ui, HomeActions()) }
    }

    @Test
    fun twoLines() = snapScreen("home_two_lines") {
        val ui = home.copy(
            line = LineChoice(listOf(PERSONAL, BUSINESS)),
            balance = HomeBalance(2_411_425, Instant.parse("2026-10-06T05:40:00Z"), 1),
            balanceLines = listOf(
                BalanceLine("Personal ··11", 2_315_075, Instant.parse("2026-10-06T05:40:00Z")),
                BalanceLine("Business ··78", 96_350, Instant.parse("2026-09-02T20:37:00Z")),
            ),
        )
        ShellFrame(Tab.HOME, onSelect = {}) { HomeContent(ui, HomeActions()) }
    }

    @Test
    fun noSms() = snapScreen("home_no_sms") {
        ShellFrame(Tab.HOME, onSelect = {}) { HomeContent(HomeUi(loaded = true, greeting = "Good morning", smsGranted = false), HomeActions()) }
    }

    @Test
    fun landscape() = snapScreenLandscape("home") { ShellFrame(Tab.HOME, onSelect = {}) { HomeContent(home, HomeActions()) } }

    @Test
    fun updateAvailable() = snapScreen("home_update") {
        ShellFrame(Tab.HOME, onSelect = {}) { HomeContent(home.copy(update = HomeUpdate.Available("2.0.0-beta.2")), HomeActions()) }
    }

    @Test
    fun updateFailed() = snapScreen("home_update_failed") {
        ShellFrame(Tab.HOME, onSelect = {}) {
            HomeContent(home.copy(update = HomeUpdate.Failed("2.0.0-beta.2", "There isn't enough space on this phone to install the update.")), HomeActions())
        }
    }
}
