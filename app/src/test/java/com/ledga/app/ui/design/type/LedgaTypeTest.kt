package com.ledga.app.ui.design.type

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LedgaTypeTest {

    @Test
    fun `every style is set in bundled Inter, never the platform default`() {
        LedgaType.all.forEach { (name, style) ->
            assertEquals(Inter, style.fontFamily, name)
            assertNotEquals(FontFamily.Default, style.fontFamily, name)
        }
    }

    @Test
    fun `amount styles use tabular figures and text styles don't`() {
        LedgaType.all.forEach { (name, style) ->
            assertEquals(if (name in LedgaType.tabular) "tnum" else null, style.fontFeatureSettings, name)
        }
    }

    @Test
    fun `the scale matches spec 10_2`() {
        assertEquals(34.sp, LedgaType.balance.fontSize)
        assertEquals(FontWeight.ExtraBold, LedgaType.balance.fontWeight)
        assertEquals((-0.03).em, LedgaType.balance.letterSpacing)
        assertEquals(24.sp, LedgaType.screenTitle.fontSize)
        assertEquals(FontWeight.ExtraBold, LedgaType.screenTitle.fontWeight)
        assertEquals(26.sp, LedgaType.amountL.fontSize)
        assertEquals(15.sp, LedgaType.section.fontSize)
        assertEquals(FontWeight.ExtraBold, LedgaType.section.fontWeight)
        assertEquals(14.sp, LedgaType.cardTitle.fontSize)
        assertEquals(FontWeight.Bold, LedgaType.cardTitle.fontWeight)
        assertEquals(14.sp, LedgaType.body.fontSize)
        assertEquals(FontWeight.Medium, LedgaType.body.fontWeight)
        assertEquals(FontWeight.SemiBold, LedgaType.bodyStrong.fontWeight)
        assertEquals(12.sp, LedgaType.caption.fontSize)
        assertEquals(11.sp, LedgaType.overline.fontSize)
        assertEquals(FontWeight.Bold, LedgaType.overline.fontWeight)
        assertEquals(0.06.em, LedgaType.overline.letterSpacing)
    }

    @Test
    fun `material typography is Inter throughout`() {
        with(LedgaType.material) {
            listOf(displayLarge, headlineSmall, titleMedium, bodyLarge, bodyMedium, bodySmall, labelLarge, labelSmall)
                .forEach { assertEquals(Inter, it.fontFamily) }
        }
    }
}
