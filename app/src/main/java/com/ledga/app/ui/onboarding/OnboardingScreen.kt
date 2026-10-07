package com.ledga.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ledga.app.ui.app.HeroIcon
import com.ledga.app.ui.app.grouped
import com.ledga.app.ui.backup.LineQuestionsContent
import com.ledga.app.ui.design.components.Banner
import com.ledga.app.ui.design.components.BannerTone
import com.ledga.app.ui.design.components.LinkButton
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.format.DateLabels
import com.ledga.app.ui.design.icons.Fluent
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Radii
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.app.work.ImportProgress
import com.ledga.app.work.RestoreProgress

/** R126: what the import step's backup offer does. */
data class RestoreOfferActions(
    val onRestore: () -> Unit = {},
    val onStartFresh: () -> Unit = {},
    val onAnswer: (Long, Int?) -> Unit = { _, _ -> },
    val onAllowPhone: () -> Unit = {},
)

/**
 * Spec §10.4 onboarding, one step at a time. Stateless: [OnboardingRoute] owns the ViewModel and the permission
 * dialogs. Each step's content scrolls and its actions stay pinned to the bottom, so they're reachable at 1.3× font
 * and with the keyboard open.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onName: (String) -> Unit,
    onNext: () -> Unit,
    onAllowSms: () -> Unit,
    onSkip: () -> Unit,
    onImport: () -> Unit,
    onLineName: (Long, String) -> Unit,
    onAllowNotifications: () -> Unit,
    restore: RestoreOfferActions = RestoreOfferActions(),
) {
    when (state.step) {
        Step.WELCOME -> WelcomeStep(state, onName, onNext)
        Step.SMS -> SmsStep(state, onAllowSms, onSkip)
        Step.IMPORT -> ImportStep(state, onImport, onNext, onLineName, restore)
        Step.NOTIFICATIONS -> NotificationsStep(state, onAllowNotifications, onSkip)
    }
}

@Composable
private fun WelcomeStep(state: OnboardingState, onName: (String) -> Unit, onNext: () -> Unit) = StepFrame(
    state,
    icon = "fluent_sparkles",
    title = "Welcome to Ledga",
    lead = "Every M-Pesa payment, sorted and added up. Everything stays on this phone.",
    actions = { Primary("Get started", onNext) },
) {
    OutlinedTextField(
        value = state.name,
        onValueChange = onName,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Your name (optional)") },
        singleLine = true,
        textStyle = LedgaType.body,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
    )
}

/** The trust moment, as mocked (`detail-v1.html` → `onb`). */
@Composable
private fun SmsStep(state: OnboardingState, onAllowSms: () -> Unit, onSkip: () -> Unit) = StepFrame(
    state,
    icon = "fluent_incoming_envelope",
    title = "Let Ledga read your M-Pesa messages",
    lead = "That's how every payment shows up here automatically, with no typing.",
    actions = {
        Primary("Allow SMS access", onAllowSms)
        LinkButton("Not now", onSkip)
    },
) {
    Bullet("Only messages from MPESA and FULIZA are read")
    Bullet("Nothing is ever sent to a server")
    Bullet("You can turn this off any time in Settings")
    TrustCard("Your data stays on this phone. If Android backup is on, a private copy is kept in your Google account so you can restore on a new phone.")
}

