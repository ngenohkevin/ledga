package com.ledga.app.data.backup

import androidx.room.withTransaction
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.ingest.ParseStatus
import com.ledga.app.data.legacy.LegacyChoice
import com.ledga.app.data.lines.SimDirectory
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.LineRow
import com.ledga.app.data.room.MetaKeys
import com.ledga.app.data.room.MetaRow
import com.ledga.app.data.room.OverrideRow
import com.ledga.app.data.room.RuleRow
import com.ledga.app.data.room.SmsRow
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.room.toCore
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.data.settings.TextSize
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.data.legacy.V1RulePattern
import com.ledga.core.derive.LegacyAutoCategorizer
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
import java.util.UUID
import kotlinx.coroutines.CancellationException

enum class RestoreMode { MERGE, REPLACE }

data class RestoreReport(val messagesAdded: Int, val paymentsBefore: Int, val paymentsAfter: Int) {
    val paymentsAdded: Int get() = (paymentsAfter - paymentsBefore).coerceAtLeast(0)
}

/**
 * Spec §12.3 (R121–R123). A restore keeps a copy of this phone first, writes the backup in one transaction (which also
 * clears the stored parser version, so a restore cut short is rebuilt at the next start), applies the portable
 * settings when asked, then rebuilds every payment and writes a fresh snapshot. It never alerts: nothing here touches
 * `alerts` or queues alert work.
 *
 * Merge: messages deduplicated by hash (one already here with no line takes the backup's); overrides fill only empty
 * fields; the person's categories and rules are added unless this phone has the same key or the same words; a built-in
 * choice applies only where this phone still has the built-in default. Replace clears this phone first.
 */
