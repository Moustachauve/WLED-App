package ca.cgagnier.wlednativeandroid.shared

import ca.cgagnier.wlednativeandroid.domain.CalendarDate
import okio.FileSystem

expect fun getPlatformName(): String

expect fun currentTimeMillis(): Long

expect fun currentLocalDate(): CalendarDate

internal expect class SynchronizedObject()

internal expect inline fun <R> synchronized(lock: SynchronizedObject, block: () -> R): R

expect val fileSystem: FileSystem
