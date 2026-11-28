package ca.cgagnier.wlednativeandroid.service.soundsync

import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transform

/**
 * Resolves the list of distinct IP/hostname targets for unicast Sound Sync from [devices].
 *
 * Returns `null` when [isPaused] is `true` or when all visible devices are currently
 * transitioning through [WebsocketStatus.CONNECTING] with none online yet, so callers keep
 * their active target list while the app is backgrounded or resuming connections.
 */
fun resolveSoundSyncUnicastAddresses(devices: List<DeviceWithState>, isPaused: Boolean): List<String>? {
    if (isPaused) return null
    val visibleDevices = devices.filter { !it.device.isHidden }
    val onlineAddresses = visibleDevices
        .filter { it.isOnline }
        .map { it.device.address }
        .distinct()
    val isReconnectingAll = onlineAddresses.isEmpty() &&
        visibleDevices.any { it.websocketStatus == WebsocketStatus.CONNECTING }
    return if (isReconnectingAll) null else onlineAddresses
}

/**
 * Emits distinct lists of online, non-hidden device addresses for unicast Sound Sync.
 * While [DeviceConnectionManager.isPaused] is true (e.g. backgrounded), previous targets are
 * preserved rather than cleared.
 */
fun DeviceConnectionManager.soundSyncUnicastAddresses(): Flow<List<String>> = combine(
    allDevicesWithState,
    isPaused,
) { devices, paused ->
    resolveSoundSyncUnicastAddresses(devices, paused)
}.transform { addresses ->
    if (addresses != null) {
        emit(addresses)
    }
}.distinctUntilChanged()

/**
 * Returns the current list of online, non-hidden device addresses for unicast Sound Sync,
 * or an empty list if targets are currently paused or reconnecting.
 */
fun DeviceConnectionManager.currentSoundSyncUnicastAddresses(): List<String> =
    resolveSoundSyncUnicastAddresses(allDevicesWithState.value, isPaused.value) ?: emptyList()