class Restorer(
    private val db: LedgaDatabase,
    private val deriver: Deriver,
    private val settings: SettingsStore,
    private val snapshots: Snapshots,
    private val sims: SimDirectory,
    private val device: DeviceId,
    private val clock: Clock,
) {
    /** R115: the backup was written by this phone (same fingerprint), so its SIM ids hold here. */
    fun sameDevice(incoming: Incoming): Boolean {
        val here = runCatching { device.value() }.getOrNull() ?: return false
        return incoming.data.device == here
    }

    /** The line questions a restore of [incoming] would ask on this phone. */
    suspend fun plan(incoming: Incoming, mode: RestoreMode): LinePlan = LineMatching.plan(
        incoming.data.lines,
        if (mode == RestoreMode.REPLACE) emptyList() else db.linesDao().all(),
        sims.active(),
        sameDevice(incoming),
    )

    suspend fun restore(
        incoming: Incoming,
        mode: RestoreMode,
        answers: Map<Long, Int?>,
        applySettings: Boolean,
        requestId: String = UUID.randomUUID().toString(),
        progress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): RestoreReport {
        // Final review I1: the copy is taken unless this very restore was already written (a retry after the write);
        // a retry after a failure before the write takes it again from the untouched phone.
        if (db.metaDao().get(MetaKeys.RESTORE_REQUEST) != requestId) snapshots.saveBeforeRestore()
        val before = db.transactionsDao().countShown()
        val added = write(incoming, mode, answers, requestId)
        if (applySettings) incoming.data.settings?.let { settings.applyPortable(it) }
        deriver.rebuildAll(progress)
        try {
            snapshots.write()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Unit // R119: a snapshot failure never fails what triggered it
        }
        return RestoreReport(added, before, db.transactionsDao().countShown())
    }

    /** Everything but the rebuild, all or nothing. Returns the messages added. */
    internal suspend fun write(incoming: Incoming, mode: RestoreMode, answers: Map<Long, Int?>, requestId: String = UUID.randomUUID().toString()): Int = db.withTransaction {
        val now = clock.instant()
        val sameDevice = sameDevice(incoming)
        if (mode == RestoreMode.REPLACE) wipe()
        val local = db.linesDao().all()
        val plan = LineMatching.plan(incoming.data.lines, local, sims.active(), sameDevice)
        val lineIds = applyLines(LineMatching.resolve(plan, answers, local), incoming.data.lines)
        applyCategories(incoming.data.categories)
        applyRules(incoming.data.rules, incoming.data.systemRulesOff)
        val added = applySms(incoming.data.sms, lineIds, sameDevice)
        applyOverrides(incoming.data.overrides, lineIds, now)
        if (incoming.origin == BackupOrigin.V1) applyV1Choices(incoming.v1Choices, now)
        // R122: a rebuild is owed until rebuildAll records the versions; a start after a crash here sees it.
        db.metaDao().put(MetaRow(MetaKeys.PARSER_VERSION, "0"))
        db.metaDao().put(MetaRow(MetaKeys.RESTORE_REQUEST, requestId))
        added
    }

    private suspend fun wipe() {
        val r = db.restoreDao()
        r.deleteSms()
        r.deleteTransactions()
        r.deleteOverrides()
        r.deleteUserRules()
        r.enableBuiltInRules()
        r.deleteUserCategories()
        Categories.SEED.forEach { r.resetBuiltIn(it.key, it.name, it.icon3d, it.tracked) }
        r.deleteLines()
    }

    /** Backup line id → this phone's line id. */
    private suspend fun applyLines(targets: Map<Long, LineTarget>, entries: List<LineEntry>): Map<Long, Long> {
        val byId = entries.associateBy { it.id }
        val ids = mutableMapOf<Long, Long>()
        for ((backupId, target) in targets) {
            val e = byId[backupId] ?: continue
            ids[backupId] = when (target) {
                is LineTarget.Existing -> target.lineId
                is LineTarget.OnSim -> db.linesDao().bySubscription(target.subscriptionId)?.id ?: insertLine(e, target.subscriptionId, target.number ?: e.phoneNumber)
                LineTarget.Own -> insertLine(e, null, e.phoneNumber)
            }
        }
        return ids
    }

    private suspend fun insertLine(e: LineEntry, subscriptionId: Int?, number: String?): Long {
        val primary = e.isPrimary && db.linesDao().all().none { it.isPrimary }
        return db.linesDao().insert(
            LineRow(subscriptionId = subscriptionId, phoneNumber = number, displayName = e.displayName, color = e.color, isPrimary = primary, createdAt = Instant.ofEpochMilli(e.createdAt)),
        )
    }

    private suspend fun applyCategories(entries: List<CategoryEntry>) {
        val dao = db.categoriesDao()
        for (c in entries) {
            val group = CategoryGroup.entries.firstOrNull { it.name == c.group } ?: continue
            val here = dao.get(c.key)
            when (c.origin) {
                CategoryOrigin.USER.name -> if (here == null) {
                    dao.insertIgnore(CategoryRow(c.key, c.name, group, c.icon3d, c.color, c.colorDark, c.tracked, c.sortOrder, CategoryOrigin.USER, c.archived))
                }
                CategoryOrigin.SYSTEM.name -> {
                    val seed = Categories.seed(c.key) ?: continue
                    if (here == null || here.origin != CategoryOrigin.SYSTEM) continue
                    // R121: a built-in choice fills only what this phone left at the built-in default.
                    if (here.name == seed.name && c.name != seed.name) dao.rename(c.key, c.name)
                    if (here.icon3d == seed.icon3d && c.icon3d != seed.icon3d) dao.setIcon(c.key, c.icon3d)
                    if (here.color == null && c.color != null && c.colorDark != null) dao.setColor(c.key, c.color, c.colorDark)
                    if (here.tracked == seed.tracked && c.tracked != seed.tracked) dao.setTracked(c.key, c.tracked)
                }
            }
        }
    }

    private suspend fun applyRules(rules: List<RuleEntry>, systemOff: List<RuleKey>) {
        val dao = db.rulesDao()
        for (r in rules) {
            val stored = RuleField.entries.firstOrNull { it.name == r.field } ?: continue
            val action = RuleAction.entries.firstOrNull { it.name == r.action } ?: continue
            // Final review I4: a backup made before beta.2 can hold v1's paybill-form rule (R173), which v2 never matches.
            val (field, pattern) = (if (stored == RuleField.NAME_CONTAINS) V1RulePattern.translate(r.pattern) else null) ?: (stored to r.pattern)
            // R121: this phone's own rule for the same words wins.
            if (dao.userLike(field.name, pattern, action.name).isNotEmpty()) continue
            dao.insert(
                RuleRow(field = field, pattern = pattern, action = action, categoryKey = r.categoryKey, origin = RuleOrigin.USER,
                    priority = r.priority, createdAt = Instant.ofEpochMilli(r.createdAt), enabled = r.enabled),
            )
        }
        val builtIns = dao.all().filter { it.origin == RuleOrigin.SYSTEM && it.enabled }
        for (k in systemOff) {
            builtIns.filter { it.field.name == k.field && it.pattern.equals(k.pattern, ignoreCase = true) && it.action.name == k.action && it.categoryKey == k.categoryKey }
                .forEach { dao.setEnabled(it.id, false) }
        }
    }

    private suspend fun applySms(entries: List<SmsEntry>, lineIds: Map<Long, Long>, sameDevice: Boolean): Int {
        var added = 0
        for (e in entries) {
            if (!MpesaParser.isMpesaSender(e.sender)) continue
            val at = Instant.ofEpochMilli(e.receivedAt)
            val line = e.line?.let(lineIds::get)
            val hash = SmsText.hash(e.body)
            val s = ParseStatus.of(MpesaParser.parse(e.body, at))
            val id = db.smsDao().insertIgnore(
                SmsRow(
                    sender = e.sender, body = e.body, bodyHash = hash, receivedAt = at,
                    // R115: another phone's SIM ids mean nothing here; the line carries where it came from.
                    subscriptionId = if (sameDevice) e.subscriptionId else null,
                    lineId = line, code = s.code, source = SmsSource.IMPORT, status = s.status, statusReason = s.reason,
                    parserVersion = MpesaParser.VERSION,
                ),
            )
            if (id != -1L) added++ else if (line != null) db.smsDao().fillLine(hash, line)
        }
        return added
    }

    private suspend fun applyOverrides(entries: List<OverrideEntry>, lineIds: Map<Long, Long>, now: Instant) {
        val dao = db.overridesDao()
        for (o in entries) {
            val line = o.line?.let(lineIds::get)
            val here = dao.get(o.code)
            if (here == null) {
                dao.upsert(OverrideRow(o.code, o.categoryKey, o.note, line, o.ownAccount, o.hidden, Instant.ofEpochMilli(o.updatedAt)))
                continue
            }
            // R121: this phone's choices win; the backup's fill only what it left empty (its hidden flag stays).
            val filled = here.copy(
                categoryKey = here.categoryKey ?: o.categoryKey,
                note = here.note ?: o.note,
                lineId = here.lineId ?: line,
                ownAccount = here.ownAccount ?: o.ownAccount,
            )
            if (filled != here) dao.upsert(filled.copy(updatedAt = now))
        }
    }

    /** R123: v1's own choices, by the migration's test; v1 custom categories have no name in the export, so stay out. */
    private suspend fun applyV1Choices(choices: List<V1Choice>, now: Instant) {
        val groups = db.categoriesDao().all().associate { it.key to it.groupKey }
        val engine = RuleEngine(db.rulesDao().all().map { it.toCore() }, groups::get)
        val parsed = choices.map { it.code }.chunked(Deriver.CHUNK).flatMap { db.smsDao().parsedByCodes(it) }
            .mapNotNull { (MpesaParser.parse(it.body, it.receivedAt) as? ParseOutcome.Parsed)?.sms }
            .associateBy { it.code }
        for (c in choices) {
            val (key, own) = LegacyChoice.of(c.categoryId, c.type, c.recipientName, c.accountNumber, LegacyAutoCategorizer.DEFAULT_RULES, parsed[c.code], engine, ::v1Target)
                ?: continue
            if (key == null && own == null) continue
            val here = db.overridesDao().get(c.code)
            val base = here ?: OverrideRow(c.code, null, null, null, null, hidden = false, updatedAt = now)
            val filled = base.copy(categoryKey = base.categoryKey ?: key, ownAccount = base.ownAccount ?: own)
            if (filled != here) db.overridesDao().upsert(filled.copy(updatedAt = now))
        }
    }

    private fun v1Target(mapping: LegacyMapping, legacyId: Long): Pair<String?, Boolean?> = when (mapping) {
        is LegacyMapping.ToCategory -> mapping.key to null
        LegacyMapping.OwnAccount -> null to true
        is LegacyMapping.Custom -> null to null
    }
}

/** R114: the person's settings from a backup, through the setters' own checks. */
suspend fun SettingsStore.applyPortable(p: PortableSettings) {
    Appearance.entries.firstOrNull { it.name == p.appearance }?.let { setAppearance(it) }
    TextSize.entries.firstOrNull { it.name == p.textSize }?.let { setTextSize(it) }
    setDisplayName(p.displayName)
    setNotifyDaily(p.notifyDaily)
    setDailySummaryMinute(p.dailySummaryMinute)
    setNotifyWeekly(p.notifyWeekly)
    setNotifyLarge(p.notifyLarge)
    setLargeThreshold(p.largeThresholdCents)
    setNotifyFuliza(p.notifyFuliza)
}
