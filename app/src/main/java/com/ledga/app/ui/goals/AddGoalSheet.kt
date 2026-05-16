package com.ledga.app.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ledga.app.data.db.entity.ContributionRule
import com.ledga.app.ui.theme.CatAirtime
import com.ledga.app.ui.theme.CatFood
import com.ledga.app.ui.theme.CatReceived
import com.ledga.app.ui.theme.CatSavings
import com.ledga.app.ui.theme.CatShopping
import com.ledga.app.ui.theme.CatTransport
import com.ledga.app.ui.theme.LedgaAccent
import com.ledga.app.ui.theme.LedgaText
import com.ledga.app.ui.theme.Space
import com.ledga.app.util.DateUtils

private enum class RuleChoice(val label: String, val help: String) {
    AllSavings(
        label = "All savings deposits",
        help = "Every M-Shwari and KCB M-Pesa deposit counts.",
    ),
    ToRecipient(
        label = "Specific recipient or bill",
        help = "Match any outflow whose recipient name contains a fragment you give (e.g., \"School fees\").",
    ),
    Manual(
        label = "Manual",
        help = "You'll tap \"Add to goal\" inside any transaction's detail.",
    ),
}

private val ColorChoices = listOf(
    LedgaAccent, CatTransport, CatFood, CatAirtime,
    CatReceived, CatSavings, CatShopping,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGoalSheet(
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        targetAmount: Double,
        targetDate: Long?,
        rule: ContributionRule,
        colorHex: String,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var ruleChoice by remember { mutableStateOf(RuleChoice.AllSavings) }
    var recipientFragment by remember { mutableStateOf("") }
    var targetDate by remember { mutableStateOf<Long?>(null) }
    var color by remember { mutableStateOf(LedgaAccent) }
    var pickingDate by remember { mutableStateOf(false) }

    if (pickingDate) {
        val ps = rememberDatePickerState(initialSelectedDateMillis = targetDate)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    targetDate = ps.selectedDateMillis
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = ps) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.Screen, vertical = Space.s4)
                .padding(bottom = Space.s6),
            verticalArrangement = Arrangement.spacedBy(Space.s5),
        ) {
            Text(
                text = "New goal",
                style = LedgaText.TitleL,
                color = MaterialTheme.colorScheme.onSurface,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name (e.g. School fees)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Target amount (Ksh)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { pickingDate = true }
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    text = targetDate?.let { "Target date · ${DateUtils.formatDate(it)}" }
                        ?: "Target date (optional)",
                    style = LedgaText.BodyL,
                    color = if (targetDate != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "How to track".uppercase(),
                style = LedgaText.Overline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RuleChoice.entries.forEach { choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { ruleChoice = choice }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        RadioButton(
                            selected = ruleChoice == choice,
                            onClick = { ruleChoice = choice },
                        )
                        Column(
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = choice.label,
                                style = LedgaText.BodyL,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = choice.help,
                                style = LedgaText.Caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (ruleChoice == RuleChoice.ToRecipient) {
                OutlinedTextField(
                    value = recipientFragment,
                    onValueChange = { recipientFragment = it },
                    label = { Text("Recipient name fragment") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                text = "Color".uppercase(),
                style = LedgaText.Overline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                ColorChoices.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c)
                            .then(
                                if (c == color)
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .clickable { color = c }
                    )
                }
            }

            val target = amountText.toDoubleOrNull() ?: 0.0
            val canSubmit = name.isNotBlank() && target > 0 &&
                    (ruleChoice != RuleChoice.ToRecipient || recipientFragment.isNotBlank())

            Button(
                enabled = canSubmit,
                onClick = {
                    val rule = when (ruleChoice) {
                        RuleChoice.AllSavings -> ContributionRule.AllSavingsDeposits
                        RuleChoice.ToRecipient ->
                            ContributionRule.ToRecipient(recipientFragment.trim())
                        RuleChoice.Manual -> ContributionRule.Manual
                    }
                    onCreate(name.trim(), target, targetDate, rule, color.toHex())
                },
                colors = ButtonDefaults.buttonColors(containerColor = LedgaAccent),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Create goal") }
        }
    }
}

private fun Color.toHex(): String {
    val argb = this.value.toLong() shr 32 and 0xFFFFFFFFL
    return "#%06X".format(argb.toInt() and 0xFFFFFF)
}
