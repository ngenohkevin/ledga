package com.ledga.app.ui.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.settings.SettingsStore
import com.ledga.app.data.settings.TextSize
import com.ledga.app.ui.design.theme.Appearance
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppearanceUi(val loaded: Boolean = false, val appearance: Appearance = Appearance.SYSTEM, val textSize: TextSize = TextSize.SYSTEM)

/** You → Appearance (R76): `LedgaRoot` already applies both, so a choice shows at once. */
@HiltViewModel
class AppearanceViewModel @Inject constructor(private val settings: SettingsStore) : ViewModel() {
    val ui: StateFlow<AppearanceUi> = settings.settings.map { AppearanceUi(true, it.appearance, it.textSize) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceUi())

    fun setAppearance(a: Appearance) {
        viewModelScope.launch { settings.setAppearance(a) }
    }

    fun setTextSize(t: TextSize) {
        viewModelScope.launch { settings.setTextSize(t) }
    }
}
