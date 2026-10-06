package com.ledga.app.ui.home

import com.ledga.core.time.Periods
import com.ledga.core.money.Money
import com.ledga.core.chart.Bucket
import com.ledga.app.data.trackers.TrackerSummary
import kotlin.test.assertTrue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.ledga.app.data.derive.HomeBalance
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.testing.txRow
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.core.model.Categories
import com.ledga.core.time.PeriodType
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class HomeBehaviourTest {
    @get:Rule val compose = createComposeRule()
    private val categories = Categories.SEED.associate {
        it.key to CategoryRow(it.key, it.name, it.group, it.icon3d, null, null, it.tracked, it.sortOrder, CategoryOrigin.SYSTEM, false)
    }
    private val ui = HomeUi(
        loaded = true,
        greeting = "Good morning",
        name = "Amani",
        balance = HomeBalance(2_315_075, Instant.parse("2026-10-06T05:40:00Z"), null),
        spending = SpendingCardUi(type = PeriodType.MONTH, spentCents = 873_500, bars = List(6) { 100_000L }, labels = listOf("May", "Jun", "Jul", "Aug", "Sep", "Oct"), shortLabels = listOf("M", "J", "J", "A", "S", "O")),
        recent = listOf(txRow(at = Instant.parse("2026-10-06T05:40:00Z"))),
        categories = categories,
        today = LocalDate.parse("2026-10-06"),
        hasHistory = true,
    )

    /** A node whose click carries this TalkBack label (`onClickLabel`). */
    private fun hasClickLabel(label: String) = SemanticsMatcher("click label '$label'") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    private fun show(state: HomeUi, actions: HomeActions) = compose.setContent {
        LedgaTheme(Appearance.LIGHT, reducedMotion = true) { HomeContent(state, actions) }
    }

    @Test
    fun `Home warns when v1's notes and categories couldn't be moved`() {
        show(ui.copy(legacyImportFailed = true), HomeActions())
        compose.onNodeWithText(LEGACY_IMPORT_FAILED_TEXT).assertExists()
    }

    @Test
    fun `the notifications banner offers Turn on and Not now`() {
        val calls = mutableListOf<String>()
        show(ui.copy(notificationsNudge = true), HomeActions(onTurnOnNotifications = { calls += "on" }, onNotNow = { calls += "not now" }))
        compose.onNodeWithText("Turn on").performClick()
        compose.onNodeWithText("Not now").performClick()
        assertEquals(listOf("on", "not now"), calls)
    }

    @Test
    fun `the spending card opens Spending, and its segments switch the period without opening it`() {
        val calls = mutableListOf<String>()
        show(ui, HomeActions(onPeriod = { calls += it.name }, onSpending = { calls += "spending" }))
        compose.onNodeWithText("Week").performClick()
        compose.onNodeWithText("Spent this month").performClick()
        assertEquals(listOf("WEEK", "spending"), calls)
    }

    @Test
    fun `a Recent row opens the payment, and a long press changes its category`() {
        val calls = mutableListOf<String>()
        show(ui, HomeActions(onOpenTx = { calls += "open $it" }, onPickCategory = { calls += "pick $it" }))
        compose.onNodeWithText("KPLC Prepaid").performClick()
        compose.onNodeWithText("KPLC Prepaid").performSemanticsAction(SemanticsActions.OnLongClick)
        assertEquals(listOf("open TJK4AB12FA", "pick TJK4AB12FA"), calls)
    }

    @Test
    fun `the avatar opens You and the search button searches`() {
        val calls = mutableListOf<String>()
        show(ui, HomeActions(onProfile = { calls += "you" }, onSearch = { calls += "search" }))
        compose.onNode(hasClickLabel("Open You")).performClick()
        compose.onNodeWithContentDescription("Search payments").performClick()
        assertEquals(listOf("you", "search"), calls)
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val node = compose.onNodeWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNode()
        return mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.single()
    }

    private fun showLarge(state: HomeUi) = compose.setContent {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) { HomeContent(state, HomeActions()) }
        }
    }

    @Test
    fun `at 1_3x the two-action banner never breaks a word`() {
        showLarge(ui.copy(notificationsNudge = true))
        val layout = layoutOf("Turn on notifications")
        val longestWord = layout.multiParagraph.intrinsics.minIntrinsicWidth
        assertTrue(longestWord <= layout.size.width + 0.5f, "the banner's text column (${layout.size.width} px) is narrower than its longest word ($longestWord px)")
    }

    @Test
    fun `at 1_3x the spending card's fees line and footer each stay on one line`() {
        showLarge(ui.copy(spending = ui.spending.copy(feeCents = 6_300, span = "1${Char(0x2013)}6 Oct", deltaPercent = -10, comparedWith = "same days Sep", inCents = 1_250_000, averageCents = 3_510_000)))
        for (text in listOf("incl. Ksh 63 fees", "Received", "Avg/month")) {
            assertEquals(1, layoutOf(text).lineCount, "'$text' wrapped")
        }
    }

    @Test
    fun `at 1_3x a tracker tile keeps "usually by the 12th" whole`() {
        val water = categories.getValue(Categories.WATER)
        val months = Periods.lastN(PeriodType.MONTH, Instant.parse("2026-10-06T06:00:00Z"), 13).mapIndexed { i, p ->
            Bucket(p, Money(if (i == 12) 0 else 61_000), if (i == 12) 0 else 1)
        }
        showLarge(ui.copy(trackers = listOf(TrackerSummary(water, months, 61_000, 12, null))))
        val layout = layoutOf("usually by the 12th")
        val needs = layout.multiParagraph.intrinsics.maxIntrinsicWidth
        assertTrue(needs <= layout.size.width + 0.5f, "the tile cuts its caption: it needs $needs px and has ${layout.size.width}")
    }
}
