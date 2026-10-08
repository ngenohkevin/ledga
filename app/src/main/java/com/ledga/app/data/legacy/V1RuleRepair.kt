package com.ledga.app.data.legacy

import com.ledga.app.data.room.LedgaDatabase
import com.ledga.core.derive.RuleField
import com.ledga.core.derive.RuleOrigin

/**
 * R175: a phone that imported v1 before R173 still holds v1's paybill-form name rules, which v2's reading of the payee
 * never matches. Rewritten as R173 imports them; returns how many changed (the caller re-files payments then).
 */
class V1RuleRepair(private val db: LedgaDatabase) {
    suspend fun run(): Int {
        var changed = 0
        for (rule in db.rulesDao().all()) {
            if (rule.origin != RuleOrigin.USER || rule.field != RuleField.NAME_CONTAINS) continue
            val (field, pattern) = V1RulePattern.translate(rule.pattern) ?: continue
            db.rulesDao().update(rule.copy(field = field, pattern = pattern))
            changed++
        }
        return changed
    }
}
