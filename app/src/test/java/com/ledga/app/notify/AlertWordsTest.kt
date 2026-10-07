package com.ledga.app.notify

import com.ledga.app.data.room.dao.BiggestPayment
import com.ledga.app.data.room.dao.SpentCount
import com.ledga.app.testing.fulizaTxRow
import com.ledga.app.testing.txRow
import com.ledga.core.model.TxKind
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Test

/** R111: what each alert says. Synthetic payments. */
class AlertWordsTest {
    @Test
    fun `a large payment names who was paid, or what it was, and its category`() {
        val kplc = AlertWords.large(txRow(), "Electricity")
        assertEquals("large:TJK4AB12FA", kplc.key)
        assertEquals("Ksh 1,000 to KPLC Prepaid", kplc.title)
        assertEquals("Large payment · Electricity", kplc.body)
        assertEquals("TJK4AB12FA", kplc.targetCode)
        assertEquals(NotificationTap.Payment("TJK4AB12FA"), kplc.tap)
        assertEquals("Large payment", AlertWords.large(txRow(), null).body)
        assertEquals("Ksh 5,000 withdrawn", AlertWords.large(txRow(kind = TxKind.WITHDRAW_AGENT, amountCents = 500_000, name = "SAMPLE AGENT"), null).title)
        assertEquals("Ksh 500 of airtime", AlertWords.large(txRow(kind = TxKind.AIRTIME_SELF, amountCents = 50_000, name = null), null).title)
        assertEquals("Ksh 2,500.50 paid", AlertWords.large(txRow(kind = TxKind.BUY_GOODS, amountCents = 250_050, name = null), null).title)
    }

    @Test
    fun `a Fuliza draw says what it covered, of which payment, and what is owed`() {
        val a = AlertWords.fulizaDraw(fulizaTxRow())
        assertEquals("fuliza-draw:TJK4AB12EA", a.key)
        assertEquals("Fuliza covered Ksh 463", a.title)
        assertEquals("Of a Ksh 2,500 payment to Jane Tester · you owe Ksh 6,418.36, due 2 Nov", a.body)
        val alone = fulizaTxRow().copy(kind = TxKind.FULIZA_ONLY, amountCents = 46_300, counterpartyName = null, fulizaDueDate = null)
        assertEquals("You owe Ksh 6,418.36", AlertWords.fulizaDraw(alone).body)
    }

    @Test
    fun `a summary's change reads in whole percents, and a tiny one as about the same`() {
        assertEquals("20% less than last week", AlertWords.change(800_000, 1_000_000))
        assertEquals("25% more than last week", AlertWords.change(1_250_000, 1_000_000))
        assertEquals("about the same as last week", AlertWords.change(1_004_000, 1_000_000))
        assertEquals("1% more than last week", AlertWords.change(1_005_000, 1_000_000))
    }

    @Test
    fun `a day of one payment says so, a nameless one says what it was, and a first week has nothing to compare`() {
        val day = LocalDate.parse("2026-10-05")
        val kplc = BiggestPayment("TJK4AB12FA", TxKind.PAYBILL, "KPLC PREPAID", 100_000)
        assertEquals("One payment: KPLC Prepaid Ksh 1,000", AlertWords.daily(day, day, SpentCount(100_000, 1), kplc).body)
        val airtime = BiggestPayment("TJK4AB12FG", TxKind.AIRTIME_SELF, null, 5_000)
        assertEquals("Across 2 payments · biggest: Airtime Ksh 50", AlertWords.daily(day, day, SpentCount(7_000, 2), airtime).body)
        val week = AlertWords.weekly(day, 800_000, 0, AlertWords.CategoryShare("Groceries", 600_000))
        assertEquals("weekly:2026-10-05", week.key)
        assertEquals("Most on Groceries (Ksh 6,000)", week.body)
    }

    @Test
    fun `a Fuliza reminder says how soon, and names the line only when given one`() {
        val due = LocalDate.parse("2026-11-02")
        assertEquals("Fuliza Ksh 6,418.36 due today", AlertWords.fulizaDue(FulizaDue(1, 641_836, due, 0), null).title)
        assertEquals("Fuliza Ksh 6,418.36 due tomorrow", AlertWords.fulizaDue(FulizaDue(1, 641_836, due, 1), null).title)
        assertEquals("Due 2 Nov", AlertWords.fulizaDue(FulizaDue(1, 641_836, due, 1), null).body)
        assertEquals("fuliza-due:1:2026-11-02:0d", AlertWords.fulizaDue(FulizaDue(1, 641_836, due, 0), "Personal ··11").key)
    }
}
