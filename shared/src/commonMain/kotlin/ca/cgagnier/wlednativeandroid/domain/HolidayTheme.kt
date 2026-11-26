package ca.cgagnier.wlednativeandroid.domain

import kotlinx.serialization.Serializable

@Serializable
enum class HolidayTheme {
    WinterHolidays,
    NewYear,
}

data class CalendarDate(val month: Int, val dayOfMonth: Int) {
    init {
        require(month in MIN_MONTH..MAX_MONTH) { "Month must be in $MIN_MONTH..$MAX_MONTH, got $month" }
        require(dayOfMonth in MIN_DAY..MAX_DAY) { "Day of month must be in $MIN_DAY..$MAX_DAY, got $dayOfMonth" }
    }

    internal val monthDayKey: Int
        get() = month * MONTH_MULTIPLIER + dayOfMonth

    companion object {
        private const val MIN_MONTH = 1
        private const val MAX_MONTH = 12
        private const val MIN_DAY = 1
        private const val MAX_DAY = 31
        private const val MONTH_MULTIPLIER = 100
    }
}

internal data class HolidayDateRange(val start: CalendarDate, val end: CalendarDate) {
    operator fun contains(date: CalendarDate): Boolean {
        val dateKey = date.monthDayKey
        val startKey = start.monthDayKey
        val endKey = end.monthDayKey
        return if (startKey <= endKey) {
            dateKey in startKey..endKey
        } else {
            dateKey >= startKey || dateKey <= endKey
        }
    }
}
