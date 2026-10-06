package com.ledga.app.ui.home

import androidx.paging.testing.asSnapshot
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.TestViewModels
import com.ledga.app.testing.fulizaTxRow
import com.ledga.app.testing.selectedLine
import com.ledga.app.testing.twoLines
import com.ledga.app.testing.txRow
import com.ledga.app.time.LiveClock
import com.ledga.core.model.Categories
import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FulizaSheetViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-06T06:00:00Z"))
    private val vms = TestViewModels()

    @After fun close() {
        vms.stopAll()
        db.close()
    }

    @Test
    fun `the sheet follows the chosen line - its status and only its Fuliza payments`() = runTest {
        twoLines(db)
        db.transactionsDao().upsertAll(
            listOf(
                fulizaTxRow().copy(lineId = 1), // owes Ksh 6,418.36
                txRow(
                    code = "TJK4AB12RA", kind = TxKind.FULIZA_REPAY_AUTO, name = null, account = null, categoryKey = Categories.FULIZA,
                    amountCents = 20_000, lineId = 2, fulizaOutstandingCents = 30_000, at = Instant.parse("2026-10-04T07:00:00Z"),
                ),
                txRow(code = "TJK4AB12RB", lineId = 2), // not Fuliza
            ),
        )
        val line = selectedLine(db, FakePrefsStore())
        val vm = vms.track(FulizaSheetViewModel(LedgerQueries(db), db, line, LiveClock(clock) { awaitCancellation() }))
        assertEquals(Money(671_836), vm.ui.first { it.loaded && it.status != null }.status?.outstanding)
        line.select(2)
        val business = vm.ui.first { it.lineLabel == "Business ··78" }
        assertEquals(Money(30_000), business.status?.outstanding)
        assertEquals(listOf("TJK4AB12RA"), vm.items.asSnapshot().map { it.code })
    }
}
