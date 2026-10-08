package com.ledga.app.data.legacy

import com.ledga.core.derive.RuleEngine
import com.ledga.core.derive.RuleField
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** R173: v1's paybill payee "<NAME> for account[ <ACCOUNT>]" as v2 reads it. Synthetic names. */
class V1RulePatternTest {
    @Test
    fun `v1's paybill payee becomes v2's name, with the account when v1 kept one`() {
        assertEquals(RuleField.NAME_CONTAINS to "SAMPLE SACCO", V1RulePattern.translate("SAMPLE SACCO for account"))
        assertEquals(RuleField.NAME_CONTAINS to "SAMPLE SACCO", V1RulePattern.translate("  SAMPLE SACCO  FOR ACCOUNT "))
        assertEquals(RuleField.NAME_AND_ACCOUNT to RuleEngine.nameAndAccount("SAMPLE BANK", "7788"), V1RulePattern.translate("SAMPLE BANK for account 7788"))
    }

    @Test
    fun `anything else is left as it is`() {
        assertNull(V1RulePattern.translate("SAMPLE SHOP"))
        assertNull(V1RulePattern.translate("for account"))
        assertNull(V1RulePattern.translate("SAMPLE ACCOUNTS"))
    }
}
