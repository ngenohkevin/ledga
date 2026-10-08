package com.ledga.core.update

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Spec §13.1: version names, their codes and their order. */
class AppVersionTest {
    private fun v(text: String) = AppVersion.parse(text)!!

    @Test
    fun `reads stable and beta names, with or without the tag's v`() {
        assertEquals(AppVersion(2, 0, 0), v("2.0.0"))
        assertEquals(AppVersion(2, 0, 0, 3), v("2.0.0-beta.3"))
        assertEquals(AppVersion(2, 0, 0, 3), v("v2.0.0-beta.3"))
        assertEquals(AppVersion(1, 4, 7), v("v1.4.7"))
    }

    @Test
    fun `codes follow the spec's formula`() {
        assertEquals(2_000_003, v("2.0.0-beta.3").code)
        assertEquals(2_000_099, v("2.0.0").code)
        assertEquals(2_010_205, v("2.1.2-beta.5").code)
        assertEquals(1_060_099, v("1.6.0").code)
    }

    @Test
    fun `a beta sorts before its full release and after the release before it`() {
        val order = listOf("1.6.0", "2.0.0-beta.1", "2.0.0-beta.2", "2.0.0-beta.10", "2.0.0", "2.0.1-beta.1", "2.0.1", "2.1.0").map(::v)
        assertEquals(order, order.shuffled(Random(7)).sorted())
    }

    @Test
    fun `anything else isn't a version`() {
        val notVersions = listOf(
            "", "v", "2.0", "2.0.0.1", "2.0.0-beta.0", "2.0.0-beta.99", "2.0.0-rc.1", "2.100.0", "2.0.100",
            "2.0.0-beta.3-dev", "nightly", "v2.0.0 x",
        )
        for (text in notVersions) assertNull(AppVersion.parse(text), text)
    }

    @Test
    fun `the installed build drops Ledga dev's suffix`() {
        assertEquals(v("2.0.0-beta.1"), AppVersion.ofBuild("2.0.0-beta.1-dev"))
        assertEquals(v("2.0.0"), AppVersion.ofBuild("2.0.0"))
        assertNull(AppVersion.ofBuild("1.6"))
    }

    @Test
    fun `writes itself back as its name`() {
        assertEquals("2.0.0-beta.3", v("v2.0.0-beta.3").toString())
        assertEquals("2.1.0", v("2.1.0").toString())
        assertTrue(v("2.0.0-beta.3").isBeta)
    }
}
