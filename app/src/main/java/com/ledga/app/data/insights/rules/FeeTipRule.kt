package com.ledga.app.data.insights.rules

import com.ledga.app.data.db.entity.InsightSeverity
import com.ledga.app.data.db.entity.InsightType
import com.ledga.app.data.insights.InsightCandidate
import com.ledga.app.data.insights.InsightRule
import com.ledga.app.data.insights.RuleContext
import com.ledga.app.data.parser.TransactionType
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Fee-tip insight.
 *
 * If total transaction costs in the **current calendar month** exceed
 * [MIN_FEES_KSH], surface a tip pointing out where the fees came from.
 * The dominant fee source drives the copy.
 *
 * Natural key: `fees:{yyyy-MM}` — one per month, refreshes as the running
 * total climbs through the month.
 */
class FeeTipRule @Inject constructor() : InsightRule {

    override fun evaluate(ctx: RuleContext): List<InsightCandidate> {
        val monthStart = startOfMonth(ctx.now)
        val monthTxns = ctx.transactions.filter { it.timestamp >= monthStart }
        val totalFees = monthTxns.sumOf { it.transactionCost }
        if (totalFees < MIN_FEES_KSH) return emptyList()

        val byType = monthTxns
            .filter { it.transactionCost > 0 }
            .groupBy { it.type }
            .mapValues { (_, txns) -> txns.sumOf { it.transactionCost } }

        val dominant = byType.maxByOrNull { it.value }
        val dominantSource = dominant?.key?.let(::sourceLabel) ?: "transactions"
        val dominantAmount = dominant?.value ?: 0.0

        return listOf(
            InsightCandidate(
                naturalKey = "fees:${monthKey(ctx.now)}",
                type = InsightType.FEE_TIP,
                severity = InsightSeverity.INFO,
                typeLabel = "FEE TIP",
                headline = "You paid Ksh ${totalFees.roundToInt()} in fees this month.",
                body = "Most came from $dominantSource (Ksh ${dominantAmount.roundToInt()}). " +
                        "Fewer, larger withdrawals could trim this.",
                ctaLabel = null,
            )
        )
    }

    private fun sourceLabel(type: TransactionType): String = when (type) {
        TransactionType.WITHDRAW_AGENT -> "agent withdrawals"
        TransactionType.WITHDRAW_ATM -> "ATM withdrawals"
        TransactionType.SEND -> "person-to-person sends"
        TransactionType.PAY_BILL -> "paybill payments"
        TransactionType.BUY_GOODS -> "till payments"
        TransactionType.MPESA_GLOBAL -> "international transfers"
        else -> type.name.lowercase().replace('_', ' ')
    }

    private fun startOfMonth(nowMs: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun monthKey(nowMs: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        return "%d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    companion object {
        const val MIN_FEES_KSH = 200.0
    }
}
