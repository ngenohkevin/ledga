package com.ledga.app.ui.you

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.ui.app.DetailFrame
import com.ledga.app.ui.design.components.EmptyState
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.OutlinePill
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType

data class UnreadableActions(val onBack: () -> Unit = {}, val onShare: (UnreadableMessage) -> Unit = {})

/** Messages Ledga couldn't read (R78): when each arrived, the sender, the raw text (Inter, selectable) and Share. */
@Composable
fun UnreadableContent(ui: UnreadableUi, actions: UnreadableActions, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    DetailFrame("Messages Ledga couldn't read", onBack = actions.onBack, modifier = modifier) {
        when {
            !ui.loaded -> Unit
            ui.messages.isEmpty() -> EmptyState("fluent_check_mark_button", "Ledga read every message", "Any M-Pesa message it can't read shows up here.")
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item { Text(UnreadableText.NOTE, style = LedgaType.body, color = c.muted) }
                items(ui.messages, key = { it.id }) { m ->
                    LedgaCard(Modifier.fillMaxWidth()) {
                        Text("${DateLabels.dateTime(m.receivedAt)} · ${m.sender}", style = LedgaType.caption, color = c.muted)
                        SelectionContainer { Text(m.body, Modifier.padding(vertical = Spacing.s), style = LedgaType.body, color = c.ink) }
                        OutlinePill("Share", { actions.onShare(m) }, icon = Ph.ShareNetwork)
                    }
                }
            }
        }
    }
}

/** The share sheet with the text alone (spec §14: the person chooses where it goes). */
fun unreadableShareIntent(text: String): Intent =
    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share message")

@Composable
fun UnreadableScreen(onBack: () -> Unit, vm: UnreadableViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    UnreadableContent(ui, UnreadableActions(onBack = onBack, onShare = { context.startActivity(unreadableShareIntent(UnreadableText.share(it))) }))
}
