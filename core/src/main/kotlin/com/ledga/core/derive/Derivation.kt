package com.ledga.core.derive

import com.ledga.core.model.Categories
import com.ledga.core.model.FlowKind
import com.ledga.core.model.KindDefaults
import com.ledga.core.model.TxKind
import com.ledga.core.money.Money
import com.ledga.core.parse.Counterparty
import java.time.Instant
import java.time.LocalDate

/** User intent for one M-Pesa code. Survives every rebuild (Phase 2 `overrides` table). */
data class Override(
    val code: String,
    val categoryKey: String? = null,
    val note: String? = null,
    val lineId: Long? = null,
    /** Tri-state: null = follow own-account rules. */
    val ownAccount: Boolean? = null,
    val hidden: Boolean = false,
)

/** One derived transaction: the Phase 2 `transactions` row minus its id. Fully rebuildable from SMS + overrides + rules. */
data class DerivedTx(
    val code: String,
    val lineId: Long?,
    val occurredAt: Instant,
    val occurredAtApprox: Boolean,
    val kind: TxKind,
    val flow: FlowKind,
    val amount: Money,
    /** All fees paid: transaction cost + Fuliza access fee. */
    val fee: Money,
    val balance: Money?,
    val counterpartyName: String?,
    val counterpartyPhone: String?,
    val counterpartyAccount: String?,
    val counterpartyKey: String?,
    val destinationCountry: String?,
    val reversesCode: String?,
    val isReversed: Boolean,
    val fulizaDrawn: Money?,
    val fulizaFee: Money?,
    val fulizaOutstanding: Money?,
    val fulizaLimit: Money?,
    val fulizaDueDate: LocalDate?,
    val categoryKey: String,
    val note: String?,
    val isHidden: Boolean,
    val searchText: String,
    val smsCount: Int,
)

object Derivation {
    /** Bump on any derivation behaviour change: Phase 2 rebuilds all transactions when it changes. */
    const val VERSION: Int = 1

    fun derive(sources: List<SourceSms>, override: Override?, rules: RuleEngine): DerivedTx {
        val a = Assembler.assemble(sources)
        require(override == null || override.code == a.code) { "override for ${override?.code} applied to ${a.code}" }
        val cp = a.counterparty
        val flow = flowFor(a.kind, cp, override, rules)
        return DerivedTx(
            code = a.code,
            lineId = override?.lineId ?: a.lineId,
            occurredAt = a.occurredAt,
            occurredAtApprox = a.occurredAtApprox,
            kind = a.kind,
            flow = flow,
            amount = a.amount,
            fee = a.fee,
            balance = a.balance,
            counterpartyName = cp?.name,
            counterpartyPhone = cp?.phone,
            counterpartyAccount = cp?.accountRef,
            counterpartyKey = cp?.key,
            destinationCountry = a.destinationCountry,
            reversesCode = a.reversesCode,
            isReversed = false,
            fulizaDrawn = a.fuliza?.drawn,
            fulizaFee = a.fulizaFee,
            fulizaOutstanding = a.fuliza?.outstanding,
            fulizaLimit = a.fuliza?.availableLimit,
            fulizaDueDate = a.fuliza?.dueDate,
            categoryKey = categoryFor(a.kind, flow, cp, override, rules),
            note = override?.note,
            isHidden = override?.hidden ?: false,
            searchText = SearchText.build(a.code, cp, override?.note, a.amount),
            smsCount = a.smsCount,
        )
    }

    /**
     * Re-resolve flow and category after a rule/category change. No re-parse; everything else is kept.
     * Recomputes ONLY flow and categoryKey (for rule/category changes). It ignores override.note / hidden /
     * lineId: when any of those change, re-derive the code with [derive].
     */
    fun reclassify(tx: DerivedTx, override: Override?, rules: RuleEngine): DerivedTx {
        val cp = Counterparty(tx.counterpartyName, tx.counterpartyPhone, tx.counterpartyAccount, null)
        val flow = flowFor(tx.kind, cp, override, rules)
        return tx.copy(flow = flow, categoryKey = categoryFor(tx.kind, flow, cp, override, rules))
    }

    private fun flowFor(kind: TxKind, cp: Counterparty?, override: Override?, rules: RuleEngine): FlowKind =
        Classifier.flowOf(kind, override?.ownAccount ?: rules.isOwnAccount(cp))

    private fun categoryFor(kind: TxKind, flow: FlowKind, cp: Counterparty?, override: Override?, rules: RuleEngine): String =
        override?.categoryKey
            ?: (if (flow == FlowKind.OWN_OUT || flow == FlowKind.OWN_IN) Categories.OWN_ACCOUNTS else null)
            ?: rules.categoryFor(cp, flow)
            ?: KindDefaults.categoryFor(kind)
}
