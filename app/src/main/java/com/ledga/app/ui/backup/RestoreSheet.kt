package com.ledga.app.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.ledga.app.data.backup.LineQuestion
import com.ledga.app.data.backup.RestoreMode
import com.ledga.app.ui.design.components.Banner
import com.ledga.app.ui.design.components.BannerTone
import com.ledga.app.ui.design.components.ChoiceChip
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.app.ui.you.ChoiceRow

data class RestoreDraftActions(
    val onMode: (RestoreMode) -> Unit = {},
    val onAnswer: (Long, Int?) -> Unit = { _, _ -> },
    val onAllowPhone: () -> Unit = {},
    val onRestore: () -> Unit = {},
)

private const val PHONE_ACCESS = "Allow phone access so Ledga can put this backup's lines on this phone's SIMs."

/** The restore sheet's body (spec §12.3): what the backup holds, Merge or Replace, the line questions, then Restore. */
@Composable
fun RestoreDraftContent(draft: RestoreDraft, phoneAccess: Boolean, actions: RestoreDraftActions, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(BackupText.draftLine(draft), style = LedgaType.body, color = c.ink2)
        Column(Modifier.selectableGroup()) {
            ChoiceRow(
                "Add to what's on this phone",
                draft.mode == RestoreMode.MERGE,
                { actions.onMode(RestoreMode.MERGE) },
                subtitle = "Keeps everything here and adds what's missing",
            )
            ChoiceRow(
                "Replace everything on this phone",
                draft.mode == RestoreMode.REPLACE,
                { actions.onMode(RestoreMode.REPLACE) },
                subtitle = "This phone's history becomes the backup's",
            )
        }
        if (draft.simsUnreadable && !phoneAccess) {
            Banner(PHONE_ACCESS, BannerTone.Info, icon = Ph.SimCard, actionLabel = "Allow", onAction = actions.onAllowPhone)
        }
        LineQuestionsContent(draft.questions, draft.answers, actions.onAnswer)
        PrimaryPill("Restore", actions.onRestore, Modifier.fillMaxWidth().padding(top = Spacing.s), enabled = draft.ready)
    }
}

/** R115: "Which SIM was <line>?" for each line Ledga couldn't match; one choice each. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LineQuestionsContent(questions: List<LineQuestion>, answers: Map<Long, Int?>, onAnswer: (Long, Int?) -> Unit) {
    val c = LedgaTheme.colors
    questions.forEach { q ->
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("Which SIM was ${q.line.displayName}?", style = LedgaType.bodyStrong, color = c.ink)
            FlowRow(
                Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                val answered = q.line.id in answers
                q.sims.forEach { sim ->
                    ChoiceChip(BackupText.simLabel(sim), answered && answers[q.line.id] == sim.subscriptionId, { onAnswer(q.line.id, sim.subscriptionId) }, role = Role.RadioButton)
                }
                ChoiceChip(BackupText.KEEP_OWN, answered && answers[q.line.id] == null, { onAnswer(q.line.id, null) }, role = Role.RadioButton)
            }
        }
    }
}
