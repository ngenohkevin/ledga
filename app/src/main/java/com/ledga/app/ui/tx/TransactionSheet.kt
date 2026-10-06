package com.ledga.app.ui.tx

import kotlin.random.Random
import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.data.room.TxRow
import com.ledga.app.ui.design.components.CategoryIcon
import com.ledga.app.ui.design.components.InfoChip
import com.ledga.app.ui.design.components.InitialAvatar
import com.ledga.app.ui.design.components.Leading
import com.ledga.app.ui.design.components.LedgaModalSheet
import com.ledga.app.ui.design.components.LinkButton
import com.ledga.app.ui.design.components.ListRow
import com.ledga.app.ui.design.components.OutlinePill
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.components.RowDivider
import com.ledga.app.ui.design.components.RowTrailing
import com.ledga.app.ui.design.components.SkeletonRow
import com.ledga.app.ui.design.components.WellSize
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Radii
import com.ledga.app.ui.design.tokens.Sizes
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FALLBACK_ICON = "fluent_package"
private const val COPIED_MS = 2_000L

/** Everything the sheet can do; the host wires it to the ViewModel, the picker, the share sheet and the clipboard. */
data class TxSheetActions(
    val onChangeCategory: () -> Unit = {},
    val onSaveNote: (String) -> Unit = {},
    val onOwnAccount: (Boolean) -> Unit = {},
    val onConfirmOwn: (allFromName: Boolean) -> Unit = {},
    val onCancelOwn: () -> Unit = {},
    val onCopyCode: (String) -> Unit = {},
    val onMoveLine: (Long) -> Unit = {},
    val onShare: () -> Unit = {},
    val onHide: () -> Unit = {},
    val onUnhide: () -> Unit = {},
)

/**
 * The transaction sheet's body (spec §10.4, mockups `txsheet` and `txsheet-fz`):
 * - the header, its chips and its facts;
 * - rows for the category, the note, "My own account" (only for kinds that can be own-account) and the original SMS;
 * - Share and Hide.
 *
 * It holds no data of its own, only what is open. [startEditingNote] and [startSmsOpen] let screenshots show those states.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionSheetContent(
    state: TxSheetState,
    actions: TxSheetActions,
    modifier: Modifier = Modifier,
    startEditingNote: Boolean = false,
    startSmsOpen: Boolean = false,
) {
    val tx = state.tx
    if (tx == null) {
        Column(modifier.fillMaxWidth()) { repeat(4) { SkeletonRow() } }
        return
    }
    val c = LedgaTheme.colors
    var editingNote by rememberSaveable(tx.code) { mutableStateOf(startEditingNote) }
    var smsOpen by rememberSaveable(tx.code) { mutableStateOf(startSmsOpen) }
    val inflow = TxText.isInflow(tx.flow)
    val icon = state.category?.icon3d ?: FALLBACK_ICON
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (val leading = TxText.leading(tx, icon)) {
            is Leading.Icon -> CategoryIcon(leading.key, contentDescription = null, size = WellSize.XLarge)
            is Leading.Avatar -> InitialAvatar(leading.name, leading.inflow, size = WellSize.XLarge)
        }
        Text(
            TxText.title(tx),
            Modifier.padding(top = Spacing.m).semantics { heading() },
            style = LedgaType.section,
            color = c.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            AmountFormat.signedKsh(tx.amountCents, inflow),
            Modifier.padding(top = 2.dp),
            style = LedgaType.amountL,
            color = if (inflow) c.inflow else c.ink,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
            Modifier.padding(top = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TxText.chips(tx).forEach { InfoChip(it.text, tone = it.tone) }
        }
        Facts(state, tx, actions, Modifier.padding(top = Spacing.l))
        Column(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
            SheetRow(icon, "Category", TxText.categoryLabel(state.categoryName, state.category?.tracked == true), "Change category", actions.onChangeCategory)
            RowDivider()
            if (editingNote) {
                NoteEditor(
                    initial = tx.note.orEmpty(),
                    onSave = {
                        actions.onSaveNote(it)
                        editingNote = false
                    },
                    onCancel = { editingNote = false },
                )
            } else {
                SheetRow("fluent_memo", "Note", tx.note ?: "Add a note", if (tx.note == null) "Add a note" else "Edit note") { editingNote = true }
            }
            if (state.canBeOwnAccount) {
                RowDivider()
                ListRow(
                    title = "My own account",
                    subtitle = "Payments here aren't spending",
                    iconKey = "fluent_bank",
                    trailing = RowTrailing.Toggle(state.isOwnAccount, actions.onOwnAccount),
                )
                state.pendingOwn?.let { OwnChoice(it, actions) }
            }
            RowDivider()
            SmsRow(state.sms, smsOpen) { smsOpen = !smsOpen }
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            OutlinePill("Share", actions.onShare, Modifier.weight(1f), icon = Ph.ShareNetwork)
            if (tx.isHidden) {
                OutlinePill("Unhide", actions.onUnhide, Modifier.weight(1f), icon = Ph.EyeSlash)
            } else {
                OutlinePill("Hide", actions.onHide, Modifier.weight(1f), icon = Ph.EyeSlash)
            }
        }
    }
}

/** The facts panel on plate (mockup `txsheet`): Code with copy, Date, Fuliza's part, Balance after, Line. */
@Composable
private fun Facts(state: TxSheetState, tx: TxRow, actions: TxSheetActions, modifier: Modifier) {
    val c = LedgaTheme.colors
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.stat)).background(c.plate).padding(horizontal = Spacing.m, vertical = 4.dp),
    ) {
        TxText.facts(tx, state.line).forEach { fact ->
            when {
                fact.copyable -> FactRow(fact) { CopyButton(fact.value, actions.onCopyCode) }
                fact.isLine && state.canMoveLine -> LineFact(fact, state, actions)
                else -> FactRow(fact)
            }
        }
    }
}

