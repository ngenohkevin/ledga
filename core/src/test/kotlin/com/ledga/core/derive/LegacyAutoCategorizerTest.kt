package com.ledga.core.derive

import com.ledga.core.derive.LegacyAutoCategorizer.DEFAULT_RULES
import com.ledga.core.derive.LegacyAutoCategorizer.LegacyRule
import com.ledga.core.model.Categories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegacyAutoCategorizerTest {
    private fun cat(type: String, name: String?, account: String? = null, rules: List<LegacyRule> = DEFAULT_RULES) =
        LegacyAutoCategorizer.categorize(type, name, account, rules)

    @Test
    fun `default rules are v1's twenty, in order`() {
        assertEquals((1L..20L).toList(), DEFAULT_RULES.map { it.id })
        assertEquals(LegacyRule(1, 1, "RECIPIENT_NAME", "NAIVAS"), DEFAULT_RULES.first())
        assertEquals(LegacyRule(10, 3, "PAYBILL", "888880"), DEFAULT_RULES[9])
        assertEquals(LegacyRule(20, 5, "RECIPIENT_NAME", "PIZZA INN"), DEFAULT_RULES.last())
    }

    @Test
    fun `type defaults and rule matching reproduce v1, quirks included`() {
        assertEquals(6, cat("SEND", "JANE TESTER"))
        assertEquals(1, cat("BUY_GOODS", "SAMPLE NAIVAS OUTLET"))      // substring, not whole word
        assertEquals(2, cat("BUY_GOODS", "LITTLE SHOP"))               // v1 quirk: LITTLE ⊂ LITTLE SHOP
        assertEquals(3, cat("PAY_BILL", "SAMPLE POWER", "888880"))     // paybill rule vs customer account
        assertEquals(13, cat("PAY_BILL", null, "888880"))              // no name → no rules at all
        assertEquals(13, cat("BUY_GOODS", "SAMPLE STORES"))
        assertEquals(12, cat("FULIZA", null))
        assertEquals(12, cat("KCB_MPESA", "KCB M-Pesa"))
        assertEquals(11, cat("MPESA_GLOBAL", null))
        assertEquals(13, cat("UNKNOWN", null))
        assertEquals(13, cat("SOMETHING_NEW", null))
        assertNull(LegacyAutoCategorizer.typeDefault("BUY_GOODS"))
    }

    @Test
    fun `own-account rules come after the defaults in id order`() {
        val withOwn = DEFAULT_RULES + LegacyRule(21, 14, "RECIPIENT_NAME", "EXAMPLE BANK")
        assertEquals(14, cat("RECEIVED", "EXAMPLE BANK LIMITED", rules = withOwn))
        assertEquals(1, cat("SEND", "NAIVAS EXAMPLE BANK", rules = withOwn)) // rule 1 wins over rule 21
        assertEquals(14, cat("RECEIVED", "Example Bank Limited", rules = withOwn.shuffled()))
    }

    @Test
    fun `user choices are told apart from parser drift`() {
        fun choice(stored: Long?, type: String, name: String?, account: String? = null) =
            LegacyAutoCategorizer.isUserChoice(stored, type, name, account, DEFAULT_RULES)
        // Drift seen in the real export (synthetic names):
        assertFalse(choice(13, "SEND", "HUSTLER FUND"), "was UNKNOWN → Other before a v1 parser fix")
        assertFalse(choice(13, "DEPOSIT", "SAMPLE AGENCIES"), "was UNKNOWN → Other before a v1 parser fix")
        assertFalse(choice(6, "SEND", "KPLC PREPAID"), "name re-parsed after insert; stored the type default")
        // Genuine choices:
        assertTrue(choice(9, "RECEIVED", "EXAMPLE BANK LIMITED"))
        assertTrue(choice(9, "SEND", "SAMPLE BANK PAYBILL"))
        assertTrue(choice(14, "SEND", "NAIVAS"), "marked own account although a default rule matches")
        // No disagreement / nothing stored:
        assertFalse(choice(6, "SEND", "JANE TESTER"))
        assertFalse(choice(null, "SEND", "JANE TESTER"))
    }

    @Test
    fun `legacy ids map to v2 categories`() {
        assertEquals(LegacyMapping.ToCategory(Categories.GROCERIES), LegacyCategoryMap.map(1))
        assertEquals(LegacyMapping.ToCategory(Categories.OTHER), LegacyCategoryMap.map(3))
        assertEquals(LegacyMapping.ToCategory(Categories.SENT_TO_PEOPLE), LegacyCategoryMap.map(6))
        assertEquals(LegacyMapping.ToCategory(Categories.CASH_DEPOSIT), LegacyCategoryMap.map(9))
        assertEquals(LegacyMapping.ToCategory(Categories.SAVINGS), LegacyCategoryMap.map(12))
        assertEquals(LegacyMapping.OwnAccount, LegacyCategoryMap.map(14))
        assertEquals(LegacyMapping.Custom(15), LegacyCategoryMap.map(15))
        (1L..13L).forEach { id ->
            val m = LegacyCategoryMap.map(id)
            assertTrue(m is LegacyMapping.ToCategory && Categories.seed(m.key) != null, "id $id → $m")
        }
    }

    @Test
    fun `a Bills choice yields to any v2 Bills and utilities rule`() {
        assertNull(LegacyCategoryMap.overrideFor(3, Categories.ELECTRICITY))
        assertNull(LegacyCategoryMap.overrideFor(3, Categories.WATER))
        assertNull(LegacyCategoryMap.overrideFor(3, Categories.INTERNET))
        assertNull(LegacyCategoryMap.overrideFor(3, Categories.TV))
        assertNull(LegacyCategoryMap.overrideFor(3, Categories.RENT))
        assertEquals(LegacyMapping.ToCategory(Categories.OTHER), LegacyCategoryMap.overrideFor(3, Categories.FOOD))
        assertEquals(LegacyMapping.ToCategory(Categories.OTHER), LegacyCategoryMap.overrideFor(3, null))
        assertEquals(LegacyMapping.ToCategory(Categories.FOOD), LegacyCategoryMap.overrideFor(5, Categories.ELECTRICITY))
        assertEquals(Categories.FUEL, LegacyCategoryMap.carTag("FUEL"))
        assertEquals(Categories.CAR_SERVICE, LegacyCategoryMap.carTag("SERVICE"))
        assertNull(LegacyCategoryMap.carTag("BOAT"))
    }
}
