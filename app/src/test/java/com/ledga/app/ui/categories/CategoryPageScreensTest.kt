package com.ledga.app.ui.categories

import com.ledga.app.data.edit.CategoryLooks
import com.ledga.app.data.edit.RulePreview
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.trackers.CategoryMeasure
import com.ledga.app.testing.snapScreen
import com.ledga.app.testing.snapScreenLandscape
import com.ledga.app.testing.txRow
import com.ledga.app.ui.app.ShellFrame
import com.ledga.app.ui.design.charts.Bar
import com.ledga.app.ui.design.components.SheetScaffold
import com.ledga.app.ui.rules.AddRuleContent
import com.ledga.app.ui.rules.ShownPreview
import com.ledga.core.chart.Bucket
import com.ledga.core.model.CategoryGroup
import com.ledga.core.model.Categories
import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

/** 4e spec §3.2: a category's page (spending, money in, your own, empty), landscape, and its add-rule and colour sheets. Synthetic values. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class CategoryPageScreensTest {
    private val today = LocalDate.parse("2026-10-06")
    private val groceries = Categories.seed(Categories.GROCERIES)!!.let {
        CategoryRow(it.key, it.name, it.group, it.icon3d, null, null, false, it.sortOrder, CategoryOrigin.SYSTEM, false)
    }
    private val received = Categories.seed(Categories.RECEIVED)!!.let {
        CategoryRow(it.key, it.name, it.group, it.icon3d, null, null, false, it.sortOrder, CategoryOrigin.SYSTEM, false)
    }
    private val pets = CategoryRow("user_pets", "Pets", CategoryGroup.EVERYDAY, "fluent_dog_face", "#0277BD", "#63C3FF", false, 100, CategoryOrigin.USER, false)
    private val now = Instant.parse("2026-10-06T06:00:00Z")
    private val months = Periods.lastN(PeriodType.MONTH, now, 12).mapIndexed { i, p -> Bucket(p, Money(listOf(41_000L, 38_500, 0, 52_000, 47_250, 39_900, 44_100, 61_300, 45_800, 40_200, 48_700, 18_400)[i]), if (i == 2) 0 else 6) }
    private fun bars(ms: List<Bucket>) = ms.mapIndexed { i, b ->
        Bar(b.period.start.month.name.take(3), listOf(b.total.cents), "", inProgress = i == ms.lastIndex)
    }
    private val page = CategoryPageUi(
        loaded = true,
        category = groceries,
        buckets = months,
        bars = bars(months),
        selectedIndex = 11,
        averageCents = 45_770,
        thisMonthCents = 18_400,
        lastMonthCents = 48_700,
        monthlyAverageCents = 45_770,
        year = 2026,
        yearSoFarCents = 397_650,
        topPlaces = listOf(TopPlaceUi("a", "Corner Shop", 182_300, 24), TopPlaceUi("b", "Green Grocer", 96_450, 11), TopPlaceUi("c", "Naivas", 61_200, 5)),
        payments = listOf(
            txRow(code = "TJK4AB12WA", kind = TxKind.BUY_GOODS, name = "CORNER SHOP", account = null, amountCents = 6_400, categoryKey = Categories.GROCERIES),
            txRow(code = "TJK4AB12WB", kind = TxKind.BUY_GOODS, name = "GREEN GROCER", account = null, amountCents = 12_000, categoryKey = Categories.GROCERIES, at = Instant.parse("2026-10-03T15:40:00Z")),
        ),
        paymentCount = 52,
        rules = listOf(RuleUi(11, "Name has Naivas", builtIn = true, enabled = true), RuleUi(12, "Name has Quickmart", builtIn = true, enabled = false)),
        today = today,
    )

    @Test fun spent() = snapScreen("category_page") { ShellFrame(null, onSelect = {}) { CategoryPageContent(page, CategoryPageActions()) } }

    @Test fun received() = snapScreen("category_page_received") {
        ShellFrame(null, onSelect = {}) {
            CategoryPageContent(
                page.copy(category = received, measure = CategoryMeasure.RECEIVED, topPlaces = listOf(TopPlaceUi("j", "Jane Tester", 1_250_000, 4)), rules = emptyList()),
                CategoryPageActions(),
            )
        }
    }

    @Test fun own() = snapScreen("category_page_own") {
        ShellFrame(null, onSelect = {}) { CategoryPageContent(page.copy(category = pets, topPlaces = emptyList(), rules = listOf(RuleUi(902, "Name has Sample Vet", false, true))), CategoryPageActions()) }
    }

    @Test fun empty() = snapScreen("category_page_empty") {
        ShellFrame(null, onSelect = {}) { CategoryPageContent(page.copy(category = pets, paymentCount = 0, payments = emptyList(), topPlaces = emptyList(), rules = emptyList()), CategoryPageActions()) }
    }

    @Test fun landscape() = snapScreenLandscape("category_page") { ShellFrame(null, onSelect = {}) { CategoryPageContent(page, CategoryPageActions()) } }

    @Test
    fun addRule() = snapScreen("add_rule") {
        SheetScaffold("Add a rule") {
            AddRuleContent("Electricity", "sample power", "", ShownPreview("sample power", "", RulePreview(3, 2, 1)), onName = {}, onAccount = {}, onSave = {})
        }
    }

    @Test
    fun colours() = snapScreen("category_colours") { SheetScaffold("Colour") { ColourChoiceContent(selected = CategoryLooks.SWATCHES[8].light, onPick = {}) } }
}
