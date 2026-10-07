package com.ledga.app.data.lines

import com.ledga.app.data.room.LedgaDatabase
import com.ledga.app.data.room.toDerived
import com.ledga.core.derive.LinePlacer
import com.ledga.core.time.Nairobi
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** R116, R128, R129: where the payments not on a line could go. Writes go through `TransactionEdits`. */
class LinePlacements(private val db: LedgaDatabase) {
    /** [placed]: code → line; [byLine]: how many each line gets; [left]: what the balances can't place. */
    data class Proposal(val placed: Map<String, Long>, val byLine: Map<Long, Int>, val left: Int)

    /** Every payment (hidden ones too: the wallet moved) through [LinePlacer], over this phone's lines. */
    suspend fun propose(): Proposal {
        val lines = db.linesDao().all().map { it.id }.toSet()
        val txs = db.transactionsDao().all()
        return withContext(Dispatchers.Default) {
            val p = LinePlacer.place(txs.map { it.toDerived() }, lines)
            Proposal(p.placed, p.placed.values.groupingBy { it }.eachCount(), p.left)
        }
    }

    /** Payments not on a line from [from] to [to], whole Nairobi days, both ends. */
    suspend fun inDates(from: LocalDate, to: LocalDate): List<String> {
        val (a, b) = if (to.isBefore(from)) to to from else from to to
        return db.transactionsDao().unassignedIn(a.atStartOfDay(Nairobi.ZONE).toInstant(), b.plusDays(1).atStartOfDay(Nairobi.ZONE).toInstant())
    }

    fun observeUnassigned(): Flow<Int> = db.transactionsDao().observeUnassigned()
}
