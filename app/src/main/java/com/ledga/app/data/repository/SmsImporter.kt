package com.ledga.app.data.repository

import com.ledga.app.data.parser.MpesaSmsParser
import com.ledga.app.data.parser.ParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(
    val total: Int,
    val imported: Int,
    val failed: Int,
    /** Distinct SIM lines the import attributed transactions to. */
    val accountsDetected: Int = 0,
)

@Singleton
class SmsImporter @Inject constructor(
    private val inbox: MpesaInbox,
    private val transactionRepository: TransactionRepository,
    private val accountsRepository: AccountsRepository,
    private val settingsRepository: SettingsRepository,
) {
    /** Full-history import: onboarding and the Settings re-import button. */
    suspend fun importHistory(
        onProgress: (imported: Int, total: Int) -> Unit = { _, _ -> }
    ): ImportResult = withContext(Dispatchers.IO) {
        importRows(inbox.read(), onProgress)
    }

    /**
     * Catch-up sync, run on every app start. The SMS receiver can miss
     * messages — OEM battery killers, force-stopped app, an update mid-SMS —
     * and without this the gap silently persists until the user finds the
     * manual re-import button. Incremental: only inbox rows newer than the
     * watermark (minus an overlap window) are scanned, and duplicate codes
     * are dropped by insertTransaction, so running it often is cheap.
     *
     * First run (watermark unset) does a full import — for installs that
     * predate the watermark this also fills any historical gaps in one go.
     */
    suspend fun catchUp(): ImportResult = withContext(Dispatchers.IO) {
        val watermark = settingsRepository.getSmsSyncWatermark().first()
        if (watermark == 0L) return@withContext importHistory()
        importRows(inbox.read(since = watermark - OVERLAP_MS))
    }

    private suspend fun importRows(
        smsList: List<MpesaInbox.Row>,
        onProgress: (imported: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportResult {
        val total = smsList.size
        var imported = 0
        var failed = 0

        // Attribute each SMS to its SIM at import time — the inbox keeps a
        // sub_id per message, so dual-SIM users get per-line history from
        // day one instead of needing a manual backfill afterwards.
        val accountIdBySub = mutableMapOf<Int, Long?>()
        suspend fun accountIdFor(rawSubscriptionId: Int): Long? {
            val sub = accountsRepository.resolveSubscriptionId(rawSubscriptionId)
            if (!accountIdBySub.containsKey(sub)) {
                accountIdBySub[sub] = accountsRepository.getOrCreateForSubscription(sub)?.id
            }
            return accountIdBySub[sub]
        }

        smsList.forEachIndexed { index, sms ->
            val result = MpesaSmsParser.parse(sms.body, sms.date)
            when (result) {
                is ParseResult.Success -> {
                    val id = transactionRepository.insertTransaction(
                        parsed = result.transaction,
                        accountId = accountIdFor(sms.subscriptionId),
                    )
                    if (id != -1L) imported++
                }
                is ParseResult.Failure -> failed++
            }
            onProgress(index + 1, total)
        }

        // Advance the watermark to the newest inbox row we processed so the
        // next catch-up only scans what arrived after this run.
        val previous = settingsRepository.getSmsSyncWatermark().first()
        val newest = smsList.maxOfOrNull { it.date } ?: 0L
        settingsRepository.setSmsSyncWatermark(
            maxOf(previous, newest, if (previous == 0L) System.currentTimeMillis() else 0L)
        )

        return ImportResult(
            total = total,
            imported = imported,
            failed = failed,
            accountsDetected = accountIdBySub.values.filterNotNull().distinct().size,
        )
    }

    companion object {
        /**
         * Re-scan window behind the watermark. SMS `date` is receive time
         * and can land slightly out of order across SIMs/reboots; the code
         * dedupe makes overlap free, missing a message is not.
         */
        private const val OVERLAP_MS = 6 * 60 * 60 * 1000L
    }
}
