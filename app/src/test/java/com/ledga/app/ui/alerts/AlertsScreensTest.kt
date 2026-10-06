package com.ledga.app.ui.alerts

import com.ledga.app.data.alerts.AlertType
import com.ledga.app.testing.snapScreen
import com.ledga.app.testing.snapScreenLandscape
import com.ledga.app.ui.app.ShellFrame
import java.time.Instant
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spec §15.2: Alerts (not mocked; R71), light/dark × 1.0/1.3, empty, landscape. Synthetic values only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class AlertsScreensTest {
    private val today = LocalDate.parse("2026-10-06")
    private val alerts = listOf(
        AlertUi("large:TJK4AB12LA", AlertType.LARGE, "Ksh 12,350 to Jane Tester", "A large payment from Personal ··11.", Instant.parse("2026-10-06T05:40:00Z"), true, "TJK4AB12LA"),
        AlertUi("fuliza-draw:TJK4AB12EA", AlertType.FULIZA_DRAW, "Fuliza covered Ksh 463", "Of a Ksh 2,500 payment. You owe Ksh 6,418.36, due 2 Nov.", Instant.parse("2026-10-05T14:10:00Z"), true, "TJK4AB12EA"),
        AlertUi("daily:2026-10-04", AlertType.DAILY, "Spent Ksh 4,120 on Sunday", "Across 3 payments. The biggest: Rubis, Ksh 3,060.", Instant.parse("2026-10-04T17:00:00Z"), false, null),
        AlertUi("weekly:2026-10-04", AlertType.WEEKLY, "This week: Ksh 18,240", "9% less than last week. Most went to Fuel.", Instant.parse("2026-10-04T16:00:00Z"), false, null),
        AlertUi("fuliza-due:1:2026-11-02:3d", AlertType.FULIZA_DUE, "Fuliza Ksh 6,418.36 due in 3 days", "Due 2 Nov on Personal ··11.", Instant.parse("2025-10-30T06:00:00Z"), false, null),
    )

    @Test
    fun alerts() = snapScreen("alerts") { ShellFrame(null, onSelect = {}) { AlertsContent(AlertsUi(true, alerts, today), AlertsActions()) } }

    @Test
    fun empty() = snapScreen("alerts_empty") { ShellFrame(null, onSelect = {}) { AlertsContent(AlertsUi(true, emptyList(), today), AlertsActions()) } }

    @Test
    fun landscape() = snapScreenLandscape("alerts") { ShellFrame(null, onSelect = {}) { AlertsContent(AlertsUi(true, alerts, today), AlertsActions()) } }
}
