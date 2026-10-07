package com.ledga.app.ui.categories

import com.ledga.app.data.edit.CategoryLooks

/** What Categories & rules says (R67, R72). */
object CategoryText {
    /** "No rules", "1 rule", "3 rules · 1 off": every rule counts, and the switched-off ones are named. */
    fun rulesLine(on: Int, off: Int): String {
        val total = on + off
        if (total == 0) return "No rules"
        val rules = "$total ${if (total == 1) "rule" else "rules"}"
        return if (off > 0) "$rules · $off off" else rules
    }

    /** "Built-in", "Yours", "Built-in · off" (R73). */
    fun ruleOrigin(rule: RuleUi): String = (if (rule.builtIn) "Built-in" else "Yours") + if (rule.enabled) "" else " · off"

    /** R72: what archiving keeps, before it happens. */
    fun archiveText(payments: Int, rules: Int): String {
        val kept = when (payments) {
            0 -> "No payments use it"
            1 -> "Its 1 payment keeps it"
            else -> "Its $payments payments keep it"
        }
        val filing = when (rules) {
            0 -> "."
            1 -> ", and 1 rule still files new payments here."
            else -> ", and $rules rules still file new payments here."
        }
        return "It leaves the category picker, the filters and Trackers. $kept$filing You can bring it back from Categories."
    }

    /** "fluent_hammer_and_wrench" → "Hammer and wrench" (TalkBack's name for an icon choice). */
    fun iconName(key: String): String = key.removePrefix("fluent_").replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** A stored light colour's swatch name; "Default" for none or a v1 colour that isn't a swatch. */
    fun swatchName(light: String?): String = CategoryLooks.SWATCHES.firstOrNull { it.light.equals(light, ignoreCase = true) }?.name ?: "Default"
}
