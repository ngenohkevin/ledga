package com.ledga.app.ui.design.type

import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.assertTrue

/** The vendored Inter files (R18): present, subset small, and still carrying tabular figures. */
class FontFilesTest {
    private val weights = listOf("regular", "medium", "semibold", "bold", "extrabold")
    private fun font(weight: String) = File("src/main/res/font/inter_$weight.ttf")

    @Test
    fun `five Inter weights are bundled and subset small`() {
        weights.forEach { w ->
            val f = font(w)
            assertTrue(f.isFile, "${f.path} missing")
            assertTrue(f.length() in 50_000L..160_000L, "${f.path} is ${f.length()} bytes")
        }
    }

    @Test
    fun `subsetting kept tabular figures`() {
        weights.forEach { w -> assertTrue("tnum" in gsubFeatureTags(font(w).readBytes()), "$w lost tnum") }
    }

    @Test
    fun `the OFL licence ships with the app`() {
        assertTrue(File("src/main/assets/licenses/inter-OFL.txt").readText().contains("SIL OPEN FONT LICENSE"))
    }

    /** Feature tags in the font's GSUB FeatureList (OpenType: table directory → GSUB header → FeatureList). */
    private fun gsubFeatureTags(bytes: ByteArray): Set<String> {
        val b = ByteBuffer.wrap(bytes) // big-endian, as OpenType is
        val numTables = b.getShort(4).toInt() and 0xFFFF
        val gsub = (0 until numTables).map { 12 + 16 * it }
            .firstOrNull { String(bytes, it, 4, Charsets.US_ASCII) == "GSUB" }
            ?.let { b.getInt(it + 8) } ?: error("no GSUB table")
        val featureList = gsub + (b.getShort(gsub + 6).toInt() and 0xFFFF)
        val count = b.getShort(featureList).toInt() and 0xFFFF
        return (0 until count).map { String(bytes, featureList + 2 + 6 * it, 4, Charsets.US_ASCII) }.toSet()
    }
}
