package com.ledga.app.ui.design.icons

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.activity.ComponentActivity
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.ui.design.components.CategoryIcon
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.theme.LedgaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/** Final review C1: an icon drawn from the catalog follows its key when the key changes in place. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class CatalogIconBehaviourTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** The pixels under the node tagged [tag], drawn from the window (Robolectric never delivers captureToImage's frame). */
    private fun pixels(tag: String): IntArray {
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val r = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        val w = r.width.toInt()
        val h = r.height.toInt()
        return IntArray(w * h).also { bitmap.getPixels(it, 0, w, r.left.toInt(), r.top.toInt(), w, h) }
    }

    /** Draws [from], switches it in place to [to], and checks it now looks exactly like a fresh [to] beside it. */
    private fun followsKey(from: String, to: String) {
        CatalogBitmaps.preload(ApplicationProvider.getApplicationContext<Context>().assets, listOf(from, to))
        var key by mutableStateOf(from)
        compose.setContent {
            LedgaTheme(Appearance.LIGHT, reducedMotion = true) {
                Row {
                    CategoryIcon(key, contentDescription = null, modifier = Modifier.testTag("changing"))
                    CategoryIcon(to, contentDescription = null, modifier = Modifier.testTag("fresh"))
                }
            }
        }
        compose.runOnIdle { key = to }
        compose.waitForIdle()
        assertTrue(pixels("changing").contentEquals(pixels("fresh")), "$from → $to still draws the old icon")
    }

    @Test
    fun `an icon follows its key from one catalog icon to another (C1)`() = followsKey("fluent_guide_dog", "fluent_teapot")

    @Test
    fun `an icon follows its key from an unknown key to a catalog icon (C1)`() = followsKey("fluent_from_a_newer_backup", "fluent_teapot")
}
