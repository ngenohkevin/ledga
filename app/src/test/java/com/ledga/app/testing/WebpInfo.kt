package com.ledga.app.testing

/** Width, height and losslessness from a WebP's RIFF chunks (simple VP8L, or the VP8X extended header). */
data class WebpInfo(val width: Int, val height: Int, val lossless: Boolean) {
    companion object {
        fun of(bytes: ByteArray): WebpInfo {
            fun tag(at: Int) = String(bytes, at, 4, Charsets.US_ASCII)
            fun le(at: Int, n: Int) = (0 until n).fold(0) { acc, i -> acc or ((bytes[at + i].toInt() and 0xFF) shl (8 * i)) }
            require(tag(0) == "RIFF" && tag(8) == "WEBP") { "not a WebP" }
            var width = -1
            var height = -1
            var lossless = false
            var p = 12
            while (p + 8 <= bytes.size) {
                val size = le(p + 4, 4)
                when (tag(p)) {
                    "VP8L" -> {
                        lossless = true
                        if (width < 0) {
                            val bits = le(p + 9, 4) // after the 0x2F signature byte
                            width = (bits and 0x3FFF) + 1
                            height = ((bits ushr 14) and 0x3FFF) + 1
                        }
                    }
                    "VP8X" -> {
                        width = le(p + 12, 3) + 1
                        height = le(p + 15, 3) + 1
                    }
                }
                p += 8 + size + (size and 1)
            }
            return WebpInfo(width, height, lossless)
        }
    }
}
