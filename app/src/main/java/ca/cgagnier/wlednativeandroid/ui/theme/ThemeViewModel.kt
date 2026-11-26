package ca.cgagnier.wlednativeandroid.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.domain.CalendarDate
import ca.cgagnier.wlednativeandroid.domain.HolidayTheme
import ca.cgagnier.wlednativeandroid.domain.HolidayThemeResolver
import ca.cgagnier.wlednativeandroid.repository.ThemeSettings
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.shared.currentLocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ThemeUiState(val theme: ThemeSettings, val holidayTheme: HolidayTheme?)

class ThemeViewModel(
    preferencesRepository: UserPreferencesRepository,
    private val currentDateProvider: () -> CalendarDate = ::currentLocalDate,
) : ViewModel() {

    private val currentDate = MutableStateFlow(currentDateProvider())

    /**
     * Current theme and holiday preferences, or `null` while initial values are still loading
     * from DataStore.
     *
     * `MainActivity` uses `null` in `SplashScreen.setKeepOnScreenCondition` (and `WLEDNativeTheme`
     * defers rendering while `null`) to avoid a light/dark or holiday flash on startup. Must remain
     * [SharingStarted.Eagerly] without `runBlocking`, as blocking the main thread on DataStore can
     * deadlock if another `Dispatchers.Main` coroutine holds DataStore's initialization lock.
     */
    val themeState: StateFlow<ThemeUiState?> = combine(
        preferencesRepository.themeMode,
        preferencesRepository.holidayThemesEnabled,
        preferencesRepository.holidayThemeOverride,
        currentDate,
    ) { themeMode, enabled, override, date ->
        ThemeUiState(
            theme = themeMode,
            holidayTheme = HolidayThemeResolver.resolve(enabled = enabled, override = override, date = date),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    fun refreshDate() {
        currentDate.value = currentDateProvider()
    }
}
