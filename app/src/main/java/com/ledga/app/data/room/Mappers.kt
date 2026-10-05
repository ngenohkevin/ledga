package com.ledga.app.data.room

import com.ledga.core.derive.DerivedTx
import com.ledga.core.derive.Override
import com.ledga.core.derive.Rule
import com.ledga.core.money.Money
import java.time.Instant

fun DerivedTx.toRow(): TxRow = TxRow(
    code = code, lineId = lineId, occurredAt = occurredAt, occurredAtApprox = occurredAtApprox, kind = kind, flow = flow,
    amountCents = amount.cents, feeCents = fee.cents, balanceCents = balance?.cents,
    counterpartyName = counterpartyName, counterpartyPhone = counterpartyPhone, counterpartyAccount = counterpartyAccount,
    counterpartyKey = counterpartyKey, destinationCountry = destinationCountry, reversesCode = reversesCode, isReversed = isReversed,
    fulizaDrawnCents = fulizaDrawn?.cents, fulizaFeeCents = fulizaFee?.cents, fulizaOutstandingCents = fulizaOutstanding?.cents,
    fulizaLimitCents = fulizaLimit?.cents, fulizaDueDate = fulizaDueDate, categoryKey = categoryKey, note = note,
    isHidden = isHidden, searchText = searchText, smsCount = smsCount,
)

fun TxRow.toDerived(): DerivedTx = DerivedTx(
    code = code, lineId = lineId, occurredAt = occurredAt, occurredAtApprox = occurredAtApprox, kind = kind, flow = flow,
    amount = Money(amountCents), fee = Money(feeCents), balance = balanceCents?.let(::Money),
    counterpartyName = counterpartyName, counterpartyPhone = counterpartyPhone, counterpartyAccount = counterpartyAccount,
    counterpartyKey = counterpartyKey, destinationCountry = destinationCountry, reversesCode = reversesCode, isReversed = isReversed,
    fulizaDrawn = fulizaDrawnCents?.let(::Money), fulizaFee = fulizaFeeCents?.let(::Money),
    fulizaOutstanding = fulizaOutstandingCents?.let(::Money), fulizaLimit = fulizaLimitCents?.let(::Money),
    fulizaDueDate = fulizaDueDate, categoryKey = categoryKey, note = note, isHidden = isHidden,
    searchText = searchText, smsCount = smsCount,
)

fun OverrideRow.toCore(): Override = Override(code, categoryKey, note, lineId, ownAccount, hidden)

fun Override.toRow(updatedAt: Instant): OverrideRow = OverrideRow(code, categoryKey, note, lineId, ownAccount, hidden, updatedAt)

fun RuleRow.toCore(): Rule = Rule(id, field, pattern, action, categoryKey, origin, priority, createdAt, enabled)
