package ca.cgagnier.wlednativeandroid.shared

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformTest {
    @Test
    fun testPlatformNameIsNotEmpty() {
        assertTrue(getPlatformName().isNotEmpty())
    }

    @Test
    fun testCurrentTimeMillisIsPositive() {
        assertTrue(currentTimeMillis() > 0)
    }

    @Test
    fun testCurrentLocalDateIsValid() {
        val date = currentLocalDate()
        assertTrue(date.month in 1..12)
        assertTrue(date.dayOfMonth in 1..31)
    }
}
