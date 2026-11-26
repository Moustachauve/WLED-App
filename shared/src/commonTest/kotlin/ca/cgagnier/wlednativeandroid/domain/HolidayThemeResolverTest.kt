package ca.cgagnier.wlednativeandroid.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class HolidayThemeResolverTest {

    @Test
    fun resolveForDate_christmasRange_returnsChristmas() {
        listOf(
            CalendarDate(month = 12, dayOfMonth = 1),
            CalendarDate(month = 12, dayOfMonth = 15),
            CalendarDate(month = 12, dayOfMonth = 25),
            CalendarDate(month = 12, dayOfMonth = 28),
        ).forEach { date ->
            assertEquals(HolidayTheme.Christmas, HolidayThemeResolver.resolveForDate(date), "date=$date")
        }
    }

    @Test
    fun resolveForDate_newYearRange_returnsNewYear() {
        listOf(
            CalendarDate(month = 12, dayOfMonth = 29),
            CalendarDate(month = 12, dayOfMonth = 31),
            CalendarDate(month = 1, dayOfMonth = 1),
            CalendarDate(month = 1, dayOfMonth = 3),
        ).forEach { date ->
            assertEquals(HolidayTheme.NewYear, HolidayThemeResolver.resolveForDate(date), "date=$date")
        }
    }

    @Test
    fun resolveForDate_outsideHolidayWindows_returnsNull() {
        listOf(
            CalendarDate(month = 1, dayOfMonth = 4),
            CalendarDate(month = 2, dayOfMonth = 29),
            CalendarDate(month = 7, dayOfMonth = 4),
            CalendarDate(month = 10, dayOfMonth = 31),
            CalendarDate(month = 11, dayOfMonth = 30),
        ).forEach { date ->
            assertNull(HolidayThemeResolver.resolveForDate(date), "date=$date")
        }
    }

    @Test
    fun resolve_whenDisabled_returnsNullEvenWithOverrideAndActiveDate() {
        val result = HolidayThemeResolver.resolve(
            enabled = false,
            override = HolidayTheme.Christmas,
            date = CalendarDate(month = 12, dayOfMonth = 25),
        )
        assertNull(result)
    }

    @Test
    fun resolve_whenEnabledWithOverride_returnsOverrideOutsideHolidayDate() {
        assertEquals(
            HolidayTheme.Christmas,
            HolidayThemeResolver.resolve(
                enabled = true,
                override = HolidayTheme.Christmas,
                date = CalendarDate(month = 7, dayOfMonth = 15),
            ),
        )
        assertEquals(
            HolidayTheme.NewYear,
            HolidayThemeResolver.resolve(
                enabled = true,
                override = HolidayTheme.NewYear,
                date = CalendarDate(month = 12, dayOfMonth = 25),
            ),
        )
    }

    @Test
    fun resolve_whenEnabledWithNullOverride_delegatesToCalendarDate() {
        listOf(
            CalendarDate(month = 12, dayOfMonth = 25) to HolidayTheme.Christmas,
            CalendarDate(month = 1, dayOfMonth = 1) to HolidayTheme.NewYear,
            CalendarDate(month = 6, dayOfMonth = 1) to null,
        ).forEach { (date, expected) ->
            assertEquals(
                expected,
                HolidayThemeResolver.resolve(enabled = true, override = null, date = date),
                "date=$date",
            )
        }
    }

    @Test
    fun calendarDate_invalidMonthOrDay_throwsIllegalArgumentException() {
        listOf(
            0 to 1,
            13 to 1,
            1 to 0,
            1 to 32,
        ).forEach { (month, day) ->
            assertFailsWith<IllegalArgumentException>("month=$month, day=$day") {
                CalendarDate(month = month, dayOfMonth = day)
            }
        }
    }
}
