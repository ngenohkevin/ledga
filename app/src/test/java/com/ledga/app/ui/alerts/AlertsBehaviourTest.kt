package com.ledga.app.ui.alerts

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.ledga.app.data.alerts.AlertType
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class AlertsBehaviourTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.parse("2026-10-06")
    private val large = AlertUi("large:TJK4AB12LA", AlertType.LARGE, "Ksh 12,350 to Jane Tester", "A large payment.", Instant.parse("2026-10-06T05:40:00Z"), true, "TJK4AB12LA")
    private val daily = AlertUi("daily:2026-10-05", AlertType.DAILY, "Spent Ksh 4,120 yesterday", "Across 3 payments.", Instant.parse("2026-10-05T17:00:00Z"), false, null)

    @Test
    fun `a payment alert opens its payment, and a summary opens nothing`() {
        val opened = mutableListOf<String>()
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                AlertsContent(AlertsUi(true, listOf(large, daily), today), AlertsActions(onOpenTx = { opened += it }))
            }
        }
        compose.onNodeWithContentDescription(AlertText.speech(large, today)).performClick()
        assertEquals(listOf("TJK4AB12LA"), opened)
        compose.onNodeWithContentDescription(AlertText.speech(daily, today)).assertHasNoClickAction()
    }

    @Test
    fun `Back leaves Alerts`() {
        var back = 0
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) { AlertsContent(AlertsUi(true, emptyList(), today), AlertsActions(onBack = { back++ })) }
        }
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, back)
    }
}
