package com.ledga.core.audit

import com.ledga.core.parse.SmsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import java.time.Instant

/** One stored row of the owner's v1 export. LOCAL ONLY: never commit or print its contents. */
data class CorpusRow(
    val code: String,
    val type: String,
    val amount: Double,
    val fee: Double,
    val balance: Double,
    val recipientName: String?,
    val accountNumber: String?,
    val categoryId: Long?,
    val fulizaAmount: Double?,
    val fulizaOutstanding: Double?,
    val reversedCode: String?,
    val rawSms: String,
    val timestamp: Instant,
)

object Corpus {
    val file: File
        get() = System.getenv("LEDGA_CORPUS")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(System.getProperty("user.home"), "Documents/ledga-export/data.json")

    fun available(): Boolean = file.isFile

    /**
     * Any failure is rethrown as a sanitized error: the original exception (and its message, which
     * can embed raw SMS text or field values) is never attached, so nothing leaks to logs/reports.
     */
    fun load(): List<CorpusRow> {
        var index = -1
        try {
            val root = Json.parseToJsonElement(file.readText()).jsonObject
            return root.getValue("transactions").jsonArray.mapIndexed { i, el ->
                index = i
                val o = el.jsonObject
                CorpusRow(
                    code = o.req(o.str("transactionCode")),
                    type = o.req(o.str("type")),
                    amount = o.req(o.dbl("amount")),
                    fee = o.req(o.dbl("transactionCost")),
                    balance = o.req(o.dbl("balance")),
                    recipientName = o.str("recipientName"),
                    accountNumber = o.str("accountNumber"),
                    categoryId = o["categoryId"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.long,
                    fulizaAmount = o.dbl("fulizaAmount"),
                    fulizaOutstanding = o.dbl("fulizaOutstanding"),
                    reversedCode = o.str("reversedTransactionCode"),
                    rawSms = o.req(o.str("rawSms")),
                    timestamp = Instant.ofEpochMilli(o.getValue("timestamp").jsonPrimitive.long),
                )
            }
        } catch (e: Throwable) {
            val where = if (index >= 0) " at row $index" else ""
            throw IllegalStateException("corpus unreadable$where (${e::class.simpleName})")
        }
    }

    private fun <T : Any> JsonObject.req(v: T?): T = v ?: throw NoSuchElementException()

    /** v1 stored doubles; round to cents for comparison. Test-only bridge. */
    fun cents(v1: Double): Long = Math.round(v1 * 100)

    private fun JsonObject.field(name: String): JsonElement? = this[name]?.takeUnless { it is JsonNull }
    private fun JsonObject.str(name: String): String? = field(name)?.jsonPrimitive?.contentOrNull
    private fun JsonObject.dbl(name: String): Double? = field(name)?.jsonPrimitive?.double

    // ---- Skeleton masker: shows message SHAPE only ----

    private val KEEP = setOf(
        "CODE", "AMT", "DATE", "TIME", "PHONE", "NUM",
        "Confirmed", "confirmed", "You", "you", "have", "has", "been", "received", "from", "on", "at", "New", "new",
        "M-PESA", "MPESA", "M-Pesa", "balance", "is", "Transaction", "cost", "sent", "to", "for", "account", "Account",
        "paid", "Number", "withdrawn", "Withdraw", "an", "ATM", "deposited", "Give", "cash", "bought", "of", "airtime",
        "purchased", "Fuliza", "amount", "Interest", "Access", "Fee", "charged", "Total", "outstanding", "due", "used",
        "fully", "partially", "pay", "your", "Your", "Available", "available", "limit", "transfered", "transferred",
        "KCB", "Saving", "M-Shwari", "Global", "GlobalPay", "via", "reversed", "Reversal", "successfully", "credited",
        "and", "APP", "B2C", "Bank", "BANK", "LIMITED", "Limited", "LTD", "Ltd", "Hustler", "Fund", "Failed",
    )
    /** Greedy prefix = cut after the LAST structural sentence; promo tails vary and say nothing about shape. */
    private val TAIL = Regex("""^(.*(?:Transaction cost, ?<AMT>\.?|M-?PESA (?:account )?balance is <AMT>\.?)).*$""")
    private val WORD = Regex("""[A-Za-z][A-Za-z0-9'\-]*""")
    private val WORD_RUN = Regex("""\bw(?:[ .,&()/\-]+w\b)+""")

    fun skeleton(body: String): String {
        var s = SmsText.normalize(body)
        s = s.replace(Regex("""^[A-Z0-9]{10}\b"""), "<CODE>")
        s = s.replace(Regex("""\b(?=[A-Z0-9]*[A-Z])[A-Z0-9]{10}\b"""), "<CODE>")
        s = s.replace(Regex("""Ksh\s?[\d,]+(?:\.\d{1,2})?"""), "<AMT>")
        s = s.replace(Regex("""\b\d{1,2}/\d{1,2}/\d{2,4}\b"""), "<DATE>")
        s = s.replace(Regex("""\b\d{1,2}:\d{2}\s*[AaPp][Mm]"""), "<TIME>")
        s = s.replace(Regex("""\+?\d{2,4}\*{2,4}\d{2,3}|\+\d{7,15}|\b(?:254|0)[17]\d{8}\b"""), "<PHONE>")
        s = s.replace(Regex("""\b\d[\d,.]*"""), "<NUM>")
        s = s.replace(TAIL) { "${it.groupValues[1]} ~" }
        s = s.replace(WORD) { if (it.value in KEEP) it.value else "w" }
        return s.replace(WORD_RUN, "w+")
    }
}
