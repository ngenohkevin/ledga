package com.ledga.app.data.repository

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.telephony.SubscriptionManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared reader for M-Pesa rows in Android's SMS inbox, including each
 * message's SIM subscription id when the OEM preserves it.
 *
 * The subscription column name varies:
 * - "sub_id" on AOSP and most OEMs (since ~API 22)
 * - some Samsung / Xiaomi builds use "sim_id" or omit it entirely
 * We probe both and fall back to a cursor without the column, in which case
 * every row reports [SubscriptionManager.INVALID_SUBSCRIPTION_ID].
 */
@Singleton
class MpesaInbox @Inject constructor(
    private val contentResolver: ContentResolver,
) {
    data class Row(
        val body: String,
        val date: Long,
        val subscriptionId: Int,
    )

    /** @param since only rows with a receive date strictly after this (0 = everything). */
    fun read(since: Long = 0L): List<Row> {
        val uri = Uri.parse("content://sms/inbox")
        readWithColumns(uri, arrayOf("body", "date", "sub_id"), since)?.let { return it }
        readWithColumns(uri, arrayOf("body", "date", "sim_id"), since)?.let { return it }
        return readWithColumns(uri, arrayOf("body", "date"), since).orEmpty()
    }

    private fun readWithColumns(uri: Uri, projection: Array<String>, since: Long): List<Row>? = try {
        // FULIZA: borrow confirmations can arrive from a dedicated sender id.
        val selection = if (since > 0) "address IN (?, ?) AND date > ?" else "address IN (?, ?)"
        val args = if (since > 0) arrayOf("MPESA", "FULIZA", since.toString())
        else arrayOf("MPESA", "FULIZA")
        val cursor: Cursor? = contentResolver.query(
            uri,
            projection,
            selection,
            args,
            "date DESC",
        )
        cursor?.use { c ->
            val bodyIdx = c.getColumnIndex("body")
            val dateIdx = c.getColumnIndex("date")
            val subIdx = projection.lastIndex
                .takeIf { projection.size > 2 }
                ?.let { c.getColumnIndex(projection[it]) }
                ?: -1
            val rows = mutableListOf<Row>()
            while (c.moveToNext()) {
                val body = if (bodyIdx >= 0) c.getString(bodyIdx) else continue
                val date = if (dateIdx >= 0) c.getLong(dateIdx) else 0L
                val sub = if (subIdx >= 0 && !c.isNull(subIdx)) c.getInt(subIdx)
                else SubscriptionManager.INVALID_SUBSCRIPTION_ID
                rows += Row(body = body, date = date, subscriptionId = sub)
            }
            rows
        }
    } catch (_: IllegalArgumentException) {
        // Projection contained an unknown column — try the next fallback.
        null
    } catch (_: SecurityException) {
        // READ_SMS not granted — caller should have checked, but be defensive.
        null
    }
}
