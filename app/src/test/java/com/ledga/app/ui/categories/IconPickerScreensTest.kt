package com.ledga.app.ui.categories

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ledga.app.testing.snapScreen
import com.ledga.app.ui.design.components.SheetScaffold
import com.ledga.app.ui.design.icons.CatalogBitmaps
import com.ledga.app.ui.design.icons.IconCatalog
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** D5: the icon sheet, with the real set: as it opens, and searched for "dog". */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xhdpi")
class IconPickerScreensTest {
    private val assets = ApplicationProvider.getApplicationContext<Context>().assets
    private val icons = IconCatalog.all(assets).also { all -> CatalogBitmaps.preload(assets, all.take(120).map { it.key } + IconCatalog.search(all, "dog").map { it.key }) }

    @Test
    fun opened() = snapScreen("icon_picker") { SheetScaffold("Icon") { IconPickerContent("fluent_dog_face", icons, "", {}, {}) } }

    @Test
    fun searched() = snapScreen("icon_picker_search") { SheetScaffold("Icon") { IconPickerContent("fluent_dog_face", icons, "dog", {}, {}) } }
}
