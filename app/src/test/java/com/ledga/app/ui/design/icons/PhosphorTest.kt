package com.ledga.app.ui.design.icons

import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhosphorTest {
    private val regular = listOf(
        "ArrowClockwise", "ArrowCounterClockwise", "ArrowDownLeft", "ArrowLeft", "ArrowRight", "ArrowUpRight",
        "Bell", "BellSlash", "CalendarBlank", "CaretDown", "CaretLeft", "CaretRight", "CaretUp", "ChartBar",
        "Check", "CheckCircle", "Copy", "DotsThree", "DownloadSimple", "EyeSlash", "HandCoins", "House", "Info",
        "MagnifyingGlass", "PencilSimple", "Plus", "Receipt", "ShareNetwork", "SimCard", "SlidersHorizontal",
        "SquaresFour", "Trash", "UserCircle", "Warning", "WarningCircle", "X",
    )
    private val fill = listOf("ChartBarFill", "HouseFill", "ReceiptFill", "SquaresFourFill", "UserCircleFill")
    private val bold = listOf(
        "ArrowDownLeftBold", "ArrowUpRightBold", "CaretDownBold", "CaretRightBold", "CaretUpBold",
        "CheckBold", "PlusBold", "XBold",
    )

    @Test
    fun `the full chrome set is vendored`() {
        assertEquals(36, regular.size)
        assertEquals((regular + fill + bold).sorted(), Ph.all.keys.sorted())
    }

    @Test
    fun `every icon is a 24 dp vector on Phosphor's 256 grid with path data`() {
        Ph.all.forEach { (name, icon) ->
            assertEquals(24.dp, icon.defaultWidth, name)
            assertEquals(256f, icon.viewportWidth, name)
            assertEquals(256f, icon.viewportHeight, name)
            assertTrue(icon.root.size > 0, name)
            assertTrue((icon.root[0] as VectorPath).pathData.isNotEmpty(), name)
        }
    }

    @Test
    fun `each tab has an outline icon and a filled one`() {
        listOf("House", "Receipt", "ChartBar", "UserCircle").forEach {
            assertTrue(it in Ph.all && "${it}Fill" in Ph.all, it)
        }
    }
}
