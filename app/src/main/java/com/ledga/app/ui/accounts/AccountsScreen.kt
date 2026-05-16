package com.ledga.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledga.app.data.db.entity.MpesaAccount
import com.ledga.app.ui.components.parseColor
import com.ledga.app.ui.components.v2.Avatar
import com.ledga.app.ui.components.v2.BackLeading
import com.ledga.app.ui.components.v2.BentoCard
import com.ledga.app.ui.components.v2.LedgaTopBar
import com.ledga.app.ui.theme.CatAirtime
import com.ledga.app.ui.theme.CatFood
import com.ledga.app.ui.theme.CatInternational
import com.ledga.app.ui.theme.CatReceived
import com.ledga.app.ui.theme.CatSavings
import com.ledga.app.ui.theme.CatShopping
import com.ledga.app.ui.theme.CatTransport
import com.ledga.app.ui.theme.LedgaAccent
import com.ledga.app.ui.theme.LedgaAccentDeep
import com.ledga.app.ui.theme.LedgaText
import com.ledga.app.ui.theme.Space

private val ColorChoices = listOf(
    LedgaAccent, CatTransport, CatFood, CatAirtime,
    CatReceived, CatInternational, CatShopping, CatSavings,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    onOpenBackfill: () -> Unit = {},
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var editingAccount by remember { mutableStateOf<MpesaAccount?>(null) }

    if (editingAccount != null) {
        EditAccountDialog(
            account = editingAccount!!,
            onDismiss = { editingAccount = null },
            onRename = { newName -> viewModel.rename(editingAccount!!.id, newName) },
            onRecolor = { hex -> viewModel.recolor(editingAccount!!.id, hex) },
            onSetPrimary = { viewModel.setPrimary(editingAccount!!.id) },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LedgaTopBar(title = "Accounts", leading = { BackLeading(onBack) })
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.Screen)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(Space.Section),
        ) {
            if (state.accounts.isEmpty()) {
                BentoCard(
                    overline = "No accounts yet",
                    title = "Lines appear automatically",
                    icon = Icons.Filled.SimCard,
                    iconTint = LedgaAccentDeep,
                ) {
                    Text(
                        text = "When a new M-Pesa SMS lands, Ledga registers the SIM " +
                                "it came from as an account. Two SIMs = two accounts.",
                        style = LedgaText.BodyM,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                BentoCard(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    state.accounts.forEachIndexed { index, account ->
                        AccountManagementRow(
                            account = account,
                            onClick = { editingAccount = account },
                        )
                        if (index < state.accounts.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = Space.Card),
                            )
                        }
                    }
                }
            }

            BentoCard(
                overline = "Backfill",
                title = "Tag historical transactions",
                icon = Icons.Filled.History,
                iconTint = LedgaAccentDeep,
                onClick = onOpenBackfill,
            ) {
                Text(
                    text = "Imported transactions don't know which line they came " +
                            "from. Tag them in bulk so Combined / per-line views work " +
                            "on your full history.",
                    style = LedgaText.BodyM,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(Space.s5))
        }
    }
}

@Composable
private fun AccountManagementRow(account: MpesaAccount, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = Space.Card, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        Avatar(initials = account.displayName.initials(), color = parseColor(account.colorHex), size = 40.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = account.displayName,
                    style = LedgaText.TitleS,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (account.isPrimary) {
                    Spacer(modifier = Modifier.size(6.dp))
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Primary",
                        tint = LedgaAccentDeep,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                text = account.phoneNumber ?: "Subscription ${account.subscriptionId}",
                style = LedgaText.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EditAccountDialog(
    account: MpesaAccount,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onRecolor: (String) -> Unit,
    onSetPrimary: () -> Unit,
) {
    var name by remember { mutableStateOf(account.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Edit account", style = LedgaText.TitleM) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s5)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )

                Text(
                    text = "Color".uppercase(),
                    style = LedgaText.Overline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                    ColorChoices.forEach { color ->
                        ColorSwatch(
                            color = color,
                            selected = color.toHex() == account.colorHex,
                            onClick = { onRecolor(color.toHex()) },
                        )
                    }
                }

                if (!account.isPrimary) {
                    TextButton(onClick = { onSetPrimary(); onDismiss() }) {
                        Text("Make primary line")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onRename(name); onDismiss() }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                else Modifier
            )
            .clickable { onClick() }
    )
}

private fun Color.toHex(): String {
    val argb = this.value.toLong() shr 32 and 0xFFFFFFFFL
    return "#%06X".format(argb.toInt() and 0xFFFFFF)
}

private fun String.initials(): String =
    split(" ", limit = 2)
        .mapNotNull { it.firstOrNull()?.toString() }
        .joinToString("")
        .take(2)
        .uppercase()
