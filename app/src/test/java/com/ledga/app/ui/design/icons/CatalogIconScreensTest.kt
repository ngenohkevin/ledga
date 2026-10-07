package com.ledga.app.ui.design.icons

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.testing.snapScreen
import com.ledga.app.ui.design.components.CategoryIcon
import com.ledga.app.ui.design.theme.LedgaTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** D5: a catalog icon draws on the same plate as a bundled one; an unknown key draws Other's (R85). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class CatalogIconScreensTest {
    private val keys = listOf("fluent_dog_face", "fluent_guide_dog", "fluent_teapot", "fluent_bicycle", "fluent_shopping_cart", "fluent_from_a_newer_backup")

    @Test
    fun catalogIcons() {
        CatalogBitmaps.preload(ApplicationProvider.getApplicationContext<Context>().assets, keys)
        snapScreen("catalog_icons") {
            // On the screen's canvas: nothing else paints a background here.
            Box(Modifier.fillMaxSize().background(LedgaTheme.colors.canvas)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.forEach { CategoryIcon(it, contentDescription = null) }
                }
            }
        }
    }
}
