package com.ledga.app.ui.trackers

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.trackers.Trackers
import com.ledga.app.testing.FakePrefsStore
import com.ledga.app.testing.MutableClock
import com.ledga.app.testing.TestDb
import com.ledga.app.testing.TestViewModels
import com.ledga.app.testing.selectedLine
import com.ledga.app.time.LiveClock
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.core.model.Categories
import java.time.Instant
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Trackers tab with its real ViewModel over a test database. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class TrackersRouteTest {
    @get:Rule val compose = createComposeRule()
    private val db = TestDb.inMemory()
    private val clock = MutableClock(Instant.parse("2026-10-06T06:00:00Z"))
    private val edits = TransactionEdits(db, Deriver(db, clock), clock)
    private val stopped = StoppedTrackers(edits)
    private val vms = TestViewModels()

    @After fun close() {
        vms.stopAllOnMainLooper()
        db.close()
    }

    private fun tracked() = runBlocking { db.categoriesDao().all().first { it.key == Categories.ELECTRICITY }.tracked }

    /** Steps frames by hand: the test clock would otherwise run straight through the snackbar's timeout. */
    private fun frames(n: Int = 5) = repeat(n) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    @Test
    fun `a tracker stopped from its detail says so here, and Undo tracks it again (R51)`() {
        runBlocking { edits.setTracked(Categories.ELECTRICITY, false) }
        stopped.post(StoppedTracking(Categories.ELECTRICITY, "Electricity"))
        val vm = vms.track(
            TrackersViewModel(Trackers(db, LedgerQueries(db)), db, selectedLine(db, FakePrefsStore()), LiveClock(clock) { awaitCancellation() }, edits, stopped),
        )
        compose.mainClock.autoAdvance = false
        compose.setContent { LedgaTheme(Appearance.LIGHT, reducedMotion = true) { TrackersTab(onOpen = {}, vm = vm) } }
        frames()
        compose.onNodeWithText("Stopped tracking Electricity").assertIsDisplayed()
        assertNull(stopped.latest.value, "shown once")
        compose.onNodeWithText("Undo").performClick()
        repeat(50) {
            if (!tracked()) {
                frames(1)
                Thread.sleep(20)
            }
        }
        assertTrue(tracked(), "Undo tracks it again")
    }
}
