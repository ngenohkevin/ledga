package com.ledga.app.data.backup

import com.ledga.app.data.lines.Sim
import com.ledga.app.data.room.LineRow

/** Where a backup's line goes on this phone (R115). */
sealed interface LineTarget {
    /** A line this phone already has. */
    data class Existing(val lineId: Long) : LineTarget

    /** A new line for one of this phone's SIMs. */
    data class OnSim(val subscriptionId: Int, val number: String?) : LineTarget

    /** A line of its own, with no live SIM: its history stays together. */
    data object Own : LineTarget
}

/** "Which SIM was <line>?" with this phone's [sims] to choose from, and "Keep it as its own line". */
data class LineQuestion(val line: LineEntry, val sims: List<Sim>)

/** [simsUnreadable]: some line had nothing to match against because Ledga can't see this phone's SIMs (no phone access). */
data class LinePlan(val decided: Map<Long, LineTarget>, val questions: List<LineQuestion>, val simsUnreadable: Boolean)

/**
 * Owner call B (R115). On the same phone (the backup's device fingerprint is this phone's), a line keeps its SIM id.
 * Otherwise a line or SIM with the same number is it (spec §9.2); otherwise the person is asked once, given this phone's
 * SIMs. With no SIMs to list there is nothing to ask: the line stays its own.
 */
object LineMatching {
    fun plan(backup: List<LineEntry>, local: List<LineRow>, sims: List<Sim>, sameDevice: Boolean): LinePlan {
        val decided = linkedMapOf<Long, LineTarget>()
        val questions = mutableListOf<LineQuestion>()
        var unreadable = false
        for (b in backup.sortedBy { it.id }) {
            val sub = b.subscriptionId
            val target: LineTarget? = if (sameDevice && sub != null) {
                onSim(sub, b.phoneNumber, local)
            } else {
                local.firstOrNull { sameNumber(it.phoneNumber, b.phoneNumber) }?.let { LineTarget.Existing(it.id) }
                    ?: sims.firstOrNull { sameNumber(it.number, b.phoneNumber) }?.let { onSim(it.subscriptionId, it.number, local) }
            }
            when {
                target != null -> decided[b.id] = target
                sims.isNotEmpty() -> questions += LineQuestion(b, sims)
                else -> {
                    decided[b.id] = LineTarget.Own
                    unreadable = true
                }
            }
        }
        return LinePlan(decided, questions, unreadable)
    }

    /** [answers]: backup line id → the chosen SIM's subscription id, or null for "Keep it as its own line" (also when unanswered). */
    fun resolve(plan: LinePlan, answers: Map<Long, Int?>, local: List<LineRow>): Map<Long, LineTarget> =
        plan.decided + plan.questions.associate { q ->
            val sub = answers[q.line.id]
            q.line.id to (sub?.let { s -> onSim(s, q.sims.firstOrNull { it.subscriptionId == s }?.number, local) } ?: LineTarget.Own)
        }

    /** The same phone number however it is written: the last nine digits ("0712…", "+254 712…"). */
    fun sameNumber(a: String?, b: String?): Boolean {
        val x = a?.filter(Char::isDigit)?.takeLast(9) ?: return false
        val y = b?.filter(Char::isDigit)?.takeLast(9) ?: return false
        return x.length == 9 && x == y
    }

    private fun onSim(subscriptionId: Int, number: String?, local: List<LineRow>): LineTarget =
        local.firstOrNull { it.subscriptionId == subscriptionId }?.let { LineTarget.Existing(it.id) } ?: LineTarget.OnSim(subscriptionId, number)
}
