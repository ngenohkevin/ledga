package com.ledga.core.derive

import com.ledga.core.model.Categories
import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind
import com.ledga.core.parse.Counterparty
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuleEngineTest {
    private val t0 = Instant.parse("2026-01-01T00:00:00Z")
    private val groupOf: (String) -> com.ledga.core.model.CategoryGroup? = { Categories.seed(it)?.group }
    private fun cp(name: String?, phone: String? = null, account: String? = null) = Counterparty(name, phone, account, null)
    private fun user(id: Long, field: RuleField, pattern: String, category: String? = null, action: RuleAction = RuleAction.SET_CATEGORY, at: Long = 0, enabled: Boolean = true) =
        Rule(id, field, pattern, action, category, RuleOrigin.USER, 0, t0.plusSeconds(at), enabled)
    private val system = RuleEngine.systemRules(t0)

    @Test
    fun `system rules categorise by whole words only`() {
        val e = RuleEngine(system, groupOf)
        assertEquals(Categories.ELECTRICITY, e.categoryFor(cp("KPLC PREPAID"), FlowKind.SPEND))
        assertEquals(Categories.FUEL, e.categoryFor(cp("SHELL SERVICE STATION KAREN"), FlowKind.SPEND))
        assertNull(e.categoryFor(cp("SHELLY BEAUTY PARLOUR"), FlowKind.SPEND))
        assertEquals(Categories.TRANSPORT, e.categoryFor(cp("LITTLE CAB KENYA"), FlowKind.SPEND))
        assertNull(e.categoryFor(cp("LITTLE SHOP"), FlowKind.SPEND))
        assertNull(e.categoryFor(cp("SAMPLE STORES KITENGELA BRANCH"), FlowKind.SPEND))
        assertEquals(Categories.AIRTIME_DATA, e.categoryFor(cp("SAFARICOM DATA BUNDLES"), FlowKind.SPEND))
        assertNull(e.categoryFor(null, FlowKind.SPEND))
    }

    @Test
    fun `rules never recategorise money flowing the other way`() {
        val e = RuleEngine(system + user(100, RuleField.NAME_CONTAINS, "JANE TESTER", Categories.RENT), groupOf)
        assertEquals(Categories.RENT, e.categoryFor(cp("JANE TESTER"), FlowKind.SPEND))
        assertNull(e.categoryFor(cp("JANE TESTER"), FlowKind.INCOME), "a payment rule must not touch receipts")
        assertNull(e.categoryFor(cp("KPLC"), FlowKind.INCOME), "a KPLC refund is not Electricity spending")
        val incomeRule = RuleEngine(listOf(user(101, RuleField.NAME_CONTAINS, "SAMPLE EMPLOYER", Categories.OTHER_INCOME)), groupOf)
        assertEquals(Categories.OTHER_INCOME, incomeRule.categoryFor(cp("SAMPLE EMPLOYER LTD"), FlowKind.INCOME))
        assertNull(incomeRule.categoryFor(cp("SAMPLE EMPLOYER LTD"), FlowKind.SPEND))
    }

    @Test
    fun `user rules beat system rules and the newest user rule wins`() {
        val e = RuleEngine(
            system + user(100, RuleField.NAME_CONTAINS, "KPLC", Categories.WATER, at = 10) +
                user(101, RuleField.NAME_CONTAINS, "KPLC PREPAID", Categories.RENT, at = 20),
            groupOf,
        )
        assertEquals(Categories.RENT, e.categoryFor(cp("KPLC PREPAID"), FlowKind.SPEND))
        assertEquals(Categories.WATER, e.categoryFor(cp("KPLC POSTPAID"), FlowKind.SPEND))
        assertEquals(listOf(101L, 100L), e.ordered.take(2).map { it.id })
    }

    @Test
    fun `account and phone rules, disabled rules and unknown categories`() {
        val e = RuleEngine(
            listOf(
                user(1, RuleField.ACCOUNT_EQUALS, "37100000001", Categories.ELECTRICITY),
                user(2, RuleField.PHONE_EQUALS, "0712345111", Categories.RENT),
                user(3, RuleField.NAME_CONTAINS, "SAMPLE", Categories.FOOD, enabled = false),
                user(4, RuleField.NAME_CONTAINS, "GHOST", "deleted_category"),
            ),
            groupOf,
        )
        assertEquals(Categories.ELECTRICITY, e.categoryFor(cp("SAMPLE POWER", account = " 37100000001 "), FlowKind.SPEND))
        assertNull(e.categoryFor(cp("SAMPLE POWER", account = "37100000002"), FlowKind.SPEND))
        assertEquals(Categories.RENT, e.categoryFor(cp("LANDLORD", phone = "0712***111"), FlowKind.SPEND))
        assertNull(e.categoryFor(cp("SAMPLE STORES"), FlowKind.SPEND), "disabled rule must not match")
        assertNull(e.categoryFor(cp("GHOST SHOP"), FlowKind.SPEND), "rule to an unknown category is skipped")
    }

    @Test
    fun `own-account rules and flow classification`() {
        val e = RuleEngine(listOf(user(1, RuleField.NAME_CONTAINS, "EXAMPLE BANK LIMITED", action = RuleAction.MARK_OWN_ACCOUNT)), groupOf)
        assertTrue(e.isOwnAccount(cp("EXAMPLE BANK LIMITED")))
        assertFalse(e.isOwnAccount(cp("OTHER BANK LIMITED")))
        assertNull(e.categoryFor(cp("EXAMPLE BANK LIMITED"), FlowKind.SPEND), "own-account rules don't set categories")

        assertEquals(FlowKind.OWN_OUT, Classifier.flowOf(TxKind.PAYBILL, ownAccount = true))
        assertEquals(FlowKind.OWN_IN, Classifier.flowOf(TxKind.RECEIVE, ownAccount = true))
        assertEquals(FlowKind.SPEND, Classifier.flowOf(TxKind.WITHDRAW_AGENT, ownAccount = true), "cash is never own-account")
        assertEquals(FlowKind.SPEND, Classifier.flowOf(TxKind.SEND, ownAccount = false))
        assertEquals(FlowKind.LOAN_REPAY, Classifier.flowOf(TxKind.FULIZA_REPAY_AUTO, ownAccount = false))
    }

    @Test
    fun `system rules are numbered in seed order`() {
        assertEquals((1L..system.size).toList(), system.map { it.id })
        assertTrue(system.all { it.origin == RuleOrigin.SYSTEM && it.field == RuleField.NAME_CONTAINS && it.enabled })
    }

    @Test
    fun `ampersand in a seeded pattern is matched literally`() {
        val e = RuleEngine(system, groupOf)
        assertEquals(Categories.WATER, e.categoryFor(cp("SAMPLE WATER & SEWERAGE CO"), FlowKind.SPEND))
    }

    @Test
    fun `phone rule on a full number does not match a different person sharing its ends`() {
        val e = RuleEngine(listOf(user(1, RuleField.PHONE_EQUALS, "0712345111", Categories.RENT)), groupOf)
        val rule = e.ordered.single()
        assertFalse(e.matches(rule, cp("OTHER", phone = "0712999111")))
        assertTrue(e.matches(rule, cp("JANE", phone = "0712345111")))
        assertTrue(e.matches(rule, cp("JANE", phone = "0712***111")))
        assertTrue(e.matches(rule, cp("JANE", phone = "+254712345111")))
    }

    @Test
    fun `blank or degenerate patterns match nothing`() {
        val names = listOf(cp("SAMPLE WATER & SEWERAGE CO"), cp("B9 - KIOSK"))
        listOf("", "  ", " - ").forEach { p ->
            val e = RuleEngine(listOf(user(1, RuleField.NAME_CONTAINS, p, Categories.RENT)), groupOf)
            names.forEach { assertFalse(e.matches(e.ordered.single(), it), "pattern '$p'") }
        }
        val acc = RuleEngine(listOf(user(2, RuleField.ACCOUNT_EQUALS, "  ", Categories.RENT)), groupOf)
        assertFalse(acc.matches(acc.ordered.single(), cp("X", account = "")))
        assertFalse(acc.matches(acc.ordered.single(), cp("X", account = " ")))
        val ph = RuleEngine(listOf(user(3, RuleField.PHONE_EQUALS, "123", Categories.RENT)), groupOf)
        assertFalse(ph.matches(ph.ordered.single(), cp("X", phone = "123")))
        assertFalse(ph.matches(ph.ordered.single(), cp("X", phone = "0712345123")))
    }

    @Test
    fun `only this account number is scoped to the business it was set on`() {
        val rule = user(100, RuleField.NAME_AND_ACCOUNT, RuleEngine.nameAndAccount("SAMPLE ACADEMY", "ADM 1024"), Categories.SCHOOL)
        val e = RuleEngine(listOf(rule), groupOf)
        assertEquals(Categories.SCHOOL, e.categoryFor(cp("SAMPLE ACADEMY", account = "ADM 1024"), FlowKind.SPEND))
        assertEquals(Categories.SCHOOL, e.categoryFor(cp("SAMPLE ACADEMY", account = "adm  1024"), FlowKind.SPEND), "case and spacing")
        assertNull(e.categoryFor(cp("SAMPLE ACADEMY", account = "ADM 2048"), FlowKind.SPEND), "another account at the same business")
        assertNull(e.categoryFor(cp("SAMPLE CLINIC", account = "ADM 1024"), FlowKind.SPEND), "the same account number at another business")
        assertNull(e.categoryFor(cp("SAMPLE ACADEMY"), FlowKind.SPEND), "no account at all")
    }

    @Test
    fun `a scoped account pattern without both halves matches nothing`() {
        val broken = listOf(
            "SAMPLE ACADEMY",
            RuleEngine.nameAndAccount("", "ADM 1024"),
            RuleEngine.nameAndAccount("SAMPLE ACADEMY", " "),
        )
        broken.forEachIndexed { i, pattern ->
            val e = RuleEngine(listOf(user(200L + i, RuleField.NAME_AND_ACCOUNT, pattern, Categories.SCHOOL)), groupOf)
            assertNull(e.categoryFor(cp("SAMPLE ACADEMY", account = "ADM 1024"), FlowKind.SPEND), "pattern #$i")
            assertNull(RuleEngine.splitNameAndAccount(pattern), "pattern #$i does not split")
        }
        assertEquals("SAMPLE ACADEMY" to "ADM 1024", RuleEngine.splitNameAndAccount(RuleEngine.nameAndAccount(" SAMPLE  ACADEMY ", "ADM 1024")))
    }
}
