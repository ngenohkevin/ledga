package com.ledga.app.data.legacy

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.ledga.app.data.ingest.ParseStatus
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.OverrideRow
import com.ledga.app.data.room.RuleRow
import com.ledga.app.data.room.dao.LegacyCategoryRow
import com.ledga.app.data.room.dao.LegacyRuleRow
import com.ledga.app.data.room.dao.LegacyTxRow
import com.ledga.app.data.room.toCore
import com.ledga.core.derive.LegacyAutoCategorizer
import com.ledga.core.derive.LegacyCategoryMap
import com.ledga.core.derive.LegacyMapping
import com.ledga.core.derive.RuleAction
import com.ledga.core.derive.RuleEngine
import com.ledga.core.derive.RuleField
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.model.Categories
import com.ledga.core.model.CategoryGroup
import com.ledga.core.parse.MpesaParser
import com.ledga.core.parse.ParseOutcome
import com.ledga.core.parse.SmsText
import java.time.Clock
import java.time.Instant

data class LegacyImportReport(
    val smsRehashed: Int,
    val smsDuplicatesDropped: Int,
    val userCategories: Int,
    val userRules: Int,
    val skippedRules: Int,
    val categoryOverrides: Int,
    val ownAccountOverrides: Int,
    val notes: Int,
    val carTags: Int,
) {
    companion object {
        val NONE = LegacyImportReport(0, 0, 0, 0, 0, 0, 0, 0, 0)
    }
}

/**
 * One-time import of v1 user intent after MIGRATION_5_6 (spec §8 step 3). All-or-nothing: it runs in one
 * transaction and drops the `legacy_*` tables last, so "pending" == "legacy_tx exists". The caller then runs the
 * full inbox rescan (Phase 5) and `Deriver.rebuildAll`. [checkpoint] is a test seam that may throw between steps.
 */
