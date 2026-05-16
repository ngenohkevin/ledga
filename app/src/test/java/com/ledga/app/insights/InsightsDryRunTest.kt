package com.ledga.app.insights

import com.ledga.app.data.db.DefaultData
import com.ledga.app.data.db.entity.InsightSeverity
import com.ledga.app.data.db.entity.InsightType
import com.ledga.app.data.db.entity.TransactionEntity
import com.ledga.app.data.insights.InsightCandidate
import com.ledga.app.data.insights.InsightRule
import com.ledga.app.data.insights.RuleContext
import com.ledga.app.data.insights.rules.AnomalyRule
import com.ledga.app.data.insights.rules.FeeTipRule
import com.ledga.app.data.insights.rules.FulizaAutoPayRule
import com.ledga.app.data.insights.rules.FulizaRule
import com.ledga.app.data.insights.rules.PositiveNudgeRule
import com.ledga.app.data.insights.rules.RecurringRule
import com.ledga.app.data.parser.MpesaSmsParser
import com.ledga.app.data.parser.ParseResult
import com.ledga.app.data.parser.TransactionDirection
import com.ledga.app.data.parser.TransactionType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Sweeps the 5 insight rules forward through real exported history one
 * week at a time, recording every distinct insight that would have fired
 * (deduped by naturalKey) along with the week it first appeared.
 *
 * Used to sanity-check the rule thresholds before users hit them live.
 * Skipped on machines without the export. Never asserts — it just prints.
 */
class InsightsDryRunTest {

    private val exportPath = File(System.getProperty("user.home"), "Documents/ledga-export/data.json")

    @Serializable
    private data class ExportRoot(val transactions: List<Exported>)

    @Serializable
    private data class Exported(
        val transactionCode: String,
        val type: String,
        val amount: Double,
        val transactionCost: Double = 0.0,
        val recipientName: String? = null,
        val recipientPhone: String? = null,
        val accountNumber: String? = null,
        val destinationCountry: String? = null,
        val balance: Double = 0.0,
        val direction: String,
        val categoryId: Long? = null,
        val fulizaAmount: Double? = null,
        val fulizaOutstanding: Double? = null,
        val reversedTransactionCode: String? = null,
        val rawSms: String = "",
        val timestamp: Long,
    )

    private val rules: List<InsightRule> = listOf(
        AnomalyRule(),
        RecurringRule(),
        FeeTipRule(),
        FulizaRule(),
        FulizaAutoPayRule(),
        PositiveNudgeRule(),
    )

    @Test
    fun `insights dry-run over exported history`() {
        assumeTrue("Skipping: export not present at ${exportPath.absolutePath}", exportPath.exists())

        val json = Json { ignoreUnknownKeys = true }
        val txns = json.decodeFromString<ExportRoot>(exportPath.readText())
            .transactions
            .mapNotNull { it.toEntityOrNull() }
            .sortedBy { it.timestamp }

        require(txns.isNotEmpty()) { "Export contained no transactions" }

        val categoriesById = DefaultData.DEFAULT_CATEGORIES.associateBy { it.id }

        // Walk forward in 7-day steps from the first transaction's week to today.
        val firstTs = txns.first().timestamp
        val lastTs = txns.last().timestamp
        val startWeek = startOfWeek(firstTs)
        val endWeek = startOfWeek(System.currentTimeMillis())

        val firstFired = mutableMapOf<String, FiredInsight>()
        val firesPerWeek = mutableMapOf<String, Int>()
        var weekCount = 0

        var weekStart = startWeek
        while (weekStart <= endWeek) {
            weekCount++
            val now = weekStart + WEEK_MS - 1
            val ninetyDaysAgo = now - 90L * DAY_MS
            val window = txns.asSequence()
                .filter { it.timestamp in ninetyDaysAgo..now }
                .sortedByDescending { it.timestamp }
                .toList()

            val ctx = RuleContext(now = now, transactions = window, categoriesById = categoriesById)
            val firedThisWeek = mutableListOf<InsightCandidate>()
            rules.forEach { rule ->
                runCatching { firedThisWeek.addAll(rule.evaluate(ctx)) }
            }

            firedThisWeek.forEach { c ->
                if (firstFired.putIfAbsent(c.naturalKey, FiredInsight(c, weekStart)) == null) {
                    val week = weekKey(weekStart)
                    firesPerWeek.merge(week, 1) { a, _ -> a + 1 }
                }
            }
            weekStart += WEEK_MS
        }

        printReport(
            txnCount = txns.size,
            weekCount = weekCount,
            firstFired = firstFired,
            firesPerWeek = firesPerWeek,
            firstTs = firstTs,
            lastTs = lastTs,
        )
    }

