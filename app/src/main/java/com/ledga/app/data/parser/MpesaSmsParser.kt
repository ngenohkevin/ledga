package com.ledga.app.data.parser

import com.ledga.app.util.DateUtils

enum class TransactionType {
    SEND, BUY_GOODS, PAY_BILL, WITHDRAW_AGENT, WITHDRAW_ATM,
    DEPOSIT, RECEIVED, AIRTIME_SELF, AIRTIME_OTHER, MPESA_GLOBAL,
    FULIZA, FULIZA_REPAYMENT, FULIZA_REVERSAL, MSHWARI, KCB_MPESA,
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
    val rawSms: String
)

sealed interface ParseResult {
    data class Success(val transaction: ParsedTransaction) : ParseResult
    data class Failure(val rawSms: String, val reason: String) : ParseResult
}

object MpesaSmsParser {

    private val AMOUNT_REGEX = Regex("""Ksh([\d,]+\.\d{2})""")
    private val TRANSACTION_CODE_REGEX = Regex("""^([A-Z0-9]{10})\s""")
    private val DATE_REGEX = Regex("""on (\d{1,2}/\d{1,2}/\d{2,4} at \d{1,2}:\d{2} [AP]M)""")
    private val BALANCE_REGEX = Regex("""(?:balance is|account balance is)\s*Ksh([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
    private val COST_REGEX = Regex("""Transaction cost,?\s*Ksh([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
    private val FULIZA_AMOUNT_REGEX = Regex("""Fuliza M-PESA amount is Ksh([\d,]+\.\d{2})""")
    private val FULIZA_OUTSTANDING_REGEX = Regex("""Fuliza M-PESA outstanding amount is Ksh([\d,]+\.\d{2})""")

    fun isMpesaMessage(sender: String): Boolean {
        return sender.equals("MPESA", ignoreCase = true)
    }

    fun parse(smsBody: String, smsTimestamp: Long = System.currentTimeMillis()): ParseResult {
        val code = extractTransactionCode(smsBody)
            ?: return ParseResult.Failure(smsBody, "No transaction code found")

        if (!smsBody.contains("Confirmed", ignoreCase = true)) {
            return ParseResult.Failure(smsBody, "Not a confirmed transaction")
        }

        val timestamp = extractDate(smsBody) ?: smsTimestamp
        val balance = extractBalance(smsBody) ?: 0.0
        val cost = extractCost(smsBody) ?: 0.0

        return try {
            val transaction = when {
                isReversal(smsBody) -> parseReversal(smsBody, code, balance, timestamp)
                isFulizaReversal(smsBody) -> parseFulizaReversal(smsBody, code, timestamp)
                isFulizaRepayment(smsBody) -> parseFulizaRepayment(smsBody, code, timestamp)
                isMshwari(smsBody) -> parseMshwari(smsBody, code, balance, cost, timestamp)
                isKcbMpesa(smsBody) -> parseKcbMpesa(smsBody, code, balance, cost, timestamp)
                isMpesaGlobal(smsBody) -> parseMpesaGlobal(smsBody, code, balance, cost, timestamp)
                isFuliza(smsBody) -> parseFuliza(smsBody, code, balance, cost, timestamp)
                isDeposit(smsBody) -> parseDeposit(smsBody, code, balance, timestamp)
                isReceived(smsBody) -> parseReceived(smsBody, code, balance, timestamp)
                isAirtimeSelf(smsBody) -> parseAirtimeSelf(smsBody, code, balance, cost, timestamp)
                isAirtimeOther(smsBody) -> parseAirtimeOther(smsBody, code, balance, cost, timestamp)
                isWithdrawAtm(smsBody) -> parseWithdrawAtm(smsBody, code, balance, cost, timestamp)
                isWithdrawAgent(smsBody) -> parseWithdrawAgent(smsBody, code, balance, cost, timestamp)
                isPayBill(smsBody) -> parsePayBill(smsBody, code, balance, cost, timestamp)
                isBuyGoods(smsBody) -> parseBuyGoods(smsBody, code, balance, cost, timestamp)
                isSendMoney(smsBody) -> parseSendMoney(smsBody, code, balance, cost, timestamp)
                else -> parseUnknown(smsBody, code, balance, cost, timestamp)
            }
            ParseResult.Success(transaction)
        } catch (e: Exception) {
            ParseResult.Failure(smsBody, "Parse error: ${e.message}")
        }
    }

    // --- Type detection ---

    private fun isReversal(sms: String) =
        sms.contains("has been reversed") && !sms.contains("Fuliza", ignoreCase = true)

    private fun isFulizaReversal(sms: String) =
        sms.contains("Fuliza M-PESA of", ignoreCase = true) && sms.contains("has been reversed")

    private fun isFulizaRepayment(sms: String) =
        sms.contains("paid", ignoreCase = true) && sms.contains("to Fuliza M-PESA", ignoreCase = true)

    private fun isMshwari(sms: String) =
        sms.contains("M-Shwari", ignoreCase = true)

    private fun isKcbMpesa(sms: String) =
        sms.contains("KCB M-Pesa", ignoreCase = true) || sms.contains("KCB M-PESA", ignoreCase = true)

    private fun isMpesaGlobal(sms: String) =
        sms.contains("M-PESA Global", ignoreCase = true) || sms.contains("MPESA Global", ignoreCase = true)

    private fun isFuliza(sms: String) =
        sms.contains("Fuliza M-PESA amount is", ignoreCase = true)

    private fun isDeposit(sms: String) =
        sms.contains("You have deposited", ignoreCase = true)

    private fun isReceived(sms: String) =
        sms.contains("You have received", ignoreCase = true)

    private fun isAirtimeSelf(sms: String) =
        sms.contains("airtime purchased", ignoreCase = true)

    private fun isAirtimeOther(sms: String) =
        sms.contains("bought", ignoreCase = true) && sms.contains("airtime for", ignoreCase = true)

    private fun isWithdrawAtm(sms: String) =
        sms.contains("withdrawn", ignoreCase = true) && sms.contains("from an ATM", ignoreCase = true)

    private fun isWithdrawAgent(sms: String) =
        sms.contains("withdrawn", ignoreCase = true) && sms.contains("from ", ignoreCase = true)

    private fun isPayBill(sms: String) =
        sms.contains("paid to", ignoreCase = true) && sms.contains("Account Number", ignoreCase = true)

    private fun isBuyGoods(sms: String) =
        sms.contains("paid to", ignoreCase = true) && !sms.contains("Account Number", ignoreCase = true)

    private fun isSendMoney(sms: String) =
        sms.contains("sent to", ignoreCase = true)

    // --- Parsers ---

    private fun parseSendMoney(sms: String, code: String, balance: Double, cost: Double, timestamp: Long): ParsedTransaction {
        val amount = extractFirstAmount(sms) ?: 0.0
        val sendToRegex = Regex("""sent to\s+(.+?)\s+(\d{10,})\s+on""", RegexOption.IGNORE_CASE)
        val match = sendToRegex.find(sms)
        return ParsedTransaction(
            transactionCode = code, type = TransactionType.SEND, amount = amount,
            transactionCost = cost, recipientName = match?.groupValues?.get(1)?.trim(),
            recipientPhone = match?.groupValues?.get(2), accountNumber = null,
            destinationCountry = null, balance = balance, direction = TransactionDirection.OUTFLOW,
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
        val paidToRegex = Regex("""paid to\s+(.+?)\.\s*Account Number\s+(\S+?)\.?\s+on""", RegexOption.IGNORE_CASE)
        val match = paidToRegex.find(sms)
        return ParsedTransaction(
            transactionCode = code, type = TransactionType.PAY_BILL, amount = amount,
            transactionCost = cost, recipientName = match?.groupValues?.get(1)?.trim(),
            recipientPhone = null, accountNumber = match?.groupValues?.get(2)?.trim(),
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
        return ParsedTransaction(
            transactionCode = code, type = TransactionType.DEPOSIT, amount = amount,
            transactionCost = 0.0, recipientName = null, recipientPhone = null,
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

        val sendToRegex = Regex("""sent to\s+(.+?)\s+(\d{10,})\s+on""", RegexOption.IGNORE_CASE)
        val match = sendToRegex.find(sms)

        return ParsedTransaction(
            transactionCode = code, type = TransactionType.FULIZA, amount = amount,
            transactionCost = cost, recipientName = match?.groupValues?.get(1)?.trim(),
            recipientPhone = match?.groupValues?.get(2), accountNumber = null,
            destinationCountry = null, balance = balance, direction = TransactionDirection.OUTFLOW,
            fulizaAmount = fulizaAmt, fulizaOutstanding = fulizaOut,
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
        return ParsedTransaction(
            transactionCode = code, type = TransactionType.REVERSAL, amount = 0.0,
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
