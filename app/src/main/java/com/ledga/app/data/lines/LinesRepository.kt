package com.ledga.app.data.lines

import com.ledga.app.data.room.LineRow
import com.ledga.app.data.room.dao.LinesDao
import kotlinx.coroutines.flow.Flow
import java.time.Clock

/**
 * M-Pesa lines (spec §9.1–9.2), ported from v1's AccountsRepository (R26). A line is created on first sight of a
 * subscription id, re-linked by phone number when a SIM's id changes (slot move, eSIM re-provisioning), and never
 * renamed, recoloured or deleted by the app: user-set fields are the user's.
 */
class LinesRepository(
    private val dao: LinesDao,
    private val sims: SimDirectory,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun observe(): Flow<List<LineRow>> = dao.observeAll()

    suspend fun all(): List<LineRow> = dao.all()

    /**
     * A message's usable subscription id. Some phones omit it; then the only active SIM sent it. With two or more
     * active SIMs Ledga never guesses (spec §9.1): the message stays unattributed.
     */
    fun resolve(subscriptionId: Int?): Int? =
        subscriptionId?.takeIf { it >= 0 } ?: sims.active().singleOrNull()?.subscriptionId

    /** The line for [subscriptionId] (already resolved), created on first sight; null when there is no subscription id. */
    suspend fun lineFor(subscriptionId: Int?): Long? {
        val sub = subscriptionId?.takeIf { it >= 0 } ?: return null
        dao.bySubscription(sub)?.let { return it.id }
        // An id a re-linked SIM used to have: its older messages still carry it, and they belong to the same line.
        dao.lineOfPastMessages(sub)?.let { return it }
        val sim = sims.find(sub)
        val all = dao.all()
        // A SIM that moved slot gets a new subscription id but is the same line, with the same history.
        val number = sim?.number
        if (number != null) {
            all.firstOrNull { it.phoneNumber == number }?.let { existing ->
                dao.update(existing.copy(subscriptionId = sub))
                return existing.id
            }
        }
        val id = dao.insert(
            LineRow(
                subscriptionId = sub,
                phoneNumber = number,
                displayName = sim?.displayName ?: autoName(all),
                color = COLORS[all.size % COLORS.size],
                isPrimary = all.isEmpty(),
                createdAt = clock.instant(),
            ),
        )
        return if (id == -1L) dao.bySubscription(sub)?.id else id
    }

    /** Every app start: re-link SIMs whose id changed (by number) and fill numbers that became readable. */
    suspend fun syncActive() {
        val active = sims.active()
        if (active.isEmpty()) return
        val all = dao.all()
        for (sim in active) {
            val byId = all.firstOrNull { it.subscriptionId == sim.subscriptionId }
            if (byId != null) {
                if (byId.phoneNumber == null && sim.number != null) dao.update(byId.copy(phoneNumber = sim.number))
                continue
            }
            val number = sim.number ?: continue
            all.firstOrNull { it.phoneNumber == number }?.let { dao.update(it.copy(subscriptionId = sim.subscriptionId)) }
        }
    }

    /** R65: the person's name for a line, spaces collapsed, cut to [NAME_MAX]. False (nothing saved) for a blank name. */
    suspend fun rename(id: Long, name: String): Boolean {
        val clean = name.replace(WS, " ").trim().take(NAME_MAX).trim()
        if (clean.isEmpty()) return false
        dao.rename(id, clean)
        return true
    }

    companion object {
        /** The name a line gets when the SIM can't name it. */
        val AUTO_NAME = Regex("""Line (\d+)""")

        /**
         * "Line N", one above the highest automatic number in use (final review M5): a merge can leave Line 1 + Line 3,
         * and counting the lines would name the next one Line 3 again.
         */
        fun autoName(all: List<LineRow>): String {
            val highest = all.mapNotNull { AUTO_NAME.matchEntire(it.displayName)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
            return "Line ${maxOf(highest, all.size) + 1}"
        }

        /** New lines' colours, in order: the chart colours of palette C's first four hues. */
        val COLORS = listOf("#0E9F6E", "#1E7FD8", "#8A4FC7", "#E0457B")

        /** R65: a line's name. */
        const val NAME_MAX = 24
        private val WS = Regex("\\s+")
    }
}
