package com.ledga.app.data.derive

import androidx.room.withTransaction
import com.ledga.app.data.ingest.ParseStatus
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.MetaKeys
import com.ledga.app.data.room.MetaRow
import com.ledga.app.data.room.toCore
import com.ledga.app.data.room.toDerived
import com.ledga.app.data.room.toRow
import com.ledga.core.derive.Derivation
import com.ledga.core.derive.Override
import com.ledga.core.derive.RuleEngine
import com.ledga.core.derive.SourceSms
import com.ledga.core.parse.MpesaParser
import com.ledga.core.parse.ParseOutcome
import java.time.Clock

/**
 * Runs `:core` over stored SMS (spec §7.2): every `transactions` row is rebuilt from its PARSED `sms` rows,
 * its override and the current rules. Lists passed to `IN (...)` are chunked to [CHUNK] (API 26: 999 variables).
 */
class Deriver(private val db: LedgaDatabase, private val clock: Clock = Clock.systemUTC()) {
    private val sms get() = db.smsDao()
    private val txs get() = db.transactionsDao()

    suspend fun ruleEngine(): RuleEngine {
        val groups = db.categoriesDao().all().associate { it.key to it.groupKey }
        return RuleEngine(db.rulesDao().all().map { it.toCore() }, groups::get)
    }

    /** Re-derives [codes]; deletes those with no PARSED SMS left; refreshes isReversed around them. */
    suspend fun rederive(codes: Collection<String>) {
        if (codes.isEmpty()) return
        val engine = ruleEngine()
        codes.distinct().chunked(CHUNK).forEach { chunk -> db.withTransaction { deriveChunk(chunk, engine) } }
    }

    private suspend fun deriveChunk(codes: List<String>, engine: RuleEngine) {
        val overrides = db.overridesDao().byCodes(codes).associateBy { it.code }
        val sources = sms.parsedByCodes(codes).mapNotNull { row ->
            (MpesaParser.parse(row.body, row.receivedAt) as? ParseOutcome.Parsed)
                ?.let { SourceSms(row.id, it.sms, row.receivedAt, row.lineId) }
        }.groupBy { it.parsed.code }
        val rows = sources.map { (code, src) -> Derivation.derive(src, overrides[code]?.toCore(), engine).toRow() }
        val gone = codes.filterNot { it in sources }
        val affected = codes.toMutableSet()
        if (gone.isNotEmpty()) {
            affected += txs.reversesCodesOf(gone)
            txs.deleteCodes(gone)
        }
        txs.upsertAll(rows)
        rows.mapNotNullTo(affected) { it.reversesCode }
        affected.toList().chunked(CHUNK).forEach { txs.refreshReversed(it) }
    }

    /**
     * Full rebuild (§7.2): re-parse every SMS row with the current parser, re-derive every code, drop
     * transactions with no PARSED SMS, recompute isReversed, then record the versions used.
     */
    suspend fun rebuildAll(progress: suspend (done: Int, total: Int) -> Unit = { _, _ -> }) {
        var afterId = 0L
        while (true) {
            val page = sms.pageAfter(afterId, PAGE)
            if (page.isEmpty()) break
            db.withTransaction {
                page.forEach { row ->
                    val s = ParseStatus.of(MpesaParser.parse(row.body, row.receivedAt))
                    sms.updateParse(row.id, s.code, s.status, s.reason, MpesaParser.VERSION)
                }
            }
            afterId = page.last().id
        }
        txs.deleteWithoutSms()
        val engine = ruleEngine()
        val codes = sms.parsedCodes()
        codes.chunked(CHUNK).forEachIndexed { i, chunk ->
            db.withTransaction { deriveChunk(chunk, engine) }
            progress(minOf((i + 1) * CHUNK, codes.size), codes.size)
        }
        txs.refreshAllReversed()
        db.metaDao().put(MetaRow(MetaKeys.PARSER_VERSION, MpesaParser.VERSION.toString()))
        db.metaDao().put(MetaRow(MetaKeys.DERIVATION_VERSION, Derivation.VERSION.toString()))
    }

    /** After a rule or category change: re-resolve flow and category only (no re-parse). */
    suspend fun reclassifyAll() {
        val engine = ruleEngine()
        val overrides = db.overridesDao().all().associateBy { it.code }
        db.withTransaction {
            txs.all().forEach { row ->
                val before = row.toDerived()
                val after = Derivation.reclassify(before, overrides[row.code]?.toCore(), engine)
                if (after.flow != before.flow || after.categoryKey != before.categoryKey) {
                    txs.updateClassification(row.code, after.flow, after.categoryKey)
                }
            }
        }
    }

    /** Stores user intent for one code and re-derives it (note, hidden and line need a full derive, not reclassify). */
    suspend fun saveOverride(override: Override) {
        db.overridesDao().upsert(override.toRow(clock.instant()))
        rederive(listOf(override.code))
    }

    suspend fun needsRebuild(): Boolean =
        db.metaDao().get(MetaKeys.PARSER_VERSION) != MpesaParser.VERSION.toString() ||
            db.metaDao().get(MetaKeys.DERIVATION_VERSION) != Derivation.VERSION.toString()

    companion object {
        const val CHUNK = 500
        private const val PAGE = 1000
    }
}
