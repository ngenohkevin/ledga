package com.ledga.app.ui.tx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LineRow
import com.ledga.app.testing.Sms
import com.ledga.app.testing.fulizaTxRow
import com.ledga.app.testing.snapScreen
import com.ledga.app.testing.snapScreenLandscape
import com.ledga.app.testing.txRow
import com.ledga.app.ui.design.components.SheetScaffold
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.core.model.Categories
import com.ledga.core.model.CategoryGroup
import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

/** Spec §15.2: the transaction sheet's body, light/dark × 1.0/1.3, plus landscape (M5). Synthetic data only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class TransactionSheetScreensTest {
    private val today = LocalDate.parse("2026-10-06")
    private val created = Instant.parse("2026-01-01T00:00:00Z")
    private val electricity = CategoryRow(Categories.ELECTRICITY, "Electricity", CategoryGroup.BILLS_UTILITIES, "fluent_high_voltage", null, null, true, 0, CategoryOrigin.SYSTEM, false)
    private val sentToPeople = CategoryRow(Categories.SENT_TO_PEOPLE, "Sent to people", CategoryGroup.MONEY, "fluent_outbox_tray", null, null, false, 15, CategoryOrigin.SYSTEM, false)
    private val received = CategoryRow(Categories.RECEIVED, "Received", CategoryGroup.MONEY_IN, "fluent_inbox_tray", null, null, false, 20, CategoryOrigin.SYSTEM, false)
    private val personal = LineRow(1, 1, "0712000023", "Personal", "#0E9F6E", true, created)
    private val business = LineRow(2, 2, "0733000087", "Business", "#1E7FD8", false, created)

    /** The sheet's body as `LedgaModalSheet` shows it: Robolectric doesn't capture the modal's dialog window. */
    @Composable
    private fun Sheet(state: TxSheetState, startEditingNote: Boolean = false, startSmsOpen: Boolean = false) {
        Box(Modifier.fillMaxSize().background(LedgaTheme.colors.canvas), contentAlignment = Alignment.BottomCenter) {
            SheetScaffold(title = null) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TransactionSheetContent(state, TxSheetActions(), startEditingNote = startEditingNote, startSmsOpen = startSmsOpen)
                }
            }
        }
    }

    @Test
    fun paybill() = snapScreen("txsheet_paybill") {
        Sheet(TxSheetState(txRow(), electricity, listOf(personal, business), listOf(Sms.KPLC), today))
    }

    @Test
    fun fuliza() = snapScreen("txsheet_fuliza") {
        Sheet(
            TxSheetState(fulizaTxRow(), sentToPeople, listOf(personal), listOf(Sms.send("TJK4AB12EA", "2,500.00", "9/6/26 at 7:48 PM"), Sms.COMPANION), today),
            startSmsOpen = true,
        )
    }

    @Test
    fun noteAndLongName() = snapScreen("txsheet_note") {
        val long = txRow(
            name = "A VERY LONG MERCHANT NAME THAT KEEPS GOING SUPERMARKET LIMITED",
            account = "ACCOUNT 1234567890123456789",
            amountCents = 123_456_789,
            note = "School fees for the second term, paid early",
        )
        Sheet(TxSheetState(long, electricity, listOf(personal), listOf(Sms.KPLC), today), startEditingNote = true)
    }

    @Test
    fun ownAccountChoice() = snapScreen("txsheet_own") {
        val bank = txRow(code = "TJK4AB12FC", kind = TxKind.RECEIVE, flow = FlowKind.INCOME, amountCents = 500_000, name = "EXAMPLE BANK LIMITED", account = null, categoryKey = Categories.RECEIVED, balanceCents = 510_000)
        Sheet(TxSheetState(bank, received, listOf(personal), listOf(Sms.BANK_APP), today, PendingOwn(own = true, count = 6, name = "Example Bank Limited")))
    }

    @Test
    fun landscape() = snapScreenLandscape("txsheet") {
        Sheet(TxSheetState(txRow(), electricity, listOf(personal, business), listOf(Sms.KPLC), today))
    }
}
