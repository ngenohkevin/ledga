package com.ledga.app.ui.alerts

import com.ledga.app.data.alerts.AlertType
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** R71: what Alerts and the bell say. */
class AlertTextTest {
    private val today = LocalDate.parse("2026-10-06")

    @Test
    fun `the badge counts to nine, then says 9+, and the bell says how many are unread`() {
        assertNull(AlertText.badge(0))
        assertEquals("3", AlertText.badge(3))
        assertEquals("9+", AlertText.badge(12))
        assertEquals("Alerts", AlertText.bellLabel(0))
        assertEquals("Alerts, 12 unread", AlertText.bellLabel(12))
    }

    @Test
    fun `an unknown type reads as OTHER, and TalkBack hears New first`() {
        assertEquals(AlertType.OTHER, AlertType.of("SOMETHING_FROM_A_NEWER_VERSION"))
        assertEquals(AlertType.FULIZA_DRAW, AlertType.of("FULIZA_DRAW"))
        val a = AlertUi("large:TJK4AB12LA", AlertType.LARGE, "Ksh 12,350 to Jane Tester", "A large payment.", Instant.parse("2026-10-06T05:40:00Z"), isNew = true, code = "TJK4AB12LA")
        assertEquals("New. Ksh 12,350 to Jane Tester. A large payment. 8:40 AM", AlertText.speech(a, today))
    }
}
