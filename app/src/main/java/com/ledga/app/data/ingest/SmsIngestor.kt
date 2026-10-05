package com.ledga.app.data.ingest

import androidx.room.withTransaction
import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.SmsRow
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.room.SmsStatus
import com.ledga.core.parse.MpesaParser
import com.ledga.core.parse.SmsText
import java.time.Instant

/** One SMS as a source delivers it (receiver, inbox scan, legacy, import). [lineId] is resolved by the caller (Phase 5). */
data class RawSms(
    val sender: String,
    val body: String,
    val receivedAt: Instant,
    val subscriptionId: Int?,
    val lineId: Long?,
    val source: SmsSource,
)

/** [newCodes]: PARSED codes of newly stored rows (Phase 5 fires alerts only for these). */
data class IngestResult(val inserted: Int, val duplicates: Int, val rejected: Int, val newCodes: Set<String>)

/**
 * Spec §7.2 steps 1–3: normalise + hash -> INSERT OR IGNORE -> store the parse status -> re-derive the codes touched.
 * Each chunk's inserts and its derive commit together: if deriving fails (or the app dies), the SMS rows roll back too,
 * so the next delivery of the same message is not mistaken for a duplicate and is derived then.
 */
class SmsIngestor(private val db: LedgaDatabase, private val deriver: Deriver) {

    suspend fun ingest(raw: RawSms): IngestResult = ingestAll(listOf(raw))

    suspend fun ingestAll(raws: List<RawSms>): IngestResult {
        var inserted = 0
        var duplicates = 0
        var rejected = 0
        val codes = linkedSetOf<String>()
        raws.chunked(Deriver.CHUNK).forEach { chunk ->
            db.withTransaction {
                val chunkCodes = linkedSetOf<String>()
                for (raw in chunk) {
                    if (!MpesaParser.isMpesaSender(raw.sender)) { rejected++; continue }
                    val s = ParseStatus.of(MpesaParser.parse(raw.body, raw.receivedAt))
                    val id = db.smsDao().insertIgnore(
                        SmsRow(
                            sender = raw.sender, body = raw.body, bodyHash = SmsText.hash(raw.body), receivedAt = raw.receivedAt,
                            subscriptionId = raw.subscriptionId, lineId = raw.lineId, code = s.code, source = raw.source,
                            status = s.status, statusReason = s.reason, parserVersion = MpesaParser.VERSION,
                        ),
                    )
                    if (id == -1L) {
                        duplicates++
                    } else {
                        inserted++
                        if (s.status == SmsStatus.PARSED) chunkCodes += s.code!!
                    }
                }
                deriver.rederive(chunkCodes) // joins this transaction
                codes += chunkCodes
            }
        }
        return IngestResult(inserted, duplicates, rejected, codes)
    }
}
