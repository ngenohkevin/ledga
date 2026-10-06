package com.ledga.app.ui.you

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.ui.app.DetailFrame
import com.ledga.app.ui.app.openNotificationSettings
import com.ledga.app.ui.app.showsRationale
import com.ledga.app.ui.design.components.Banner
import com.ledga.app.ui.design.components.BannerTone
import com.ledga.app.ui.design.components.LedgaCard
import com.ledga.app.ui.design.components.LedgaModalSheet
import com.ledga.app.ui.design.components.ListRow
import com.ledga.app.ui.design.components.PrimaryPill
import com.ledga.app.ui.design.components.RowDivider
import com.ledga.app.ui.design.components.RowTrailing
import com.ledga.app.ui.design.components.SkeletonRow
import com.ledga.app.ui.design.format.AmountFormat
import com.ledga.app.ui.design.icons.Ph
import com.ledga.app.ui.design.theme.LedgaTheme
import com.ledga.app.ui.design.tokens.Spacing
import com.ledga.app.ui.design.type.LedgaType

/** What You → Notifications does. Every default does nothing, for screenshots and tests. */
data class NotificationsActions(
    val onBack: () -> Unit = {},
    val onTurnOn: () -> Unit = {},
    val onDaily: (Boolean) -> Unit = {},
    val onTime: () -> Unit = {},
    val onWeekly: (Boolean) -> Unit = {},
    val onLarge: (Boolean) -> Unit = {},
    val onThreshold: () -> Unit = {},
    val onFuliza: (Boolean) -> Unit = {},
)

/** You → Notifications (spec §11, R75): the explanation, the permission banner, four switches, the time and the amount. */
@Composable
fun NotificationsContent(ui: NotificationsUi, actions: NotificationsActions, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    val s = ui.settings
    DetailFrame("Notifications", onBack = actions.onBack, modifier = modifier) {
        if (!ui.loaded) {
            Column(Modifier.padding(horizontal = Spacing.screen)) { repeat(4) { SkeletonRow() } }
            return@DetailFrame
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Text(NotificationText.EXPLANATION, style = LedgaType.body, color = c.muted)
            if (!ui.allowed) Banner(NotificationText.OFF_BANNER, BannerTone.Warning, icon = Ph.BellSlash, actionLabel = "Turn on", onAction = actions.onTurnOn)
            LedgaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                ListRow("Daily summary", subtitle = NotificationText.dailyDetail(s.dailySummaryMinute), trailing = RowTrailing.Toggle(s.notifyDaily, actions.onDaily))
                if (s.notifyDaily) {
                    RowDivider(Modifier.padding(horizontal = Spacing.m))
                    ListRow("Summary time", trailing = RowTrailing.Value(NotificationText.time(s.dailySummaryMinute)), onClick = actions.onTime)
                }
                RowDivider(Modifier.padding(horizontal = Spacing.m))
                ListRow("Weekly summary", subtitle = NotificationText.WEEKLY_DETAIL, trailing = RowTrailing.Toggle(s.notifyWeekly, actions.onWeekly))
                RowDivider(Modifier.padding(horizontal = Spacing.m))
                ListRow("Large payments", subtitle = NotificationText.largeDetail(s.largeThresholdCents), trailing = RowTrailing.Toggle(s.notifyLarge, actions.onLarge))
                if (s.notifyLarge) {
                    RowDivider(Modifier.padding(horizontal = Spacing.m))
                    ListRow("Large payment from", trailing = RowTrailing.Value(NotificationText.threshold(s.largeThresholdCents)), onClick = actions.onThreshold)
                }
                RowDivider(Modifier.padding(horizontal = Spacing.m))
                ListRow("Fuliza", subtitle = NotificationText.FULIZA_DETAIL, trailing = RowTrailing.Toggle(s.notifyFuliza, actions.onFuliza))
            }
        }
    }
}

/** The summary time (R75): M3's clock face, 12-hour, then Save. In a sheet, so it can be screenshot like any sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryTimeContent(minute: Int, onSave: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LedgaTheme.colors
    val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = false)
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        TimePicker(
            state = state,
            colors = TimePickerDefaults.colors(
                clockDialColor = c.plate,
                selectorColor = c.primary,
                containerColor = c.surfaceSheet,
                timeSelectorSelectedContainerColor = c.primarySoft,
                timeSelectorSelectedContentColor = c.onPrimarySoft,
                timeSelectorUnselectedContainerColor = c.plate,
                timeSelectorUnselectedContentColor = c.ink,
                periodSelectorSelectedContainerColor = c.primarySoft,
                periodSelectorSelectedContentColor = c.onPrimarySoft,
                periodSelectorUnselectedContentColor = c.ink,
                clockDialSelectedContentColor = c.onPrimary,
                clockDialUnselectedContentColor = c.ink,
            ),
        )
        PrimaryPill("Save", { onSave(state.hour * 60 + state.minute) }, Modifier.fillMaxWidth().padding(top = Spacing.m))
    }
}

/** The large-payment amount (R75): Ksh 100 to Ksh 1,000,000; Save waits until it is in range. */
@Composable
fun ThresholdContent(text: String, onText: (String) -> Unit, onSave: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            text,
            { t -> onText(t.filter { it.isDigit() || it == '.' || it == ',' }.take(14)) },
            Modifier.fillMaxWidth(),
            label = { Text(AmountFormat.CURRENCY) },
            singleLine = true,
            supportingText = { Text("From Ksh 100 to Ksh 1,000,000") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        PrimaryPill("Save", onSave, Modifier.fillMaxWidth().padding(top = Spacing.m), enabled = NotificationText.parseThreshold(text) != null)
    }
}

private enum class NotificationSheet { TIME, THRESHOLD }

/** You → Notifications (route): the permission (4a M3: ask first, Settings once Android won't ask) and the two sheets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit, vm: NotificationsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheet by rememberSaveable { mutableStateOf<NotificationSheet?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onPermissionResult(granted, context.showsRationale(Manifest.permission.POST_NOTIFICATIONS))
    }
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    NotificationsContent(
        ui,
        NotificationsActions(
            onBack = onBack,
            onTurnOn = {
                when {
                    ui.toSettings -> context.openNotificationSettings()
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onDaily = vm::setDaily,
            onTime = { sheet = NotificationSheet.TIME },
            onWeekly = vm::setWeekly,
            onLarge = vm::setLarge,
            onThreshold = { sheet = NotificationSheet.THRESHOLD },
            onFuliza = vm::setFuliza,
        ),
    )
    when (sheet) {
        NotificationSheet.TIME -> LedgaModalSheet(onDismiss = { sheet = null }, title = "Summary time") {
            SummaryTimeContent(ui.settings.dailySummaryMinute, onSave = {
                vm.setDailyMinute(it)
                sheet = null
            })
        }
        NotificationSheet.THRESHOLD -> {
            var text by rememberSaveable { mutableStateOf(AmountFormat.plain(ui.settings.largeThresholdCents)) }
            LedgaModalSheet(onDismiss = { sheet = null }, title = "Large payment from") {
                ThresholdContent(text, onText = { text = it }, onSave = {
                    NotificationText.parseThreshold(text)?.let(vm::setThreshold)
                    sheet = null
                })
            }
        }
        null -> Unit
    }
}
