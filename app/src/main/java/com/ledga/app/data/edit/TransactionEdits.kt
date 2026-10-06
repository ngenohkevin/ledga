package com.ledga.app.data.edit

import androidx.room.withTransaction
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.RuleRow
import com.ledga.app.data.room.TxRow
import com.ledga.app.data.room.toCore
import com.ledga.app.data.room.toDerived
import com.ledga.core.derive.Derivation
import com.ledga.core.derive.Override
import com.ledga.core.derive.Rule
import com.ledga.core.derive.RuleAction
import com.ledga.core.derive.RuleEngine
import com.ledga.core.derive.RuleField
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.model.CategoryGroup
import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind
import com.ledga.core.parse.Counterparty
import java.time.Clock

/** Where a category or own-account change applies (spec §7.4, R35–R37). */
enum class ApplyTo { THIS_ONE, ALL_FROM_NAME, THIS_ACCOUNT }

/**
 * How many payments end up with a category: [fromName] under a rule on the name, [forAccount] under "only this account
 * number" (null unless the payment is a paybill with an account). Both include the payment being edited (R36).
 */
data class ApplyCounts(val fromName: Int, val forAccount: Int?)

/**
 * Every change a person makes to a transaction (spec §7.4, `app/DATA.md`): category, note, own account, hidden, line,
 * and new categories. It merges with the existing override, writes USER rules where "apply to all" asks for one,
 * clears the overrides a rule must win over, then re-derives (`Deriver.saveOverride`) or re-classifies
 * (`Deriver.reclassifyAll`). The counts it reports are simulated with `:core`'s own `Derivation.reclassify` under the
 * would-be rules, so a switch's number is the number that changes. It never touches the `sms` table.
 */
class TransactionEdits(private val db: LedgaDatabase, private val deriver: Deriver, private val clock: Clock) {

    suspend fun setNote(code: String, note: String?) =
        deriver.saveOverride(override(code).copy(note = note?.replace(WS, " ")?.trim()?.take(NOTE_MAX)?.ifEmpty { null }))

    /** Hidden payments leave lists and totals; their SMS stay (spec §10.4). */
    suspend fun setHidden(code: String, hidden: Boolean) = deriver.saveOverride(override(code).copy(hidden = hidden))

    /** Moves a payment to another line (spec §9.2, R46); null goes back to the line its SMS arrived on. */
    suspend fun setLine(code: String, lineId: Long?) = deriver.saveOverride(override(code).copy(lineId = lineId))

    suspend fun categoryCounts(code: String, categoryKey: String): ApplyCounts {
        val tx = db.transactionsDao().get(code) ?: return ApplyCounts(0, null)
        val name = tx.counterpartyName ?: return ApplyCounts(0, null)
        val fromName = gainers(categoryRule(RuleField.NAME_CONTAINS, name, categoryKey), name, categoryKey).size
        val forAccount = accountOf(tx)?.let { account ->
            gainers(categoryRule(RuleField.NAME_AND_ACCOUNT, RuleEngine.nameAndAccount(name, account), categoryKey), name, categoryKey).size
        }
        return ApplyCounts(fromName, forAccount)
    }

    /**
     * [ApplyTo.THIS_ONE]: an override for this code. Otherwise (R36): a USER rule replacing any like it, the person's own
     * category choices cleared on every payment it will label, and this payment set even where a rule can't reach it.
     */
    suspend fun setCategory(code: String, categoryKey: String, applyTo: ApplyTo) {
        val tx = db.transactionsDao().get(code) ?: return
        val name = tx.counterpartyName
        val account = accountOf(tx)
        val rule = when {
            name == null || applyTo == ApplyTo.THIS_ONE -> null
            applyTo == ApplyTo.THIS_ACCOUNT && account != null ->
                categoryRule(RuleField.NAME_AND_ACCOUNT, RuleEngine.nameAndAccount(name, account), categoryKey)
            else -> categoryRule(RuleField.NAME_CONTAINS, name, categoryKey)
        }
        if (rule == null || name == null) {
            deriver.saveOverride(override(code).copy(categoryKey = categoryKey))
            return
        }
        val gainers = gainers(rule, name, categoryKey)
        db.withTransaction {
            db.rulesDao().deleteUser(rule.field.name, rule.pattern, rule.action.name)
            db.rulesDao().insert(rule.toRow())
            gainers.chunked(Deriver.CHUNK).forEach { db.overridesDao().clearCategory(it, clock.instant()) }
        }
        // A rule can't label every payment (an own-account move keeps "Own accounts"); the tapped one still takes it.
        if (code !in gainers) deriver.saveOverride(override(code).copy(categoryKey = categoryKey))
        deriver.reclassifyAll()
    }

    /** How many payments from this name would end up own-account ([own]) or not, under "all from <name>" (R37). */
    suspend fun ownAccountCount(code: String, own: Boolean): Int {
        val tx = db.transactionsDao().get(code) ?: return 0
        if (tx.counterpartyName == null) return 0
        return ownPlan(tx, own).endState.size
    }

    /** [allFromName] false: an override for this code. True (R37): a USER rule on, or the matching USER rules off. */
    suspend fun setOwnAccount(code: String, own: Boolean, allFromName: Boolean) {
        val tx = db.transactionsDao().get(code) ?: return
        if (!allFromName || tx.counterpartyName == null) {
            deriver.saveOverride(override(code).copy(ownAccount = own))
            return
        }
        val plan = ownPlan(tx, own)
        db.withTransaction {
            if (own) {
                db.rulesDao().deleteUser(plan.nameRule.field.name, plan.nameRule.pattern, plan.nameRule.action.name)
                db.rulesDao().insert(plan.nameRule.toRow())
            } else if (plan.removed.isNotEmpty()) {
                db.rulesDao().deleteIds(plan.removed.map { it.id })
            }
            plan.matched.chunked(Deriver.CHUNK).forEach { db.overridesDao().clearOwnAccount(it, clock.instant()) }
        }
        deriver.reclassifyAll()
    }

