package com.ledga.app.ui.you

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ledga.app.ui.app.DetailFrame
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.ListRow
import com.ledga.app.ui.design.components.RowDivider
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One open-source licence Ledga ships (R80): what it covers and its text in `assets/`. */
data class Licence(val name: String, val what: String, val asset: String)

object Licences {
    val ALL = listOf(
        Licence("Inter", "Typeface · SIL Open Font License 1.1", "licenses/inter-OFL.txt"),
        Licence("Phosphor Icons", "Icons · MIT License", "licenses/phosphor-MIT.txt"),
        Licence("Fluent Emoji", "3D icons · MIT License", "licenses/fluentui-emoji-MIT.txt"),
        Licence("Android Jetpack, Kotlin and Dagger Hilt", "Libraries · Apache License 2.0", "licenses/apache-2.0.txt"),
    )
}

/** You → About → Open-source licences (spec §10.3, R80). */
@Composable
fun LicencesContent(onBack: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    DetailFrame("Open-source licences", onBack = onBack, modifier = modifier) {
        // Scrolls like every pushed screen: held sideways at large text, the last row is below the fold (M5).
        LedgaCard(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xxl).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = Spacing.xs),
        ) {
            Licences.ALL.forEachIndexed { i, l ->
                if (i > 0) RowDivider(Modifier.padding(horizontal = Spacing.m))
                ListRow(l.name, subtitle = l.what, onClick = { onOpen(l.asset) })
            }
        }
    }
}

/** One licence's full text, selectable. */
@Composable
fun LicenceContent(name: String, text: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    DetailFrame(name, onBack = onBack, modifier = modifier) {
        SelectionContainer(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xxl)) {
            Text(text, style = LedgaType.caption, color = LedgaTheme.colors.ink2)
        }
    }
}

/** A licence (route): its text read from `assets/` off the main thread. */
@Composable
fun LicenceScreen(asset: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val text by produceState("", asset) { value = withContext(Dispatchers.IO) { context.assets.open(asset).bufferedReader().use { it.readText() } } }
    LicenceContent(Licences.ALL.firstOrNull { it.asset == asset }?.name ?: "Licence", text, onBack)
}
