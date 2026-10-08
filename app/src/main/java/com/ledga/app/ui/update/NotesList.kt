package com.ledga.app.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.core.update.NotesSection

/** Release notes as titled lists of points (spec §13.4). No notes say so (R142). */
@Composable
fun NotesList(sections: List<NotesSection>, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (sections.isEmpty()) {
            Text(UpdateText.NO_NOTES, style = LedgaType.body, color = c.muted)
            return@Column
        }
        sections.forEach { section ->
            if (section.title.isNotEmpty()) Text(section.title, style = LedgaType.bodyStrong, color = c.ink)
            section.items.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(BULLET, style = LedgaType.body, color = c.muted)
                    Text(item, Modifier.weight(1f), style = LedgaType.body, color = c.ink2)
                }
            }
        }
    }
}

private val BULLET = Char(0x2022).toString()
