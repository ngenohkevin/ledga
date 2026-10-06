package com.ledga.app.ui.you

import com.ledga.app.data.settings.TextSize
import com.ledga.app.ui.design.theme.Appearance

/** What You and its subscreens say (spec §10.4). Task 11 adds the profile, lines, data and rescan lines. */
object YouText {
    fun themeLabel(a: Appearance): String = when (a) {
        Appearance.SYSTEM -> "System"
        Appearance.LIGHT -> "Light"
        Appearance.DARK -> "Dark"
    }

    fun textSizeLabel(t: TextSize): String = when (t) {
        TextSize.SYSTEM -> "Default"
        TextSize.SMALL -> "Small"
        TextSize.MEDIUM -> "Medium"
        TextSize.LARGE -> "Large"
        TextSize.EXTRA_LARGE -> "Extra large"
    }

    /** You's row (mockup `you`): "System theme · Default text". */
    fun appearanceLine(a: Appearance, t: TextSize): String = "${themeLabel(a)} theme · ${textSizeLabel(t)} text"
}
