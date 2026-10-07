package com.ledga.app.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.settings.Settings
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.startup.Startup
import com.ledga.app.startup.StartupState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    settingsStore: SettingsStore,
    private val startup: Startup,
    private val opens: NotificationOpens,
) : ViewModel() {

    /** Null until DataStore has answered. */
    val settings: StateFlow<Settings?> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val opened = MutableStateFlow<StartupState>(StartupState.Opening)
    val startupState: StateFlow<StartupState> = opened.asStateFlow()

    /** The splash screen's condition: the appearance is known and the database is open (or has failed). */
    val ready: StateFlow<Boolean> = combine(settings, opened) { s, st -> s != null && st != StartupState.Opening }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** R100: the screen a tapped notification asked for, until `LedgaNavHost` has gone there. */
    val notificationOpens: StateFlow<OpenDestination?> = opens.destination

    fun opened(destination: OpenDestination) = opens.taken(destination)

    init {
        open()
    }

    fun retry() {
        opened.value = StartupState.Opening
        open()
    }

    private fun open() {
        viewModelScope.launch(Dispatchers.IO) { opened.value = startup.run() }
    }
}
