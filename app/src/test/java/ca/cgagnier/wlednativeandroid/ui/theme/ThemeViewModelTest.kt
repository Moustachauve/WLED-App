package ca.cgagnier.wlednativeandroid.ui.theme

import ca.cgagnier.wlednativeandroid.domain.CalendarDate
import ca.cgagnier.wlednativeandroid.domain.HolidayTheme
import ca.cgagnier.wlednativeandroid.repository.ThemeSettings
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val preferencesRepository: UserPreferencesRepository = mockk()
    private val holidayEnabledFlow = MutableStateFlow(true)
    private val holidayOverrideFlow = MutableStateFlow<HolidayTheme?>(null)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { preferencesRepository.themeMode } returns flowOf(ThemeSettings.Auto)
        every { preferencesRepository.holidayThemesEnabled } returns holidayEnabledFlow
        every { preferencesRepository.holidayThemeOverride } returns holidayOverrideFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `themeState is initially null before preferences emit`() = runTest(testDispatcher) {
        every { preferencesRepository.themeMode } returns MutableSharedFlow()

        val viewModel = ThemeViewModel(preferencesRepository)

        assertNull(viewModel.themeState.value)
    }

    @Test
    fun `themeState reflects stored theme preference once loaded`() = runTest(testDispatcher) {
        val themeFlow = MutableStateFlow(ThemeSettings.Dark)
        every { preferencesRepository.themeMode } returns themeFlow

        val viewModel = ThemeViewModel(
            preferencesRepository = preferencesRepository,
            currentDateProvider = { CalendarDate(month = 6, dayOfMonth = 15) },
        )
        advanceUntilIdle()

        assertEquals(ThemeSettings.Dark, viewModel.themeState.value?.theme)
        assertNull(viewModel.themeState.value?.holidayTheme)
    }

    @Test
    fun `themeState resolves holiday automatically for active calendar date`() = runTest(testDispatcher) {
        val viewModel = ThemeViewModel(
            preferencesRepository = preferencesRepository,
            currentDateProvider = { CalendarDate(month = 12, dayOfMonth = 25) },
        )
        advanceUntilIdle()

        assertEquals(HolidayTheme.WinterHolidays, viewModel.themeState.value?.holidayTheme)
    }

    @Test
    fun `themeState has null holiday when holiday themes are disabled`() = runTest(testDispatcher) {
        holidayEnabledFlow.value = false
        holidayOverrideFlow.value = HolidayTheme.WinterHolidays

        val viewModel = ThemeViewModel(
            preferencesRepository = preferencesRepository,
            currentDateProvider = { CalendarDate(month = 12, dayOfMonth = 25) },
        )
        advanceUntilIdle()

        assertEquals(ThemeSettings.Auto, viewModel.themeState.value?.theme)
        assertNull(viewModel.themeState.value?.holidayTheme)
    }

    @Test
    fun `themeState uses override outside active holiday window`() = runTest(testDispatcher) {
        holidayOverrideFlow.value = HolidayTheme.WinterHolidays

        val viewModel = ThemeViewModel(
            preferencesRepository = preferencesRepository,
            currentDateProvider = { CalendarDate(month = 7, dayOfMonth = 15) },
        )
        advanceUntilIdle()

        assertEquals(HolidayTheme.WinterHolidays, viewModel.themeState.value?.holidayTheme)
    }

    @Test
    fun `refreshDate updates holidayTheme when calendar date changes`() = runTest(testDispatcher) {
        var fakeDate = CalendarDate(month = 12, dayOfMonth = 28)
        val viewModel = ThemeViewModel(
            preferencesRepository = preferencesRepository,
            currentDateProvider = { fakeDate },
        )
        advanceUntilIdle()
        assertEquals(HolidayTheme.WinterHolidays, viewModel.themeState.value?.holidayTheme)

        fakeDate = CalendarDate(month = 12, dayOfMonth = 29)
        viewModel.refreshDate()
        advanceUntilIdle()

        assertEquals(HolidayTheme.NewYear, viewModel.themeState.value?.holidayTheme)
    }
}
