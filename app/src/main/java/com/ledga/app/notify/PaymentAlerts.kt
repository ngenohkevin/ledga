package com.ledga.app.notify

import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.TxRow
import com.ledga.app.data.settings.SettingsStore
import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The receiver's alerts (spec §7.2 step 4, §11, R103, R104): for each new code, a large payment and a Fuliza draw, each
 * at most once (its key). Only the receiver's codes come here: imports and rescans never alert. Each payment is read as
 * it is now, so a Fuliza companion that came in meanwhile is part of it.
 */
class PaymentAlerts(
    private val db: LedgaDatabase,
    private val settings: SettingsStore,
    private val notifier: Notifier,
    private val clock: Clock,
) {
    /**
     * How many alerts were written. [receivedAt] is when the receiver got the SMS: the 2 hours run from then, so a job
     * Android held while the phone was idle still alerts, late rather than never (final review I4, owner 2026-10-07).
     */
    suspend fun check(codes: Collection<String>, receivedAt: Instant? = null): Int {
        val s = settings.current()
        if (!s.notifyLarge && !s.notifyFuliza) return 0
        val oldest = (receivedAt ?: clock.instant()).minus(WINDOW)
        var sent = 0
        for (code in codes.distinct()) {
            val tx = db.transactionsDao().get(code) ?: continue
            if (tx.isHidden || tx.occurredAt.isBefore(oldest)) continue
            if (s.notifyLarge && isLarge(tx, s.largeThresholdCents)) {
                if (notifier.send(AlertWords.large(tx, db.categoriesDao().get(tx.categoryKey)?.name))) sent++
            }
            if (s.notifyFuliza && (tx.fulizaDrawnCents ?: 0L) > 0L) {
                if (notifier.send(AlertWords.fulizaDraw(tx))) sent++
            }
        }
        return sent
    }

    companion object {
        /** Spec §7.2 step 4: only payments at most 2 hours old when their SMS arrived. */
        val WINDOW: Duration = Duration.ofHours(2)

        /**
         * R104: a spend of the threshold or more, by its amount (fees not counted); a reversed one isn't spending. A Fuliza
         * companion whose payment hasn't come yet is not the payment: its amount is only what Fuliza covered, and the
         * payment's own alert follows when its SMS does (final review I3).
         */
        fun isLarge(tx: TxRow, thresholdCents: Long): Boolean =
            tx.flow == FlowKind.SPEND && tx.kind != TxKind.FULIZA_ONLY && !tx.isReversed && tx.amountCents >= thresholdCents
    }
}
