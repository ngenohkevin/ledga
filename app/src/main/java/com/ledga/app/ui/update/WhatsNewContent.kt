package com.ledga.app.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.core.update.NotesSection

/** R141: the sheet Home shows once after an update: this build's notes, then "Got it". */
@Composable
fun WhatsNewContent(sections: List<NotesSection>, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        NotesList(sections)
        PrimaryPill("Got it", onDone, Modifier.fillMaxWidth().padding(top = Spacing.l))
    }
}
