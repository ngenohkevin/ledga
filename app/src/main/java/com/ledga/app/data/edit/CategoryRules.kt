package com.ledga.app.data.edit

import com.ledga.app.data.room.RuleRow
import com.ledga.core.derive.RuleAction
import com.ledga.core.model.Categories

/**
 * Which rules a category's screen lists (R67): every rule that files into it, switched off or not. Own accounts lists
 * the own-account rules (`MARK_OWN_ACCOUNT` has no category: it sends payments there by taking them out of Spent).
 * The order is the caller's (`RulesDao.observeAll`).
 */
object CategoryRules {
    fun forCategory(rules: List<RuleRow>, categoryKey: String): List<RuleRow> = rules.filter { r ->
        when (r.action) {
            RuleAction.SET_CATEGORY -> r.categoryKey == categoryKey
            RuleAction.MARK_OWN_ACCOUNT -> categoryKey == Categories.OWN_ACCOUNTS
        }
    }
}
