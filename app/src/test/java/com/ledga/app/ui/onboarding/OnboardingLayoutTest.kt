package com.ledga.app.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.theme.LedgaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the welcome name field's label stays on one line at 1_3x on a 360 dp phone`() {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, 1.3f)) {
                LedgaTheme {
                    Box(Modifier.width(360.dp)) {
                        OnboardingScreen(OnboardingState(Step.WELCOME), {}, {}, {}, {}, {}, { _, _ -> }, {})
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(NAME_LABEL, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertEquals(1, layouts.single().lineCount, "a wrapped label runs into the outlined field's border")
    }

    @Test
    fun `when Android blocks SMS, the SMS step shows the way past it, with Open App info and Not now (R193)`() {
        var opened = 0
        var skipped = 0
        compose.setContent {
            LedgaTheme {
                OnboardingScreen(OnboardingState(Step.SMS, smsBlocked = true), {}, {}, {}, { skipped++ }, {}, { _, _ -> }, {}, onOpenAppInfo = { opened++ })
            }
        }
        compose.onNodeWithText("Android blocked SMS access").assertExists()
        compose.onNodeWithContentDescription("tap the three-dot menu, then Allow restricted settings", substring = true).assertExists()
        compose.onNodeWithText("Open App info").performClick()
        compose.onNodeWithText("Not now").performClick()
        assertEquals(1, opened)
        assertEquals(1, skipped)
    }

    private companion object {
        const val NAME_LABEL = "Your name (optional)"
    }
}
