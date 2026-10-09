package com.ledga.app.data.edit

import androidx.room.withTransaction
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.derive.LedgerQueries
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.OverrideRow
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
import com.ledga.core.model.Categories
import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind
import com.ledga.core.parse.Counterparty
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.Locale

/** Where a category or own-account change applies (spec §7.4, R35–R37). */
enum class ApplyTo { THIS_ONE, ALL_FROM_NAME, THIS_ACCOUNT }

/**
 * How many payments end up with a category: [fromName] under a rule on the name, [forAccount] under "only this account
 * number" (null unless the payment is a paybill with an account). Both include the payment being edited (R36).
 */
data class ApplyCounts(val fromName: Int, val forAccount: Int?)

/**
 * What "+ Add rule" would do (R48): [matches] payments end up in the category, [moving] of them aren't there now, and
 * [handFiled] of those the person had filed somewhere else themselves (their choice is cleared, as "all" says).
 */
data class RulePreview(val matches: Int, val moving: Int, val handFiled: Int, val replaces: String? = null)

/** A rule taken off a tracker (R49): what Undo needs to put it back. */
data class RemovedRule(val row: RuleRow)

/**
 * Every change a person makes to a transaction (spec §7.4, `app/DATA.md`): category, note, own account, hidden, line,
 * and categories and rules themselves. It merges with the existing override, writes USER rules where "apply to all"
 * asks for one, clears the overrides a rule must win over, then re-derives (`Deriver.saveOverride`) or re-classifies
 * (`Deriver.reclassifyAll`). The counts it reports are simulated with `:core`'s own `Derivation.reclassify` under the
 * would-be rules, so a switch's number is the number that changes. It never touches the `sms` table.
 *
 * Writes run one at a time (R63): two quick edits could otherwise interleave a reclassify with a rule write and leave a
 * row disagreeing with its override until the next one. Counting functions don't lock.
 */
class TransactionEdits(private val db: LedgaDatabase, private val deriver: Deriver, private val clock: Clock) {
    private val writes = Mutex()

    /** One at a time (R63), and never cancelled part way: leaving the screen that asked must not strand a half-done edit. */
    private suspend fun <T> serial(block: suspend () -> T): T = withContext(NonCancellable) { writes.withLock { block() } }

    suspend fun setNote(code: String, note: String?) = serial {
        deriver.saveOverride(override(code).copy(note = note?.replace(WS, " ")?.trim()?.take(NOTE_MAX)?.ifEmpty { null }))
    }

    /** Hidden payments leave lists and totals; their SMS stay (spec §10.4). */
    suspend fun setHidden(code: String, hidden: Boolean) = serial { deriver.saveOverride(override(code).copy(hidden = hidden)) }

    /** Moves a payment to another line (spec §9.2, R46); null goes back to the line its SMS arrived on. */
    suspend fun setLine(code: String, lineId: Long?) = serial { deriver.saveOverride(override(code).copy(lineId = lineId)) }

    /**
     * R128: each payment in [byCode] that is still not on a line goes to its line, as an override (like [setLine]), so a
     * rebuild keeps it. Returns the codes it moved, for Undo; a payment placed meanwhile is left where it is.
     */
    suspend fun placeOnLines(byCode: Map<String, Long>): List<String> = serial {
        val moving = byCode.keys.toList().chunked(Deriver.CHUNK).flatMap { db.transactionsDao().unassignedAmong(it) }
        putOnLines(moving, byCode)
    }

    /**
     * R194: each payment in [byCode] goes to its line as the person's placement (like [setLine]), whatever line it is on
     * now, unless the person put it on one already. Returns the codes it moved, for Undo ([unplace]).
     */
    suspend fun moveToLines(byCode: Map<String, Long>): List<String> = serial {
        val placed = byCode.keys.toList().chunked(Deriver.CHUNK).flatMap { db.overridesDao().byCodes(it) }.filter { it.lineId != null }.map { it.code }.toSet()
        putOnLines(byCode.keys.filterNot { it in placed }, byCode)
    }

    private suspend fun putOnLines(moving: List<String>, byCode: Map<String, Long>): List<String> {
        val now = clock.instant()
        moving.chunked(Deriver.CHUNK).forEach { chunk ->
            db.withTransaction {
                val existing = db.overridesDao().byCodes(chunk).associateBy { it.code }
                db.overridesDao().upsertAll(
                    chunk.map { code ->
                        (existing[code] ?: OverrideRow(code, null, null, null, null, hidden = false, updatedAt = now))
                            .copy(lineId = byCode.getValue(code), updatedAt = now)
                    },
                )
            }
        }
        deriver.rederive(moving)
        return moving
    }

