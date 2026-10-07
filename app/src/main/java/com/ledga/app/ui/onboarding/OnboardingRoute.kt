package com.ledga.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledga.app.ui.app.SmsPermissions

/** Onboarding with its ViewModel and the two system permission dialogs. [onDone] leaves for Home. */
@Composable
fun OnboardingRoute(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onDone() }
    val sms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.onSmsResult(result[Manifest.permission.READ_SMS] == true)
    }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.done() }
    val phone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onPhoneResult() }
    OnboardingScreen(
        state = state,
        onName = vm::onName,
        onNext = vm::next,
        onAllowSms = { sms.launch(SmsPermissions.ALL) },
        onSkip = vm::done,
        onImport = vm::startImport,
        onLineName = vm::onLineName,
        onAllowNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notify.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.done()
        },
        restore = RestoreOfferActions(
            onRestore = vm::restore,
            onStartFresh = vm::startFresh,
            onAnswer = vm::answer,
            onAllowPhone = { phone.launch(Manifest.permission.READ_PHONE_STATE) },
        ),
    )
}
