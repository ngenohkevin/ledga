package com.ledga.core.model

/** What an M-Pesa SMS describes. Produced by the parser (FULIZA_ONLY also by the Assembler). */
enum class TxKind {
    SEND, PAYBILL, BUY_GOODS, WITHDRAW_AGENT, WITHDRAW_ATM, DEPOSIT, RECEIVE,
    AIRTIME_SELF, AIRTIME_OTHER, GLOBAL_SEND, GLOBAL_RECEIVE, SAVINGS_OUT, SAVINGS_IN,
    FULIZA_REPAY_AUTO, FULIZA_REPAY_MANUAL, FULIZA_REVERSAL, REVERSAL,

    /** A Fuliza companion SMS; after assembly, a companion whose payment SMS is missing. */
    FULIZA_ONLY;

    val defaultFlow: FlowKind
        get() = when (this) {
            SEND, PAYBILL, BUY_GOODS, WITHDRAW_AGENT, WITHDRAW_ATM,
            AIRTIME_SELF, AIRTIME_OTHER, GLOBAL_SEND, FULIZA_ONLY -> FlowKind.SPEND
            RECEIVE, GLOBAL_RECEIVE, DEPOSIT -> FlowKind.INCOME
            SAVINGS_OUT -> FlowKind.SAVINGS_OUT
            SAVINGS_IN -> FlowKind.SAVINGS_IN
            FULIZA_REPAY_AUTO, FULIZA_REPAY_MANUAL -> FlowKind.LOAN_REPAY
            REVERSAL, FULIZA_REVERSAL -> FlowKind.REVERSAL_IN
        }

    /** The flow this kind takes when it is a move between the user's own accounts; null = cannot be. */
    val ownAccountFlow: FlowKind?
        get() = when (this) {
            SEND, PAYBILL, BUY_GOODS, GLOBAL_SEND -> FlowKind.OWN_OUT
            RECEIVE, GLOBAL_RECEIVE -> FlowKind.OWN_IN
            else -> null
        }
}
