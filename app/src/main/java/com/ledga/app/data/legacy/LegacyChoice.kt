package com.ledga.app.data.legacy

import com.ledga.core.derive.Classifier
import com.ledga.core.derive.LegacyAutoCategorizer
import com.ledga.core.derive.LegacyCategoryMap
import com.ledga.core.derive.LegacyMapping
import com.ledga.core.derive.RuleEngine
import com.ledga.core.parse.ParsedSms

/**
 * Spec §8 step 3: a v1 category becomes v2 intent only when v1 couldn't have chosen it by itself (R1), and a v1 "Bills"
 * yields to a v2 bill rule (R6). Shared by the migration ([LegacyImporter]) and a v1-export restore (R123). [resolve] maps
 * a v1 mapping to (category key, own account); null when there is no choice to keep.
 */
object LegacyChoice {
    fun of(
        legacyId: Long?,
        type: String,
        recipientName: String?,
        accountNumber: String?,
        v1Rules: List<LegacyAutoCategorizer.LegacyRule>,
        parsed: ParsedSms?,
        engine: RuleEngine,
        resolve: (LegacyMapping, Long) -> Pair<String?, Boolean?>,
    ): Pair<String?, Boolean?>? {
        if (legacyId == null || !LegacyAutoCategorizer.isUserChoice(legacyId, type, recipientName, accountNumber, v1Rules)) return null
        val ruleCategory = parsed?.let { p -> engine.categoryFor(p.counterparty, Classifier.flowOf(p.kind, engine.isOwnAccount(p.counterparty))) }
        val mapping = LegacyCategoryMap.overrideFor(legacyId, ruleCategory) ?: return null
        return resolve(mapping, legacyId)
    }
}
