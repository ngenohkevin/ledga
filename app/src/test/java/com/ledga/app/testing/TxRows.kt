package com.ledga.app.testing

import com.ledga.app.data.room.TxRow
import com.ledga.core.model.Categories
import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind
import com.ledga.core.parse.Counterparty
import java.time.Instant
import java.time.LocalDate

/**
 * A synthetic `transactions` row for UI tests (the repo is public: invented names, numbers and codes). The default is
 * an invented KPLC payment: Mon 5 Oct 2026, 2:15 PM in Nairobi, Ksh 1,000, balance after Ksh 23,150.75, line 1. Never
 * copy a value from the mockups: they carry the owner's real data.
 */
fun txRow(
    code: String = "TJK4AB12FA",
    kind: TxKind = TxKind.PAYBILL,
    flow: FlowKind = kind.defaultFlow,
    amountCents: Long = 100_000,
    feeCents: Long = 0,
    name: String? = "KPLC PREPAID",
    phone: String? = null,
    account: String? = "37100000001",
    at: Instant = Instant.parse("2026-10-05T11:15:00Z"),
    categoryKey: String = Categories.ELECTRICITY,
    balanceCents: Long? = 2_315_075,
    lineId: Long? = 1,
    note: String? = null,
    hidden: Boolean = false,
    reversed: Boolean = false,
    approx: Boolean = false,
    fulizaDrawnCents: Long? = null,
    fulizaFeeCents: Long? = null,
    fulizaOutstandingCents: Long? = null,
    fulizaLimitCents: Long? = null,
    fulizaDueDate: LocalDate? = null,
    country: String? = null,
    reversesCode: String? = null,
    smsCount: Int = 1,
): TxRow = TxRow(
    code = code,
    lineId = lineId,
    occurredAt = at,
    occurredAtApprox = approx,
    kind = kind,
    flow = flow,
    amountCents = amountCents,
    feeCents = feeCents,
    balanceCents = balanceCents,
    counterpartyName = name,
    counterpartyPhone = phone,
    counterpartyAccount = account,
    counterpartyKey = Counterparty(name, phone, account, null).key,
    destinationCountry = country,
    reversesCode = reversesCode,
    isReversed = reversed,
    fulizaDrawnCents = fulizaDrawnCents,
    fulizaFeeCents = fulizaFeeCents,
    fulizaOutstandingCents = fulizaOutstandingCents,
    fulizaLimitCents = fulizaLimitCents,
    fulizaDueDate = fulizaDueDate,
    categoryKey = categoryKey,
    note = note,
    isHidden = hidden,
    searchText = "",
    smsCount = smsCount,
)

/** A Fuliza-funded send to JANE TESTER (mockup `txsheet-fz`): Ksh 2,500, Fuliza covered Ksh 463, owed Ksh 6,418.36. */
fun fulizaTxRow(): TxRow = txRow(
    code = "TJK4AB12EA",
    kind = TxKind.SEND,
    amountCents = 250_000,
    feeCents = 1_163,
    name = "JANE TESTER",
    phone = "0712345111",
    account = null,
    categoryKey = Categories.SENT_TO_PEOPLE,
    balanceCents = 0,
    fulizaDrawnCents = 46_300,
    fulizaFeeCents = 463,
    fulizaOutstandingCents = 641_836,
    fulizaDueDate = LocalDate.parse("2026-11-02"),
    smsCount = 2,
)
