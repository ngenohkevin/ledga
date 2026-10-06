package com.ledga.app.data.lines

import com.ledga.app.testing.FakeSims
import com.ledga.app.testing.TestDb
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class LinesRepositoryTest {
    private val db = TestDb.inMemory()
    private val sims = FakeSims()
    private val repo = LinesRepository(db.linesDao(), sims, Clock.fixed(Instant.parse("2026-10-06T07:00:00Z"), ZoneOffset.UTC))

    @Test
    fun `a new SIM becomes a line named after it, and the first line is primary`() = runTest {
        sims.add(Sim(3, "Safaricom", "0712000001"))
        val id = assertNotNull(repo.lineFor(3))
        val line = db.linesDao().all().single()
        assertEquals(id, line.id)
        assertEquals("Safaricom", line.displayName)
        assertEquals("0712000001", line.phoneNumber)
        assertTrue(line.isPrimary)
    }

    @Test
    fun `without phone access a new SIM is Line N`() = runTest {
        repo.lineFor(3)
        repo.lineFor(4)
        assertEquals(listOf("Line 1", "Line 2"), db.linesDao().all().map { it.displayName })
        assertEquals(listOf(true, false), db.linesDao().all().map { it.isPrimary })
    }

    @Test
    fun `the same subscription is always the same line, and no subscription is no line`() = runTest {
        assertEquals(repo.lineFor(3), repo.lineFor(3))
        assertEquals(1, db.linesDao().all().size)
        assertNull(repo.lineFor(null))
    }

    @Test
    fun `a SIM that moved slot is re-linked by its number, not duplicated`() = runTest {
        sims.add(Sim(3, "Safaricom", "0712000001"))
        val first = repo.lineFor(3)
        sims.clear()
        sims.add(Sim(7, "Safaricom", "0712000001"))
        assertEquals(first, repo.lineFor(7))
        assertEquals(7, db.linesDao().all().single().subscriptionId)
    }

    @Test
    fun `no subscription id means the only active SIM, and two SIMs are never guessed`() {
        assertNull(repo.resolve(null))
        sims.add(Sim(3, null, null))
        assertEquals(3, repo.resolve(null))
        assertEquals(3, repo.resolve(-1))
        assertEquals(5, repo.resolve(5))
        sims.add(Sim(4, null, null))
        assertNull(repo.resolve(null))
    }

    @Test
    fun `start-up sync fills a number that became readable and re-links a moved SIM`() = runTest {
        repo.lineFor(3) // created without phone access: no number
        sims.add(Sim(9, "Airtel", "0712000002"))
        repo.lineFor(9)
        sims.clear()
        sims.add(Sim(3, "Safaricom", "0712000001"))
        sims.add(Sim(11, "Airtel", "0712000002")) // the second SIM moved slot
        repo.syncActive()
        val bySub = db.linesDao().all().associateBy { it.subscriptionId }
        assertEquals("0712000001", bySub[3]?.phoneNumber)
        assertEquals("0712000002", bySub[11]?.phoneNumber)
        assertEquals(2, bySub.size)
        assertEquals("Line 1", bySub[3]?.displayName) // sync never renames
    }

    @Test
    fun `rename trims the name`() = runTest {
        val id = assertNotNull(repo.lineFor(3))
        repo.rename(id, "  Work  ")
        assertEquals("Work", db.linesDao().all().single().displayName)
    }
}
