package ca.cgagnier.wlednativeandroid.shared

import ca.cgagnier.wlednativeandroid.domain.CalendarDate
import okio.FileSystem
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSDate
import platform.Foundation.NSRecursiveLock
import platform.Foundation.timeIntervalSince1970

private const val MILLIS_PER_SECOND = 1000

actual fun getPlatformName(): String = "iOS"

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()

actual fun currentLocalDate(): CalendarDate {
    val calendar = NSCalendar.calendarWithIdentifier(NSCalendarIdentifierGregorian)
        ?: NSCalendar.currentCalendar
    val components = calendar.components(
        NSCalendarUnitMonth or NSCalendarUnitDay,
        fromDate = NSDate(),
    )
    return CalendarDate(
        month = components.month.toInt(),
        dayOfMonth = components.day.toInt(),
    )
}

internal actual class SynchronizedObject {
    val nsLock = NSRecursiveLock()
}

internal actual inline fun <R> synchronized(lock: SynchronizedObject, block: () -> R): R {
    lock.nsLock.lock()
    try {
        return block()
    } finally {
        lock.nsLock.unlock()
    }
}

actual val fileSystem: FileSystem = FileSystem.SYSTEM
