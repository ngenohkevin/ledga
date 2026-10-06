package com.ledga.app.ui.design.icons

import com.ledga.app.data.edit.CategoryLooks
import com.ledga.app.testing.WebpInfo
import com.ledga.core.model.Categories
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FluentIconsTest {
    private val dir = File("src/main/res/drawable-nodpi")

    @Test
    fun `every seeded category has a vendored 3D icon`() {
        Categories.SEED.forEach { seed ->
            assertTrue(seed.icon3d in FluentIcons.byKey, seed.icon3d)
            assertTrue(File(dir, "${seed.icon3d}.webp").isFile, seed.icon3d)
        }
    }

    @Test
    fun `each 3D icon is used by exactly one category`() {
        val keys = Categories.SEED.map { it.icon3d }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `every vendored file is in the lookup and nothing else is`() {
        val files = dir.listFiles { f -> f.name.startsWith("fluent_") }!!.map { it.nameWithoutExtension }.toSet()
        assertEquals(FluentIcons.byKey.keys, files)
        assertEquals(60, files.size)
    }

    @Test
    fun `icons are 96 px lossless WebP within the size budget`() {
        var total = 0L
        FluentIcons.byKey.keys.forEach { key ->
            val bytes = File(dir, "$key.webp").readBytes()
            total += bytes.size
            val info = WebpInfo.of(bytes)
            assertEquals(96, info.width, key)
            assertEquals(96, info.height, key)
            assertTrue(info.lossless, key)
            assertTrue(bytes.size <= 16_000, "$key is ${bytes.size} bytes")
        }
        assertTrue(total <= 400_000, "total $total bytes")
    }

    @Test
    fun `an unknown key falls back to Other's icon`() {
        val other = FluentIcons.byKey.getValue("fluent_package")
        assertEquals(other, Fluent.resOf("fluent_from_a_newer_version"))
        assertEquals(other, Fluent.resOf(""))
        assertEquals(Categories.seed(Categories.OTHER)!!.icon3d, Fluent.FALLBACK)
    }

    @Test
    fun `the MIT licence ships with the app`() {
        assertTrue(File("src/main/assets/licenses/fluentui-emoji-MIT.txt").readText().contains("MIT License"))
    }

    @Test
    fun `every icon a category of your own can take is vendored, and none belongs to a built-in category (R68)`() {
        val builtIn = Categories.SEED.map { it.icon3d }.toSet()
        assertEquals(17, CategoryLooks.ICONS.size)
        assertEquals(CategoryLooks.DEFAULT_ICON, CategoryLooks.ICONS.first())
        CategoryLooks.ICONS.forEach { key ->
            assertTrue(key in FluentIcons.byKey, key)
            assertTrue(key !in builtIn, "$key belongs to a built-in category")
        }
        assertEquals(CategoryLooks.ICONS.size, CategoryLooks.ICONS.toSet().size)
    }
}
