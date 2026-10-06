package com.ledga.app.ui.trackers

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.ledga.app.data.room.CategoryOrigin
import com.ledga.app.data.room.CategoryRow
import com.ledga.app.ui.app.ShellFrame
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Contrast
import com.ledga.core.model.Categories
import java.time.LocalDate
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tracker detail's "Matched by" chips must read as chips, not loose text (golden review, light theme). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class TrackerDetailChipsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a rule chip stands out from what is around it`() {
        val seed = Categories.seed(Categories.ELECTRICITY)!!
        val ui = TrackerDetailUi(
            loaded = true,
            category = CategoryRow(seed.key, seed.name, seed.group, seed.icon3d, null, null, true, seed.sortOrder, CategoryOrigin.SYSTEM, false),
            rules = listOf(RuleChipUi(2, "Name has KPLC")),
            year = 2026,
            today = LocalDate.parse("2026-10-06"),
        )
        compose.setContent { LedgaTheme(Appearance.LIGHT, reducedMotion = true) { ShellFrame(null, onSelect = {}) { TrackerDetailContent(ui, TrackerDetailActions()) } } }
        val text = compose.onNodeWithText("Name has KPLC", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { root.draw(Canvas(bitmap)) }
        val dp = root.resources.displayMetrics.density
        val y = text.center.y.toInt()
        val chip = Color(bitmap.getPixel((text.left - 4 * dp).toInt(), y)) // inside the chip's 10 dp start padding
        val around = Color(bitmap.getPixel((text.left - 14 * dp).toInt(), y)) // just outside the chip
        val ratio = Contrast.ratio(chip, around)
        assertTrue(ratio >= 1.08, "the chip's background barely differs from what is around it (contrast $ratio)")
    }
}
