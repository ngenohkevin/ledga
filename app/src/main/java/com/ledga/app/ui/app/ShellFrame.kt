package com.ledga.app.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.ledga.app.ui.design.components.LedgaBottomBar
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import java.util.Locale

/**
 * The four-tab frame (spec §10.4): the screen above, `LedgaBottomBar` below. The bar pads the navigation bar itself;
 * the screen pads the status bar. [selected] null hides the bar (onboarding, detail screens). [content] keeps its place
 * in the tree either way, so a NavHost inside it survives the bar appearing and disappearing.
 *
 * [horizontalInsets] keeps the screen clear of a landscape camera cutout and a side navigation bar (owner ruling M5,
 * 2026-10-06). The padding consumes those insets, so a screen inside that pads `safeDrawing` itself is not padded twice.
 */
@Composable
fun ShellFrame(
    selected: Tab?,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
    horizontalInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxSize().background(LedgaTheme.colors.canvas)) {
        Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(horizontalInsets)) { content() }
        if (selected != null) LedgaBottomBar(selected = selected.ordinal, onSelect = { onSelect(Tab.entries[it]) })
    }
}

/** A tab screen's title (spec §10.2: screen title 24/800), announced as a heading. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) = Text(
    text,
    modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.m).semantics { heading() },
    style = LedgaType.screenTitle,
    color = LedgaTheme.colors.ink,
)

/** "1,111": a count with thousands separators (onboarding's import counts). */
internal fun grouped(n: Int): String = String.format(Locale.ENGLISH, "%,d", n)
