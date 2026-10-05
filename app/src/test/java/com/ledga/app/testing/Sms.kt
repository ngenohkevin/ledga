package com.ledga.app.testing

import java.time.Instant

/** Synthetic M-Pesa SMS (the repo is public): invented names, numbers, codes, amounts and dates. */
object Sms {
    fun at(iso: String): Instant = Instant.parse(iso)

    const val SEND = "TJK4AB12FB Confirmed. Ksh500.00 sent to JANE TESTER 0712345111 on 21/3/26 at 1:30 PM. New M-PESA balance is Ksh1,200.00. Transaction cost, Ksh7.00."
    const val KPLC = "TJK4AB12FA Confirmed. Ksh1,000.00 sent to KPLC PREPAID for account 37100000001 on 21/3/26 at 3:00 PM New M-PESA balance is Ksh2,000.00. Transaction cost, Ksh0.00."
    const val PURCHASE = "TJK4AB12EA Confirmed. Ksh2,500.00 paid to SAMPLE SUPERMARKET. on 9/6/26 at 7:48 PM.New M-PESA balance is Ksh0.00. Transaction cost, Ksh0.00."
    const val COMPANION = "TJK4AB12EA Confirmed. Fuliza M-PESA amount is Ksh 463.00. Access Fee charged Ksh 4.63. Total Fuliza M-PESA outstanding amount is Ksh6,418.36 due on 02/11/26. To check daily charges, Dial *334#OK Select Query Charges"
    const val BANK_APP = "TJK4AB12FC Confirmed. You have received Ksh5,000.00 from EXAMPLE BANK LIMITED- APP on 2/4/26 at 9:15 AM. New M-PESA balance is Ksh5,100.00."
    const val REVERSAL = "TJK4AB12FE Confirmed. Transaction TJK4AB12FB has been reversed on 21/3/26 at 3:00 PM and Ksh500.00 is credited to your M-PESA account. New M-PESA account balance is Ksh1,700.00."
    const val REPAY_FULL = "TJK4AB12FF Confirmed. Ksh 463.00 from your M-PESA has been used to fully pay your outstanding Fuliza M-PESA. Available Fuliza M-PESA limit is Ksh 1000.00. Your M-PESA balance is 537.00."
    const val BALANCE_CHECK = "TJK4AB12CD Confirmed. Your account balance was: M-PESA Account : Ksh612.00 Business Account : Ksh0.00 on 17/3/26 at 2:05 PM. Transaction cost, Ksh0.00."
    const val UNREADABLE = "TJKABCDEFG Confirmed. Some new M-PESA feature we don't know about."

    /** A send to JANE TESTER. [whenText] is the body's "d/M/yy at h:mm a". */
    fun send(code: String, amount: String = "500.00", whenText: String = "21/3/26 at 1:30 PM") =
        "$code Confirmed. Ksh$amount sent to JANE TESTER 0712345111 on $whenText. New M-PESA balance is Ksh1,200.00. Transaction cost, Ksh7.00."

    /** A current-format paybill ("sent to BIZ for account ACC"). */
    fun paybill(code: String, name: String, account: String, amount: String, whenText: String = "22/3/26 at 9:00 AM") =
        "$code Confirmed. Ksh$amount sent to $name for account $account on $whenText New M-PESA balance is Ksh3,000.00. Transaction cost, Ksh0.00."

    fun receive(code: String, from: String, amount: String, whenText: String = "24/3/26 at 6:00 PM") =
        "$code Confirmed.You have received Ksh$amount from $from on $whenText New M-PESA balance is Ksh1,800.00."

    fun buyGoods(code: String, merchant: String, amount: String, whenText: String = "5/4/26 at 8:05 AM") =
        "$code Confirmed. Ksh$amount paid to $merchant. on $whenText.New M-PESA balance is Ksh820.00. Transaction cost, Ksh0.00."
}
