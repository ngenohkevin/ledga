package com.ledga.app.ui.you

import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.lines.PhoneAccess
import com.ledga.app.data.lines.Sim
import com.ledga.app.testing.BUSINESS
import com.ledga.app.testing.FakeSims
import com.ledga.app.testing.MainDispatcherRule
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.PERSONAL
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.TestViewModels
import com.ledga.app.testing.twoLines
import com.ledga.app.testing.txRow
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R65: the lines, their payments, renames and the phone-access offer. Synthetic lines. */
@RunWith(RobolectricTestRunner::class)
class LinesViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val db = TestDb.inMemory()
    private val sims = FakeSims()
    private val clock = MutableClock(Instant.parse("2026-10-06T06:00:00Z"))
    private val repo = LinesRepository(db.linesDao(), sims, clock)
    private var phone = true
    private val vms = TestViewModels()

    private fun vm() = vms.track(LinesViewModel(repo, db, PhoneAccess { phone }))

    @After fun close() {
        vms.stopAll()
        db.close()
    }

    @Test
    fun `each line with its payments, and the payments not on a line counted apart`() = runTest {
        twoLines(db)
        db.transactionsDao().upsertAll(
            listOf(txRow(code = "TJK4AB12NA", lineId = 1), txRow(code = "TJK4AB12NB", lineId = 1), txRow(code = "TJK4AB12NC", lineId = 2), txRow(code = "TJK4AB12ND", lineId = null)),
        )
        val ui = vm().ui.first { it.loaded && it.lines.size == 2 }
        assertEquals(listOf("Personal ··11" to 2, "Business ··78" to 1), ui.lines.map { it.label to it.payments })
        assertEquals(1, ui.unattributed)
    }

    @Test
    fun `a blank name is refused, and a long one is cut to 24`() = runTest {
        twoLines(db)
        val vm = vm()
        val refused = CompletableDeferred<Boolean>()
        vm.rename(PERSONAL.id, "  ") { refused.complete(it) }
        assertFalse(refused.await())
        val saved = CompletableDeferred<Boolean>()
        vm.rename(BUSINESS.id, "A".repeat(40)) { saved.complete(it) }
        saved.await()
        assertEquals("A".repeat(24), vm.ui.first { ui -> ui.lines.any { it.line.id == BUSINESS.id && it.line.displayName.length == 24 } }.lines.first { it.line.id == BUSINESS.id }.line.displayName)
    }

    @Test
    fun `phone access granted later reads the SIMs again`() = runTest {
        db.linesDao().insert(PERSONAL.copy(phoneNumber = null))
        phone = false
        val vm = vm()
        assertFalse(vm.ui.first { it.loaded }.phoneAccess)
        sims.add(Sim(PERSONAL.subscriptionId!!, "Personal", "0712345111"))
        phone = true
        vm.refresh()
        assertEquals("0712345111", vm.ui.first { ui -> ui.lines.singleOrNull()?.line?.phoneNumber != null }.lines.single().line.phoneNumber)
    }
}
