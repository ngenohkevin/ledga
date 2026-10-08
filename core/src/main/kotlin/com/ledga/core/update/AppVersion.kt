package com.ledga.core.update

/**
 * A Ledga version (spec §13.1): `X.Y.Z` (stable) or `X.Y.Z-beta.N` (N 1–98). Its [code] is the Android versionCode,
 * major·1,000,000 + minor·10,000 + patch·100 + stage (N for a beta, 99 for stable): every beta sorts before its full
 * release, and all of v2 after v1's 21. Comparing two versions compares their codes. `app/build.gradle.kts` computes the
 * same code from `version.properties`.
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int, val beta: Int? = null) : Comparable<AppVersion> {
    init {
        require(major in 0..MAX_MAJOR && minor in 0..99 && patch in 0..99) { "out of range: $major.$minor.$patch" }
        require(beta == null || beta in 1..98) { "a beta number must be 1 to 98: $beta" }
    }

    val isBeta: Boolean get() = beta != null

    val code: Int get() = major * 1_000_000 + minor * 10_000 + patch * 100 + (beta ?: STABLE_STAGE)

    override fun compareTo(other: AppVersion): Int = code.compareTo(other.code)

    override fun toString(): String = "$major.$minor.$patch" + (beta?.let { "-beta.$it" } ?: "")

    companion object {
        /** Ledga dev's versionName ends with this (R25). */
        const val DEV_SUFFIX = "-dev"

        private const val STABLE_STAGE = 99

        /** The largest major whose code still fits an Int. */
        private const val MAX_MAJOR = 2_000

        private val FORMAT = Regex("""v?(\d{1,4})\.(\d{1,2})\.(\d{1,2})(?:-beta\.(\d{1,2}))?""")

        /** "2.0.0", "2.0.0-beta.3", or a tag ("v2.0.0"); null for anything else, including a beta outside 1–98. */
        fun parse(text: String): AppVersion? {
            val m = FORMAT.matchEntire(text.trim()) ?: return null
            val (major, minor, patch) = (1..3).map { m.groupValues[it].toInt() }
            val beta = m.groupValues[4].takeIf { it.isNotEmpty() }?.toInt()
            return runCatching { AppVersion(major, minor, patch, beta) }.getOrNull()
        }

        /** The installed build's version: its versionName without Ledga dev's suffix. */
        fun ofBuild(versionName: String): AppVersion? = parse(versionName.removeSuffix(DEV_SUFFIX))
    }
}
