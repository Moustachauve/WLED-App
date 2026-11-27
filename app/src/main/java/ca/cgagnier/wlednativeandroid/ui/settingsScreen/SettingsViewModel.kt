package ca.cgagnier.wlednativeandroid.ui.settingsScreen

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.domain.HolidayTheme
import ca.cgagnier.wlednativeandroid.repository.ThemeSettings
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.soundsync.SoundSyncManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STATE_FLOW_TIMEOUT_MS = 5000L

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val soundSyncManager: SoundSyncManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    val settingsState = combine(
        preferencesRepository.autoDiscovery,
        preferencesRepository.showOfflineDevicesLast,
        preferencesRepository.showHiddenDevices,
        preferencesRepository.themeMode,
        preferencesRepository.soundSyncBetaEnabled,
    ) { autoDiscovery, showOfflineDevicesLast, showHiddenDevices, theme, soundSyncBetaEnabled ->
        SettingsState(
            isAutoDiscoveryEnabled = autoDiscovery,
            showOfflineLast = showOfflineDevicesLast,
            showHiddenDevices = showHiddenDevices,
            theme = theme,
            isSoundSyncBetaEnabled = soundSyncBetaEnabled,
        )
    }.combine(preferencesRepository.holidayThemesEnabled) { state, enabled ->
        state.copy(isHolidayThemesEnabled = enabled)
    }.combine(preferencesRepository.holidayThemeOverride) { state, override ->
        state.copy(holidayThemeOverride = override)
    }.stateIn(viewModelScope, WhileSubscribed(STATE_FLOW_TIMEOUT_MS), SettingsState())

    fun setAutoDiscover(enabled: Boolean) = viewModelScope.launch(ioDispatcher) {
        preferencesRepository.updateAutoDiscovery(enabled)
    }
    fun setShowOfflineDevicesLast(enabled: Boolean) = viewModelScope.launch(ioDispatcher) {
        preferencesRepository.updateShowOfflineDeviceLast(enabled)
    }
    fun setShowHiddenDevices(enabled: Boolean) = viewModelScope.launch(ioDispatcher) {
        preferencesRepository.updateShowHiddenDevices(enabled)
    }
    fun setTheme(theme: ThemeSettings) = viewModelScope.launch(ioDispatcher) {
        preferencesRepository.updateThemeMode(theme)
    }
    fun setHolidayThemesEnabled(enabled: Boolean) = viewModelScope.launch(ioDispatcher) {
        preferencesRepository.updateHolidayThemesEnabled(enabled)
    }
    fun setHolidayThemeOverride(override: HolidayTheme?) = viewModelScope.launch(ioDispatcher) {
        preferencesRepository.updateHolidayThemeOverride(override)
    }
    fun setSoundSyncBetaEnabled(enabled: Boolean) {
        if (!enabled) {
            soundSyncManager.stop()
        }
        viewModelScope.launch(ioDispatcher) {
            preferencesRepository.updateSoundSyncBetaEnabled(enabled)
        }
    }
}

@Stable
data class SettingsState(
    val isAutoDiscoveryEnabled: Boolean = true,
    val showOfflineLast: Boolean = true,
    val showHiddenDevices: Boolean = false,
    val theme: ThemeSettings = ThemeSettings.Auto,
    val isHolidayThemesEnabled: Boolean = true,
    val holidayThemeOverride: HolidayTheme? = null,
    val isSoundSyncBetaEnabled: Boolean = false,
)
