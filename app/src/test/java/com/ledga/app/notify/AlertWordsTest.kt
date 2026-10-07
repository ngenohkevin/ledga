package com.ledga.app.notify

import com.ledga.app.testing.fulizaTxRow
import com.ledga.app.testing.txRow
import com.ledga.core.model.TxKind
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
}
