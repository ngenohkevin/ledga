package com.ledga.app.notify

import com.ledga.app.data.room.FulizaReading
import com.ledga.core.model.TxKind
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Test

/** Spec §11, R105: which reminders are owed on a day. Synthetic readings. */
class FulizaRemindersTest {
    private val today = LocalDate.parse("2026-10-30") // a Friday

    private fun draw(code: String, line: Long?, owed: Long, due: String) =
        FulizaReading(code, line, TxKind.SEND, Instant.parse("2026-10-03T09:00:00Z"), 250_000, owed, null, LocalDate.parse(due))

    @Test
    fun `three days before and on the day, only while something is owed`() {
        assertEquals(
            listOf(FulizaDue(1, 641_836, LocalDate.parse("2026-11-02"), 3)),
            FulizaReminders.due(listOf(draw("TJK4AB12FA", 1, 641_836, "2026-11-02")), today),
        )
        assertEquals(0, FulizaReminders.due(listOf(draw("TJK4AB12FA", 1, 641_836, "2026-10-30")), today).single().daysLeft)
        assertEquals(emptyList(), FulizaReminders.due(listOf(draw("TJK4AB12FA", 1, 641_836, "2026-11-05")), today), "5 days away")
        assertEquals(emptyList(), FulizaReminders.due(listOf(draw("TJK4AB12FA", 1, 641_836, "2026-10-29")), today), "already past")
        val repaid = FulizaReading("TJK4AB12FF", 1, TxKind.FULIZA_REPAY_AUTO, Instant.parse("2026-10-20T09:00:00Z"), 641_836, null, 100_000, null)
        assertEquals(emptyList(), FulizaReminders.due(listOf(draw("TJK4AB12FA", 1, 641_836, "2026-11-02"), repaid), today), "repaid in full")
    }

    @Test
    fun `each line gets its own, and payments not on a line count only on a phone whose readings have no line`() {
        val readings = listOf(
            draw("TJK4AB12FA", 1, 641_836, "2026-11-02"),
            draw("TJK4AB12FB", 2, 120_000, "2026-10-30"),
            draw("TJK4AB12FC", null, 999_900, "2026-11-01"),
        )
        assertEquals(listOf(1L to 3, 2L to 0), FulizaReminders.due(readings, today).map { it.lineId to it.daysLeft })
        assertEquals(listOf(null to 2), FulizaReminders.due(listOf(readings[2]), today).map { it.lineId to it.daysLeft })
    }

    @Test
    fun `keys follow the spec, one for the days before and one for the day`() {
        val due = LocalDate.parse("2026-11-02")
        assertEquals("fuliza-due:1:2026-11-02:3d", FulizaReminders.key(FulizaDue(1, 641_836, due, 2)))
        assertEquals("fuliza-due:none:2026-11-02:0d", FulizaReminders.key(FulizaDue(null, 641_836, due, 0)))
    }
}
