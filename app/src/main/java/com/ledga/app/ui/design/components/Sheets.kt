package com.ledga.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Radii
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType

private val SheetShape = RoundedCornerShape(topStart = Radii.sheetTop, topEnd = Radii.sheetTop)

/** A sheet's body: drag handle, optional title (a TalkBack heading), then content (spec §10.4). */
@Composable
fun SheetScaffold(
    title: String?,
    modifier: Modifier = Modifier,
    showHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LedgaTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(c.surfaceSheet, SheetShape)
            .padding(horizontal = Spacing.screen)
            .padding(top = if (showHandle) 0.dp else Spacing.s, bottom = Spacing.xxl),
    ) {
        if (showHandle) DragHandle(Modifier.align(Alignment.CenterHorizontally))
        if (title != null) {
            Text(title, Modifier.padding(bottom = Spacing.m).semantics { heading() }, style = LedgaType.section, color = c.ink)
        }
        content()
    }
}

/** The 36×4 dp handle (faint is decoration). */
@Composable
fun DragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(vertical = 10.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(LedgaTheme.colors.faint),
    )
}

/**
 * The modal bottom sheet for the transaction, category, filter and line-switcher UIs: 28 dp top corners on
 * surfaceSheet. M3 owns the drag handle slot, which carries TalkBack's expand and dismiss actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgaModalSheet(
    onDismiss: () -> Unit,
    title: String?,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LedgaTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = c.surfaceSheet,
        contentColor = c.ink,
        dragHandle = { DragHandle() },
    ) {
        SheetScaffold(title, showHandle = false, content = content)
    }
}
