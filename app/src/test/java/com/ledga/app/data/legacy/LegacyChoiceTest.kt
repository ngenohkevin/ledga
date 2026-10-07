package com.ledga.app.data.legacy

import com.ledga.core.derive.LegacyAutoCategorizer
import com.ledga.core.derive.LegacyMapping
import com.ledga.core.derive.RuleEngine
import com.ledga.core.model.Categories
import com.ledga.app.testing.Sms
import com.ledga.core.parse.MpesaParser
import com.ledga.core.parse.ParseOutcome
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** Spec §8 step 3 (R1, R6, R123): which v1 category choices become v2 overrides. Synthetic payments. */
class LegacyChoiceTest {
    private val engine = RuleEngine(RuleEngine.systemRules(Instant.EPOCH)) { Categories.seed(it)?.group }
    private val send = (MpesaParser.parse(Sms.SEND, Instant.parse("2026-03-21T10:30:00Z")) as ParseOutcome.Parsed).sms
    private val resolve: (LegacyMapping, Long) -> Pair<String?, Boolean?> = { mapping, _ ->
        when (mapping) {
            is LegacyMapping.ToCategory -> mapping.key to null
            LegacyMapping.OwnAccount -> null to true
            is LegacyMapping.Custom -> null to null
        }
    }

    private fun choice(id: Long?) =
        LegacyChoice.of(id, "SEND", "JANE TESTER", null, LegacyAutoCategorizer.DEFAULT_RULES, send, engine, resolve)

    @Test
    fun `what v1 chose by itself is no choice`() {
        val auto = LegacyAutoCategorizer.categorize("SEND", "JANE TESTER", null, LegacyAutoCategorizer.DEFAULT_RULES)
        assertNull(choice(auto))
        assertNull(choice(null))
    }

    @Test
    fun `a category the person picked in v1 comes across`() {
        assertEquals(Categories.GROCERIES to null, choice(1))
    }

    @Test
    fun `v1's My Accounts is "my own account"`() {
        assertEquals(null to true, choice(LegacyAutoCategorizer.MY_ACCOUNTS))
    }
}
