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

    private val listingPrefs = combine(
        preferencesRepository.autoDiscovery,
        preferencesRepository.showOfflineDevicesLast,
        preferencesRepository.showHiddenDevices,
    ) { autoDiscovery, showOfflineDevicesLast, showHiddenDevices ->
        ListingPrefs(
            isAutoDiscoveryEnabled = autoDiscovery,
            showOfflineLast = showOfflineDevicesLast,
            showHiddenDevices = showHiddenDevices,
        )
    }

    private val holidayPrefs = combine(
        preferencesRepository.holidayThemesEnabled,
        preferencesRepository.holidayThemeOverride,
    ) { enabled, override ->
        HolidayPrefs(
            isEnabled = enabled,
            override = override,
        )
    }

    val settingsState = combine(
        listingPrefs,
        preferencesRepository.themeMode,
        holidayPrefs,
        preferencesRepository.soundSyncBetaEnabled,
    ) { listing, theme, holiday, soundSyncBetaEnabled ->
        SettingsState(
            isAutoDiscoveryEnabled = listing.isAutoDiscoveryEnabled,
            showOfflineLast = listing.showOfflineLast,
            showHiddenDevices = listing.showHiddenDevices,
            theme = theme,
            isHolidayThemesEnabled = holiday.isEnabled,
            holidayThemeOverride = holiday.override,
            isSoundSyncBetaEnabled = soundSyncBetaEnabled,
        )
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

    private data class ListingPrefs(
        val isAutoDiscoveryEnabled: Boolean,
        val showOfflineLast: Boolean,
        val showHiddenDevices: Boolean,
    )

    private data class HolidayPrefs(val isEnabled: Boolean, val override: HolidayTheme?)
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
