package com.ledga.app.testing

import com.ledga.app.data.room.LedgerRow
import com.ledga.core.derive.LegacyAutoCategorizer
import com.ledga.core.derive.LegacyCategoryMap
import com.ledga.core.derive.LegacyMapping
import com.ledga.core.model.Categories
import com.ledga.core.model.CategoryGroup
import com.ledga.core.model.FlowKind
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToLong

/** One Nairobi month of spending, v1.6's rule against v2's ledger (R160). Cents stay out of anything printed. */
data class MonthCompare(
    val month: YearMonth,
    val v1Cents: Long,
    val v2SpendCents: Long,
    val v2FeeCents: Long,
    /** Payments both count as spending, and how many of them have the same amount. */
    val both: Int,
    val sameAmount: Int,
    /** Payments only v1 counted, by v1 type; only v2 counted, by v2 kind. */
    val v1Only: Map<String, Int>,
    val v2Only: Map<String, Int>,
)

/** How v2 filed the payments v1 filed under one category. */
data class CategoryCompare(val v1Category: String, val total: Int, val agree: Int, val elsewhere: Map<String, Int>)

/** The v1 → v2 upgrade compared payment by payment, keyed by code (R160). */
object UpgradeComparison {
    private val NAIROBI: ZoneId = ZoneId.of("Africa/Nairobi")

    /** v1.6's `TransactionDao.getTotalSpending` leaves these types out. */
    private val NOT_SPENDING = setOf("FULIZA_REPAYMENT", "FULIZA_AUTO_PAY", "MSHWARI", "KCB_MPESA")

    /** v1's default category names, ids 1..14, as labels. */
    private val V1_NAMES = listOf(
        "Groceries", "Transport", "Bills & Utilities", "Airtime & Data", "Food & Dining", "Send Money", "Received",
        "Withdrawal", "Deposit", "Shopping", "International", "Savings & Loans", "Other", "My Accounts",
    )

    /** v1.6's spending rule: outflows, minus those types, transfer categories and payments a reversal names. */
    fun v1Counted(rows: List<V1ExportTx>, transferIds: Set<Long> = setOf(LegacyAutoCategorizer.MY_ACCOUNTS)): List<V1ExportTx> {
        val reversed = rows.mapNotNull { it.reversedCode }.toSet()
        return rows.filter { r ->
            r.direction == "OUTFLOW" && r.type !in NOT_SPENDING && (r.categoryId == null || r.categoryId !in transferIds) && r.code !in reversed
        }
    }

    fun months(v1: List<V1ExportTx>, ledger: List<LedgerRow>): List<MonthCompare> {
        val counted = v1Counted(v1).associateBy { it.code }
        val spends = ledger.filter { it.spendCents > 0 }.associateBy { it.code }
        val months = (counted.values.map { month(it.timestamp) } + ledger.map { month(it.occurredAt) }).toSortedSet()
        return months.map { m ->
            val v1m = counted.values.filter { month(it.timestamp) == m }
            val v2m = ledger.filter { month(it.occurredAt) == m }
            val both = v1m.filter { it.code in spends }
            MonthCompare(
                month = m,
                v1Cents = v1m.sumOf { cents(it.amount) },
                v2SpendCents = v2m.sumOf { it.spendCents },
                v2FeeCents = v2m.sumOf { it.feeCents },
                both = both.size,
                sameAmount = both.count { cents(it.amount) == spends.getValue(it.code).spendCents },
                v1Only = v1m.filter { it.code !in spends }.groupingBy { it.type }.eachCount(),
                v2Only = v2m.filter { it.spendCents > 0 && it.code !in counted }.groupingBy { it.kind.name }.eachCount(),
            )
        }
    }

    fun categories(v1: List<V1ExportTx>, ledger: List<LedgerRow>): List<CategoryCompare> {
        val byCode = ledger.associateBy { it.code }
        return v1.filter { it.categoryId != null && it.code in byCode }.groupBy { it.categoryId!! }.toSortedMap().map { (id, rows) ->
            val off = rows.map { byCode.getValue(it.code) }.filterNot { agrees(id, it) }
            CategoryCompare(label(id), rows.size, rows.size - off.size, off.groupingBy(::where).eachCount())
        }
    }

    /** "+1.3%", "0.0%", or "n/a" when v1 counted nothing that month. */
    fun percent(v1Cents: Long, v2Cents: Long): String =
        if (v1Cents == 0L) "n/a" else "%+.1f%%".format(Locale.ROOT, (v2Cents - v1Cents) * 100.0 / v1Cents)

    private fun agrees(id: Long, row: LedgerRow): Boolean = when (val m = LegacyCategoryMap.map(id)) {
        is LegacyMapping.ToCategory -> row.categoryKey == m.key ||
            (id == LegacyAutoCategorizer.BILLS && Categories.seed(row.categoryKey)?.group == CategoryGroup.BILLS_UTILITIES)
        LegacyMapping.OwnAccount -> row.flow == FlowKind.OWN_OUT || row.flow == FlowKind.OWN_IN
        is LegacyMapping.Custom -> row.categoryKey == "legacy_${m.legacyId}"
    }

    private fun where(row: LedgerRow): String = if (row.flow == FlowKind.OWN_OUT || row.flow == FlowKind.OWN_IN) "own account" else row.categoryKey

    private fun label(id: Long): String = V1_NAMES.getOrNull((id - 1).toInt()) ?: "custom $id"

    private fun month(at: Instant): YearMonth = YearMonth.from(at.atZone(NAIROBI))

    private fun cents(amount: Double): Long = (amount * 100).roundToLong()
}
