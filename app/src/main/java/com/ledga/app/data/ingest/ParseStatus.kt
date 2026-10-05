package com.ledga.app.data.ingest

import com.ledga.app.data.room.SmsStatus
import com.ledga.core.parse.ParseOutcome

/** How a parse outcome is stored on its `sms` row. */
data class ParseStatus(val code: String?, val status: SmsStatus, val reason: String?) {
    companion object {
        fun of(outcome: ParseOutcome): ParseStatus = when (outcome) {
            is ParseOutcome.Parsed -> ParseStatus(outcome.sms.code, SmsStatus.PARSED, null)
            is ParseOutcome.Ignored -> ParseStatus(null, SmsStatus.IGNORED, outcome.reason.name)
            is ParseOutcome.Unreadable -> ParseStatus(null, SmsStatus.UNREADABLE, outcome.reason)
        }
    }
}
