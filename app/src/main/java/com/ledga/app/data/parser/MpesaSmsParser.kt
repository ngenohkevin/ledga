package com.ledga.app.data.parser

import com.ledga.app.util.DateUtils

enum class TransactionType {
    SEND, BUY_GOODS, PAY_BILL, WITHDRAW_AGENT, WITHDRAW_ATM,
    DEPOSIT, RECEIVED, AIRTIME_SELF, AIRTIME_OTHER, MPESA_GLOBAL,
    FULIZA, FULIZA_REPAYMENT, FULIZA_REVERSAL, FULIZA_AUTO_PAY,
    MSHWARI, KCB_MPESA,
    REVERSAL, UNKNOWN
}

enum class TransactionDirection { INFLOW, OUTFLOW }

data class ParsedTransaction(
    val transactionCode: String,
    val type: TransactionType,
    val amount: Double,
    val transactionCost: Double,
    val recipientName: String?,
    val recipientPhone: String?,
    val accountNumber: String?,
    val destinationCountry: String?,
    val balance: Double,
    val direction: TransactionDirection,
    val fulizaAmount: Double?,
    val fulizaOutstanding: Double?,
    val reversedTransactionCode: String?,
    val timestamp: Long,
    val rawSms: String,
    /** "Available Fuliza M-PESA limit is Ksh X" — set post-hoc in [MpesaSmsParser.parse]. */
    val fulizaLimit: Double? = null
)

sealed interface ParseResult {
    data class Success(val transaction: ParsedTransaction) : ParseResult
    data class Failure(val rawSms: String, val reason: String) : ParseResult
}

object MpesaSmsParser {

