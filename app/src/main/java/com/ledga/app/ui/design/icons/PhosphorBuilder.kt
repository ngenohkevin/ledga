package com.ledga.app.ui.design.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** A Phosphor icon (filled outlines on a 256-unit grid) at a 24 dp default size. Tint it with `Icon(tint = …)`. */
internal fun phosphor(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = "Ph.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 256f,
        viewportHeight = 256f,
    ).apply {
        paths.forEach { d -> addPath(pathData = PathParser().pathStringToNodes(d), fill = SolidColor(Color.Black)) }
    }.build()
