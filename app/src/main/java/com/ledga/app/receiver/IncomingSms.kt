package com.ledga.app.receiver

import com.ledga.app.data.ingest.IngestResult
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.room.SmsSource
import com.ledga.app.work.BackgroundWork
import java.time.Clock

/**
 * What the receiver does with one delivery (spec §9.1, §7.2 step 4, R112): resolve the line, store the messages
 * (deduped against the inbox scans by body hash), then queue alerts for the payments that are new. A message already
 * stored, by a scan or an earlier delivery, is a duplicate here and alerts nothing.
 */
class IncomingSms(
    private val lines: LinesRepository,
    private val ingestor: SmsIngestor,
    private val work: BackgroundWork,
    private val clock: Clock,
) {
    suspend fun store(messages: List<SmsPart>, subscriptionId: Int?): IngestResult {
        val lineId = lines.lineFor(lines.resolve(subscriptionId))
        val now = clock.instant()
        val result = ingestor.ingestAll(messages.map { RawSms(it.sender, it.body, now, subscriptionId, lineId, SmsSource.RECEIVER) })
        if (result.newCodes.isNotEmpty()) work.alertsFor(result.newCodes, now)
        return result
    }
}
