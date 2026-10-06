package com.ledga.app.ui.design.tokens

import androidx.compose.ui.unit.dp

/** Spec §10.1 spacing scale 4/8/12/16/20/24/32; screens pad 16 dp. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val screen = 16.dp
}

/** Spec §10.1 shapes: cards 24, tiles 20, icon wells 14 (R20: smaller wells scale down), sheets 28 at the top. Pills use RoundedCornerShape(percent = 50). */
object Radii {
    val card = 24.dp
    val tile = 20.dp
    val stat = 16.dp
    val well = 14.dp
    val sheetTop = 28.dp
    val tooltip = 10.dp
}

object Sizes {
    /** Spec §10.5: every touch target is at least 48 dp. */
    val touchTarget = 48.dp
    val hairline = 1.dp
    val chipHeight = 32.dp
    val iconSmall = 16.dp
    val icon = 20.dp
    val iconLarge = 24.dp
}
