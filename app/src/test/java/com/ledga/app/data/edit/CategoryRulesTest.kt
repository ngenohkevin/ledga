package com.ledga.app.data.edit

import com.ledga.app.data.room.RuleRow
import com.ledga.core.derive.RuleAction
import com.ledga.core.derive.RuleField
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.model.Categories
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.Test

/** R67: which rules a category's screen lists. */
class CategoryRulesTest {
    private fun rule(id: Long, action: RuleAction, key: String?, enabled: Boolean = true) =
        RuleRow(id, RuleField.NAME_CONTAINS, "SAMPLE $id", action, key, RuleOrigin.USER, 0, Instant.EPOCH, enabled)

    @Test
    fun `a category lists the rules that file into it, switched off ones too, and Own accounts lists the own-account rules`() {
        val rules = listOf(
            rule(1, RuleAction.SET_CATEGORY, Categories.SCHOOL),
            rule(2, RuleAction.SET_CATEGORY, Categories.SCHOOL, enabled = false),
            rule(3, RuleAction.MARK_OWN_ACCOUNT, null),
            rule(4, RuleAction.SET_CATEGORY, Categories.FUEL),
        )
        assertEquals(listOf(1L, 2L), CategoryRules.forCategory(rules, Categories.SCHOOL).map { it.id })
        assertEquals(listOf(3L), CategoryRules.forCategory(rules, Categories.OWN_ACCOUNTS).map { it.id })
        assertEquals(emptyList(), CategoryRules.forCategory(rules, Categories.WATER))
    }
}
