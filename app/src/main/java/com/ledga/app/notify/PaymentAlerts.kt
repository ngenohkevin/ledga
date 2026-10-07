package com.ledga.app.notify

import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.TxRow
import com.ledga.app.data.settings.SettingsStore
import com.ledga.core.model.FlowKind
import java.time.Clock
import java.time.Duration

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
    /** How many alerts were written. */
    suspend fun check(codes: Collection<String>): Int {
        val s = settings.current()
        if (!s.notifyLarge && !s.notifyFuliza) return 0
        val oldest = clock.instant().minus(WINDOW)
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
        /** Spec §7.2 step 4: only payments from the last 2 hours. */
        val WINDOW: Duration = Duration.ofHours(2)

        /** R104: a spend of the threshold or more, by its amount (fees not counted); a reversed one isn't spending. */
        fun isLarge(tx: TxRow, thresholdCents: Long): Boolean =
            tx.flow == FlowKind.SPEND && !tx.isReversed && tx.amountCents >= thresholdCents
    }
}