    // Amount: handles "Ksh500.00", "Ksh 500.00", "Ksh5,000.00"
    private val AMOUNT_REGEX = Regex("""Ksh\s?([\d,]+\.\d{2})""")
    // Transaction code: 10 alphanumeric chars at start, may have newline/space after
    private val TRANSACTION_CODE_REGEX = Regex("""^([A-Z0-9]{10})\s""")
    // Date: handles both 2-digit and 4-digit year
    private val DATE_REGEX = Regex("""on\s+(\d{1,2}/\d{1,2}/\d{2,4}\s+at\s+\d{1,2}:\d{2}\s+[AP]M)""", RegexOption.IGNORE_CASE)
    // Balance: multiple formats — note "Ksh" is optional. Fuliza auto-pay SMS
    // reads "Your M-PESA balance is 8581.08" with no Ksh prefix on some
    // ROMs / carrier formats, and we still need to capture it.
    private val BALANCE_REGEX = Regex(
        """(?:M-PESA balance is|MPESA balance is|account balance is)\s*(?:Ksh)?\s?([\d,]+\.\d{2})""",
        RegexOption.IGNORE_CASE,
    )
    private val COST_REGEX = Regex("""Transaction cost,?\s*Ksh\s?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
    private val FULIZA_AMOUNT_REGEX = Regex("""Fuliza M-PESA amount is Ksh\s?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
    private val FULIZA_OUTSTANDING_REGEX = Regex("""(?:Fuliza M-PESA outstanding amount is|Total Fuliza M-PESA outstanding amount is)\s*Ksh\s?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
    private val FULIZA_LIMIT_REGEX = Regex("""(?:Available |available )Fuliza M-PESA limit is Ksh\s?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
    // The 1% Fuliza access fee on borrows. M-PESA phrases it either
    // "Interest charged Ksh 10.73" or "Access Fee charged Ksh 0.30".
    private val FULIZA_INTEREST_REGEX = Regex("""(?:Interest charged|Access Fee charged),?\s*(?:of\s*)?Ksh\s?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)

    fun isMpesaMessage(sender: String): Boolean {
        // Fuliza borrow confirmations can arrive from a dedicated "FULIZA"
        // sender id rather than "MPESA" — without it, borrows go untracked
        // while their repayments (sent from MPESA) are captured.
        val s = sender.trim()
        return s.equals("MPESA", ignoreCase = true) || s.equals("FULIZA", ignoreCase = true)
    }

    fun parse(smsBody: String, smsTimestamp: Long = System.currentTimeMillis()): ParseResult {
        // Normalize: trim, collapse newlines/extra spaces
        val sms = smsBody.trim().replace(Regex("""\s*\n\s*"""), " ")

        val code = extractTransactionCode(sms)
            ?: return ParseResult.Failure(smsBody, "No transaction code found")

        if (!sms.contains("confirmed", ignoreCase = true)) {
            return ParseResult.Failure(smsBody, "Not a confirmed transaction")
        }

        // Skip balance check messages (not transactions)
        if (sms.contains("Your account balance was", ignoreCase = true) ||
            sms.contains("account balance was:", ignoreCase = true)) {
            return ParseResult.Failure(smsBody, "Balance check — not a transaction")
        }

        val timestamp = extractDate(sms) ?: smsTimestamp
        val balance = extractBalance(sms) ?: 0.0
        val cost = extractCost(sms) ?: 0.0

        return try {
            val transaction = when {
                // Reversals (check before Fuliza since some reversals mention Fuliza)
                isReversalAlt(sms) -> parseReversalAlt(sms, code, balance, timestamp)
                isReversal(sms) -> parseReversal(sms, code, balance, timestamp)
                isFulizaReversal(sms) -> parseFulizaReversal(sms, code, timestamp)

                // Fuliza auto-deductions (must check before generic Fuliza)
                isFulizaAutoPay(sms) -> parseFulizaAutoPay(sms, code, balance, timestamp)
                isFulizaRepayment(sms) -> parseFulizaRepayment(sms, code, timestamp)

                // Savings products
                isMshwari(sms) -> parseMshwari(sms, code, balance, cost, timestamp)
                isKcbMpesa(sms) -> parseKcbMpesa(sms, code, balance, cost, timestamp)

                // International
                isMpesaGlobal(sms) -> parseMpesaGlobal(sms, code, balance, cost, timestamp)

                // Fuliza borrow (has "Fuliza M-PESA amount is" or "Total Fuliza M-PESA outstanding")
                isFuliza(sms) -> parseFuliza(sms, code, balance, cost, timestamp)

                // Standard types
                isDeposit(sms) -> parseDeposit(sms, code, balance, timestamp)
                isReceived(sms) -> parseReceived(sms, code, balance, timestamp)
                isAirtimeSelf(sms) -> parseAirtimeSelf(sms, code, balance, cost, timestamp)
                isAirtimeOther(sms) -> parseAirtimeOther(sms, code, balance, cost, timestamp)
                isWithdrawAtm(sms) -> parseWithdrawAtm(sms, code, balance, cost, timestamp)
                isWithdrawAlt(sms) -> parseWithdrawAlt(sms, code, balance, cost, timestamp)
                isWithdrawAgent(sms) -> parseWithdrawAgent(sms, code, balance, cost, timestamp)
                isPayBill(sms) -> parsePayBill(sms, code, balance, cost, timestamp)
                isBuyGoods(sms) -> parseBuyGoods(sms, code, balance, cost, timestamp)
                isSendMoney(sms) -> parseSendMoney(sms, code, balance, cost, timestamp)
                else -> parseUnknown(sms, code, balance, cost, timestamp)
            }
            // The available Fuliza limit can ride along on any SMS type
            // (auto-pay, borrow, even regular payments) — attach it post-hoc
            // so every parser benefits without threading it through each one.
            val fulizaLimit = FULIZA_LIMIT_REGEX.find(sms)?.groupValues?.get(1)?.let { parseAmount(it) }
            ParseResult.Success(
                if (fulizaLimit != null) transaction.copy(fulizaLimit = fulizaLimit) else transaction
            )
        } catch (e: Exception) {
            ParseResult.Failure(smsBody, "Parse error: ${e.message}")
        }
    }

    // --- Type detection ---

    private fun isReversal(sms: String) =
        sms.contains("has been reversed") && !sms.contains("Fuliza", ignoreCase = true) &&
                !sms.contains("Reversal of transaction", ignoreCase = true)

    private fun isReversalAlt(sms: String) =
        sms.contains("Reversal of transaction", ignoreCase = true) &&
                sms.contains("has been successfully reversed", ignoreCase = true)

    private fun isFulizaReversal(sms: String) =
        sms.contains("Fuliza M-PESA of", ignoreCase = true) && sms.contains("has been reversed")

    private fun isFulizaAutoPay(sms: String) =
        sms.contains("from your M-PESA has been used to", ignoreCase = true) &&
                sms.contains("pay your outstanding Fuliza", ignoreCase = true)

    private fun isFulizaRepayment(sms: String) =
        sms.contains("paid", ignoreCase = true) && sms.contains("to Fuliza M-PESA", ignoreCase = true)

    private fun isMshwari(sms: String) =
        sms.contains("M-Shwari", ignoreCase = true)

    private fun isKcbMpesa(sms: String) =
        sms.contains("KCB M-Pesa", ignoreCase = true) || sms.contains("KCB M-PESA", ignoreCase = true)

    private fun isMpesaGlobal(sms: String) =
        sms.contains("M-PESA Global", ignoreCase = true) || sms.contains("MPESA Global", ignoreCase = true)

    private fun isFuliza(sms: String) =
        sms.contains("Fuliza M-PESA amount is", ignoreCase = true) ||
                sms.contains("Total Fuliza M-PESA outstanding", ignoreCase = true)

    private fun isDeposit(sms: String) =
        sms.contains("You have deposited", ignoreCase = true) ||
                sms.contains("deposited", ignoreCase = true) ||
                // "Give KshX cash to AGENT" — agent-deposit format, user hands cash in
                Regex("""Give\s+Ksh\s?[\d,]+\.\d{2}\s+cash\s+to""", RegexOption.IGNORE_CASE)
                    .containsMatchIn(sms)

    private fun isReceived(sms: String) =
        sms.contains("You have received", ignoreCase = true)

    // "airtime purchased" OR "bought...of airtime" without a phone number after
    private fun isAirtimeSelf(sms: String): Boolean {
        if (sms.contains("airtime purchased", ignoreCase = true)) return true
        if (sms.contains("bought", ignoreCase = true) && sms.contains("of airtime", ignoreCase = true)) {
            // Self if no phone number after "airtime for"
            return !sms.contains("airtime for", ignoreCase = true)
        }
        return false
    }

    private fun isAirtimeOther(sms: String) =
        sms.contains("bought", ignoreCase = true) && sms.contains("airtime for", ignoreCase = true)

    private fun isWithdrawAtm(sms: String) =
        sms.contains("withdrawn", ignoreCase = true) && sms.contains("from an ATM", ignoreCase = true)

    // Alt format: "Withdraw Ksh6,500.00 from 031824 - Agent Name"
    private fun isWithdrawAlt(sms: String) =
        Regex("""Withdraw\s+Ksh""", RegexOption.IGNORE_CASE).containsMatchIn(sms)

    private fun isWithdrawAgent(sms: String) =
        sms.contains("withdrawn", ignoreCase = true) && sms.contains("from ", ignoreCase = true)

    private fun isPayBill(sms: String) =
        sms.contains("paid to", ignoreCase = true) &&
                (sms.contains("Account Number", ignoreCase = true) || sms.contains("for account", ignoreCase = true))

    private fun isBuyGoods(sms: String) =
        sms.contains("paid to", ignoreCase = true) && !sms.contains("Account Number", ignoreCase = true) &&
                !sms.contains("for account", ignoreCase = true)

    private fun isSendMoney(sms: String): Boolean {
        // Standard: "Ksh500.00 sent to JOHN ..."
        if (sms.contains("sent to", ignoreCase = true)) return true
        // Variant: "You have sent Ksh500.00 to Hustler Fund on ..."
        // Amount sits between "sent" and "to", so the literal "sent to" miss.
        return Regex("""\bsent\s+Ksh\s?[\d,]+\.\d{2}\s+to\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(sms)
    }

    // --- Parsers ---

    private fun parseSendMoney(sms: String, code: String, balance: Double, cost: Double, timestamp: Long): ParsedTransaction {
        val amount = extractFirstAmount(sms) ?: 0.0
        // Standard: "sent to NAME PHONE on"
        val sendToRegex = Regex("""sent to\s+(.+?)\s+(\d{10,})\s+on""", RegexOption.IGNORE_CASE)
        val match = sendToRegex.find(sms)
        // Alt 1: "sent to NAME on" (no phone)
        val sendToAltRegex = Regex("""sent to\s+(.+?)\s+(?:on|for)""", RegexOption.IGNORE_CASE)
        // Alt 2: "sent KshX to NAME on" — Hustler Fund and similar products
        val sendAmountToRegex = Regex(
            """sent\s+Ksh\s?[\d,]+\.\d{2}\s+to\s+(.+?)\s+on""",
            RegexOption.IGNORE_CASE,
        )
        val altMatch = match
            ?: sendToAltRegex.find(sms)
            ?: sendAmountToRegex.find(sms)

        return ParsedTransaction(
            transactionCode = code, type = TransactionType.SEND, amount = amount,
            transactionCost = cost,
            recipientName = (match ?: altMatch)?.groupValues?.get(1)?.trim(),
            recipientPhone = match?.groupValues?.get(2),
            accountNumber = null, destinationCountry = null, balance = balance,
            direction = TransactionDirection.OUTFLOW,
            fulizaAmount = null, fulizaOutstanding = null, reversedTransactionCode = null,
            timestamp = timestamp, rawSms = sms
        )
    }

    private fun parseBuyGoods(sms: String, code: String, balance: Double, cost: Double, timestamp: Long): ParsedTransaction {
        val amount = extractFirstAmount(sms) ?: 0.0
        val paidToRegex = Regex("""paid to\s+(.+?)\.?\s+on""", RegexOption.IGNORE_CASE)
        val match = paidToRegex.find(sms)
        return ParsedTransaction(
            transactionCode = code, type = TransactionType.BUY_GOODS, amount = amount,
            transactionCost = cost, recipientName = match?.groupValues?.get(1)?.trim(),
            recipientPhone = null, accountNumber = null, destinationCountry = null,
            balance = balance, direction = TransactionDirection.OUTFLOW,
            fulizaAmount = null, fulizaOutstanding = null, reversedTransactionCode = null,
            timestamp = timestamp, rawSms = sms
        )
    }

    private fun parsePayBill(sms: String, code: String, balance: Double, cost: Double, timestamp: Long): ParsedTransaction {
        val amount = extractFirstAmount(sms) ?: 0.0
        // Standard: "paid to BUSINESS. Account Number 12345. on"
        val paidToRegex = Regex("""paid to\s+(.+?)\.\s*Account Number\s+(\S+?)\.?\s+on""", RegexOption.IGNORE_CASE)
        val match = paidToRegex.find(sms)
        // Alt: "sent to NAME for account ACCOUNT on"
        val altRegex = Regex("""sent to\s+(.+?)\s+for account\s+(.+?)\s+on""", RegexOption.IGNORE_CASE)
        val altMatch = if (match == null) altRegex.find(sms) else null

        return ParsedTransaction(
            transactionCode = code, type = TransactionType.PAY_BILL, amount = amount,
            transactionCost = cost,
            recipientName = match?.groupValues?.get(1)?.trim() ?: altMatch?.groupValues?.get(1)?.trim(),
            recipientPhone = null,
            accountNumber = match?.groupValues?.get(2)?.trim() ?: altMatch?.groupValues?.get(2)?.trim(),
            destinationCountry = null, balance = balance, direction = TransactionDirection.OUTFLOW,
            fulizaAmount = null, fulizaOutstanding = null, reversedTransactionCode = null,
            timestamp = timestamp, rawSms = sms
        )
    }

    private fun parseWithdrawAgent(sms: String, code: String, balance: Double, cost: Double, timestamp: Long): ParsedTransaction {
        val amount = extractFirstAmount(sms) ?: 0.0
        val fromRegex = Regex("""withdrawn.*?from\s+(.+?)\s+(\d{4,})\s+on""", RegexOption.IGNORE_CASE)
        val match = fromRegex.find(sms)
        return ParsedTransaction(
            transactionCode = code, type = TransactionType.WITHDRAW_AGENT, amount = amount,
            transactionCost = cost, recipientName = match?.groupValues?.get(1)?.trim(),
            recipientPhone = match?.groupValues?.get(2), accountNumber = null,
            destinationCountry = null, balance = balance, direction = TransactionDirection.OUTFLOW,
            fulizaAmount = null, fulizaOutstanding = null, reversedTransactionCode = null,
            timestamp = timestamp, rawSms = sms
        )
    }

    // Alt: "Withdraw Ksh6,500.00 from 031824 - Agent Name Street"
    private fun parseWithdrawAlt(sms: String, code: String, balance: Double, cost: Double, timestamp: Long): ParsedTransaction {
        val amount = extractFirstAmount(sms) ?: 0.0
        val altRegex = Regex("""Withdraw\s+Ksh\s?[\d,]+\.\d{2}\s+from\s+(\d+)\s*-\s*(.+?)\s+(?:New |new )""", RegexOption.IGNORE_CASE)
        val match = altRegex.find(sms)
        return ParsedTransaction(
            transactionCode = code, type = TransactionType.WITHDRAW_AGENT, amount = amount,
            transactionCost = cost,
            recipientName = match?.groupValues?.get(2)?.trim(),
            recipientPhone = match?.groupValues?.get(1),
            accountNumber = null, destinationCountry = null, balance = balance,
            direction = TransactionDirection.OUTFLOW,
            fulizaAmount = null, fulizaOutstanding = null, reversedTransactionCode = null,
            timestamp = timestamp, rawSms = sms
        )
    }

    private fun parseWithdrawAtm(sms: String, code: String, balance: Double, cost: Double, timestamp: Long): ParsedTransaction {
        val amount = extractFirstAmount(sms) ?: 0.0
        return ParsedTransaction(
            transactionCode = code, type = TransactionType.WITHDRAW_ATM, amount = amount,
            transactionCost = cost, recipientName = "ATM", recipientPhone = null,
            accountNumber = null, destinationCountry = null, balance = balance,
            direction = TransactionDirection.OUTFLOW, fulizaAmount = null, fulizaOutstanding = null,
            reversedTransactionCode = null, timestamp = timestamp, rawSms = sms
        )
    }

    private fun parseDeposit(sms: String, code: String, balance: Double, timestamp: Long): ParsedTransaction {
        val amount = extractFirstAmount(sms) ?: 0.0
        // "Give Ksh300.00 cash to Jamag Holdings ... New M-PESA balance" — extract the agent.
        val giveCashRegex = Regex(
            """Give\s+Ksh\s?[\d,]+\.\d{2}\s+cash\s+to\s+(.+?)\s+New M-PESA balance""",
            RegexOption.IGNORE_CASE,
        )
        val giveCashMatch = giveCashRegex.find(sms)
        return ParsedTransaction(
            transactionCode = code, type = TransactionType.DEPOSIT, amount = amount,
            transactionCost = 0.0,
            recipientName = giveCashMatch?.groupValues?.get(1)?.trim(),
            recipientPhone = null,
            accountNumber = null, destinationCountry = null, balance = balance,
            direction = TransactionDirection.INFLOW, fulizaAmount = null, fulizaOutstanding = null,
            reversedTransactionCode = null, timestamp = timestamp, rawSms = sms
        )
    }

    private fun parseReceived(sms: String, code: String, balance: Double, timestamp: Long): ParsedTransaction {
        val amount = extractFirstAmount(sms) ?: 0.0
        val fromRegex = Regex("""received.*?from\s+(.+?)\s+(\d{10,})\s+on""", RegexOption.IGNORE_CASE)
        val match = fromRegex.find(sms)
        return ParsedTransaction(
            transactionCode = code, type = TransactionType.RECEIVED, amount = amount,
            transactionCost = 0.0, recipientName = match?.groupValues?.get(1)?.trim(),
            recipientPhone = match?.groupValues?.get(2), accountNumber = null,
            destinationCountry = null, balance = balance, direction = TransactionDirection.INFLOW,
            fulizaAmount = null, fulizaOutstanding = null, reversedTransactionCode = null,
            timestamp = timestamp, rawSms = sms
        )
    }

    private fun parseAirtimeSelf(sms: String, code: String, balance: Double, cost: Double, timestamp: Long): ParsedTransaction {
        val amount = extractFirstAmount(sms) ?: 0.0
        return ParsedTransaction(
            transactionCode = code, type = TransactionType.AIRTIME_SELF, amount = amount,
            transactionCost = cost, recipientName = null, recipientPhone = null,
            accountNumber = null, destinationCountry = null, balance = balance,
            direction = TransactionDirection.OUTFLOW, fulizaAmount = null, fulizaOutstanding = null,
            reversedTransactionCode = null, timestamp = timestamp, rawSms = sms
        )
    }

    private fun parseAirtimeOther(sms: String, code: String, balance: Double, cost: Double, timestamp: Long): ParsedTransaction {
        val amount = extractFirstAmount(sms) ?: 0.0
        val forRegex = Regex("""airtime for\s+(\d{10,})""", RegexOption.IGNORE_CASE)
        val match = forRegex.find(sms)
        return ParsedTransaction(
            transactionCode = code, type = TransactionType.AIRTIME_OTHER, amount = amount,
            transactionCost = cost, recipientName = null, recipientPhone = match?.groupValues?.get(1),
            accountNumber = null, destinationCountry = null, balance = balance,
            direction = TransactionDirection.OUTFLOW, fulizaAmount = null, fulizaOutstanding = null,
            reversedTransactionCode = null, timestamp = timestamp, rawSms = sms
        )
    }

    private fun parseMpesaGlobal(sms: String, code: String, balance: Double, cost: Double, timestamp: Long): ParsedTransaction {
        val amount = extractFirstAmount(sms) ?: 0.0
        val globalRegex = Regex("""sent to\s+(.+?)\s+(\+\d{7,15})\s+\((.+?)\)""", RegexOption.IGNORE_CASE)
        val match = globalRegex.find(sms)
        return ParsedTransaction(
            transactionCode = code, type = TransactionType.MPESA_GLOBAL, amount = amount,
            transactionCost = cost, recipientName = match?.groupValues?.get(1)?.trim(),
            recipientPhone = match?.groupValues?.get(2), accountNumber = null,
            destinationCountry = match?.groupValues?.get(3)?.trim(), balance = balance,
            direction = TransactionDirection.OUTFLOW, fulizaAmount = null, fulizaOutstanding = null,
            reversedTransactionCode = null, timestamp = timestamp, rawSms = sms
        )
    }

    private fun parseFuliza(sms: String, code: String, balance: Double, cost: Double, timestamp: Long): ParsedTransaction {
        val amount = extractFirstAmount(sms) ?: 0.0
        val fulizaAmt = FULIZA_AMOUNT_REGEX.find(sms)?.groupValues?.get(1)?.let { parseAmount(it) }
        val fulizaOut = FULIZA_OUTSTANDING_REGEX.find(sms)?.groupValues?.get(1)?.let { parseAmount(it) }
        // The access fee ("Interest charged") is a real cost — fold it into
        // transactionCost so the fees figures stop understating Fuliza.
        val interest = FULIZA_INTEREST_REGEX.find(sms)?.groupValues?.get(1)?.let { parseAmount(it) } ?: 0.0

        val sendToRegex = Regex("""sent to\s+(.+?)\s+(\d{10,})\s+on""", RegexOption.IGNORE_CASE)
        val match = sendToRegex.find(sms)

        return ParsedTransaction(
            transactionCode = code, type = TransactionType.FULIZA,
            amount = fulizaAmt ?: amount,
            transactionCost = cost + interest, recipientName = match?.groupValues?.get(1)?.trim(),
            recipientPhone = match?.groupValues?.get(2), accountNumber = null,
            destinationCountry = null, balance = balance, direction = TransactionDirection.OUTFLOW,
            fulizaAmount = fulizaAmt, fulizaOutstanding = fulizaOut,
            reversedTransactionCode = null, timestamp = timestamp, rawSms = sms
        )
    }

    // "Ksh X from your M-PESA has been used to fully/partially pay your outstanding Fuliza"
    // Fuliza is M-Pesa's overdraft loan; when fresh money lands, the wallet
    // auto-deducts to repay it.
    //
    // Note: the SMS exposes the *available* Fuliza limit (e.g. how much you
    // can still borrow), NOT the remaining outstanding. For a full clear the
    // outstanding is unambiguously 0. For a partial clear we don't know the
    // user's maximum Fuliza limit from this SMS alone, so we leave it null —
    // FulizaRule will then defer to the previous outstanding-bearing
    // transaction instead of misreporting the available-limit value as
    // "you owe X".
    private fun parseFulizaAutoPay(sms: String, code: String, balance: Double, timestamp: Long): ParsedTransaction {
        val amount = extractFirstAmount(sms) ?: 0.0
        val isFullPay = sms.contains("fully pay", ignoreCase = true)

        return ParsedTransaction(
            transactionCode = code, type = TransactionType.FULIZA_AUTO_PAY, amount = amount,
            transactionCost = 0.0,
            recipientName = if (isFullPay) "Fuliza overdraft (auto-cleared)"
                            else "Fuliza overdraft (auto-partial)",
            recipientPhone = null, accountNumber = null, destinationCountry = null,
            balance = balance, direction = TransactionDirection.OUTFLOW,
            fulizaAmount = null,
            fulizaOutstanding = if (isFullPay) 0.0 else null,
            reversedTransactionCode = null, timestamp = timestamp, rawSms = sms
        )
    }

    private fun parseFulizaRepayment(sms: String, code: String, timestamp: Long): ParsedTransaction {
        val amount = extractFirstAmount(sms) ?: 0.0
        val fulizaOut = FULIZA_OUTSTANDING_REGEX.find(sms)?.groupValues?.get(1)?.let { parseAmount(it) }
        return ParsedTransaction(
            transactionCode = code, type = TransactionType.FULIZA_REPAYMENT, amount = amount,
            transactionCost = 0.0, recipientName = null, recipientPhone = null,
            accountNumber = null, destinationCountry = null, balance = 0.0,
            direction = TransactionDirection.OUTFLOW, fulizaAmount = null, fulizaOutstanding = fulizaOut,
            reversedTransactionCode = null, timestamp = timestamp, rawSms = sms
        )
    }

    private fun parseFulizaReversal(sms: String, code: String, timestamp: Long): ParsedTransaction {
        val amount = extractFirstAmount(sms) ?: 0.0
        val fulizaOut = FULIZA_OUTSTANDING_REGEX.find(sms)?.groupValues?.get(1)?.let { parseAmount(it) }
        return ParsedTransaction(
            transactionCode = code, type = TransactionType.FULIZA_REVERSAL, amount = amount,
            transactionCost = 0.0, recipientName = null, recipientPhone = null,
            accountNumber = null, destinationCountry = null, balance = 0.0,
            direction = TransactionDirection.INFLOW, fulizaAmount = null, fulizaOutstanding = fulizaOut,
            reversedTransactionCode = null, timestamp = timestamp, rawSms = sms
        )
    }

    private fun parseReversal(sms: String, code: String, balance: Double, timestamp: Long): ParsedTransaction {
        val reversedCodeRegex = Regex("""Transaction\s+([A-Z0-9]{10})\s+has been reversed""")
        val match = reversedCodeRegex.find(sms)
        // Credited amount, when present ("...and Ksh500.00 is credited to your
        // M-PESA account"). Deliberately NOT extractFirstAmount — in the short
        // format the only Ksh figure is the balance, which must not be
        // recorded as the reversal amount.
        val creditedRegex = Regex("""Ksh\s?([\d,]+\.\d{2})\s+is credited""", RegexOption.IGNORE_CASE)
        val credited = creditedRegex.find(sms)?.groupValues?.get(1)?.let { parseAmount(it) } ?: 0.0
        return ParsedTransaction(
            transactionCode = code, type = TransactionType.REVERSAL, amount = credited,
            transactionCost = 0.0, recipientName = null, recipientPhone = null,
            accountNumber = null, destinationCountry = null, balance = balance,
            direction = TransactionDirection.INFLOW, fulizaAmount = null, fulizaOutstanding = null,
            reversedTransactionCode = match?.groupValues?.get(1), timestamp = timestamp, rawSms = sms
        )
    }

    // "Reversal of transaction X has been successfully reversed ... Ksh200.00 is credited"
    private fun parseReversalAlt(sms: String, code: String, balance: Double, timestamp: Long): ParsedTransaction {
        val reversedCodeRegex = Regex("""Reversal of transaction\s+([A-Z0-9]{10})""", RegexOption.IGNORE_CASE)
        val match = reversedCodeRegex.find(sms)
        val amount = extractFirstAmount(sms) ?: 0.0
        return ParsedTransaction(
            transactionCode = code, type = TransactionType.REVERSAL, amount = amount,
            transactionCost = 0.0, recipientName = null, recipientPhone = null,
            accountNumber = null, destinationCountry = null, balance = balance,
            direction = TransactionDirection.INFLOW, fulizaAmount = null, fulizaOutstanding = null,
            reversedTransactionCode = match?.groupValues?.get(1), timestamp = timestamp, rawSms = sms
        )
    }

    private fun parseMshwari(sms: String, code: String, balance: Double, cost: Double, timestamp: Long): ParsedTransaction {
        val amount = extractFirstAmount(sms) ?: 0.0
        val isInflow = sms.contains("from M-Shwari", ignoreCase = true)
        return ParsedTransaction(
            transactionCode = code, type = TransactionType.MSHWARI, amount = amount,
            transactionCost = cost, recipientName = "M-Shwari", recipientPhone = null,
            accountNumber = null, destinationCountry = null, balance = balance,
            direction = if (isInflow) TransactionDirection.INFLOW else TransactionDirection.OUTFLOW,
            fulizaAmount = null, fulizaOutstanding = null, reversedTransactionCode = null,
            timestamp = timestamp, rawSms = sms
        )
    }

    private fun parseKcbMpesa(sms: String, code: String, balance: Double, cost: Double, timestamp: Long): ParsedTransaction {
        val amount = extractFirstAmount(sms) ?: 0.0
        val isInflow = sms.contains("from KCB", ignoreCase = true)
        return ParsedTransaction(
            transactionCode = code, type = TransactionType.KCB_MPESA, amount = amount,
            transactionCost = cost, recipientName = "KCB M-Pesa", recipientPhone = null,
            accountNumber = null, destinationCountry = null, balance = balance,
            direction = if (isInflow) TransactionDirection.INFLOW else TransactionDirection.OUTFLOW,
            fulizaAmount = null, fulizaOutstanding = null, reversedTransactionCode = null,
            timestamp = timestamp, rawSms = sms
        )
    }

    private fun parseUnknown(sms: String, code: String, balance: Double, cost: Double, timestamp: Long): ParsedTransaction {
        val amount = extractFirstAmount(sms) ?: 0.0
        return ParsedTransaction(
            transactionCode = code, type = TransactionType.UNKNOWN, amount = amount,
            transactionCost = cost, recipientName = null, recipientPhone = null,
            accountNumber = null, destinationCountry = null, balance = balance,
            direction = TransactionDirection.OUTFLOW, fulizaAmount = null, fulizaOutstanding = null,
            reversedTransactionCode = null, timestamp = timestamp, rawSms = sms
        )
    }

    // --- Extraction helpers ---

    private fun extractTransactionCode(sms: String): String? {
        return TRANSACTION_CODE_REGEX.find(sms)?.groupValues?.get(1)
    }

    private fun extractFirstAmount(sms: String): Double? {
        return AMOUNT_REGEX.find(sms)?.groupValues?.get(1)?.let { parseAmount(it) }
    }

    private fun extractBalance(sms: String): Double? {
        return BALANCE_REGEX.find(sms)?.groupValues?.get(1)?.let { parseAmount(it) }
    }

    private fun extractCost(sms: String): Double? {
        return COST_REGEX.find(sms)?.groupValues?.get(1)?.let { parseAmount(it) }
    }

    private fun extractDate(sms: String): Long? {
        val match = DATE_REGEX.find(sms) ?: return null
        return DateUtils.parseMpesaDate(match.groupValues[1])
    }

    private fun parseAmount(amountStr: String): Double {
        return amountStr.replace(",", "").toDoubleOrNull() ?: 0.0
    }
}
