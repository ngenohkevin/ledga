package com.ledga.app.ui.design.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.ui.design.tokens.Contrast
import com.ledga.app.ui.design.tokens.DarkColors
import com.ledga.app.ui.design.tokens.LedgaColors
import com.ledga.app.ui.design.tokens.LightColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class LedgaThemeTest {
    @get:Rule val compose = createComposeRule()

    private class Seen(var colors: LedgaColors? = null, var primary: Color? = null, var reduced: Boolean? = null)

    private fun seen(appearance: Appearance): Seen {
        val seen = Seen()
        compose.setContent {
            LedgaTheme(appearance) {
                seen.colors = LedgaTheme.colors
                seen.primary = MaterialTheme.colorScheme.primary
                seen.reduced = LedgaTheme.reducedMotion
            }
        }
        compose.waitForIdle()
        return seen
    }

    @Test
    fun `dark appearance overrides a light system`() {
        val s = seen(Appearance.DARK)
        assertEquals(DarkColors, s.colors)
        assertEquals(DarkColors.primary, s.primary)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `light appearance overrides a dark system`() {
        val s = seen(Appearance.LIGHT)
        assertEquals(LightColors, s.colors)
        assertEquals(LightColors.primary, s.primary)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `system appearance follows the night setting`() {
        assertEquals(DarkColors, seen(Appearance.SYSTEM).colors)
    }

    @Test
    fun `system appearance is light by day`() {
        assertEquals(LightColors, seen(Appearance.SYSTEM).colors)
    }

    @Test
    fun `remove animations turns on reduced motion`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertEquals(true, seen(Appearance.LIGHT).reduced)
    }

    @Test
    fun `animations are on by default`() {
        assertFalse(seen(Appearance.LIGHT).reduced!!)
    }

    @Test
    fun `M3 components inherit the tokens`() {
        var scheme: ColorScheme? = null
        compose.setContent { LedgaTheme(Appearance.LIGHT, reducedMotion = false) { scheme = MaterialTheme.colorScheme } }
        compose.waitForIdle()
        with(scheme!!) {
            assertEquals(LightColors.canvas, background)
            assertEquals(LightColors.surface, surface)
            assertEquals(LightColors.danger, error)
            assertEquals(LightColors.ink, inverseSurface)
            assertEquals(LightColors.muted, onSurfaceVariant)
            assertTrue(surfaceTint == Color.Transparent)
        }
    }
    private fun schemes(): List<ColorScheme> {
        val seen = mutableListOf<ColorScheme>()
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = false) { seen += MaterialTheme.colorScheme }
            LedgaTheme(Appearance.DARK, reducedMotion = false) { seen += MaterialTheme.colorScheme }
        }
        compose.waitForIdle()
        return seen.takeLast(2)
    }

    @Test
    fun `snackbar actions are readable - M3 draws them in inversePrimary on inverseSurface`() {
        schemes().forEach { scheme ->
            val ratio = Contrast.ratio(scheme.inversePrimary, scheme.inverseSurface)
            assertTrue(ratio >= 4.5, "inversePrimary on inverseSurface is %.2f:1".format(ratio))
        }
    }

    @Test
    fun `M3 outlines are visible - OutlinedTextField borders need 3 to 1 against the surface`() {
        schemes().forEach { scheme ->
            val ratio = Contrast.ratio(scheme.outline, scheme.surface)
            assertTrue(ratio >= 3.0, "outline on surface is %.2f:1".format(ratio))
        }
    }
}
