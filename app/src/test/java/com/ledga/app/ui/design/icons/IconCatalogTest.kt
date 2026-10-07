package com.ledga.app.ui.design.icons

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** D5, R85, R86, R98: the full Fluent Emoji 3D set bundled in assets/icons3d. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconCatalogTest {
    private val assets = ApplicationProvider.getApplicationContext<Context>().assets
    private val sample = IconCatalog.parse(
        "key\tname\tgroup\tkeywords\n" +
            "fluent_dog_face\tdog face\tAnimals & Nature\tdog|face|pet\n" +
            "fluent_cafe_test\tcafé au lait\tFood & Drink\tcoffee|milk\n" +
            "fluent_hot_beverage\thot beverage\tFood & Drink\tcoffee|drink|tea\n",
    )

    @Test
    fun `the index lists the whole set once each, in the set's group order`() {
        val icons = IconCatalog.all(assets)
        assertEquals(1595, icons.size)
        assertEquals(icons.size, icons.map { it.key }.toSet().size, "a key is listed twice")
        assertTrue(icons.all { it.group in IconCatalog.GROUPS }, "a group outside the set's own")
        assertEquals(icons.map { IconCatalog.GROUPS.indexOf(it.group) }.sorted(), icons.map { IconCatalog.GROUPS.indexOf(it.group) })
        assertTrue(icons.all { Regex("fluent_[a-z0-9_]+").matches(it.key) })
    }

    @Test
    fun `every icon the app already draws is in the set, so any category's icon can be shown chosen`() {
        val keys = IconCatalog.all(assets).map { it.key }.toSet()
        assertEquals(emptySet(), FluentIcons.byKey.keys - keys)
    }

    @Test
    fun `every catalog icon is a drawable or a 96 px asset that decodes`() {
        val missing = IconCatalog.all(assets).filter { it.key !in FluentIcons.byKey }.filter { icon ->
            val bitmap = runCatching { assets.open(IconCatalog.assetPath(icon.key)).use(BitmapFactory::decodeStream) }.getOrNull()
            bitmap == null || bitmap.width != 96 || bitmap.height != 96
        }
        assertEquals(emptyList(), missing.map { it.key })
    }

    @Test
    fun `search matches names and keywords, whatever the case, spaces or accents (R98)`() {
        assertEquals(listOf("fluent_dog_face"), IconCatalog.search(sample, "  DOG ").map { it.key })
        assertEquals(listOf("fluent_dog_face"), IconCatalog.search(sample, "pet").map { it.key })
        assertEquals(listOf("fluent_cafe_test"), IconCatalog.search(sample, "cafe").map { it.key })
        assertEquals(listOf("fluent_cafe_test", "fluent_hot_beverage"), IconCatalog.search(sample, "coffee").map { it.key })
        assertEquals(sample, IconCatalog.search(sample, "   "))
        assertEquals(emptyList(), IconCatalog.search(sample, "zebra"))
    }

    @Test
    fun `a name reads as a sentence, and a key's file sits under icons3d`() {
        assertEquals("Dog face", IconCatalog.displayName(sample.first()))
        assertEquals("icons3d/dog_face.webp", IconCatalog.assetPath("fluent_dog_face"))
    }
}