    /** Undo of [placeOnLines]: those payments leave their line again; override rows left saying nothing go. */
    suspend fun unplace(codes: List<String>) = serial {
        val now = clock.instant()
        codes.chunked(Deriver.CHUNK).forEach { chunk ->
            db.withTransaction {
                db.overridesDao().clearLine(chunk, now)
                db.overridesDao().deleteEmpty(chunk)
            }
        }
        deriver.rederive(codes)
    }

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
    suspend fun setCategory(code: String, categoryKey: String, applyTo: ApplyTo) = serial { setCategoryNow(code, categoryKey, applyTo) }

    private suspend fun setCategoryNow(code: String, categoryKey: String, applyTo: ApplyTo) {
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
        writeRule(rule, gainers)
        // A rule can't label every payment (an own-account move keeps "Own accounts"); the tapped one still takes it.
        if (code !in gainers) deriver.saveOverride(override(code).copy(categoryKey = categoryKey))
        deriver.reclassifyAll()
    }

    /** How many payments from this name change if "all from <name>" becomes own-account ([own]) or not (R37). */
    suspend fun ownAccountCount(code: String, own: Boolean): Int {
        val tx = db.transactionsDao().get(code) ?: return 0
        val name = tx.counterpartyName ?: return 0
        // Exactly the payments that change: those that can be own-account and aren't already where [own] puts them.
        return ownCandidates(name).count { it.kind.ownAccountFlow != null && isOwn(it.flow) != own }
    }

    /**
     * [allFromName] false: an override for this code. True (R37): the name's USER own-account rule on, or off with an
     * exception on any of its payments a broader rule of the person's still marks own.
     */
    suspend fun setOwnAccount(code: String, own: Boolean, allFromName: Boolean) = serial { setOwnAccountNow(code, own, allFromName) }

    private suspend fun setOwnAccountNow(code: String, own: Boolean, allFromName: Boolean) {
        val tx = db.transactionsDao().get(code) ?: return
        val name = tx.counterpartyName
        if (!allFromName || name == null) {
            deriver.saveOverride(override(code).copy(ownAccount = own))
            return
        }
        val nameRule = ownRule(name)
        val candidates = ownCandidates(name).map { it.code }
        db.withTransaction {
            // Only the rule "all from <name>" makes: a broader rule the person already has (v1's imports bring some)
            // stays, so payments from other names never change behind the count.
            db.rulesDao().deleteUser(nameRule.field.name, nameRule.pattern, nameRule.action.name)
            if (own) db.rulesDao().insert(nameRule.toRow())
            candidates.chunked(Deriver.CHUNK).forEach { db.overridesDao().clearOwnAccount(it, clock.instant()) }
        }
        deriver.reclassifyAll()
        if (!own) {
            // A broader rule may still mark some of them own: an exception on each keeps "all N from <name>" true.
            candidates.filter { c -> db.transactionsDao().get(c)?.flow?.let(::isOwn) == true }
                .forEach { c -> deriver.saveOverride(override(c).copy(ownAccount = false)) }
        }
    }

    /** A new category in [group] (R43), or the existing one with that name there. Returns its key. */
    suspend fun createCategory(name: String, group: CategoryGroup): String = serial {
        val clean = name.replace(WS, " ").trim().take(CATEGORY_NAME_MAX)
        require(clean.isNotEmpty()) { "a category needs a name" }
        val all = db.categoriesDao().all()
        // R72: an archived category's name stays taken; making it again brings that one back.
        val existing = all.firstOrNull { it.groupKey == group && it.name.equals(clean, ignoreCase = true) }
        if (existing != null) {
            if (existing.archived) db.categoriesDao().setArchived(existing.key, false)
            return@serial existing.key
        }
        val base = "user_" + clean.lowercase().replace(NON_KEY, "_").trim('_').ifEmpty { "category" }
        val key = generateSequence(1) { it + 1 }.map { if (it == 1) base else "${base}_$it" }.first { k -> all.none { it.key == k } }
        db.categoriesDao().insertIgnore(
            CategoryRow(
                key = key, name = clean, groupKey = group, icon3d = NEW_CATEGORY_ICON, color = null, colorDark = null,
                tracked = false, sortOrder = (all.maxOfOrNull { it.sortOrder } ?: 0) + 1, origin = CategoryOrigin.USER, archived = false,
            ),
        )
        key
    }

