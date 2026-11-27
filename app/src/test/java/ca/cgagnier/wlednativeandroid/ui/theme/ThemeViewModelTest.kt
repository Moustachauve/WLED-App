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

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { preferencesRepository.themeMode } returns flowOf(ThemeSettings.Auto)
        every { preferencesRepository.holidayThemesEnabled } returns flowOf(true)
        every { preferencesRepository.holidayThemeOverride } returns flowOf(null)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(date: () -> CalendarDate = { CalendarDate(month = 6, dayOfMonth = 15) }) =
        ThemeViewModel(preferencesRepository = preferencesRepository, currentDateProvider = date)

    @Test
    fun `themeState is initially null before preferences emit`() = runTest(testDispatcher) {
        every { preferencesRepository.themeMode } returns MutableSharedFlow()

        val viewModel = createViewModel()

        assertNull(viewModel.themeState.value)
    }

    @Test
    fun `themeState reflects stored theme preference once loaded`() = runTest(testDispatcher) {
        every { preferencesRepository.themeMode } returns MutableStateFlow(ThemeSettings.Dark)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ThemeSettings.Dark, viewModel.themeState.value?.theme)
    }

    @Test
    fun `themeState resolves holiday automatically for active calendar date`() = runTest(testDispatcher) {
        val viewModel = createViewModel { CalendarDate(month = 12, dayOfMonth = 25) }
        advanceUntilIdle()

        assertEquals(HolidayTheme.WinterHolidays, viewModel.themeState.value?.holidayTheme)
    }

    @Test
    fun `refreshDate updates holidayTheme when calendar date changes`() = runTest(testDispatcher) {
        var fakeDate = CalendarDate(month = 12, dayOfMonth = 28)
        val viewModel = createViewModel { fakeDate }
        advanceUntilIdle()
        assertEquals(HolidayTheme.WinterHolidays, viewModel.themeState.value?.holidayTheme)

        fakeDate = CalendarDate(month = 12, dayOfMonth = 29)
        viewModel.refreshDate()
        advanceUntilIdle()

        assertEquals(HolidayTheme.NewYear, viewModel.themeState.value?.holidayTheme)
    }
}
