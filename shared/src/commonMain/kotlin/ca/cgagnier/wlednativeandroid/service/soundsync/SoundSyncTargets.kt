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
 * Returns an empty list when [selectedDeviceMacs] is empty, and `null` when [isPaused] is `true`
 * or when no visible device is online (including while connections are resuming after returning
 * to the foreground), so callers keep their last active target list instead of clearing it during
 * reconnects.
 */
fun resolveSoundSyncUnicastAddresses(
    devices: List<DeviceWithState>,
    isPaused: Boolean,
    selectedDeviceMacs: Set<String>,
    showHiddenDevices: Boolean,
): List<String>? {
    if (selectedDeviceMacs.isEmpty()) return emptyList()
    if (isPaused) return null
    val visibleDevices = devices.filter { showHiddenDevices || !it.device.isHidden }
    if (visibleDevices.none { it.isOnline }) return null
    return visibleDevices
        .asSequence()
        .filter { it.isOnline && it.device.macAddress in selectedDeviceMacs }
        .map { it.device.address }
        .distinct()
        .toList()
}

/**
 * Emits distinct lists of online device addresses matching [selectedDeviceMacs] for unicast Sound Sync.
 * Previous targets are preserved while [DeviceConnectionManager.isPaused] is true or
 * while no visible device is online, unless [selectedDeviceMacs] is empty.
 */
fun DeviceConnectionManager.soundSyncUnicastAddresses(
    selectedDeviceMacs: Flow<Set<String>>,
    showHiddenDevices: Flow<Boolean>,
): Flow<List<String>> = combine(
    allDevicesWithState,
    isPaused,
    selectedDeviceMacs,
    showHiddenDevices,
) { devices, paused, selectedMacs, showHidden ->
    resolveSoundSyncUnicastAddresses(
        devices = devices,
        isPaused = paused,
        selectedDeviceMacs = selectedMacs,
        showHiddenDevices = showHidden,
    )
}.filterNotNull().distinctUntilChanged()

/**
 * Returns the current list of online device addresses matching [selectedDeviceMacs] for unicast
 * Sound Sync, or an empty list if none qualify or targets are currently paused or offline.
 */
fun DeviceConnectionManager.currentSoundSyncUnicastAddresses(
    selectedDeviceMacs: Set<String>,
    showHiddenDevices: Boolean,
): List<String> = resolveSoundSyncUnicastAddresses(
    devices = allDevicesWithState.value,
    isPaused = isPaused.value,
    selectedDeviceMacs = selectedDeviceMacs,
    showHiddenDevices = showHiddenDevices,
) ?: emptyList()
