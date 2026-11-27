package ca.cgagnier.wlednativeandroid.model

/**
 * Minimal description of a WLED device sent to a companion watch app.
 *
 * Device configuration (names, discovery, firmware updates, ...) stays on the phone: the watch only
 * receives what it needs to reach a device over HTTP and control it.
 */
data class WatchDeviceSnapshot(
    /** MAC address, stable across IP changes. */
    val macAddress: String,
    val name: String,
    val address: String,
)

/**
 * Projects the known devices into the list shown on the watch.
 *
 * Hidden devices, devices still in AP mode and devices without an address are left out, since the
 * watch can't reach them. Names follow [resolveDeviceName] and the list is sorted case-insensitively.
 */
fun List<Device>.toWatchDeviceSnapshots(defaultName: String): List<WatchDeviceSnapshot> =
    filter { !it.isHidden && it.macAddress != AP_MODE_MAC_ADDRESS && it.address.isNotBlank() }
        .map { device ->
            WatchDeviceSnapshot(
                macAddress = device.macAddress,
                name = resolveDeviceName(device, defaultName),
                address = device.address,
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
