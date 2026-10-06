package com.ledga.app.ui.tx

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import com.ledga.app.data.room.LineRow
import com.ledga.app.testing.Sms
import com.ledga.app.testing.txRow
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.core.model.Categories
import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind
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
class TransactionSheetBehaviourTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.parse("2026-10-06")
    private val created = Instant.parse("2026-01-01T00:00:00Z")
    private val personal = LineRow(1, 1, "0712000023", "Personal", "#0E9F6E", true, created)
    private val business = LineRow(2, 2, "0733000087", "Business", "#1E7FD8", false, created)

    private fun show(state: TxSheetState, actions: TxSheetActions = TxSheetActions()) = compose.setContent {
        LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
            Column(Modifier.verticalScroll(rememberScrollState())) { TransactionSheetContent(state, actions) }
        }
    }

    @Test
    fun `copying the code confirms it on the button`() {
        val copied = mutableListOf<String>()
        show(TxSheetState(txRow(), today = today), TxSheetActions(onCopyCode = { copied += it }))
        // A paused clock: the tick reverts after 2 s, and an advancing test clock would run that delay at once.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Copy code").performClick()
        // Idle sends the click's state write to the recomposer (a paused clock doesn't); one frame then shows the tick.
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(listOf("TJK4AB12FA"), copied)
        compose.onNodeWithContentDescription("Code copied").assertExists()
    }

    @Test
    fun `Hide hands the code back for Undo`() {
        var hidden = 0
        show(TxSheetState(txRow(), today = today), TxSheetActions(onHide = { hidden++ }))
        compose.onNodeWithText("Hide").performScrollTo().performClick()
        assertEquals(1, hidden)
    }

    @Test
    fun `a hidden payment offers Unhide`() {
        var unhidden = 0
        show(TxSheetState(txRow(hidden = true), today = today), TxSheetActions(onUnhide = { unhidden++ }))
        compose.onNodeWithText("Unhide").performScrollTo().performClick()
        assertEquals(1, unhidden)
    }

    @Test
    fun `only kinds that can be own-account show the switch`() {
        val repay = txRow(kind = TxKind.FULIZA_REPAY_AUTO, flow = FlowKind.LOAN_REPAY, name = null, account = null, categoryKey = Categories.FULIZA)
        show(TxSheetState(repay, today = today))
        compose.onNodeWithText("My own account").assertDoesNotExist()
    }

    @Test
    fun `the note editor saves what was typed`() {
        val saved = mutableListOf<String>()
        show(TxSheetState(txRow(), today = today), TxSheetActions(onSaveNote = { saved += it }))
        compose.onNodeWithText("Add a note").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("School fees")
        compose.onNodeWithText("Save note").performScrollTo().performClick()
        assertEquals(listOf("School fees"), saved)
    }

    @Test
    fun `with two lines the Line fact moves the payment`() {
        val moved = mutableListOf<Long>()
        show(TxSheetState(txRow(lineId = 1), lines = listOf(personal, business), today = today), TxSheetActions(onMoveLine = { moved += it }))
        compose.onNodeWithText("Personal ··23").performClick()
        compose.onNodeWithText("Business ··87").performClick()
        assertEquals(listOf(2L), moved)
    }

    @Test
    fun `the original SMS is set in Ledga's own font, not the phone's`() {
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TransactionSheetContent(TxSheetState(txRow(), sms = listOf(Sms.KPLC), today = today), TxSheetActions(), startSmsOpen = true)
                }
            }
        }
        val node = compose.onNodeWithText("TJK4AB12FA Confirmed", substring = true, useUnmergedTree = true).fetchSemanticsNode()
        val layout = mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.single()
        // A system family (Monospace, Default) follows the phone's font setting, which on Samsung can be a script font.
        assertEquals(LedgaType.caption.fontFamily, layout.layoutInput.style.fontFamily)
    }
}
