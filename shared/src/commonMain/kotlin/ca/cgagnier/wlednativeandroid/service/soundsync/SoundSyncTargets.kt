package ca.cgagnier.wlednativeandroid.service.soundsync

import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/**
 * Resolves the list of distinct IP/hostname targets for unicast Sound Sync from [devices].
 *
 * Returns `null` when [isPaused] is `true` or when no visible device is online (including
 * while connections are resuming after returning to the foreground), so callers keep their
 * last active target list instead of clearing it during reconnects.
 */
fun resolveSoundSyncUnicastAddresses(devices: List<DeviceWithState>, isPaused: Boolean): List<String>? {
    if (isPaused) return null
    val onlineAddresses = devices
        .asSequence()
        .filter { !it.device.isHidden && it.isOnline }
        .map { it.device.address }
        .distinct()
        .toList()
    return onlineAddresses.ifEmpty { null }
}

/**
 * Emits distinct lists of online, non-hidden device addresses for unicast Sound Sync.
 * Previous targets are preserved while [DeviceConnectionManager.isPaused] is true or
 * while no visible device is online.
 */
fun DeviceConnectionManager.soundSyncUnicastAddresses(): Flow<List<String>> = combine(
    allDevicesWithState,
    isPaused,
) { devices, paused ->
    resolveSoundSyncUnicastAddresses(devices, paused)
}.filterNotNull().distinctUntilChanged()

/**
 * Returns the current list of online, non-hidden device addresses for unicast Sound Sync,
 * or an empty list if targets are currently paused or offline.
 */
fun DeviceConnectionManager.currentSoundSyncUnicastAddresses(): List<String> =
    resolveSoundSyncUnicastAddresses(allDevicesWithState.value, isPaused.value) ?: emptyList()
