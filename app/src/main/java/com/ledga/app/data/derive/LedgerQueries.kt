package com.ledga.app.data.derive

import androidx.paging.PagingSource
import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.LineBalance
import com.ledga.app.data.room.TxRow
import com.ledga.app.data.room.dao.CategoryTotal
import com.ledga.core.derive.SearchText
import com.ledga.core.money.Money
import com.ledga.core.time.InstantRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The read side screens use (spec §7.5). Ranges are `:core` InstantRanges; an open range (live period) has a null end. */
class LedgerQueries(private val db: LedgaDatabase) {

    fun spent(range: InstantRange, lineId: Long? = null): Flow<Money> =
        db.ledgerDao().spent(range.start, range.endExclusive, lineId).map(::Money)

    fun moneyIn(range: InstantRange, lineId: Long? = null): Flow<Money> =
        db.ledgerDao().moneyIn(range.start, range.endExclusive, lineId).map(::Money)

    fun spentByCategory(range: InstantRange, lineId: Long? = null): Flow<List<CategoryTotal>> =
        db.ledgerDao().spentByCategory(range.start, range.endExclusive, lineId)

    suspend fun combinedBalance(): Money? = combine(db.transactionsDao().latestBalances())

    /** [query] as typed; matched against `searchText` (names, phone, code, account, note, amount spellings). */
    fun transactions(lineId: Long?, query: String?): PagingSource<Int, TxRow> =
        db.transactionsDao().page(lineId, query?.let(::likePattern))

    companion object {
        /** Σ of each line's latest balance; with no line attribution at all, the latest overall (§7.5). */
        fun combine(latest: List<LineBalance>): Money? {
            val attributed = latest.filter { it.lineId != null }
            val pick = attributed.ifEmpty { latest }
            return if (pick.isEmpty()) null else Money(pick.sumOf { it.balanceCents })
        }

        /** Normalised like `searchText`, with LIKE's wildcards escaped; null for a blank query. */
        fun likePattern(query: String): String? {
            val q = SearchText.normalizeQuery(query).takeIf { it.isNotEmpty() } ?: return null
            val escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            return "%$escaped%"
        }
    }
}
