package com.ledga.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.repository.ImportResult
import com.ledga.app.data.repository.SettingsRepository
import com.ledga.app.data.repository.SmsImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportProgress(
    val current: Int = 0,
    val total: Int = 0,
    val isRunning: Boolean = false,
    val result: ImportResult? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val smsImporter: SmsImporter
) : ViewModel() {

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep

    private val _importProgress = MutableStateFlow(ImportProgress())
    val importProgress: StateFlow<ImportProgress> = _importProgress

    fun nextStep() {
        _currentStep.value = (_currentStep.value + 1).coerceAtMost(3)
    }

    fun startImport() {
        viewModelScope.launch {
            _importProgress.value = ImportProgress(isRunning = true)
            val result = smsImporter.importHistory { current, total ->
                _importProgress.value = ImportProgress(
                    current = current,
                    total = total,
                    isRunning = true
                )
            }
            _importProgress.value = ImportProgress(
                current = result.total,
                total = result.total,
                isRunning = false,
                result = result
            )
            nextStep()
        }
    }

    fun skipImport() {
        nextStep()
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted()
        }
    }
}
