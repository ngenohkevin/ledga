package com.ledga.app.ui.design.icons

import androidx.annotation.DrawableRes

/** Lookup for Fluent Emoji 3D icons (spec §10.3). */
object Fluent {
    /** Other's icon: what an unknown key (a newer backup, a hand-edited row) shows instead of crashing. */
    const val FALLBACK = "fluent_package"

    @DrawableRes
    fun resOf(key: String): Int = FluentIcons.byKey[key] ?: FluentIcons.byKey.getValue(FALLBACK)
}