class LegacyImporter(
    private val db: LedgaDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val checkpoint: (String) -> Unit = {},
) {
    private val legacy get() = db.legacyDao()

    suspend fun isPending(): Boolean =
        legacy.count(SimpleSQLiteQuery("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'legacy_tx'")) > 0

    suspend fun run(): LegacyImportReport {
        if (!isPending()) return LegacyImportReport.NONE
        return db.withTransaction {
            val now = clock.instant()
            val (rehashed, duplicates) = rehashLegacySms()
            checkpoint("sms")
            val categories = legacy.legacyCategories(SimpleSQLiteQuery("SELECT * FROM legacy_categories"))
            val txRows = legacy.legacyTx(SimpleSQLiteQuery("SELECT * FROM legacy_tx"))
            val legacyRules = legacy.legacyRules(SimpleSQLiteQuery("SELECT * FROM legacy_rules ORDER BY id"))
            val targets = Targets(categories)
            val userCategories = importCustomCategories(categories, txRows, targets)
            checkpoint("categories")
            val (userRules, skippedRules) = importUserRules(legacyRules, targets, now)
            checkpoint("rules")
            val overrides = importOverrides(txRows, legacyRules, targets, now)
            checkpoint("overrides")
            listOf("legacy_tx", "legacy_rules", "legacy_categories")
                .forEach { db.openHelper.writableDatabase.execSQL("DROP TABLE IF EXISTS `$it`") }
            LegacyImportReport(
                rehashed, duplicates, userCategories, userRules, skippedRules,
                overrides.category, overrides.ownAccount, overrides.notes, overrides.carTags,
            )
        }
    }

    /** R10: real hash + status for every provisional legacy row; a row whose real hash exists is a duplicate. */
    private suspend fun rehashLegacySms(): Pair<Int, Int> {
        var rehashed = 0
        var duplicates = 0
        for (row in db.smsDao().legacyPending()) {
            val s = ParseStatus.of(MpesaParser.parse(row.body, row.receivedAt))
            val changed = db.smsDao().rehash(row.id, SmsText.hash(row.body), s.code, s.status, s.reason, MpesaParser.VERSION)
            if (changed == 0) { db.smsDao().delete(row.id); duplicates++ } else rehashed++
        }
        return rehashed to duplicates
    }

    /** What a v1 category id means in v2. Transfer categories (14 and any custom transfer) are "own account". */
    private class Targets(categories: List<LegacyCategoryRow>) {
        val transferIds = categories.filter { it.isTransfer }.map { it.id }.toSet() + LegacyAutoCategorizer.MY_ACCOUNTS
        fun customKey(id: Long) = "legacy_$id"
        fun isCustom(id: Long) = LegacyCategoryMap.map(id) is LegacyMapping.Custom

        /** A mapping -> (categoryKey, ownAccount). */
        fun resolve(mapping: LegacyMapping, legacyId: Long): Pair<String?, Boolean?> = when {
            legacyId in transferIds -> null to true
            mapping is LegacyMapping.ToCategory -> mapping.key to null
            mapping is LegacyMapping.Custom -> customKey(mapping.legacyId) to null
            else -> null to true // LegacyMapping.OwnAccount
        }
    }

    /** R12: a USER category per non-transfer custom v1 category; MONEY_IN when most of its rows were INFLOW. */
    private suspend fun importCustomCategories(categories: List<LegacyCategoryRow>, txRows: List<LegacyTxRow>, targets: Targets): Int {
        var created = 0
        categories.filter { targets.isCustom(it.id) && it.id !in targets.transferIds }.forEach { c ->
            val rows = txRows.filter { it.categoryId == c.id }
            val inflow = rows.count { it.direction == "INFLOW" }
            val group = if (inflow * 2 > rows.size) CategoryGroup.MONEY_IN else CategoryGroup.EVERYDAY
            val id = db.categoriesDao().insertIgnore(
                CategoryRow(
                    key = targets.customKey(c.id), name = c.name, groupKey = group, icon3d = Categories.seed(Categories.OTHER)!!.icon3d,
                    color = c.color, colorDark = null, tracked = false, sortOrder = 1000 + c.id.toInt(), origin = CategoryOrigin.USER, archived = false,
                ),
            )
            if (id != -1L) created++
        }
        return created
    }

    /**
     * v1 rules beyond its defaults become USER rules. Skipped: TILL (never in SMS, no v2 equivalent), and rules into
     * v1 "Bills & Utilities" — R6 at the rule level: v2 has no catch-all Bills category (it maps to Other), and a USER
     * rule to Other would outrank v2's Electricity/Water/Internet/TV system rules forever; a biller no v2 rule knows
     * still lands in Other through its kind default.
     */
    private suspend fun importUserRules(legacyRules: List<LegacyRuleRow>, targets: Targets, now: Instant): Pair<Int, Int> {
        val defaults = LegacyAutoCategorizer.DEFAULT_RULES.map { Triple(it.categoryId, it.matchType, it.matchValue.uppercase()) }.toSet()
        var imported = 0
        var skipped = 0
        legacyRules.filter { Triple(it.categoryId, it.matchType, it.matchValue.uppercase()) !in defaults }.forEach { r ->
            val field = when (r.matchType) {
                "RECIPIENT_NAME" -> RuleField.NAME_CONTAINS
                "PAYBILL" -> RuleField.ACCOUNT_EQUALS
                "PHONE" -> RuleField.PHONE_EQUALS
                else -> null
            }
            val pattern = r.matchValue.trim()
            if (field == null || pattern.isEmpty() || r.categoryId == LegacyAutoCategorizer.BILLS) { skipped++; return@forEach }
            val (categoryKey, own) = targets.resolve(LegacyCategoryMap.map(r.categoryId), r.categoryId)
            db.rulesDao().insert(
                RuleRow(
                    field = field, pattern = pattern,
                    action = if (own == true) RuleAction.MARK_OWN_ACCOUNT else RuleAction.SET_CATEGORY,
                    categoryKey = if (own == true) null else categoryKey,
                    origin = RuleOrigin.USER, priority = 0, createdAt = now,
                ),
            )
            imported++
        }
        return imported to skipped
    }

    private data class OverrideCounts(val category: Int, val ownAccount: Int, val notes: Int, val carTags: Int)

    private suspend fun importOverrides(txRows: List<LegacyTxRow>, legacyRules: List<LegacyRuleRow>, targets: Targets, now: Instant): OverrideCounts {
        val v1Rules = legacyRules.map { LegacyAutoCategorizer.LegacyRule(it.id, it.categoryId, it.matchType, it.matchValue) }
        val groups = db.categoriesDao().all().associate { it.key to it.groupKey }
        val engine = RuleEngine(db.rulesDao().all().map { it.toCore() }, groups::get)
        val parsedByCode = txRows.map { it.code }.chunked(500).flatMap { db.smsDao().parsedByCodes(it) }
            .mapNotNull { row -> (MpesaParser.parse(row.body, row.receivedAt) as? ParseOutcome.Parsed)?.sms }
            .associateBy { it.code }
        var category = 0
        var ownAccount = 0
        var notes = 0
        var carTags = 0
        for (t in txRows) {
            var categoryKey: String? = null
            var own: Boolean? = null
            LegacyChoice.of(t.categoryId, t.type, t.recipientName, t.accountNumber, v1Rules, parsedByCode[t.code], engine, targets::resolve)
                ?.let { (key, isOwn) ->
                    categoryKey = key
                    own = isOwn
                }
            val car = t.carTag?.let(LegacyCategoryMap::carTag)
            if (car != null) categoryKey = car
            val note = t.note?.trim()?.takeIf { it.isNotEmpty() }
            if (categoryKey == null && own == null && note == null) continue
            db.overridesDao().upsert(OverrideRow(t.code, categoryKey, note, lineId = null, ownAccount = own, hidden = false, updatedAt = now))
            if (car != null) carTags++
            if (categoryKey != null) category++
            if (own == true) ownAccount++
            if (note != null) notes++
        }
        return OverrideCounts(category, ownAccount, notes, carTags)
    }
}
