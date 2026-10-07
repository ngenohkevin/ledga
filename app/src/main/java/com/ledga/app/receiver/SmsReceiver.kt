package com.ledga.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import com.ledga.core.parse.MpesaParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
 * Spec §9.1: SMS_RECEIVED at priority 999 (manifest). Joins multi-part messages and hands them to [IncomingSms], which
 * stores them on their line and queues their alerts (spec §7.2 step 4, R112).
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {
    @Inject lateinit var incoming: IncomingSms

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
                incoming.store(messages, subscription)
            } catch (e: Exception) {
                // Nothing is lost: the next catch-up reads the same message from the inbox (without an alert, spec §11).
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
