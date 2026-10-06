package com.ledga.app.ui.trackers

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.trackers.TrackerSummary
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.core.chart.Bucket
import com.ledga.core.model.Categories
import com.ledga.core.money.Money
import com.ledga.core.time.PeriodType
import com.ledga.core.time.Periods
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
class TrackersBehaviourTest {
    @get:Rule val compose = createComposeRule()
    private val categories = Categories.SEED.map { CategoryRow(it.key, it.name, it.group, it.icon3d, null, null, it.tracked, it.sortOrder, CategoryOrigin.SYSTEM, false) }
    private val electricity = TrackerSummary(
        categories.first { it.key == Categories.ELECTRICITY },
        Periods.lastN(PeriodType.MONTH, Instant.parse("2026-10-06T06:00:00Z"), 13).map { Bucket(it, Money(95_000), 1) },
        95_000, null, null,
    )
    private val school = categories.first { it.key == Categories.SCHOOL }

    @Test
    fun `a row opens its tracker, and the plus button and the last row offer a category to track`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                TrackersContent(
                    TrackersUi(loaded = true, trackers = listOf(electricity), untracked = listOf(school), today = LocalDate.parse("2026-10-06")),
                    TrackersActions(onOpen = { calls += "open $it" }, onTrackNew = { calls += "track" }),
                )
            }
        }
        compose.onNodeWithContentDescription("Electricity, Ksh 950 this month", substring = true).performClick() // the legend says "Electricity" too
        compose.onNodeWithContentDescription("Track a category").performClick()
        compose.onNodeWithText("+ Track another category").performClick()
        assertEquals(listOf("open electricity", "track", "track"), calls)
    }

    @Test
    fun `picking a category tracks it`() {
        val picked = mutableListOf<String>()
        compose.setContent { LedgaTheme(Appearance.LIGHT, reducedMotion = true) { TrackCategoryContent(listOf(school), onTrack = { picked += it }) } }
        compose.onNodeWithText("School").performClick()
        assertEquals(listOf(Categories.SCHOOL), picked)
    }
}
