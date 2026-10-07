package com.ledga.app.ui.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max
import kotlin.math.floor

/** R88: the trackers strip's tile width. */
object StripLayout {
    /**
     * The width that shows n whole tiles and half of the next in [view] (the room from the first tile to the screen's
     * edge), n being the most whole tiles of at least [min] that fit beside that half; never below [min].
     */
    fun tileWidth(view: Dp, min: Dp, gap: Dp): Dp {
        val n = maxOf(1, floor((view - min * 0.5f) / (min + gap)).toInt())
        return max((view - gap * n) / (n + 0.5f), min)
    }
}
