package com.ledga.app.ui.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.ledga.app.testing.SPECIMEN_QUALIFIERS
import com.ledga.app.testing.snap
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SPECIMEN_QUALIFIERS)
class ChromeSpecimenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun chrome() = compose.snap("chrome") { ChromeSpecimen() }
}

@Composable
private fun ChromeSpecimen() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Banner("Ledga 2.0.1 is ready to install", BannerTone.Info, icon = Ph.DownloadSimple, actionLabel = "Install", onAction = {})
        Banner("Updating your history…", BannerTone.Progress, progress = 0.4f)
        Banner("Notifications are off", BannerTone.Warning, icon = Ph.BellSlash, actionLabel = "Turn on", onAction = {})
        Banner("Ledga couldn't read 3 messages", BannerTone.Danger, actionLabel = "Review", onAction = {})
        SheetScaffold(title = "Category") {
            Text("Sheet content sits here.", style = LedgaType.body, color = LedgaTheme.colors.ink2)
        }
        LedgaCard(contentPadding = PaddingValues(0.dp)) {
            SkeletonRow()
            RowDivider()
            SkeletonRow()
        }
        LedgaCard {
            EmptyState(
                "fluent_magnifying_glass_tilted_left", "No matches",
                "Try a name, phone number, M-Pesa code or amount.", actionLabel = "Clear search", onAction = {},
            )
        }
        LedgaBottomBar(selected = 0, onSelect = {})
    }
}
