package com.ledga.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelTest {

    @Test
    fun `default flows follow the spec table`() {
        val expected = mapOf(
            TxKind.SEND to FlowKind.SPEND, TxKind.PAYBILL to FlowKind.SPEND,
            TxKind.BUY_GOODS to FlowKind.SPEND, TxKind.WITHDRAW_AGENT to FlowKind.SPEND,
            TxKind.WITHDRAW_ATM to FlowKind.SPEND, TxKind.AIRTIME_SELF to FlowKind.SPEND,
            TxKind.AIRTIME_OTHER to FlowKind.SPEND, TxKind.GLOBAL_SEND to FlowKind.SPEND,
            TxKind.FULIZA_ONLY to FlowKind.SPEND,
            TxKind.RECEIVE to FlowKind.INCOME, TxKind.GLOBAL_RECEIVE to FlowKind.INCOME,
            TxKind.DEPOSIT to FlowKind.INCOME,
            TxKind.SAVINGS_OUT to FlowKind.SAVINGS_OUT, TxKind.SAVINGS_IN to FlowKind.SAVINGS_IN,
            TxKind.FULIZA_REPAY_AUTO to FlowKind.LOAN_REPAY, TxKind.FULIZA_REPAY_MANUAL to FlowKind.LOAN_REPAY,
            TxKind.REVERSAL to FlowKind.REVERSAL_IN, TxKind.FULIZA_REVERSAL to FlowKind.REVERSAL_IN,
        )
        assertEquals(TxKind.entries.toSet(), expected.keys)
        expected.forEach { (kind, flow) -> assertEquals(flow, kind.defaultFlow, "$kind") }
    }

    @Test
    fun `only sends and receipts can become own-account movements`() {
        val out = setOf(TxKind.SEND, TxKind.PAYBILL, TxKind.BUY_GOODS, TxKind.GLOBAL_SEND)
        val inn = setOf(TxKind.RECEIVE, TxKind.GLOBAL_RECEIVE)
        TxKind.entries.forEach { k ->
            when (k) {
                in out -> assertEquals(FlowKind.OWN_OUT, k.ownAccountFlow)
                in inn -> assertEquals(FlowKind.OWN_IN, k.ownAccountFlow)
                else -> assertNull(k.ownAccountFlow, "$k")
            }
        }
    }

    @Test
    fun `groups accept only compatible flows`() {
        listOf(CategoryGroup.BILLS_UTILITIES, CategoryGroup.CAR, CategoryGroup.EVERYDAY, CategoryGroup.MONEY)
            .forEach { g -> FlowKind.entries.forEach { f -> assertEquals(f == FlowKind.SPEND, g.accepts(f), "$g/$f") } }
        FlowKind.entries.forEach { f -> assertEquals(f == FlowKind.INCOME, CategoryGroup.MONEY_IN.accepts(f), "$f") }
        FlowKind.entries.forEach { f ->
            assertEquals(f != FlowKind.SPEND && f != FlowKind.INCOME, CategoryGroup.NOT_SPENDING.accepts(f), "$f")
        }
    }

    @Test
    fun `seed has 27 categories with unique keys, unique icons, ordered`() {
        val seed = Categories.SEED
        assertEquals(27, seed.size)
        assertEquals(seed.size, seed.map { it.key }.toSet().size)
        assertEquals(seed.size, seed.map { it.icon3d }.toSet().size, "each 3D icon used once")
        assertEquals(seed.indices.toList(), seed.map { it.sortOrder })
        seed.forEach { assertTrue(it.icon3d.matches(Regex("fluent_[a-z_]+")), it.icon3d) }
        assertEquals(
            setOf(Categories.ELECTRICITY, Categories.WATER, Categories.FUEL, Categories.CAR_SERVICE),
            seed.filter { it.tracked }.map { it.key }.toSet(),
        )
        assertEquals(CategoryGroup.CAR, Categories.seed(Categories.FUEL)!!.group)
        assertNull(Categories.seed("nope"))
    }

    @Test
    fun `every kind default exists and its group accepts the kind's default flow`() {
        TxKind.entries.forEach { k ->
            val seed = assertNotNull(Categories.seed(KindDefaults.categoryFor(k)), "$k")
            assertTrue(seed.group.accepts(k.defaultFlow), "$k → ${seed.key} (${seed.group}) must accept ${k.defaultFlow}")
        }
        assertEquals(Categories.SENT_TO_PEOPLE, KindDefaults.categoryFor(TxKind.SEND))
        assertEquals(Categories.OTHER, KindDefaults.categoryFor(TxKind.PAYBILL))
        assertEquals(Categories.OTHER, KindDefaults.categoryFor(TxKind.FULIZA_ONLY))
        assertEquals(Categories.RECEIVED, KindDefaults.categoryFor(TxKind.GLOBAL_RECEIVE))
        assertEquals(Categories.FULIZA, KindDefaults.categoryFor(TxKind.FULIZA_REPAY_AUTO))
        val own = Categories.seed(Categories.OWN_ACCOUNTS)!!
        assertTrue(own.group.accepts(FlowKind.OWN_OUT) && own.group.accepts(FlowKind.OWN_IN))
    }

    @Test
    fun `system rules are normalised, unique and target spending categories`() {
        val rules = SystemRules.SEED
        assertTrue(rules.size >= 50)
        assertEquals(rules.size, rules.map { it.pattern }.toSet().size, "duplicate pattern")
        rules.forEach { r ->
            assertEquals(r.pattern.trim().uppercase().replace(Regex("\\s+"), " "), r.pattern, "not normalised: ${r.pattern}")
            val target = assertNotNull(Categories.seed(r.categoryKey), "unknown target ${r.categoryKey}")
            assertTrue(target.group.accepts(FlowKind.SPEND), "${r.pattern} → ${r.categoryKey} is not a spending category")
        }
        assertFalse(rules.any { it.pattern == "BRANCH" }, "bare BRANCH would match shop branches")
        assertTrue(rules.any { it.pattern == "KPLC" && it.categoryKey == Categories.ELECTRICITY })
    }
}
