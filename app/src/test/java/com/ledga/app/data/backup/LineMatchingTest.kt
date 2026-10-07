package com.ledga.app.data.backup

import com.ledga.app.data.lines.Sim
import com.ledga.app.testing.BUSINESS
import com.ledga.app.testing.PERSONAL
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** R115 (owner call B): a backup's lines go to this phone's SIMs by device, then number, then one question. */
class LineMatchingTest {
    private val personal = LineEntry(1, subscriptionId = 1, phoneNumber = "0712345111", displayName = "Personal", color = "#0E9F6E", isPrimary = true, createdAt = 0)
    private val business = LineEntry(2, subscriptionId = 2, phoneNumber = null, displayName = "Business", color = "#1E7FD8", isPrimary = false, createdAt = 0)
    private val simA = Sim(5, "SIM 1", "+254712345111")
    private val simB = Sim(6, "eSIM 1", null)

    @Test
    fun `on the same phone each line keeps its own SIM, asking nothing`() {
        val plan = LineMatching.plan(listOf(personal, business), local = listOf(PERSONAL), sims = emptyList(), sameDevice = true)
        assertEquals(mapOf(1L to LineTarget.Existing(PERSONAL.id), 2L to LineTarget.OnSim(2, null)), plan.decided)
        assertEquals(emptyList(), plan.questions)
    }

    @Test
    fun `on another phone a number matches whatever way it is written, and the rest is asked once`() {
        val plan = LineMatching.plan(listOf(personal, business), local = emptyList(), sims = listOf(simA, simB), sameDevice = false)
        assertEquals(mapOf(1L to LineTarget.OnSim(5, "+254712345111")), plan.decided)
        assertEquals(listOf(LineQuestion(business, listOf(simA, simB))), plan.questions)
        assertFalse(plan.simsUnreadable)
    }

    @Test
    fun `a line already on this phone with the same number is that line`() {
        val plan = LineMatching.plan(listOf(personal), local = listOf(PERSONAL.copy(id = 9, subscriptionId = 5)), sims = listOf(simA), sameDevice = false)
        assertEquals(mapOf(1L to LineTarget.Existing(9)), plan.decided)
    }

    @Test
    fun `without the SIMs to list, an unmatched line stays its own and says why`() {
        val plan = LineMatching.plan(listOf(business), local = emptyList(), sims = emptyList(), sameDevice = false)
        assertEquals(mapOf(2L to LineTarget.Own), plan.decided)
        assertTrue(plan.simsUnreadable)
    }

    @Test
    fun `answers pick a SIM or keep the line its own, and an unanswered one stays its own`() {
        val plan = LineMatching.plan(listOf(business), local = listOf(BUSINESS.copy(id = 7, subscriptionId = 6)), sims = listOf(simA, simB), sameDevice = false)
        assertEquals(mapOf(2L to LineTarget.Existing(7)), LineMatching.resolve(plan, mapOf(2L to 6), listOf(BUSINESS.copy(id = 7, subscriptionId = 6))))
        assertEquals(mapOf(2L to LineTarget.OnSim(5, "+254712345111")), LineMatching.resolve(plan, mapOf(2L to 5), emptyList()))
        assertEquals(mapOf(2L to LineTarget.Own), LineMatching.resolve(plan, mapOf(2L to null), emptyList()))
        assertEquals(mapOf(2L to LineTarget.Own), LineMatching.resolve(plan, emptyMap(), emptyList()))
    }

    @Test
    fun `numbers match on their last nine digits, never when one is missing or short`() {
        assertTrue(LineMatching.sameNumber("0712345111", "+254 712 345 111"))
        assertFalse(LineMatching.sameNumber("0712345111", "0712345112"))
        assertFalse(LineMatching.sameNumber(null, "0712345111"))
        assertFalse(LineMatching.sameNumber("12345", "12345"))
    }
}
