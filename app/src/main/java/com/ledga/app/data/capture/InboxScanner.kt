package com.ledga.app.data.capture

import com.ledga.app.data.derive.Deriver
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.room.SmsSource
import com.ledga.app.data.settings.SettingsStore
import java.time.Clock

enum class ScanMode { FULL, CATCH_UP }

data class ScanResult(val found: Int, val inserted: Int, val duplicates: Int, val newCodes: Set<String>)

/**
 * The inbox into the ledger (spec §9.1). FULL reads everything: onboarding's Import, You → Rescan, and the
 * post-migration rescan that re-ingests the payments v1 dropped. CATCH_UP reads from six hours before the newest
 * message already scanned: the start-up pass for anything the receiver missed. Duplicates cost nothing, because
 * `SmsIngestor` dedupes by body hash, and the overlap is what makes the catch-up safe.
 */
class InboxScanner(
    private val inbox: InboxSource,
    private val lines: LinesRepository,
    private val ingestor: SmsIngestor,
    private val settings: SettingsStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun scan(mode: ScanMode, progress: suspend (done: Int, total: Int) -> Unit = { _, _ -> }): ScanResult {
        val now = clock.millis()
        val stored = settings.current().smsWatermarkMillis
        // R157: a mark past now came from a message dated in the future (a phone clock set wrong) and says nothing
        // about what was read. This scan reads the whole inbox and puts the mark right.
        val ahead = stored > now
        val watermark = if (ahead) 0L else stored
        val since = if (mode == ScanMode.FULL || watermark == 0L) 0L else maxOf(0L, watermark - OVERLAP_MS)
        val found = inbox.read(since).sortedBy { it.receivedAt }
        val lineBySub = mutableMapOf<Int?, Long?>()
        var inserted = 0
        var duplicates = 0
        val codes = linkedSetOf<String>()
        progress(0, found.size)
        found.chunked(Deriver.CHUNK).forEachIndexed { i, chunk ->
            val raws = chunk.map { sms ->
                val sub = lines.resolve(sms.subscriptionId)
                if (!lineBySub.containsKey(sub)) lineBySub[sub] = lines.lineFor(sub)
                RawSms(sms.sender, sms.body, sms.receivedAt, sms.subscriptionId, lineBySub[sub], SmsSource.INBOX)
            }
            val r = ingestor.ingestAll(raws)
            inserted += r.inserted
            duplicates += r.duplicates
            codes += r.newCodes
            progress(minOf((i + 1) * Deriver.CHUNK, found.size), found.size)
        }
        // The mark never passes now, so a misdated message can't hide the ones that follow it.
        val newest = found.maxOfOrNull { it.receivedAt.toEpochMilli() }?.let { minOf(it, now) }
        if (ahead) settings.replaceWatermark(newest ?: 0L) else newest?.let { settings.advanceWatermark(it) }
        // Every message in the inbox has now been through the ingestor: the migration's rescan, if owed, is done.
        if (mode == ScanMode.FULL) settings.setFullRescanOwed(false)
        return ScanResult(found.size, inserted, duplicates, codes)
    }

    companion object {
        /** SMS `date` is receive time and lands out of order across SIMs and reboots; dedupe makes overlap free. */
        const val OVERLAP_MS = 6 * 60 * 60 * 1000L
    }
}
