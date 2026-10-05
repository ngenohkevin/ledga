package com.ledga.app.data.room

import androidx.room.DatabaseView
import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind
import java.time.Instant

object LedgerView {
    /**
     * THE spending definition in SQL (spec §5.3, `:core` Ledger): non-hidden rows only; a reversed row counts
     * nothing; spend = amount of SPEND, in = amount of INCOME, fee = every fee. Shared with MIGRATION_5_6,
     * which must create the view with exactly this text (Room compares view SQL verbatim).
     */
    const val QUERY = "SELECT code, lineId, occurredAt, kind, flow, categoryKey, counterpartyKey, amountCents, " +
        "CASE WHEN isReversed = 0 AND flow = 'SPEND' THEN amountCents ELSE 0 END AS spendCents, " +
        "CASE WHEN isReversed = 0 AND flow = 'INCOME' THEN amountCents ELSE 0 END AS inCents, " +
        "CASE WHEN isReversed = 0 THEN feeCents ELSE 0 END AS feeCents " +
        "FROM transactions WHERE isHidden = 0"
}

@DatabaseView(viewName = "ledger", value = LedgerView.QUERY)
data class LedgerRow(
    val code: String,
    val lineId: Long?,
    val occurredAt: Instant,
    val kind: TxKind,
    val flow: FlowKind,
    val categoryKey: String,
    val counterpartyKey: String?,
    val amountCents: Long,
    val spendCents: Long,
    val inCents: Long,
    val feeCents: Long,
)
