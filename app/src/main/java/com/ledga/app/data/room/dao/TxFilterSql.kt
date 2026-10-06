package com.ledga.app.data.room.dao

/**
 * The Transactions filter (`TransactionFilter`, R38) on alias `t`. The list (`TransactionsDao.page`) and its day totals
 * (`LedgerDao.dayTotals`) share it, so a day header always sums exactly the rows under it. Its parameters, in order:
 * includeHidden, lineId, like, flow, anyCategory, categories, from, to, minCents, counterpartyKey.
 */
internal const val TX_FILTER =
    "(:includeHidden OR t.isHidden = 0) " +
        "AND (:lineId IS NULL OR t.lineId = :lineId) " +
        "AND (:like IS NULL OR t.searchText LIKE :like ESCAPE '\\') " +
        "AND (:flow = 'ALL' OR (:flow = 'OUT' AND t.flow = 'SPEND') OR (:flow = 'IN' AND t.flow = 'INCOME') " +
        "OR (:flow = 'FULIZA' AND (t.fulizaDrawnCents IS NOT NULL " +
        "OR t.kind IN ('FULIZA_REPAY_AUTO', 'FULIZA_REPAY_MANUAL', 'FULIZA_REVERSAL', 'FULIZA_ONLY')))) " +
        "AND (:anyCategory OR t.categoryKey IN (:categories)) " +
        "AND (:from IS NULL OR t.occurredAt >= :from) AND (:to IS NULL OR t.occurredAt < :to) " +
        "AND (:minCents IS NULL OR t.amountCents >= :minCents) " +
        "AND (:counterpartyKey IS NULL OR t.counterpartyKey = :counterpartyKey)"

/**
 * Nairobi is UTC+3 all year and has been for decades (no daylight saving, R45). A Nairobi day or month in SQL is the
 * UTC epoch millisecond shifted by three hours: `(occurredAt + 10800000) / 86400000` is the Nairobi epoch day.
 */
internal const val NAIROBI_OFFSET_MS = "10800000"
