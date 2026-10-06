package com.ledga.app.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ledga.app.data.edit.TransactionEdits
import com.ledga.app.ui.design.components.LedgaModalSheet
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType
import com.ledga.app.ui.trackers.TrackerText
import kotlinx.coroutines.delay

/** "+ Add rule" (R48): a name, an optional account, and what Save will do, counted before it does it. */
@Composable
fun AddRuleContent(
    categoryName: String,
    name: String,
    account: String,
    preview: ShownPreview?,
    onName: (String) -> Unit,
    onAccount: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LedgaTheme.colors
    // R48: the count shown is for exactly this text, or it says it's counting and Save waits.
    val shown = preview?.takeIf { it.isFor(name, account) }
    val counting = shown == null && name.isNotBlank()
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text("Payments whose name has these words go to $categoryName, now and from now on.", style = LedgaType.body, color = c.muted)
        OutlinedTextField(name, onName, Modifier.fillMaxWidth(), label = { Text("Name has") }, placeholder = { Text("KPLC") }, singleLine = true)
        OutlinedTextField(account, onAccount, Modifier.fillMaxWidth(), label = { Text("Account (optional)") }, singleLine = true)
        Text(if (counting) TrackerText.COUNTING else TrackerText.rulePreview(shown?.preview, name, categoryName), style = LedgaType.caption, color = c.muted)
        PrimaryPill("Save rule", onSave, Modifier.fillMaxWidth(), enabled = shown?.preview != null)
    }
}

/** R51: rename a category everywhere. */
@Composable
fun RenameContent(name: String, refused: Boolean, onName: (String) -> Unit, onSave: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        OutlinedTextField(
            name,
            { onName(it.take(TransactionEdits.CATEGORY_NAME_MAX)) },
            Modifier.fillMaxWidth(),
            label = { Text("Name") },
            singleLine = true,
            isError = refused,
            supportingText = if (refused) ({ Text("That name is blank or already used in this group.") }) else null,
        )
        PrimaryPill("Save", onSave, Modifier.fillMaxWidth())
    }
}

/** The add-rule sheet (R48): the count waits for typing to settle, as Activity's search does. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleSheet(categoryName: String, preview: ShownPreview?, onCount: (String, String) -> Unit, onSave: (String, String) -> Unit, onClose: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(name, account) {
        delay(PREVIEW_SETTLE_MS)
        onCount(name, account)
    }
    LedgaModalSheet(onDismiss = onClose, title = "Add a rule") {
        AddRuleContent(
            categoryName, name, account, preview,
            onName = { name = it.take(TransactionEdits.RULE_NAME_MAX) },
            onAccount = { account = it },
            onSave = { onSave(name, account) },
        )
    }
}

/** R51: the rename sheet. [onSave] reports whether the name was taken; a refusal keeps the sheet open with the reason. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameSheet(current: String, onSave: (String, (Boolean) -> Unit) -> Unit, onClose: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    var refused by rememberSaveable { mutableStateOf(false) }
    LedgaModalSheet(onDismiss = onClose, title = "Rename") {
        RenameContent(
            name, refused,
            onName = {
                name = it
                refused = false
            },
            onSave = { onSave(name) { ok -> if (ok) onClose() else refused = true } },
        )
    }
}

private const val PREVIEW_SETTLE_MS = 250L
