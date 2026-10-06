package com.ledga.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import com.ledga.app.data.ingest.RawSms
import com.ledga.app.data.ingest.SmsIngestor
import com.ledga.app.data.lines.LinesRepository
import com.ledga.app.data.room.SmsSource
import com.ledga.core.parse.MpesaParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** One message, or one part of one, as SMS_RECEIVED delivers it. */
data class SmsPart(val sender: String, val body: String)

object ReceivedSms {
    /** Multi-part messages arrive as parts from one sender: join them in order. Only M-Pesa senders are kept (spec §14). */
    fun join(parts: List<SmsPart>): List<SmsPart> =
        parts.groupBy { it.sender }
            .filterKeys(MpesaParser::isMpesaSender)
            .map { (sender, group) -> SmsPart(sender, group.joinToString("") { it.body }) }

    /** The SIM from SMS_RECEIVED's extras: the modern index, then the older "subscription" key; null when absent. */
    fun subscriptionId(intent: Intent): Int? {
        val modern = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1)
        if (modern >= 0) return modern
        return intent.getIntExtra("subscription", -1).takeIf { it >= 0 }
    }
}

/**
 * Spec §9.1: SMS_RECEIVED at priority 999 (manifest). Joins multi-part messages, resolves the line and hands them to
 * `SmsIngestor`, which dedupes against the inbox scans. Phase 5 adds alerts for new codes here.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {
    @Inject lateinit var ingestor: SmsIngestor
    @Inject lateinit var lines: LinesRepository
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty()
            .map { SmsPart(it.displayOriginatingAddress.orEmpty(), it.displayMessageBody.orEmpty()) }
        val messages = ReceivedSms.join(parts)
        if (messages.isEmpty()) return
        val subscription = ReceivedSms.subscriptionId(intent)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val lineId = lines.lineFor(lines.resolve(subscription))
                val now = clock.instant()
                ingestor.ingestAll(messages.map { RawSms(it.sender, it.body, now, subscription, lineId, SmsSource.RECEIVER) })
            } catch (e: Exception) {
                // Nothing is lost: the next start-up catch-up reads the same message from the inbox.
                Log.w(TAG, "could not store an incoming M-Pesa SMS", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "Ledga"
    }
}
