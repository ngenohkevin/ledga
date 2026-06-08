package com.ledga.app.ui.accounts

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledga.app.ui.components.parseColor
import com.ledga.app.ui.components.v2.Avatar
import com.ledga.app.ui.components.v2.BackLeading
import com.ledga.app.ui.components.v2.BentoCard
import com.ledga.app.ui.components.v2.onTonal
import com.ledga.app.ui.components.v2.LedgaChip
import com.ledga.app.ui.components.v2.LedgaTopBar
import com.ledga.app.ui.theme.LedgaAccent
import com.ledga.app.ui.theme.LedgaAccentDeep
import com.ledga.app.ui.theme.LedgaAccentSoft
import com.ledga.app.ui.theme.LedgaText
import com.ledga.app.ui.theme.Space
import com.ledga.app.util.DateUtils
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackfillScreen(
    onBack: () -> Unit,
    viewModel: BackfillViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var fromDate by remember { mutableStateOf<Long?>(null) }
    var toDate by remember { mutableStateOf<Long?>(null) }
    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }

    if (pickingFrom) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = fromDate)
        DatePickerDialog(
            onDismissRequest = { pickingFrom = false },
            confirmButton = {
                TextButton(onClick = {
                    fromDate = pickerState.selectedDateMillis
                    pickingFrom = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickingFrom = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
    if (pickingTo) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = toDate)
        DatePickerDialog(
            onDismissRequest = { pickingTo = false },
            confirmButton = {
                TextButton(onClick = {
                    toDate = pickerState.selectedDateMillis
                    pickingTo = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickingTo = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LedgaTopBar(title = "Backfill", leading = { BackLeading(onBack) })

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.Screen)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(Space.Section),
        ) {
            // ---- Auto scan ----
            BentoCard(
                overline = "Best match",
                title = "Auto-scan SMS inbox",
                icon = Icons.Filled.Search,
                iconTint = LedgaAccentDeep,
            ) {
                Text(
                    text = "Re-reads every MPESA message in your phone's SMS inbox, " +
                            "matches each transaction code to Ledga's DB, and writes " +
                            "the originating SIM as the account.",
                    style = LedgaText.BodyM,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    state.isAutoRunning -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.s4),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = LedgaAccent,
                            )
                            val p = state.autoProgress
                            Text(
                                text = if (p != null) "Scanning ${p.first} / ${p.second}…"
                                else "Starting…",
                                style = LedgaText.BodyM,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    state.autoResult != null -> {
                        val r = state.autoResult!!
                        Text(
                            text = buildString {
                                append("Scanned ${r.scanned} SMS. ")
                                append("Tagged ${r.tagged} transactions.")
                                if (r.accountsCreated > 0) {
                                    append(" Created ${r.accountsCreated} new account(s).")
                                }
                                if (r.unmatched > 0) {
                                    append(" ${r.unmatched} SMS without a SIM column.")
                                }
                            },
                            style = LedgaText.BodyM,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Button(
                            onClick = { viewModel.runAuto() },
                            colors = ButtonDefaults.buttonColors(containerColor = LedgaAccent),
                        ) { Text("Run again") }
                    }
                    else -> {
                        Button(
                            onClick = { viewModel.runAuto() },
                            colors = ButtonDefaults.buttonColors(containerColor = LedgaAccent),
                        ) { Text("Start auto-scan") }
                    }
                }
            }

            // ---- Date range ----
            BentoCard(
                overline = "Fallback",
                title = "Bulk by date range",
                icon = Icons.Filled.History,
                iconTint = LedgaAccentDeep,
            ) {
                Text(
                    text = "Use this when an SMS was deleted from your phone's inbox " +
                            "but the transaction is still in Ledga.",
                    style = LedgaText.BodyM,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.accounts.isEmpty()) {
                    Text(
                        text = "Add at least one account before tagging by date range.",
                        style = LedgaText.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Account".uppercase(),
                        style = LedgaText.Overline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.s3),
                    ) {
                        state.accounts.forEach { account ->
                            val color = parseColor(account.colorHex)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Avatar(initials = account.displayName.first().toString(),
                                    color = color, size = 20.dp)
                                LedgaChip(
                                    label = account.displayName,
                                    selected = selectedAccountId == account.id,
                                    onClick = { selectedAccountId = account.id },
                                )
                            }
                        }
                    }

                    Text(
                        text = "Range".uppercase(),
                        style = LedgaText.Overline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.s4),
                    ) {
                        DateButton(
                            label = "From",
                            value = fromDate?.let { fmt(it) } ?: "Pick",
                            modifier = Modifier.weight(1f),
                            onClick = { pickingFrom = true },
                        )
                        DateButton(
                            label = "To",
                            value = toDate?.let { fmt(it) } ?: "Pick",
                            modifier = Modifier.weight(1f),
                            onClick = { pickingTo = true },
                        )
                    }

                    val ready = selectedAccountId != null && fromDate != null && toDate != null
                    Button(
                        onClick = {
                            val s = fromDate ?: return@Button
                            val e = (toDate ?: return@Button) + 86_400_000L - 1 // include the day
                            viewModel.runDateRange(selectedAccountId!!, s, e)
                        },
                        enabled = ready && !state.dateRangeRunning,
                        colors = ButtonDefaults.buttonColors(containerColor = LedgaAccent),
                    ) {
                        if (state.dateRangeRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Applying…")
                        } else {
                            Text("Tag this range")
                        }
                    }
                    state.dateRangeResult?.let { updated ->
                        Text(
                            text = "Tagged $updated transactions.",
                            style = LedgaText.BodyM,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            BentoCard(
                title = "Per-transaction override",
                tonal = true,
                tonalColor = LedgaAccentSoft,
            ) {
                Text(
                    text = "Tap any transaction to open its detail sheet — there's an " +
                            "Account row at the bottom that lets you reassign one row " +
                            "at a time. Useful for stragglers.",
                    style = LedgaText.BodyM,
                    color = onTonal(LedgaAccentSoft),
                )
            }

            Spacer(modifier = Modifier.height(Space.s5))
        }
    }
}

@Composable
private fun DateButton(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .let { it },
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                Text(text = label.uppercase(), style = LedgaText.Overline)
                Text(text = value, style = LedgaText.BodyL)
            }
        }
    }
}

private val Fmt = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)
private fun fmt(epoch: Long): String = Fmt.format(java.util.Date(epoch))
