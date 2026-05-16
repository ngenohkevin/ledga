package com.ledga.app.insights

import com.ledga.app.data.db.entity.Category
import com.ledga.app.data.db.entity.InsightSeverity
import com.ledga.app.data.db.entity.InsightType
import com.ledga.app.data.db.entity.TransactionEntity
import com.ledga.app.data.insights.RuleContext
import com.ledga.app.data.insights.rules.AnomalyRule
import com.ledga.app.data.insights.rules.FeeTipRule
import com.ledga.app.data.insights.rules.FulizaAutoPayRule
import com.ledga.app.data.insights.rules.FulizaRule
import com.ledga.app.data.insights.rules.PositiveNudgeRule
import com.ledga.app.data.insights.rules.RecurringRule
import com.ledga.app.data.parser.TransactionDirection
import com.ledga.app.data.parser.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class InsightRulesTest {

    // ---- Test fixtures --------------------------------------------------

    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.APRIL, 15, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val groceries = Category(id = 1, name = "Groceries", icon = "shopping_cart", color = "#4CAF50")
    private val transport = Category(id = 2, name = "Transport", icon = "directions_car", color = "#2196F3")
    private val bills = Category(id = 3, name = "Bills", icon = "receipt", color = "#FF9800")

    private val categories = mapOf(
        groceries.id to groceries,
        transport.id to transport,
        bills.id to bills,
    )

    private fun txn(
        amount: Double,
        daysAgo: Int = 0,
        categoryId: Long? = null,
        direction: TransactionDirection = TransactionDirection.OUTFLOW,
        type: TransactionType = TransactionType.BUY_GOODS,
        recipientName: String? = null,
        transactionCost: Double = 0.0,
        fulizaOutstanding: Double? = null,
    ): TransactionEntity = TransactionEntity(
        id = 0,
        transactionCode = "T${System.nanoTime()}",
        type = type,
        amount = amount,
        transactionCost = transactionCost,
        recipientName = recipientName,
        recipientPhone = null,
        accountNumber = null,
        destinationCountry = null,
        balance = 0.0,
        direction = direction,
        categoryId = categoryId,
        fulizaAmount = null,
        fulizaOutstanding = fulizaOutstanding,
        reversedTransactionCode = null,
        rawSms = "",
        timestamp = now - daysAgo * 86_400_000L,
    )

    private fun ctx(vararg t: TransactionEntity) =
        RuleContext(now = now, transactions = t.toList(), categoriesById = categories)

    // ---- AnomalyRule ----------------------------------------------------

    @Test
    fun `anomaly rule fires when this week spikes vs rolling baseline`() {
        // Baseline: ~2000/week on transport for last 4 weeks; this week 6000.
        val baseline = (0..3).flatMap { weekOffset ->
            listOf(txn(amount = 2000.0, daysAgo = 7 + weekOffset * 7, categoryId = transport.id))
        }
        val thisWeek = listOf(
            txn(amount = 3000.0, daysAgo = 1, categoryId = transport.id),
            txn(amount = 3000.0, daysAgo = 2, categoryId = transport.id),
        )
        val rule = AnomalyRule()
        val result = rule.evaluate(ctx(*(baseline + thisWeek).toTypedArray()))

        assertEquals(1, result.size)
        assertEquals(InsightType.ANOMALY, result[0].type)
        assertEquals(InsightSeverity.WARN, result[0].severity)
        assertTrue(
            "headline should mention category transport, got: ${result[0].headline}",
            result[0].headline.contains("transport", ignoreCase = true)
        )
        assertTrue(
            "naturalKey should embed category and week",
            result[0].naturalKey.startsWith("anomaly:${transport.id}:")
        )
    }

    @Test
    fun `anomaly rule does not fire on small categories`() {
        // Below MIN_THRESHOLD even with a big delta.
        val baseline = (0..3).flatMap { weekOffset ->
            listOf(txn(amount = 50.0, daysAgo = 7 + weekOffset * 7, categoryId = groceries.id))
        }
        val thisWeek = listOf(txn(amount = 500.0, daysAgo = 1, categoryId = groceries.id))
        val rule = AnomalyRule()
        assertEquals(0, rule.evaluate(ctx(*(baseline + thisWeek).toTypedArray())).size)
    }

    // ---- RecurringRule --------------------------------------------------

    @Test
    fun `recurring rule detects roughly-monthly merchant`() {
        // KPLC bill 3 months ago, 2 months ago, 1 month ago — ~30 day intervals.
        val txns = listOf(
            txn(amount = 2400.0, daysAgo = 1, recipientName = "KPLC"),
            txn(amount = 2500.0, daysAgo = 32, recipientName = "KPLC"),
            txn(amount = 2400.0, daysAgo = 62, recipientName = "KPLC"),
        )
        val rule = RecurringRule()
        val result = rule.evaluate(ctx(*txns.toTypedArray()))

        assertEquals(1, result.size)
        assertEquals(InsightType.RECURRING, result[0].type)
        assertTrue(result[0].headline.contains("Kplc", ignoreCase = true))
    }

    @Test
    fun `recurring rule ignores weekly merchants`() {
        // Naivas weekly trips — not a monthly bill.
        val txns = (1..5).map {
            txn(amount = 1200.0, daysAgo = it * 7, recipientName = "NAIVAS")
        }
        val rule = RecurringRule()
        assertEquals(0, rule.evaluate(ctx(*txns.toTypedArray())).size)
    }

    // ---- FeeTipRule -----------------------------------------------------

    @Test
    fun `fee tip fires when monthly fees exceed threshold`() {
        val txns = listOf(
            txn(amount = 5000.0, daysAgo = 2, transactionCost = 150.0, type = TransactionType.WITHDRAW_AGENT),
            txn(amount = 3000.0, daysAgo = 5, transactionCost = 100.0, type = TransactionType.WITHDRAW_AGENT),
        )
        val rule = FeeTipRule()
        val result = rule.evaluate(ctx(*txns.toTypedArray()))

        assertEquals(1, result.size)
        assertEquals(InsightType.FEE_TIP, result[0].type)
        assertTrue(result[0].headline.contains("250"))
    }

    @Test
    fun `fee tip stays silent below threshold`() {
        val txns = listOf(
            txn(amount = 500.0, daysAgo = 2, transactionCost = 30.0, type = TransactionType.WITHDRAW_AGENT),
        )
        assertEquals(0, FeeTipRule().evaluate(ctx(*txns.toTypedArray())).size)
    }

    // ---- FulizaRule -----------------------------------------------------

    @Test
    fun `fuliza rule fires when latest outstanding is positive`() {
        val txns = listOf(
            txn(amount = 500.0, daysAgo = 1, fulizaOutstanding = 500.0),
            txn(amount = 200.0, daysAgo = 2),
        )
        val result = FulizaRule().evaluate(ctx(*txns.toTypedArray()))
        assertEquals(1, result.size)
        assertEquals(InsightSeverity.ALERT, result[0].severity)
        assertEquals("fuliza:outstanding", result[0].naturalKey)
    }

    @Test
    fun `fuliza rule silent when outstanding cleared`() {
        val txns = listOf(
            txn(amount = 500.0, daysAgo = 1, fulizaOutstanding = 0.0),
        )
        assertEquals(0, FulizaRule().evaluate(ctx(*txns.toTypedArray())).size)
    }

    // ---- FulizaAutoPayRule ----

    @Test
    fun `fuliza auto-pay fires for full clear with positive nudge`() {
        val received = txn(
            amount = 1000.0,
            daysAgo = 0,
            type = TransactionType.RECEIVED,
            direction = TransactionDirection.INFLOW,
            recipientName = "JANE DOE",
        )
        // Auto-pay must be slightly later than the RECEIVED for the pairing logic.
        val autoPay = txn(
            amount = 800.0,
            daysAgo = 0,
            type = TransactionType.FULIZA_AUTO_PAY,
            recipientName = "Fuliza overdraft (auto-cleared)",
        )
        val rule = FulizaAutoPayRule()
        val result = rule.evaluate(ctx(received, autoPay))

        assertEquals(1, result.size)
        assertEquals(InsightType.FULIZA_AUTO_PAY, result[0].type)
        assertEquals(InsightSeverity.NUDGE, result[0].severity)
        assertTrue(
            "headline should mention 800, got: ${result[0].headline}",
            result[0].headline.contains("800")
        )
        assertTrue(
            "headline should say 'cleared', got: ${result[0].headline}",
            result[0].headline.contains("cleared", ignoreCase = true)
        )
    }

    @Test
    fun `fuliza auto-pay fires for partial as INFO`() {
        val autoPay = txn(
            amount = 200.0,
            daysAgo = 0,
            type = TransactionType.FULIZA_AUTO_PAY,
            recipientName = "Fuliza overdraft (auto-partial)",
        )
        val result = FulizaAutoPayRule().evaluate(ctx(autoPay))
        assertEquals(1, result.size)
        assertEquals(InsightSeverity.INFO, result[0].severity)
        assertTrue(result[0].headline.contains("toward your Fuliza"))
    }

    @Test
    fun `fuliza auto-pay ignores events older than 7 days`() {
        val old = txn(
            amount = 500.0,
            daysAgo = 10,
            type = TransactionType.FULIZA_AUTO_PAY,
            recipientName = "Fuliza overdraft (auto-cleared)",
        )
        assertEquals(0, FulizaAutoPayRule().evaluate(ctx(old)).size)
    }

    @Test
    fun `fuliza auto-pay naturalKey is per-transaction-code`() {
        val a = txn(amount = 100.0, daysAgo = 0, type = TransactionType.FULIZA_AUTO_PAY,
            recipientName = "Fuliza overdraft (auto-partial)")
        val b = txn(amount = 100.0, daysAgo = 1, type = TransactionType.FULIZA_AUTO_PAY,
            recipientName = "Fuliza overdraft (auto-partial)")
        val keys = FulizaAutoPayRule().evaluate(ctx(a, b)).map { it.naturalKey }.toSet()
        assertEquals(2, keys.size) // distinct keys keep both visible in history
        assertTrue(keys.all { it.startsWith("fuliza_auto_pay:") })
    }

    // ---- PositiveNudgeRule ----------------------------------------------

    @Test
    fun `positive nudge fires when category drops significantly`() {
        // Anchor 15 Apr — last month (Mar) had 5000 on groceries, this month (Apr 1-15) only 1000.
        val lastMonth = txn(amount = 5000.0, daysAgo = 30, categoryId = groceries.id)
        val thisMonth = txn(amount = 1000.0, daysAgo = 2, categoryId = groceries.id)
        val rule = PositiveNudgeRule()
        val result = rule.evaluate(ctx(lastMonth, thisMonth))

        assertEquals(1, result.size)
        assertEquals(InsightSeverity.NUDGE, result[0].severity)
        assertTrue(result[0].headline.contains("Groceries"))
    }

    @Test
    fun `positive nudge stays silent on small baselines`() {
        val lastMonth = txn(amount = 200.0, daysAgo = 30, categoryId = groceries.id)
        val thisMonth = txn(amount = 50.0, daysAgo = 2, categoryId = groceries.id)
        assertEquals(0, PositiveNudgeRule().evaluate(ctx(lastMonth, thisMonth)).size)
    }
}
