package com.ledga.app.testing

import com.ledga.app.data.lines.Sim
import com.ledga.app.data.lines.SimDirectory

/** The phone's SIMs as a test sets them; empty = no READ_PHONE_STATE. */
class FakeSims : SimDirectory {
    private val sims = mutableListOf<Sim>()

    fun add(sim: Sim) {
        sims += sim
    }

    fun clear() = sims.clear()

    override fun active(): List<Sim> = sims.toList()

    override fun find(subscriptionId: Int): Sim? = sims.firstOrNull { it.subscriptionId == subscriptionId }
}
