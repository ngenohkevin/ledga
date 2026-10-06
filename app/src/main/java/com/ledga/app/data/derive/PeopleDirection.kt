package com.ledga.app.data.derive

import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind

/**
 * People (spec §10.4, R42): who you send money to, or receive it from. Paybills, tills and own-account moves aren't
 * people, so only these kinds and flows count.
 */
enum class PeopleDirection(val flow: FlowKind, val kinds: List<TxKind>) {
    SENT(FlowKind.SPEND, listOf(TxKind.SEND, TxKind.GLOBAL_SEND)),
    RECEIVED(FlowKind.INCOME, listOf(TxKind.RECEIVE, TxKind.GLOBAL_RECEIVE)),
}
