package com.ledga.app.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType

/** R193: the way past Android 15+'s block on SMS for an app installed from a file (copy approved by the owner, 2026-10-09). */
object SmsBlockedText {
    /** " › " with a no-break space before it: a path like "Permissions › SMS" may wrap after the "›", never before it. */
    private val NEXT = "${Char(0x00A0)}${Char(0x203A)} "
    private val MENU = Char(0x22EE)

    const val TITLE = "Android blocked SMS access"
    const val LEAD = "Android blocks SMS for apps installed from a file until you allow it:"
    const val ACTION = "Open App info"
    val STEPS = listOf(
        "Open App info, then Permissions${NEXT}SMS, and tap Allow. Android says it's restricted.",
        "Back in App info, tap $MENU${NEXT}Allow restricted settings and confirm.",
        "Tap Permissions${NEXT}SMS${NEXT}Allow again, then come back to Ledga.",
    )

    /** [STEPS] as TalkBack reads them: words for "›" and "⋮", which it would read out by name (final review M12). */
    val SPOKEN = listOf(
        "Step 1: Open App info, then Permissions, then SMS, and tap Allow. Android says it's restricted.",
        "Step 2: Back in App info, tap the three-dot menu, then Allow restricted settings, and confirm.",
        "Step 3: Tap Permissions, then SMS, then Allow again, then come back to Ledga.",
    )
}

/** [SmsBlockedText.STEPS], numbered. */
@Composable
fun SmsBlockedSteps(modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        SmsBlockedText.STEPS.forEachIndexed { i, step ->
            Row(
                Modifier.clearAndSetSemantics { contentDescription = SmsBlockedText.SPOKEN[i] },
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.Top,
            ) {
                // Tabular figures: "1." and "2." the same width, so the steps' text lines up.
                Text("${i + 1}.", style = LedgaType.bodyStrong.copy(fontFeatureSettings = "tnum"), color = c.primary)
                Text(step, style = LedgaType.body, color = c.ink2)
            }
        }
    }
}
