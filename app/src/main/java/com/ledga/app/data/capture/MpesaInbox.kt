package com.ledga.app.data.capture

import android.content.ContentResolver
import android.database.sqlite.SQLiteException
import android.net.Uri
import com.ledga.core.parse.MpesaParser
import java.time.Instant

/** One M-Pesa message from the SMS inbox. [subscriptionId] is null when the phone doesn't record it. */
data class InboxSms(val sender: String, val body: String, val receivedAt: Instant, val subscriptionId: Int?)

/** M-Pesa messages received strictly after [sinceMillis] (0 = all), newest first. Empty without READ_SMS. */
fun interface InboxSource {
    fun read(sinceMillis: Long): List<InboxSms>
}

/**
 * `content://sms/inbox` (spec §9.1), ported from v1 (R26). The subscription column is `sub_id` on most phones and
 * `sim_id` on some Samsung/Xiaomi builds; some have neither. Each projection is tried in turn.
 */
class MpesaInbox(private val resolver: ContentResolver) : InboxSource {

    override fun read(sinceMillis: Long): List<InboxSms> =
        query(arrayOf("address", "body", "date", "sub_id"), sinceMillis)
            ?: query(arrayOf("address", "body", "date", "sim_id"), sinceMillis)
            ?: query(arrayOf("address", "body", "date"), sinceMillis)
            ?: emptyList()

    /** Null when this phone lacks a column (try the next projection); empty without SMS access. */
    private fun query(projection: Array<String>, since: Long): List<InboxSms>? = try {
        val selection = "address IN (${SENDERS.joinToString(",") { "?" }})" + if (since > 0) " AND date > ?" else ""
        val args = if (since > 0) SENDERS + since.toString() else SENDERS
        resolver.query(INBOX, projection, selection, args.toTypedArray(), "date DESC")?.use { c ->
            val address = c.getColumnIndex("address")
            val body = c.getColumnIndex("body")
            val date = c.getColumnIndex("date")
            val sub = if (projection.size > 3) c.getColumnIndex(projection[3]) else -1
            buildList {
                while (c.moveToNext()) {
                    val sender = c.getString(address) ?: continue
                    val text = c.getString(body) ?: continue
                    if (!MpesaParser.isMpesaSender(sender)) continue
                    val subscription = if (sub >= 0 && !c.isNull(sub)) c.getInt(sub).takeIf { it >= 0 } else null
                    add(InboxSms(sender, text, Instant.ofEpochMilli(c.getLong(date)), subscription))
                }
            }
        }
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: SQLiteException) {
        null
    } catch (e: SecurityException) {
        emptyList()
    }

    companion object {
        val INBOX: Uri = Uri.parse("content://sms/inbox")

        /** Spec §9.1 and §14: only these senders are ever read. */
        val SENDERS = listOf("MPESA", "M-PESA", "FULIZA")
    }
}
