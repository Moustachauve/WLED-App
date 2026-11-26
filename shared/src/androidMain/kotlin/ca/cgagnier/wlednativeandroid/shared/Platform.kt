package ca.cgagnier.wlednativeandroid.shared

import ca.cgagnier.wlednativeandroid.domain.CalendarDate
import okio.FileSystem
import java.util.Calendar
import java.util.GregorianCalendar

actual fun getPlatformName(): String = "Android"

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun currentLocalDate(): CalendarDate {
    val calendar = GregorianCalendar()
    return CalendarDate(
        month = calendar.get(Calendar.MONTH) + 1,
        dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH),
    )
}

internal actual typealias SynchronizedObject = Any

internal actual inline fun <R> synchronized(lock: SynchronizedObject, block: () -> R): R =
    kotlin.synchronized(lock, block)

actual val fileSystem: FileSystem = FileSystem.SYSTEM
