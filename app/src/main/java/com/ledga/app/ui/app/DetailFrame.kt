package com.ledga.app.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.components.CategoryIcon
import com.ledga.app.ui.design.components.WellSize
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType

/**
 * A pushed screen (spec §10.4: detail screens push full-screen; R83). There is no bottom bar, so it pads `safeDrawing`
 * vertically itself; `ShellFrame` already pads the sides. The [content] decides how it scrolls.
 */
@Composable
fun DetailFrame(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    iconKey: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))) {
        DetailTopBar(title, onBack, iconKey, actions)
        content()
    }
}

/** Round Back, an optional 3D icon, the title (a heading; it wraps to two lines before it ellipsizes), then [actions]. */
@Composable
fun DetailTopBar(title: String, onBack: () -> Unit, iconKey: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        RoundButton(Ph.ArrowLeft, "Back", onBack)
        if (iconKey != null) CategoryIcon(iconKey, contentDescription = null, size = WellSize.Small)
        Text(
            title,
            Modifier.weight(1f).semantics { heading() },
            style = LedgaType.screenTitle,
            color = LedgaTheme.colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

/** A 36 dp round icon button in a 48 dp target (Tracker detail's Back and overflow, every pushed screen's Back). */
@Composable
fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = LedgaTheme.colors
    IconButton(onClick = onClick) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(c.surface).border(Sizes.hairline, c.line, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = c.ink2, modifier = Modifier.size(Sizes.iconSmall))
        }
    }
}