@Composable
private fun FactRow(fact: Fact, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val c = LedgaTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 40.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(fact.label, Modifier.padding(end = Spacing.m), style = LedgaType.caption, color = c.muted)
        Text(
            fact.value,
            Modifier.weight(1f),
            style = LedgaType.bodyStrong,
            color = if (fact.tone == FactTone.Danger) c.danger else c.ink,
            textAlign = TextAlign.End,
        )
        trailing?.invoke()
    }
}

/** Copies the code; the icon turns into a tick for two seconds, which TalkBack announces as "Code copied". */
@Composable
private fun CopyButton(code: String, onCopy: (String) -> Unit) {
    val c = LedgaTheme.colors
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_MS)
            copied = false
        }
    }
    IconButton(
        onClick = {
            onCopy(code)
            copied = true
        },
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(
            if (copied) Ph.CheckBold else Ph.Copy,
            contentDescription = if (copied) "Code copied" else "Copy code",
            tint = if (copied) c.primary else c.muted,
            modifier = Modifier.size(Sizes.iconSmall),
        )
    }
}

/** R46: with two or more lines, the Line fact moves the payment to another one. */
@Composable
private fun LineFact(fact: Fact, state: TxSheetState, actions: TxSheetActions) {
    val c = LedgaTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        FactRow(
            fact,
            Modifier.heightIn(min = Sizes.touchTarget).clickable(role = Role.Button, onClickLabel = "Move to another line") { open = true },
        ) {
            Icon(Ph.CaretDownBold, contentDescription = null, tint = c.muted, modifier = Modifier.padding(start = 6.dp).size(12.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surfaceSheet) {
            state.lines.filter { it.id != state.tx?.lineId }.forEach { line ->
                DropdownMenuItem(
                    text = { Text(TxText.lineLabel(line), style = LedgaType.body, color = c.ink) },
                    onClick = {
                        open = false
                        actions.onMoveLine(line.id)
                    },
                )
            }
        }
    }
}

/** A sheet row (mockup `txsheet`): a small 3D icon, a caption over its value, and a chevron. */
@Composable
private fun SheetRow(iconKey: String, label: String, value: String, actionLabel: String, onClick: () -> Unit) {
    val c = LedgaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        CategoryIcon(iconKey, contentDescription = null, size = WellSize.Small)
        Column(Modifier.weight(1f)) {
            Text(label, style = LedgaType.caption, color = c.muted)
            Text(value, style = LedgaType.bodyStrong, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Icon(Ph.CaretRightBold, contentDescription = null, tint = c.muted, modifier = Modifier.size(Sizes.iconSmall))
    }
}

/** The inline note editor (spec §10.4 "Note (inline editor)"). The field's label stays short (`app/DESIGN.md`). */
@Composable
private fun NoteEditor(initial: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.s)) {
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.length <= TransactionEdits.NOTE_MAX) text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Note") },
            textStyle = LedgaType.body,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave(text) }),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LinkButton("Cancel", onCancel)
            PrimaryPill("Save note", { onSave(text) })
        }
    }
}

