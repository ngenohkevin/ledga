package com.ledga.app.ui.tx

import com.ledga.app.data.room.LineRow
import com.ledga.app.testing.fulizaTxRow
import com.ledga.app.testing.txRow
import com.ledga.app.ui.design.components.ChipTone
import com.ledga.app.ui.design.components.Leading
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.core.model.Categories
import com.ledga.core.model.TxKind
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals

/** What Ledga says about one transaction (spec §10.4, §10.5). Synthetic rows only. */
class TxTextTest {
    private val today = LocalDate.parse("2026-10-06")
    private val personal = LineRow(
        id = 1, subscriptionId = 1, phoneNumber = "0712000023", displayName = "Personal", color = "#0E9F6E",
        isPrimary = true, createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
    private val receipt = txRow(kind = TxKind.RECEIVE, name = "JANE TESTER", phone = "0712***111", account = null, categoryKey = Categories.RECEIVED)

    @Test
    fun `names read as people write them, and a payment without a name is titled by its kind`() {
        assertEquals("KPLC Prepaid", TxText.title(txRow()))
        assertEquals("Airtime", TxText.title(txRow(kind = TxKind.AIRTIME_SELF, name = null, account = null)))
        assertEquals("Fuliza repayment", TxText.title(txRow(kind = TxKind.FULIZA_REPAY_AUTO, name = null, account = null)))
        assertEquals("Fuliza payment", TxText.title(txRow(kind = TxKind.FULIZA_ONLY, name = null, account = null)))
    }

    @Test
    fun `a row's subtitle is the category, or the Fuliza note`() {
        assertEquals("Electricity", TxText.subtitle(txRow(), "Electricity"))
        assertEquals("Fuliza Ksh 463", TxText.subtitle(fulizaTxRow(), "Sent to people"))
    }

    @Test
    fun `people get initials while their category is the kind's own, everything else its icon`() {
        assertEquals(Leading.Avatar("Jane Tester", inflow = false), TxText.leading(fulizaTxRow(), "fluent_outbox_tray"))
        assertEquals(Leading.Icon("fluent_house"), TxText.leading(fulizaTxRow().copy(categoryKey = Categories.RENT), "fluent_house"))
        assertEquals(Leading.Avatar("Jane Tester", inflow = true), TxText.leading(receipt, "fluent_inbox_tray"))
        assertEquals(Leading.Icon("fluent_high_voltage"), TxText.leading(txRow(), "fluent_high_voltage"))
    }

    @Test
    fun `chips say what the payment was, what it cost and anything unusual`() {
        assertEquals(listOf(TxChip("Paybill · Acc 37100000001"), TxChip("Fee Ksh 0.00")), TxText.chips(txRow()))
        assertEquals(
            listOf(TxChip("Sent · 0712345111"), TxChip("Fuliza covered Ksh 463.00", ChipTone.Danger), TxChip("Fee Ksh 7.00")),
            TxText.chips(fulizaTxRow()),
            "the fee chip is the transaction cost; Fuliza's access fee is a fact of its own",
        )
        assertEquals(listOf(TxChip("Received · 0712***111")), TxText.chips(receipt), "money in shows no fee")
        val orphan = txRow(kind = TxKind.FULIZA_ONLY, name = null, account = null, approx = true, hidden = true)
        assertEquals(
            listOf(
                TxChip("Fee Ksh 0.00"),
                TxChip("Payment details missing", ChipTone.Warning),
                TxChip("Time approximate", ChipTone.Warning),
                TxChip("Hidden"),
            ),
            TxText.chips(orphan),
        )
    }

    @Test
    fun `facts list the code, date, Fuliza's part, balance and line`() {
        assertEquals(
            listOf(
                Fact("Code", "TJK4AB12EA", copyable = true),
                Fact("Date", "Mon 5 Oct 2026, 2:15 PM"),
                Fact("From your balance", "Ksh 2,037.00"),
                Fact("Fuliza access fee", "Ksh 4.63", FactTone.Danger),
                Fact("Fuliza owed after", "Ksh 6,418.36 · due 2 Nov"),
                Fact("Balance after", "Ksh 0.00"),
                Fact("Line", "Personal ··23", isLine = true),
            ),
            TxText.facts(fulizaTxRow(), personal),
        )
        assertEquals(Fact("Line", "Not set", isLine = true), TxText.facts(txRow(), null).last())
        assertEquals(Fact("Account", "37100000001"), TxText.facts(txRow(), null).single { it.label == "Account" })
    }

    @Test
    fun `the shared text is the payment itself - never the balance, Fuliza, the line or a note`() {
        assertEquals(
            listOf(
                "KPLC Prepaid",
                "${AmountFormat.MINUS}Ksh 1,000.00 · Electricity",
                "Mon 5 Oct 2026, 2:15 PM",
                "M-Pesa code TJK4AB12FA",
                "Account 37100000001",
                "Shared from Ledga",
            ).joinToString("\n"),
            TxText.shareText(txRow(note = "school fees"), "Electricity"),
        )
        val fuliza = TxText.shareText(fulizaTxRow(), "Sent to people")
        assertEquals("Fees Ksh 11.63", fuliza.lines().single { it.startsWith("Fees") })
        assertEquals(listOf<String>(), fuliza.lines().filter { it.contains("Fuliza") || it.contains("Balance") || it.startsWith("Line") })
    }

    @Test
    fun `a line is its name and the last two digits of its number, and a tracked category has a star`() {
        assertEquals("Personal ··23", TxText.lineLabel(personal))
        assertEquals("Business", TxText.lineLabel(personal.copy(displayName = "Business", phoneNumber = null)))
        assertEquals("Electricity ${Char(0x2605)}", TxText.categoryLabel("Electricity", tracked = true))
        assertEquals("Water", TxText.categoryLabel("Water", tracked = false))
    }

    @Test
    fun `TalkBack hears the flow, the name and when`() {
        assertEquals("Ksh 1,000 spent at KPLC Prepaid, yesterday 2:15 PM", TxText.speech(txRow(), today))
        assertEquals("Ksh 1,000 received from Jane Tester, yesterday 2:15 PM", TxText.speech(receipt, today))
    }

    @Test
    fun `money sent to a person is sent to them, not spent at them`() {
        val sent = txRow(kind = TxKind.SEND, name = "JANE TESTER", phone = "0712345111", account = null, categoryKey = Categories.SENT_TO_PEOPLE)
        assertEquals("Ksh 1,000 sent to Jane Tester, yesterday 2:15 PM", TxText.speech(sent, today))
        assertEquals("Ksh 1,000 spent at KPLC Prepaid, yesterday 2:15 PM", TxText.speech(txRow(), today), "a paybill is still spent at")
    }
}
