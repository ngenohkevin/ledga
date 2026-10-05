package com.ledga.core.derive

import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind

object Classifier {
    /** Own-account only re-flows kinds that can be own-account (sends/receipts); cash, airtime etc. keep their flow. */
    fun flowOf(kind: TxKind, ownAccount: Boolean): FlowKind =
        if (ownAccount) kind.ownAccountFlow ?: kind.defaultFlow else kind.defaultFlow
}
