package com.ledga.app.data.legacy

import com.ledga.core.derive.RuleEngine
import com.ledga.core.derive.RuleField

/**
 * R173: v1 read a paybill's payee as "<NAME> for account[ <ACCOUNT>]", and rules made from those payments kept that
 * text. v2 reads the name and the account apart, so such a rule becomes a name rule, or a name-and-account rule (R35).
 */
object V1RulePattern {
    private val PAYBILL = Regex("""^\s*(.*?\S)\s+for\s+account(?:\s+(.*?))?\s*$""", RegexOption.IGNORE_CASE)

    /** v2's field and pattern for a v1 name rule, or null when [pattern] isn't in v1's paybill form. */
    fun translate(pattern: String): Pair<RuleField, String>? {
        val m = PAYBILL.matchEntire(pattern) ?: return null
        val name = m.groupValues[1]
        val account = m.groupValues[2]
        if (name.none { it.isLetterOrDigit() }) return null
        return if (account.none { it.isLetterOrDigit() }) {
            RuleField.NAME_CONTAINS to name
        } else {
            RuleField.NAME_AND_ACCOUNT to RuleEngine.nameAndAccount(name, account)
        }
    }
}