    /** R50: track a category, or stop. Tracking changes no transaction, so nothing re-classifies. */
    suspend fun setTracked(categoryKey: String, tracked: Boolean) = serial { db.categoriesDao().setTracked(categoryKey, tracked) }

    /**
     * R51: renames a category everywhere. False for a blank name, an unknown key, or a name another category in its group
     * has, archived ones included (R72).
     */
    suspend fun renameCategory(categoryKey: String, name: String): Boolean = serial {
        val clean = name.replace(WS, " ").trim().take(CATEGORY_NAME_MAX)
        val all = db.categoriesDao().all()
        val category = all.firstOrNull { it.key == categoryKey }
        val taken = category != null &&
            all.any { it.key != categoryKey && it.groupKey == category.groupKey && it.name.equals(clean, ignoreCase = true) }
        if (clean.isEmpty() || category == null || taken) return@serial false
        db.categoriesDao().rename(categoryKey, clean)
        true
    }

    /**
     * What "+ Add rule" would do (R48); null when [name] has fewer than two letters or digits. [RulePreview.replaces]
     * names the category of the person's own rule for the same words that saving replaces.
     */
    suspend fun rulePreview(categoryKey: String, name: String, account: String?): RulePreview? {
        val (rule, pattern) = trackerRule(categoryKey, name, account) ?: return null
        val rows = gainerRows(rule, pattern, categoryKey)
        val moving = rows.filter { it.categoryKey != categoryKey }
        val overrides = overridesFor(moving.map { it.code })
        val replacedKey = db.rulesDao().userLike(rule.field.name, rule.pattern, rule.action.name)
            .firstOrNull { it.categoryKey != categoryKey }?.categoryKey
        val replaces = replacedKey?.let { key -> db.categoriesDao().all().firstOrNull { it.key == key }?.name }
        return RulePreview(rows.size, moving.size, moving.count { overrides[it.code]?.categoryKey != null }, replaces)
    }

    /**
     * Tracker detail's "+ Add rule" (R48, owner 2026-10-06): like "Apply to all" (R36). A USER rule replacing any like
     * it, and the person's own category choices cleared on every payment it will label. False for an unusable pattern.
     */
    suspend fun addRule(categoryKey: String, name: String, account: String?): Boolean = serial {
        val (rule, pattern) = trackerRule(categoryKey, name, account) ?: return@serial false
        writeRule(rule, gainers(rule, pattern, categoryKey))
        deriver.reclassifyAll()
        true
    }

    /** R49: the person's own rule is deleted; a built-in one is switched off (spec §7.1). Null for an unknown id. */
    suspend fun removeRule(id: Long): RemovedRule? = serial {
        val row = db.rulesDao().get(id) ?: return@serial null
        if (row.origin == RuleOrigin.USER) db.rulesDao().delete(id) else db.rulesDao().setEnabled(id, false)
        deriver.reclassifyAll()
        RemovedRule(row)
    }

    /** Undo for [removeRule]: the person's rule returns as it was (its time keeps its order); a built-in one is switched back on. */
    suspend fun restoreRule(removed: RemovedRule) = serial {
        val row = removed.row
        if (row.origin == RuleOrigin.USER) db.rulesDao().insert(row.copy(id = 0)) else db.rulesDao().setEnabled(row.id, true)
        deriver.reclassifyAll()
    }

    /**
     * R73: switches a rule on or off, the person's or built-in, and re-classifies. False for an unknown id. Switching to
     * the state it is already in changes nothing.
     */
    suspend fun setRuleEnabled(id: Long, enabled: Boolean): Boolean = serial {
        val row = db.rulesDao().get(id) ?: return@serial false
        if (row.enabled != enabled) {
            db.rulesDao().setEnabled(id, enabled)
            deriver.reclassifyAll()
        }
        true
    }

    /** D6, R91: any well-formed icon key for any category. The picker offers the set's keys; one the app can't draw shows Other's. */
    suspend fun setCategoryIcon(categoryKey: String, icon: String): Boolean = serial {
        if (!CategoryLooks.ICON_KEY.matches(icon) || db.categoriesDao().get(categoryKey) == null) return@serial false
        db.categoriesDao().setIcon(categoryKey, icon)
        true
    }

    /** D6, R74: one of [CategoryLooks.SWATCHES], both themes' colours, for any category. */
    suspend fun setCategoryColor(categoryKey: String, swatch: CategoryLooks.Swatch): Boolean = serial {
        if (swatch !in CategoryLooks.SWATCHES || db.categoriesDao().get(categoryKey) == null) return@serial false
        db.categoriesDao().setColor(categoryKey, swatch.light, swatch.dark)
        true
    }

