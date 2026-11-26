package ca.cgagnier.wlednativeandroid.domain

import ca.cgagnier.wlednativeandroid.shared.currentLocalDate

object HolidayThemeResolver {
    val holidays: List<HolidayDefinition> = listOf(
        HolidayDefinition(
            theme = HolidayTheme.WinterHolidays,
            dateRange = HolidayDateRange(
                start = CalendarDate(month = 12, dayOfMonth = 1),
                end = CalendarDate(month = 12, dayOfMonth = 28),
            ),
        ),
        HolidayDefinition(
            theme = HolidayTheme.NewYear,
            dateRange = HolidayDateRange(
                start = CalendarDate(month = 12, dayOfMonth = 29),
                end = CalendarDate(month = 1, dayOfMonth = 3),
            ),
        ),
    )

    fun resolveForDate(date: CalendarDate = currentLocalDate()): HolidayTheme? =
        holidays.firstOrNull { date in it.dateRange }?.theme

    fun resolve(enabled: Boolean, override: HolidayTheme?, date: CalendarDate = currentLocalDate()): HolidayTheme? {
        if (!enabled) return null
        return override ?: resolveForDate(date)
    }
}

data class HolidayDefinition(val theme: HolidayTheme, val dateRange: HolidayDateRange)
