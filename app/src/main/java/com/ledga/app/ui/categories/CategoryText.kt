package com.ledga.app.ui.categories

/** What Categories & rules says (R67, R72). */
object CategoryText {
    /** "No rules", "1 rule", "3 rules · 1 off": every rule counts, and the switched-off ones are named. */
    fun rulesLine(on: Int, off: Int): String {
        val total = on + off
        if (total == 0) return "No rules"
        val rules = "$total ${if (total == 1) "rule" else "rules"}"
        return if (off > 0) "$rules · $off off" else rules
    }
}
