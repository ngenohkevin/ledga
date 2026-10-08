package com.ledga.app.ui.design.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R148: a banner can be tapped as a whole, and its own actions stay their own. */
@RunWith(RobolectricTestRunner::class)
class BannerTapTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a tap on the text opens, a tap on an action does only that action`() {
        var opened = 0
        var later = 0
        var update = 0
        compose.setContent {
            LedgaTheme(appearance = Appearance.LIGHT, reducedMotion = true) {
                Banner(
                    "Ledga 2.0.1 is available", BannerTone.Info,
                    actionLabel = "Update", onAction = { update++ },
                    secondaryLabel = "Later", onSecondary = { later++ },
                    onClick = { opened++ },
                )
            }
        }
        compose.onNodeWithText("Ledga 2.0.1 is available").performClick()
        compose.onNodeWithText("Later").performClick()
        compose.onNodeWithText("Update").performClick()
        assertEquals(Triple(1, 1, 1), Triple(opened, later, update))
    }
}
