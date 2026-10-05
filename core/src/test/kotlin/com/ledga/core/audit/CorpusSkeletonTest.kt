package com.ledga.core.audit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CorpusSkeletonTest {
    @Test
    fun `skeleton hides names, numbers, codes, dates and promo tails`() {
        val body = "TJK4AB12CD Confirmed. Ksh500.00 sent to JANE TESTER 0712345111 on 21/3/26 at 1:30 PM. " +
            "New M-PESA balance is Ksh1,200.00. Transaction cost, Ksh7.00. Amount you can transact within the day is 499,493.00. Promo text here."
        val s = Corpus.skeleton(body)
        assertEquals("<CODE> Confirmed. <AMT> sent to w+ <PHONE> on <DATE> at <TIME>. New M-PESA balance is <AMT>. Transaction cost, <AMT>. ~", s)
        listOf("JANE", "TESTER", "0712", "TJK4", "500", "Promo").forEach { assertFalse(it in s, "leaked '$it' in $s") }
    }

    @Test
    fun `skeleton masks masked phones, business numbers and Fuliza figures`() {
        val s = Corpus.skeleton(
            "TJK4AB12DB Confirmed.You have received Ksh2,000.00 from SAMPLE BANK LIMITED B2C 600100 on 2/4/26 at 9:15 AM New M-PESA balance is Ksh3,100.00. Earn interest daily",
        )
        assertEquals("<CODE> Confirmed.You have received <AMT> from w BANK LIMITED B2C <NUM> on <DATE> at <TIME> New M-PESA balance is <AMT>. ~", s)
        val masked = Corpus.skeleton("TJK4AB12DB Confirmed.You have received Ksh2,000.00 from JANE TESTER 0712***111 on 2/4/26 at 9:15 AM")
        assertFalse("0712" in masked || "111" in masked, masked)
    }
}