    /** D6: a built-in category's seeded icon and palette colour again. False for a category of the person's own. */
    suspend fun resetCategoryLooks(categoryKey: String): Boolean = serial {
        val seed = Categories.seed(categoryKey) ?: return@serial false
        if (db.categoriesDao().get(categoryKey)?.origin != CategoryOrigin.SYSTEM) return@serial false
        db.categoriesDao().resetLooks(categoryKey, seed.icon3d)
        true
    }

    /**
     * R72: archives a category of the person's own, or brings it back. Archived, it leaves the picker, the filter sheet
     * and "Track a category", and stops being tracked. Its payments and rules don't change, so nothing reclassifies.
     */
    suspend fun setArchived(categoryKey: String, archived: Boolean): Boolean = serial {
        if (ownCategory(categoryKey) == null) return@serial false
        db.categoriesDao().setArchived(categoryKey, archived)
        true
    }

    private suspend fun ownCategory(key: String): CategoryRow? = db.categoriesDao().get(key)?.takeIf { it.origin == CategoryOrigin.USER }

    /** Writes [rule] in place of any USER rule like it and clears [gainers]' own category choices, in one transaction. */
    private suspend fun writeRule(rule: Rule, gainers: List<String>) {
        db.withTransaction {
            db.rulesDao().deleteUser(rule.field.name, rule.pattern, rule.action.name)
            db.rulesDao().insert(rule.toRow())
            gainers.chunked(Deriver.CHUNK).forEach { db.overridesDao().clearCategory(it, clock.instant()) }
        }
    }

    /** A tracker rule (R48) and the name its LIKE pre-filter uses; null when the name has fewer than two letters or digits. */
    private fun trackerRule(categoryKey: String, name: String, account: String?): Pair<Rule, String>? {
        val pattern = name.replace(WS, " ").trim().take(RULE_NAME_MAX).uppercase(Locale.ROOT)
        if (pattern.count(Char::isLetterOrDigit) < 2) return null
        val acc = account?.replace(WS, " ")?.trim()?.takeIf { it.any(Char::isLetterOrDigit) }
        val rule = if (acc == null) {
            categoryRule(RuleField.NAME_CONTAINS, pattern, categoryKey)
        } else {
            categoryRule(RuleField.NAME_AND_ACCOUNT, RuleEngine.nameAndAccount(pattern, acc), categoryKey)
        }
        return rule to pattern
    }

    /** The rule "all from <name>" makes for own accounts (R37). */
    private fun ownRule(name: String) =
        Rule(NEW_ID, RuleField.NAME_CONTAINS, name, RuleAction.MARK_OWN_ACCOUNT, null, RuleOrigin.USER, 0, clock.instant())

    /** The payments "all from <name>" covers: those [ownRule] matches. */
    private suspend fun ownCandidates(name: String): List<TxRow> {
        val rule = ownRule(name)
        val probe = RuleEngine(listOf(rule), groups()::get)
        return named(name).filter { probe.matches(rule, it.counterparty()) }
    }

    /** Payments that would carry [categoryKey] once [rule] replaces any USER rule like it and their own choices are cleared. */
    private suspend fun gainerRows(rule: Rule, name: String, categoryKey: String): List<TxRow> {
        val groups = groups()
        val engine = RuleEngine(db.rulesDao().all().map { it.toCore() }.filterNot { same(it, rule) } + rule, groups::get)
        val matched = named(name).filter { engine.matches(rule, it.counterparty()) }
        val overrides = overridesFor(matched.map { it.code })
        return matched.filter { t ->
            Derivation.reclassify(t.toDerived(), overrides[t.code]?.copy(categoryKey = null), engine).categoryKey == categoryKey
        }
    }

    private suspend fun gainers(rule: Rule, name: String, categoryKey: String): List<String> = gainerRows(rule, name, categoryKey).map { it.code }

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

        /** "+ Add rule"'s name field (R48). */
        const val RULE_NAME_MAX = 40
        const val NEW_CATEGORY_ICON = CategoryLooks.DEFAULT_ICON

        /** The would-be rule in a simulation: USER ties go newest-first, then highest id, as the inserted rule will. */
        private const val NEW_ID = Long.MAX_VALUE
        private val WS = Regex("\\s+")
        private val NON_KEY = Regex("[^a-z0-9]+")
    }
}