    /** A new category in [group] (R43), or the existing one with that name there. Returns its key. */
    suspend fun createCategory(name: String, group: CategoryGroup): String {
        val clean = name.replace(WS, " ").trim().take(CATEGORY_NAME_MAX)
        require(clean.isNotEmpty()) { "a category needs a name" }
        val all = db.categoriesDao().all()
        all.firstOrNull { it.groupKey == group && !it.archived && it.name.equals(clean, ignoreCase = true) }?.let { return it.key }
        val base = "user_" + clean.lowercase().replace(NON_KEY, "_").trim('_').ifEmpty { "category" }
        val key = generateSequence(1) { it + 1 }.map { if (it == 1) base else "${base}_$it" }.first { k -> all.none { it.key == k } }
        db.categoriesDao().insertIgnore(
            CategoryRow(
                key = key, name = clean, groupKey = group, icon3d = NEW_CATEGORY_ICON, color = null, colorDark = null,
                tracked = false, sortOrder = (all.maxOfOrNull { it.sortOrder } ?: 0) + 1, origin = CategoryOrigin.USER, archived = false,
            ),
        )
        return key
    }

    /** What "all from <name>" does to own-account state: the rule added or removed, who it covers, who ends up [own]. */
    private class OwnPlan(val nameRule: Rule, val removed: List<Rule>, val matched: List<String>, val endState: List<String>)

    private suspend fun ownPlan(tx: TxRow, own: Boolean): OwnPlan {
        val name = tx.counterpartyName!!
        val rules = db.rulesDao().all().map { it.toCore() }
        val groups = groups()
        val probe = RuleEngine(rules, groups::get)
        val nameRule = Rule(NEW_ID, RuleField.NAME_CONTAINS, name, RuleAction.MARK_OWN_ACCOUNT, null, RuleOrigin.USER, 0, clock.instant())
        val removed = if (own) {
            emptyList()
        } else {
            probe.ordered.filter { it.origin == RuleOrigin.USER && it.action == RuleAction.MARK_OWN_ACCOUNT && probe.matches(it, tx.counterparty()) }
        }
        val after = if (own) rules.filterNot { same(it, nameRule) } + nameRule else rules.filterNot { it in removed }
        val engine = RuleEngine(after, groups::get)
        val matched = named(name).filter { probe.matches(nameRule, it.counterparty()) }
        val overrides = overridesFor(matched.map { it.code })
        val endState = matched.filter { t ->
            t.kind.ownAccountFlow != null &&
                isOwn(Derivation.reclassify(t.toDerived(), overrides[t.code]?.copy(ownAccount = null), engine).flow) == own
        }
        return OwnPlan(nameRule, removed, matched.map { it.code }, endState.map { it.code })
    }

    /** Payments that would carry [categoryKey] once [rule] replaces any USER rule like it and their own choices are cleared. */
    private suspend fun gainers(rule: Rule, name: String, categoryKey: String): List<String> {
        val groups = groups()
        val engine = RuleEngine(db.rulesDao().all().map { it.toCore() }.filterNot { same(it, rule) } + rule, groups::get)
        val matched = named(name).filter { engine.matches(rule, it.counterparty()) }
        val overrides = overridesFor(matched.map { it.code })
        return matched.filter { t ->
            Derivation.reclassify(t.toDerived(), overrides[t.code]?.copy(categoryKey = null), engine).categoryKey == categoryKey
        }.map { it.code }
    }

    private suspend fun override(code: String): Override = db.overridesDao().get(code)?.toCore() ?: Override(code)

    private suspend fun named(name: String): List<TxRow> =
        LedgerQueries.likePattern(name)?.let { db.transactionsDao().named(it) }.orEmpty()

    private suspend fun overridesFor(codes: List<String>): Map<String, Override> =
        codes.chunked(Deriver.CHUNK).flatMap { db.overridesDao().byCodes(it) }.associate { it.code to it.toCore() }

    private suspend fun groups(): Map<String, CategoryGroup> = db.categoriesDao().all().associate { it.key to it.groupKey }

    private fun accountOf(tx: TxRow): String? =
        tx.counterpartyAccount?.takeIf { tx.kind == TxKind.PAYBILL && it.any(Char::isLetterOrDigit) }

    private fun categoryRule(field: RuleField, pattern: String, categoryKey: String) =
        Rule(NEW_ID, field, pattern, RuleAction.SET_CATEGORY, categoryKey, RuleOrigin.USER, 0, clock.instant())

    private fun same(a: Rule, b: Rule) =
        a.origin == RuleOrigin.USER && a.field == b.field && a.action == b.action && a.pattern.equals(b.pattern, ignoreCase = true)

    private fun isOwn(flow: FlowKind) = flow == FlowKind.OWN_OUT || flow == FlowKind.OWN_IN

    private fun TxRow.counterparty() = Counterparty(counterpartyName, counterpartyPhone, counterpartyAccount, null)

    private fun Rule.toRow() =
        RuleRow(field = field, pattern = pattern, action = action, categoryKey = categoryKey, origin = origin, priority = priority, createdAt = createdAt)

    companion object {
        const val NOTE_MAX = 200
        const val CATEGORY_NAME_MAX = 30
        const val NEW_CATEGORY_ICON = "fluent_label"

        /** The would-be rule in a simulation: USER ties go newest-first, then highest id, as the inserted rule will. */
        private const val NEW_ID = Long.MAX_VALUE
        private val WS = Regex("\\s+")
        private val NON_KEY = Regex("[^a-z0-9]+")
    }
}
