package com.ledga.app.ui.you

import com.ledga.app.testing.BUSINESS
import com.ledga.app.testing.PERSONAL
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Test

/** What You's rows say (mockup `you`, R77, R81). Synthetic numbers. */
class YouTextTest {
    private val dots = Char(0x2026)

    @Test
    fun `the profile counts payments since the first one, all on this phone`() {
        assertEquals("6,385 payments · since Mar 2024 · all on this phone", YouText.profileLine(6_385, LocalDate.parse("2024-03-14")))
        assertEquals("1 payment · since Oct 2026 · all on this phone", YouText.profileLine(1, LocalDate.parse("2026-10-05")))
        assertEquals("No payments yet · all on this phone", YouText.profileLine(0, null))
    }

    @Test
    fun `the Money and Data rows sum up their screens`() {
        assertEquals("Personal ··11 · Business ··78", YouText.linesLine(listOf(PERSONAL, BUSINESS)))
        assertEquals("No lines yet", YouText.linesLine(emptyList()))
        assertEquals("None", YouText.unreadableLine(0))
        assertEquals("1 message", YouText.unreadableLine(1))
        assertEquals("3 messages", YouText.unreadableLine(3))
    }

    @Test
    fun `the rescan row says how it is going (R77)`() {
        assertEquals("Recovers anything missed", YouText.rescanLine(RescanState.Idle))
        assertEquals("Starting$dots", YouText.rescanLine(RescanState.Running(0, 0)))
        assertEquals("Reading 1,200 of 10,823$dots", YouText.rescanLine(RescanState.Running(1_200, 10_823)))
        assertEquals("Done · 12 new", YouText.rescanLine(RescanState.Done(12)))
        assertEquals("Done · nothing new", YouText.rescanLine(RescanState.Done(0)))
        assertEquals("Didn't finish. Try again.", YouText.rescanLine(RescanState.Failed))
    }
}
