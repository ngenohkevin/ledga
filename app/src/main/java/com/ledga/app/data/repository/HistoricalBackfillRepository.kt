package com.ledga.app.data.repository

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
    private val inbox: MpesaInbox,
    private val transactionDao: TransactionDao,
    private val accountsRepository: AccountsRepository,
) {

    /** Code regex matches the parser — 10 alphanumeric at the message start. */
    private val codeRegex = Regex("""^([A-Z0-9]{10})\s""")

    /**
     * @param onlyUnassigned when true, only rows whose accountId is still
     * NULL are updated — manual per-transaction and date-range attributions
     * survive. Used by the silent on-upgrade backfill; the user-initiated
     * button keeps the overwrite-everything behavior (it's their explicit
     * call to re-derive attribution from the SMS DB).
     */
    suspend fun runAutoBackfill(
        onlyUnassigned: Boolean = false,
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> },
    ): BackfillResult = withContext(Dispatchers.IO) {
        val rows = inbox.read()
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
                taggedTotal += if (onlyUnassigned) {
                    transactionDao.updateAccountForCodesIfUnassigned(account.id, chunk)
                } else {
                    transactionDao.updateAccountForCodes(account.id, chunk)
                }
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

}