    private fun printReport(
        txnCount: Int,
        weekCount: Int,
        firstFired: Map<String, FiredInsight>,
        firesPerWeek: Map<String, Int>,
        firstTs: Long,
        lastTs: Long,
    ) {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        println("--- Insights dry-run ---")
        println("Transactions    : $txnCount  (${fmt.format(Date(firstTs))} → ${fmt.format(Date(lastTs))})")
        println("Weeks swept     : $weekCount")
        println("Unique insights : ${firstFired.size}")

        val byType = firstFired.values.groupingBy { it.candidate.type }.eachCount()
        println()
        println("By type:")
        InsightType.entries.forEach { type ->
            println("  %-18s %d".format(type.name, byType[type] ?: 0))
        }

        val bySeverity = firstFired.values.groupingBy { it.candidate.severity }.eachCount()
        println()
        println("By severity:")
        InsightSeverity.entries.forEach { sev ->
            println("  %-8s %d".format(sev.name, bySeverity[sev] ?: 0))
        }

        // Noisiness signal — average fires per active week.
        val activeWeeks = firesPerWeek.values.count { it > 0 }
        val avg = if (activeWeeks > 0) firstFired.size.toDouble() / activeWeeks else 0.0
        println()
        println("Active weeks    : $activeWeeks of $weekCount")
        println("Avg per active  : %.2f".format(avg))
        val noisyWeeks = firesPerWeek.entries.sortedByDescending { it.value }.take(5)
        println("Noisiest weeks  :")
        noisyWeeks.forEach { (week, count) ->
            println("  $week  →  $count insights")
        }

        // Sample headlines per type so we can sanity-check the copy.
        println()
        println("Sample headlines (first fire per type):")
        InsightType.entries.forEach { type ->
            val sample = firstFired.values
                .filter { it.candidate.type == type }
                .minByOrNull { it.weekStart }
            if (sample != null) {
                val date = fmt.format(Date(sample.weekStart))
                println("  [${type.name}]  $date")
                println("    ${sample.candidate.headline}")
                sample.candidate.body?.let { println("    └─ $it") }
            }
        }
    }

    // ---- Conversion ----

    /**
     * Re-parse each rawSms through the CURRENT parser before evaluating
     * rules. The export was created before recent parser fixes (Fuliza
     * auto-pay, withdraw alt format, etc.), so trusting the stored `type`
     * would under-count rule fires. Re-parsing simulates what a user sees
     * after tapping "Reparse unknown messages".
     */
    private fun Exported.toEntityOrNull(): TransactionEntity? {
        if (rawSms.isNotBlank()) {
            val parsed = MpesaSmsParser.parse(rawSms, timestamp)
            if (parsed is ParseResult.Success) {
                val p = parsed.transaction
                return TransactionEntity(
                    transactionCode = p.transactionCode,
                    type = p.type,
                    amount = p.amount,
                    transactionCost = p.transactionCost,
                    recipientName = p.recipientName,
                    recipientPhone = p.recipientPhone,
                    accountNumber = p.accountNumber,
                    destinationCountry = p.destinationCountry,
                    balance = p.balance,
                    direction = p.direction,
                    categoryId = categoryId, // category isn't re-derived; keep export's
                    fulizaAmount = p.fulizaAmount,
                    fulizaOutstanding = p.fulizaOutstanding,
                    reversedTransactionCode = p.reversedTransactionCode,
                    rawSms = p.rawSms,
                    timestamp = p.timestamp,
                )
            }
        }
        // Fallback to the export's stored fields if reparsing wasn't possible.
        return try {
            TransactionEntity(
                transactionCode = transactionCode,
                type = TransactionType.valueOf(type),
                amount = amount,
                transactionCost = transactionCost,
                recipientName = recipientName,
                recipientPhone = recipientPhone,
                accountNumber = accountNumber,
                destinationCountry = destinationCountry,
                balance = balance,
                direction = TransactionDirection.valueOf(direction),
                categoryId = categoryId,
                fulizaAmount = fulizaAmount,
                fulizaOutstanding = fulizaOutstanding,
                reversedTransactionCode = reversedTransactionCode,
                rawSms = rawSms,
                timestamp = timestamp,
            )
        } catch (_: Exception) {
            null
        }
    }

    private data class FiredInsight(val candidate: InsightCandidate, val weekStart: Long)

    private fun startOfWeek(ts: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ts
        val dow = ((get(Calendar.DAY_OF_WEEK) + 5) % 7) // Mon = 0
        add(Calendar.DAY_OF_YEAR, -dow)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun weekKey(weekStart: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = weekStart }
        return "%d-W%02d".format(cal.weekYear, cal.get(Calendar.WEEK_OF_YEAR))
    }

    companion object {
        private const val DAY_MS = 86_400_000L
        private const val WEEK_MS = 7L * DAY_MS
    }
}
