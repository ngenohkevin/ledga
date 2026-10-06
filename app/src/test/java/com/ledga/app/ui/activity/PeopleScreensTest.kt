package com.ledga.app.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.ledga.app.data.derive.PeopleDirection
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.dao.PersonSummary
import com.ledga.app.testing.snapScreen
import com.ledga.app.testing.snapScreenLandscape
import com.ledga.app.testing.txRow
import com.ledga.app.ui.app.ShellFrame
import com.ledga.app.ui.app.Tab
import com.ledga.app.ui.design.components.SheetScaffold
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.core.model.Categories
import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

/** Spec §15.2: Activity › People and the person sheet, light/dark × 1.0/1.3, plus landscape. Synthetic people. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class PeopleScreensTest {
    private val today = LocalDate.parse("2026-10-06")
    private val complete = LoadStates(LoadState.NotLoading(false), LoadState.NotLoading(true), LoadState.NotLoading(true))
    private val categories = Categories.SEED.associate {
        it.key to CategoryRow(it.key, it.name, it.group, it.icon3d, null, null, it.tracked, it.sortOrder, CategoryOrigin.SYSTEM, false)
    }
    private val jane = PersonRowUi("JANE TESTER|0712111", "Jane Tester", "0712345111", 23, 1_111_100, Instant.parse("2026-10-02T15:00:00Z"))
    private val people = PeopleUi(
        loaded = true,
        direction = PeopleDirection.SENT,
        minCents = 500_000,
        maxCents = 1_111_100,
        today = today,
        rows = listOf(
            jane,
            PersonRowUi("JOHN SAMPLE|0722999", "John Sample", "0722000999", 6, 860_000, Instant.parse("2026-09-28T09:00:00Z")),
            PersonRowUi("A VERY LONG", "A Very Long Person Name That Keeps Going Onwards", null, 1, 123_456_789, Instant.parse("2025-12-24T09:00:00Z")),
        ),
    )

    @Test
    fun sentTo() = snapScreen("people_sent") {
        ShellFrame(Tab.ACTIVITY, onSelect = {}) {
            ActivityContent(ActivitySegment.PEOPLE, onSegment = {}, filterCount = 0, onFilters = {}) { PeoplePane(people, PeopleActions()) }
        }
    }

    @Test
    fun personSheet() = snapScreen("person_sheet") {
        val rows = listOf(
            txRow(code = "TJK4AB12PD", kind = TxKind.RECEIVE, flow = FlowKind.INCOME, amountCents = 90_000, name = "JANE TESTER", phone = "0712345111", account = null, categoryKey = Categories.RECEIVED, at = Instant.parse("2026-10-02T15:00:00Z")),
            txRow(code = "TJK4AB12PA", kind = TxKind.SEND, amountCents = 30_000, name = "JANE TESTER", phone = "0712345111", account = null, categoryKey = Categories.SENT_TO_PEOPLE, at = Instant.parse("2026-09-23T06:00:00Z")),
            txRow(code = "TJK4AB12FB", kind = TxKind.SEND, amountCents = 50_000, name = "JANE TESTER", phone = "0712345111", account = null, categoryKey = Categories.RENT, at = Instant.parse("2026-09-01T07:00:00Z")),
        )
        val items = remember { flowOf(PagingData.from(rows, complete)) }.collectAsLazyPagingItems()
        Box(Modifier.fillMaxSize().background(LedgaTheme.colors.canvas), contentAlignment = Alignment.BottomCenter) {
            SheetScaffold(title = null) {
                PersonSheetContent(PersonSheetUi(jane, PersonSummary(1_111_100, 23, 90_000, 1), categories, today), items, onOpenTx = {})
            }
        }
    }

    @Test
    fun landscape() = snapScreenLandscape("people") {
        ShellFrame(Tab.ACTIVITY, onSelect = {}) {
            ActivityContent(ActivitySegment.PEOPLE, onSegment = {}, filterCount = 0, onFilters = {}) { PeoplePane(people, PeopleActions()) }
        }
    }
}
