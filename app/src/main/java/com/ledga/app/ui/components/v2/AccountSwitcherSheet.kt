package com.ledga.app.ui.components.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ledga.app.data.db.entity.MpesaAccount
import com.ledga.app.ui.components.parseColor
import com.ledga.app.ui.theme.LedgaAccent
import com.ledga.app.ui.theme.LedgaAccentDeep
import com.ledga.app.ui.theme.LedgaText
import com.ledga.app.ui.theme.Space

/**
 * Bottom sheet that lists every linked M-Pesa account plus a "Combined"
 * option. Opened from the AccountChip on Home.
 *
 * Selecting an entry updates the user's selected-account preference,
 * which the repository observes and re-scopes every query against.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitcherSheet(
    accounts: List<MpesaAccount>,
    selectedAccountId: Long?,
    onSelectAccount: (Long?) -> Unit,
    onDismiss: () -> Unit,
    onManage: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.Screen, vertical = Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s3),
        ) {
            Text(
                text = "Accounts",
                style = LedgaText.TitleL,
                color = MaterialTheme.colorScheme.onSurface,
            )

            AccountRow(
                title = "Combined",
                subtitle = "Every line in one view",
                color = LedgaAccent,
                initials = null,
                selected = selectedAccountId == null,
                onClick = {
                    onSelectAccount(null)
                    onDismiss()
                },
            )

            accounts.forEach { account ->
                AccountRow(
                    title = account.displayName,
                    subtitle = account.phoneNumber ?: "Subscription ${account.subscriptionId}",
                    color = parseColor(account.colorHex),
                    initials = account.displayName.initials(),
                    selected = selectedAccountId == account.id,
                    onClick = {
                        onSelectAccount(account.id)
                        onDismiss()
                    },
                )
            }

            ManageRow(onClick = {
                onDismiss()
                onManage()
            })

            Box(modifier = Modifier.padding(bottom = Space.s5))
        }
    }
}

@Composable
private fun AccountRow(
    title: String,
    subtitle: String,
    color: androidx.compose.ui.graphics.Color,
    initials: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        if (initials != null) {
            Avatar(initials = initials, color = color, size = 40.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.SimCard,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = LedgaText.TitleS,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = LedgaText.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = LedgaAccentDeep,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ManageRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = LedgaAccentDeep,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Manage accounts",
            style = LedgaText.BodyL,
            color = LedgaAccentDeep,
        )
    }
}

private fun String.initials(): String =
    split(" ", limit = 2)
        .mapNotNull { it.firstOrNull()?.toString() }
        .joinToString("")
        .take(2)
        .uppercase()
