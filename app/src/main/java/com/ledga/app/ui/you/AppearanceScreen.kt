package com.ledga.app.ui.you

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.data.settings.TextSize
import com.ledga.app.ui.app.DetailFrame
import com.ledga.app.ui.app.GroupLabel
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.RowDivider
import com.ledga.app.ui.design.theme.Appearance
import com.ledga.app.ui.design.tokens.Spacing

data class AppearanceActions(val onBack: () -> Unit = {}, val onTheme: (Appearance) -> Unit = {}, val onTextSize: (TextSize) -> Unit = {})

/** You → Appearance (R76): theme, then text size, each a radio group on a card. */
@Composable
fun AppearanceContent(ui: AppearanceUi, actions: AppearanceActions, modifier: Modifier = Modifier) {
    DetailFrame("Appearance", onBack = actions.onBack, modifier = modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xxl)) {
            GroupLabel("Theme")
            LedgaCard(Modifier.fillMaxWidth().selectableGroup(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                Appearance.entries.forEachIndexed { i, a ->
                    if (i > 0) RowDivider(Modifier.padding(horizontal = Spacing.m))
                    ChoiceRow(YouText.themeLabel(a), ui.appearance == a, { actions.onTheme(a) }, subtitle = if (a == Appearance.SYSTEM) "Follows your phone" else null)
                }
            }
            GroupLabel("Text size")
            LedgaCard(Modifier.fillMaxWidth().selectableGroup(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                TextSize.entries.forEachIndexed { i, t ->
                    if (i > 0) RowDivider(Modifier.padding(horizontal = Spacing.m))
                    ChoiceRow(YouText.textSizeLabel(t), ui.textSize == t, { actions.onTextSize(t) }, subtitle = if (t == TextSize.SYSTEM) "Follows your phone's font size" else null)
                }
            }
        }
    }
}

@Composable
fun AppearanceScreen(onBack: () -> Unit, vm: AppearanceViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    AppearanceContent(ui, AppearanceActions(onBack = onBack, onTheme = vm::setAppearance, onTextSize = vm::setTextSize))
}