/** R37: "all from <name>" or "only this payment", asked in place under the switch. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OwnChoice(p: PendingOwn, actions: TxSheetActions) {
    val c = LedgaTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.s)
            .clip(RoundedCornerShape(Radii.stat))
            .background(c.plate)
            .padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        val question = if (p.own) {
            "Treat all ${p.count} from ${p.name} as your own account?"
        } else {
            "Stop treating all ${p.count} from ${p.name} as your own account?"
        }
        Text(question, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = LedgaType.bodyStrong, color = c.ink)
        Text("Their future payments follow this too.", style = LedgaType.caption, color = c.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            PrimaryPill("All ${p.count}", { actions.onConfirmOwn(true) })
            OutlinePill("Only this payment", { actions.onConfirmOwn(false) })
            LinkButton("Cancel", actions.onCancelOwn)
        }
    }
}

/** "Original SMS · 2 messages" (spec §10.4): every SMS merged into the transaction, selectable for copying. */
@Composable
private fun SmsRow(bodies: List<String>, open: Boolean, onToggle: () -> Unit) {
    val c = LedgaTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = if (open) "Hide the SMS" else "Show the SMS", onClick = onToggle)
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        CategoryIcon("fluent_incoming_envelope", contentDescription = null, size = WellSize.Small)
        Text(
            if (bodies.size > 1) "Original SMS · ${bodies.size} messages" else "Original SMS",
            Modifier.weight(1f),
            style = LedgaType.bodyStrong,
            color = c.ink,
        )
        Icon(if (open) Ph.CaretUpBold else Ph.CaretDownBold, contentDescription = null, tint = c.muted, modifier = Modifier.size(Sizes.iconSmall))
    }
    if (open) {
        SelectionContainer {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.stat)).background(c.plate).padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                // Ledga's own Inter, never a system family: Monospace follows the phone's font setting (a script font on some Samsungs).
                bodies.forEach { Text(it, style = LedgaType.caption, color = c.ink2) }
            }
        }
    }
}

/**
 * Opens the transaction sheet for [code] (null = closed). Activity uses it now; Home and Tracker detail (4c) and
 * Alerts (4d) reuse it. [onHidden] lets the screen offer Undo after Hide; [onChangeCategory] opens the picker on top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionSheetHost(
    code: String?,
    onDismiss: () -> Unit,
    onHidden: (String) -> Unit,
    onChangeCategory: (String) -> Unit,
    vm: TxSheetViewModel = hiltViewModel(key = "tx-sheet"),
) {
    if (code == null) return
    // R62: a saveable session id survives a rotation, so re-opening the same code keeps the sheet's state.
    val session = rememberSaveable(code) { Random.nextLong() }
    LaunchedEffect(code, session) { vm.open(code, session) }
    val state by vm.state.collectAsStateWithLifecycle()
    val shown = state.takeIf { it.tx?.code == code } ?: TxSheetState()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    LedgaModalSheet(onDismiss = onDismiss, title = null) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            TransactionSheetContent(
                state = shown,
                actions = TxSheetActions(
                    onChangeCategory = { onChangeCategory(code) },
                    onSaveNote = vm::setNote,
                    onOwnAccount = vm::setOwnAccount,
                    onConfirmOwn = vm::confirmOwn,
                    onCancelOwn = vm::cancelOwn,
                    onCopyCode = { value -> scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("M-Pesa code", value))) } },
                    onMoveLine = vm::moveToLine,
                    onShare = {
                        shown.tx?.let { tx ->
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, TxText.shareText(tx, shown.categoryName))
                            context.startActivity(Intent.createChooser(send, "Share payment"))
                        }
                    },
                    onHide = {
                        vm.setHidden(true)
                        onHidden(code)
                        onDismiss()
                    },
                    onUnhide = { vm.setHidden(false) },
                ),
            )
        }
    }
}
