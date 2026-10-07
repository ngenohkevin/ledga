package com.ledga.app.ui.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.settings.Settings
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.ui.onboarding.NotificationAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotificationsUi(
    val loaded: Boolean = false,
    val settings: Settings = Settings(),
    /** R109: Android lets Ledga post (any version). */
    val allowed: Boolean = true,
    /** R109: "Turn on" opens Android's notification settings (a refusal for good, Android 8–12, or switched off there). */
    val toSettings: Boolean = false,
)

/** You → Notifications (spec §11, R75). Phase 5 reads what this saves. */
@HiltViewModel
class NotificationsViewModel @Inject constructor(private val settings: SettingsStore, private val access: NotificationAccess) : ViewModel() {
    private val ask = MutableStateFlow(access.shouldAsk())
    private val enabled = MutableStateFlow(access.enabled())
    private val blocked = MutableStateFlow(false)

    val ui: StateFlow<NotificationsUi> = combine(settings.settings, ask, enabled, blocked) { s, a, on, b ->
        NotificationsUi(true, s, allowed = on, toSettings = !on && (b || !a))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUi())

    /** On resume: notifications may have been switched in Android's settings. */
    fun refresh() {
        ask.value = access.shouldAsk()
        enabled.value = access.enabled()
        if (enabled.value) blocked.value = false
    }

    fun onPermissionResult(granted: Boolean, showRationale: Boolean) {
        refresh()
        if (!granted) blocked.value = !showRationale
    }

    fun setDaily(on: Boolean) = save { settings.setNotifyDaily(on) }

    fun setDailyMinute(minute: Int) = save { settings.setDailySummaryMinute(minute) }

    fun setWeekly(on: Boolean) = save { settings.setNotifyWeekly(on) }

    fun setLarge(on: Boolean) = save { settings.setNotifyLarge(on) }

    fun setThreshold(cents: Long) = save { settings.setLargeThreshold(cents) }

    fun setFuliza(on: Boolean) = save { settings.setNotifyFuliza(on) }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
