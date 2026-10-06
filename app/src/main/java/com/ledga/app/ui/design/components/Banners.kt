package com.ledga.app.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Radii
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.type.LedgaType

/** Home's conditional banners (spec §10.4): update ready, "Updating your history…", permission denied, read errors. */
enum class BannerTone { Info, Progress, Warning, Danger }

private data class BannerStyle(val container: Color, val content: Color, val icon: Color, val action: Color, val glyph: ImageVector)

/**
 * A full-width banner. Progress banners show [progress] when it is known, an indeterminate bar otherwise, and no
 * bar when motion is reduced (the text says what is happening).
 */
@Composable
fun Banner(
    text: String,
    tone: BannerTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    progress: Float? = null,
) {
    val c = LedgaTheme.colors
    val style = when (tone) {
        BannerTone.Info -> BannerStyle(c.primarySoft, c.onPrimarySoft, c.onPrimarySoft, c.onPrimarySoft, Ph.Info)
        BannerTone.Progress -> BannerStyle(c.plate, c.ink, c.primary, c.primary, Ph.ArrowClockwise)
        BannerTone.Warning -> BannerStyle(c.warningSoft, c.ink2, c.warning, c.ink, Ph.Warning)
        BannerTone.Danger -> BannerStyle(c.dangerSoft, c.ink2, c.danger, c.ink, Ph.WarningCircle)
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.stat))
            .background(style.container)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon ?: style.glyph, contentDescription = null, tint = style.icon, modifier = Modifier.size(Sizes.icon))
        Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text, style = LedgaType.bodyStrong, color = style.content)
            if (tone == BannerTone.Progress) {
                when {
                    progress != null -> LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = c.primary,
                        trackColor = c.barTrack,
                    )
                    !LedgaTheme.reducedMotion -> LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = c.primary,
                        trackColor = c.barTrack,
                    )
                }
            }
        }
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 10.dp),
                style = LedgaType.label,
                color = style.action,
            )
        }
    }
}
