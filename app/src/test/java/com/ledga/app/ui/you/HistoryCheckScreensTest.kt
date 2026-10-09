package com.ledga.app.ui.you

import com.ledga.app.testing.snapScreen
import com.ledga.app.ui.app.ShellFrame
import com.ledga.app.testing.snapScreenLandscape
import com.ledga.app.testing.txRow
import java.time.Instant
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.core.model.Categories

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class HistoryCheckScreensTest {
    private val today = LocalDate.parse("2026-10-06")
    private val addsUp = HistoryCheckUi(
        loaded = true,
        checked = 6_385,
        lines = listOf(LineCheckUi("Personal ··11", 5_120, 0), LineCheckUi("Business ··78", 1_265, 0)),
        today = today,
        categories = Categories.SEED.associate { it.key to CategoryRow(it.key, it.name, it.group, it.icon3d, null, null, it.tracked, it.sortOrder, CategoryOrigin.SYSTEM, false) },
    )
    private val breaks = addsUp.copy(
        breaks = listOf(
            ChainBreakUi(txRow(code = "TJK4AB12HD", at = Instant.parse("2026-10-04T06:00:00Z"), amountCents = 50_000, balanceCents = 200_000), 250_000, 200_000),
            ChainBreakUi(txRow(code = "TJK4AB12HC", at = Instant.parse("2026-09-03T06:00:00Z"), amountCents = 50_000, balanceCents = 300_000), 350_000, 300_000),
        ),
        lines = listOf(LineCheckUi("Personal ··11", 5_120, 2), LineCheckUi("Business ··78", 1_265, 0)),
    )

    @Test
    fun addsUp() = snapScreen("history_check") { ShellFrame(null, onSelect = {}) { HistoryCheckContent(addsUp, HistoryCheckActions()) } }

    @Test
    fun breaks() = snapScreen("history_check_breaks") { ShellFrame(null, onSelect = {}) { HistoryCheckContent(breaks, HistoryCheckActions()) } }

    @Test
    fun misfiled() = snapScreen("history_check_misfiled") {
        ShellFrame(null, onSelect = {}) {
            HistoryCheckContent(breaks.copy(moves = listOf(LineMoveUi(1, "Personal ··11", List(7) { "TJK4AB15A$it" }))), HistoryCheckActions())
        }
    }

    @Test
    fun landscape() = snapScreenLandscape("history_check") { ShellFrame(null, onSelect = {}) { HistoryCheckContent(breaks, HistoryCheckActions()) } }
}