@Composable
private fun ImportStep(state: OnboardingState, onImport: () -> Unit, onNext: () -> Unit, onLineName: (Long, String) -> Unit, restore: RestoreOfferActions) {
    val offer = state.offer
    if (offer != null && !state.restored) return RestoreStep(state, offer, restore)
    val preview = state.preview
    when (val p = state.import) {
        is ImportProgress.Running -> StepFrame(
            state,
            icon = "fluent_memo",
            title = "Importing your history",
            lead = "Ledga reads each M-Pesa message once and keeps your history on this phone.",
            actions = {},
        ) {
            Banner(
                if (p.total > 0) "${grouped(p.done)} of ${grouped(p.total)} messages" else "Reading your messages…",
                BannerTone.Progress,
                progress = p.fraction,
            )
        }
        is ImportProgress.Done -> StepFrame(
            state,
            icon = "fluent_check_mark_button",
            title = "Your history is ready",
            lead = "Ledga read ${grouped(p.found)} M-Pesa ${messages(p.found)}." +
                if (state.lines.size >= 2) " It found ${state.lines.size} lines on this phone: give each a name you'll recognise." else "",
            actions = { Primary("Continue", onNext) },
        ) {
            state.lines.forEach { line ->
                OutlinedTextField(
                    value = line.name,
                    onValueChange = { onLineName(line.id, it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(line.label) },
                    singleLine = true,
                    textStyle = LedgaType.body,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )
            }
        }
        ImportProgress.Failed -> StepFrame(
            state,
            icon = "fluent_warning",
            title = "The import didn't finish",
            lead = "You can carry on now and import again later from You.",
            actions = {
                Primary("Try again", onImport)
                LinkButton("Continue", onNext)
            },
        )
        ImportProgress.Idle -> if (preview == null || preview.count == 0) {
            StepFrame(
                state,
                icon = "fluent_incoming_envelope",
                title = "No M-Pesa messages yet",
                lead = "That's fine: new payments appear here as their messages arrive.",
                actions = { Primary("Continue", onNext) },
            )
        } else {
            StepFrame(
                state,
                icon = "fluent_magnifying_glass_tilted_left",
                title = "Found ${grouped(preview.count)} M-Pesa ${messages(preview.count)}",
                lead = if (state.restored) {
                    "Your backup is back. Ledga now adds this phone's messages and skips any it already has."
                } else {
                    span(preview) + "Ledga reads them once and keeps your history on this phone."
                },
                actions = { Primary("Import", onImport) },
            )
        }
    }
}

/** R126: the backup a fresh install found, before anything is imported. */
@Composable
private fun RestoreStep(state: OnboardingState, offer: RestoreOffer, actions: RestoreOfferActions) {
    val date = DateLabels.date(DateLabels.nairobiDate(offer.writtenAt))
    when (val r = state.restore) {
        is RestoreProgress.Running -> StepFrame(
            state,
            icon = "fluent_floppy_disk",
            title = "Restoring your backup",
            lead = "Ledga is putting back your payments, categories, rules and notes.",
            actions = {},
        ) {
            Banner("Restoring$ELLIPSIS", BannerTone.Progress, progress = r.fraction)
        }
        is RestoreProgress.Failed -> StepFrame(
            state,
            icon = "fluent_warning",
            title = "The restore didn't finish",
            lead = r.message,
            actions = {
                PrimaryPill("Try again", actions.onRestore, Modifier.fillMaxWidth(), enabled = offer.ready)
                LinkButton("Start fresh", actions.onStartFresh)
            },
        )
        else -> StepFrame(
            state,
            icon = "fluent_floppy_disk",
            title = "Restore your Ledga backup",
            lead = "From $date · ${grouped(offer.payments)} ${if (offer.payments == 1) "payment" else "payments"}, with your categories, " +
                "rules and notes. Ledga then adds this phone's messages.",
            actions = {
                PrimaryPill("Restore", actions.onRestore, Modifier.fillMaxWidth(), enabled = offer.ready)
                LinkButton("Start fresh", actions.onStartFresh)
            },
        ) {
            if (offer.simsUnreadable && !state.phoneAccess) {
                Banner(
                    "Allow phone access so Ledga can put your backup's lines on this phone's SIMs.",
                    BannerTone.Info,
                    icon = Ph.SimCard,
                    actionLabel = "Allow",
                    onAction = actions.onAllowPhone,
                )
            }
            LineQuestionsContent(offer.questions, offer.answers, actions.onAnswer)
        }
    }
}

private val ELLIPSIS = Char(0x2026)

@Composable
private fun NotificationsStep(state: OnboardingState, onAllow: () -> Unit, onSkip: () -> Unit) = StepFrame(
    state,
    icon = "fluent_bell",
    title = "Get a nudge when money moves",
    lead = "Ledga can tell you what you spent, and remind you before Fuliza is due.",
    actions = {
        Primary("Turn on notifications", onAllow)
        LinkButton("Not now", onSkip)
    },
) {
    Bullet("A summary of what you spent each evening")
    Bullet("An alert for large payments")
    Bullet("A reminder before Fuliza is due")
}

@Composable
private fun StepFrame(
    state: OnboardingState,
    icon: String,
    title: String,
    lead: String,
    actions: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val c = LedgaTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Spacing.xl, vertical = Spacing.l),
    ) {
        StepDots(state.steps, state.step)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeroIcon(icon, Modifier.padding(top = Spacing.xxxl))
            Text(
                title,
                Modifier.padding(top = Spacing.xl).semantics { heading() },
                style = LedgaType.screenTitle,
                color = c.ink,
                textAlign = TextAlign.Center,
            )
            Text(lead, Modifier.padding(top = Spacing.s), style = LedgaType.body, color = c.muted, textAlign = TextAlign.Center)
            Column(
                Modifier.fillMaxWidth().padding(top = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
                content = content,
            )
        }
        Column(Modifier.fillMaxWidth().padding(top = Spacing.l), horizontalAlignment = Alignment.CenterHorizontally) {
            actions()
        }
    }
}

/** The mockup's `.dots`: the current step is a wider green pill. TalkBack hears "Step 2 of 5". */
@Composable
private fun StepDots(steps: List<Step>, current: Step) {
    val c = LedgaTheme.colors
    val index = steps.indexOf(current)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Step ${index + 1} of ${steps.size}" },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        steps.forEach { step ->
            val on = step == current
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (on) 18.dp else 6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (on) c.primary else c.line),
            )
        }
    }
}

@Composable
private fun Primary(text: String, onClick: () -> Unit) = PrimaryPill(text, onClick, Modifier.fillMaxWidth())

@Composable
private fun Bullet(text: String) {
    val c = LedgaTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
        Icon(Ph.CheckCircle, contentDescription = null, tint = c.primary, modifier = Modifier.size(Sizes.icon))
        Text(text, style = LedgaType.bodyStrong, color = c.ink2)
    }
}

/** The mockup's `.trust` card: the privacy promise on primarySoft. */
@Composable
private fun TrustCard(text: String) {
    val c = LedgaTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.stat)).background(c.primarySoft).padding(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(Fluent.resOf("fluent_locked")), contentDescription = null, modifier = Modifier.size(26.dp))
        Text(text, style = LedgaType.caption, color = c.onPrimarySoft)
    }
}

private fun messages(n: Int) = if (n == 1) "message" else "messages"

/** "From 12 Mar 2023 to 6 Oct 2026. " (Nairobi dates); a single day reads "From 6 Oct 2026. ". */
private fun span(p: InboxPreview): String {
    val from = p.oldest?.let { DateLabels.date(DateLabels.nairobiDate(it)) }
    val to = p.newest?.let { DateLabels.date(DateLabels.nairobiDate(it)) }
    return when {
        from != null && to != null && from != to -> "From $from to $to. "
        to != null -> "From $to. "
        else -> ""
    }
}
