package com.ledga.app.data.repository

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.telephony.SubscriptionManager
import com.ledga.app.data.db.dao.TransactionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class BackfillResult(
    /** Total MPESA SMS scanned in the system inbox. */
    val scanned: Int,
    /** Transactions in Ledga's DB that we matched + tagged. */
    val tagged: Int,
    /** SMS that referenced a transaction code not present in Ledga's DB. */
    val unmatched: Int,
    /** Distinct accounts created during the run. */
    val accountsCreated: Int,
)

data class DateRangeBackfillResult(val updated: Int)

/**
 * Two-prong historical backfill for multi-SIM attribution.
 *
 * 1. **Auto** — re-read Android's SMS inbox (which records `sub_id` per
 *    message), extract each transaction code from the body, then bulk-update
 *    the `accountId` on every matching row in Ledga's DB. This is the highest-
 *    fidelity backfill we can offer; it's lossless when the OEM keeps sub_id.
 *
 * 2. **Date range** — for stretches where Android's SMS history was wiped or
 *    the OEM didn't preserve sub_id, the user can bulk-attribute everything
 *    in a date range to a chosen account.
 *
 * Per-transaction overrides happen elsewhere (TransactionDetailSheet).
 */
@Singleton
class HistoricalBackfillRepository @Inject constructor(
    private val contentResolver: ContentResolver,
    private val transactionDao: TransactionDao,
    private val accountsRepository: AccountsRepository,
) {

    /** Code regex matches the parser — 10 alphanumeric at the message start. */
    private val codeRegex = Regex("""^([A-Z0-9]{10})\s""")

    suspend fun runAutoBackfill(
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> },
    ): BackfillResult = withContext(Dispatchers.IO) {
        val rows = readMpesaInbox()
        val total = rows.size
        if (total == 0) return@withContext BackfillResult(0, 0, 0, 0)

        // Group transaction codes by subscription id.
        val codesBySub = mutableMapOf<Int, MutableList<String>>()
        var scanned = 0
        var unmatched = 0

        for (row in rows) {
            scanned++
            onProgress(scanned, total)
            val code = codeRegex.find(row.body)?.groupValues?.get(1) ?: continue
            // Skip if the row's sub_id is meaningless.
            val sub = row.subscriptionId
            if (sub == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                unmatched++
                continue
            }
            codesBySub.getOrPut(sub) { mutableListOf() }.add(code)
        }

        val accountsBefore = accountsRepository.getAll().size
        var taggedTotal = 0

        for ((subId, codes) in codesBySub) {
            val account = accountsRepository.getOrCreateForSubscription(subId) ?: continue
            // Update in chunks to avoid SQLite's IN-list limit (~1000 typically).
            codes.chunked(500).forEach { chunk ->
                taggedTotal += transactionDao.updateAccountForCodes(account.id, chunk)
            }
        }

        val accountsCreated = accountsRepository.getAll().size - accountsBefore
        BackfillResult(
            scanned = scanned,
            tagged = taggedTotal,
            unmatched = unmatched,
            accountsCreated = accountsCreated,
        )
    }

    suspend fun runDateRangeBackfill(
        accountId: Long,
        startTime: Long,
        endTime: Long,
    ): DateRangeBackfillResult = withContext(Dispatchers.IO) {
        DateRangeBackfillResult(
            updated = transactionDao.updateAccountForRange(accountId, startTime, endTime)
        )
    }

    /**
     * Read MPESA SMS rows + their subscription column. The column name varies:
     * - "sub_id" on AOSP and most OEMs (since ~API 22)
     * - Some Samsung / Xiaomi builds use "sim_id" or omit it entirely
     * We probe both and fall back to a cursor without the column.
     */
    private fun readMpesaInbox(): List<InboxRow> {
        val uri = Uri.parse("content://sms/inbox")
        // Try sub_id first (the AOSP name).
        readWithColumns(uri, arrayOf("body", "date", "sub_id"))?.let { return it }
        // Fallback: sim_id (Samsung ROMs).
        readWithColumns(uri, arrayOf("body", "date", "sim_id"))?.let { return it }
        // No SIM column — still useful; sub_ids will all come back invalid.
        return readWithColumns(uri, arrayOf("body", "date")).orEmpty()
    }

    private fun readWithColumns(uri: Uri, projection: Array<String>): List<InboxRow>? = try {
        val cursor: Cursor? = contentResolver.query(
            uri,
            projection,
            // FULIZA: borrow confirmations can arrive from a dedicated sender id.
            "address IN (?, ?)",
            arrayOf("MPESA", "FULIZA"),
            "date DESC",
        )
        cursor?.use { c ->
            val bodyIdx = c.getColumnIndex("body")
            val dateIdx = c.getColumnIndex("date")
            val subIdx = projection.lastIndex
                .takeIf { projection.size > 2 }
                ?.let { c.getColumnIndex(projection[it]) }
                ?: -1
            val rows = mutableListOf<InboxRow>()
            while (c.moveToNext()) {
                val body = if (bodyIdx >= 0) c.getString(bodyIdx) else continue
                val date = if (dateIdx >= 0) c.getLong(dateIdx) else 0L
                val sub = if (subIdx >= 0) c.getInt(subIdx)
                else SubscriptionManager.INVALID_SUBSCRIPTION_ID
                rows += InboxRow(body = body, date = date, subscriptionId = sub)
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

    private data class InboxRow(
        val body: String,
        val date: Long,
        val subscriptionId: Int,
    )
}
