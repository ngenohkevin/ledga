package com.ledga.app.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledga.app.data.repository.DownloadState
import com.ledga.app.data.repository.SettingsRepository
import com.ledga.app.data.repository.UpdateRepository
import com.ledga.app.worker.GitHubRelease
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class UpdateUiState(
    val release: GitHubRelease? = null,
    val downloadState: DownloadState = DownloadState.Idle,
    val isChecking: Boolean = false,
    val checkedAt: Long? = null,
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _release = MutableStateFlow<GitHubRelease?>(null)
    private val _checking = MutableStateFlow(false)
    private val _checkedAt = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<UpdateUiState> = combine(
        _release,
        updateRepository.downloadState,
        _checking,
        _checkedAt,
    ) { release, dl, checking, at ->
        UpdateUiState(
            release = release,
            downloadState = dl,
            isChecking = checking,
            checkedAt = at,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UpdateUiState())

    init {
        // First-time entry hits the cached check so we paint immediately.
        viewModelScope.launch {
            val release = updateRepository.checkNow()
            _release.value = release
            // If the background worker already pre-fetched the APK, skip the
            // download dance — the user lands on "Install now" directly.
            if (release != null) {
                updateRepository.findCachedApk(release)?.let { file ->
                    updateRepository.markReady(file)
                }
            }
        }
    }

    fun checkAgain() {
        if (_checking.value) return
        _checking.value = true
        viewModelScope.launch {
            try {
                val release = updateRepository.checkNow()
                _release.value = release
                if (release != null) {
                    updateRepository.findCachedApk(release)?.let { file ->
                        updateRepository.markReady(file)
                    }
                }
                _checkedAt.value = System.currentTimeMillis()
            } finally {
                _checking.value = false
            }
        }
    }

    fun startDownload(release: GitHubRelease) {
        viewModelScope.launch { updateRepository.download(release) }
    }

    fun install(file: File) {
        updateRepository.install(file)
    }

    fun remindLater(version: String) {
        viewModelScope.launch { settingsRepository.dismissUpdateVersion(version) }
    }
}
