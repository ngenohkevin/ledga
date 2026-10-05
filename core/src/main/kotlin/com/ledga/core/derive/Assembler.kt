package com.ledga.core.derive

import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import com.ledga.core.parse.Counterparty
import com.ledga.core.parse.FulizaFacts
import com.ledga.core.parse.ParsedSms
import java.time.Instant

/** One stored, successfully parsed `sms` row. */
data class SourceSms(
    val smsId: Long,
    val parsed: ParsedSms,
    val receivedAt: Instant,
    val lineId: Long?,
)

/** Every SMS sharing one M-Pesa code, merged into one transaction (before classification). */
data class AssembledTx(
    val code: String,
    val kind: TxKind,
    val amount: Money,
    /** All fees paid: transaction cost + Fuliza access fee. */
    val fee: Money,
    /** The Fuliza access fee alone (null when no Fuliza was involved). */
    val fulizaFee: Money?,
    val balance: Money?,
    val occurredAt: Instant,
    val occurredAtApprox: Boolean,
    val counterparty: Counterparty?,
    val destinationCountry: String?,
    val reversesCode: String?,
    val fuliza: FulizaFacts?,
    val lineId: Long?,
    val smsCount: Int,
)

object Assembler {
    private val ORDER = compareBy<SourceSms>({ it.receivedAt }, { it.smsId })

    fun assemble(sources: List<SourceSms>): AssembledTx {
        require(sources.isNotEmpty()) { "no SMS to assemble" }
        val code = sources.first().parsed.code
        require(sources.all { it.parsed.code == code }) { "mixed codes in one assembly" }

        val sorted = sources.sortedWith(ORDER)
        val (companions, payments) = sorted.partition { it.parsed.kind == TxKind.FULIZA_ONLY }
        val primary = payments.firstOrNull() ?: companions.first()
        val facts = companions.fold(primary.parsed.fuliza ?: FulizaFacts()) { acc, c -> acc.mergedWith(c.parsed.fuliza) }
            .takeUnless { it.isEmpty }
        val accessFee = facts?.accessFee
        val p = primary.parsed

        return if (payments.isNotEmpty()) {
            AssembledTx(
                code = code,
                kind = p.kind,
                amount = p.amount,
                fee = p.fee + (accessFee ?: Money.ZERO),
                fulizaFee = accessFee,
                balance = p.balance,
                occurredAt = p.occurredAt,
                occurredAtApprox = p.occurredAtApprox,
                counterparty = p.counterparty,
                destinationCountry = p.destinationCountry,
                reversesCode = p.reversesCode,
                fuliza = facts,
                lineId = primary.lineId ?: sorted.firstNotNullOfOrNull { it.lineId },
                smsCount = sources.size,
            )
        } else {
            AssembledTx(
                code = code,
                kind = TxKind.FULIZA_ONLY,
                amount = facts?.drawn ?: p.amount,
                fee = accessFee ?: Money.ZERO,
                fulizaFee = accessFee,
                balance = null,
                occurredAt = p.occurredAt,
                occurredAtApprox = p.occurredAtApprox,
                counterparty = null,
                destinationCountry = null,
                reversesCode = null,
                fuliza = facts,
                lineId = primary.lineId ?: sorted.firstNotNullOfOrNull { it.lineId },
                smsCount = sources.size,
            )
        }
    }
}
