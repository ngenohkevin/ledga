package com.ledga.app.goals

import com.ledga.app.data.db.entity.ContributionRule
import com.ledga.app.data.parser.TransactionType
import com.ledga.app.data.repository.GoalsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributionRuleTest {

    // ---- Encoding round-trip ----

    @Test
    fun `AllSavingsDeposits encodes and decodes`() {
        val encoded = ContributionRule.AllSavingsDeposits.encode()
        assertEquals(ContributionRule.AllSavingsDeposits, ContributionRule.decode(encoded))
    }

    @Test
    fun `ToRecipient round-trips with fragment intact`() {
        val rule = ContributionRule.ToRecipient("School fees")
        val decoded = ContributionRule.decode(rule.encode())
        assertEquals(rule, decoded)
    }

    @Test
    fun `Manual encodes and decodes`() {
        val encoded = ContributionRule.Manual.encode()
        assertEquals(ContributionRule.Manual, ContributionRule.decode(encoded))
    }

    @Test
    fun `decode of unknown string falls back to Manual safely`() {
        val decoded = ContributionRule.decode("SOMETHING_FROM_THE_FUTURE")
        assertEquals(ContributionRule.Manual, decoded)
    }

    @Test
    fun `ToRecipient handles fragments containing colons`() {
        // Important: the encoding uses ':' as separator. Anything after the
        // first colon should be treated as the recipient fragment, even if it
        // contains more colons.
        val tricky = ContributionRule.ToRecipient("KPLC: Prepaid")
        val decoded = ContributionRule.decode(tricky.encode())
        assertEquals(tricky, decoded)
    }

    // ---- Static matches() — what auto-attribution will use ----

    @Test
    fun `AllSavings matches MSHWARI and KCB_MPESA only`() {
        val rule = ContributionRule.AllSavingsDeposits
        assertTrue(GoalsRepository.matches(rule, TransactionType.MSHWARI, recipient = null))
        assertTrue(GoalsRepository.matches(rule, TransactionType.KCB_MPESA, recipient = "anything"))
        assertFalse(GoalsRepository.matches(rule, TransactionType.SEND, recipient = "Bob"))
        assertFalse(GoalsRepository.matches(rule, TransactionType.PAY_BILL, recipient = "KPLC"))
    }

    @Test
    fun `ToRecipient matches case-insensitively on substring`() {
        val rule = ContributionRule.ToRecipient("KPLC")
        assertTrue(GoalsRepository.matches(rule, TransactionType.PAY_BILL, recipient = "KPLC PREPAID"))
        assertTrue(GoalsRepository.matches(rule, TransactionType.PAY_BILL, recipient = "kplc postpaid"))
        assertTrue(GoalsRepository.matches(rule, TransactionType.SEND, recipient = "KPLC AGENT"))
        assertFalse(GoalsRepository.matches(rule, TransactionType.SEND, recipient = "Nairobi Water"))
        assertFalse(GoalsRepository.matches(rule, TransactionType.SEND, recipient = null))
    }

    @Test
    fun `Manual never auto-matches`() {
        val rule = ContributionRule.Manual
        assertFalse(GoalsRepository.matches(rule, TransactionType.MSHWARI, recipient = null))
        assertFalse(GoalsRepository.matches(rule, TransactionType.SEND, recipient = "Anyone"))
        assertFalse(GoalsRepository.matches(rule, TransactionType.PAY_BILL, recipient = "KPLC"))
    }
}
