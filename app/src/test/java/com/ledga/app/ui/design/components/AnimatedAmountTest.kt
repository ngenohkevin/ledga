package com.ledga.app.ui.design.components

import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@RunWith(RobolectricTestRunner::class)
class AnimatedAmountTest {
    @get:Rule val compose = createComposeRule()

    private fun show(reducedMotion: Boolean): MutableLongState {
        val cents = mutableLongStateOf(100_000L)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = reducedMotion) {
                AnimatedAmount(cents.longValue, Modifier.testTag("amount"))
            }
        }
        compose.mainClock.advanceTimeByFrame()
        return cents
    }

    private fun shown(): String =
        compose.onNodeWithTag("amount").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    @Test
    fun `with animations off a new amount shows at once`() {
        val cents = show(reducedMotion = true)
        assertEquals("Ksh 1,000", shown())
        Snapshot.withMutableSnapshot { cents.longValue = 250_000 }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertEquals("Ksh 2,500", shown())
    }

    @Test
    fun `with animations on the amount counts and lands exactly`() {
        val cents = show(reducedMotion = false)
        Snapshot.withMutableSnapshot { cents.longValue = 250_000 }
        compose.mainClock.advanceTimeBy(100)
        val midway = shown()
        assertNotEquals("Ksh 1,000", midway)
        assertNotEquals("Ksh 2,500", midway)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("Ksh 2,500", shown())
    }
}
