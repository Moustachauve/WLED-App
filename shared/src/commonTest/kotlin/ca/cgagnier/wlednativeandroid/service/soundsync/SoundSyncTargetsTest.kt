package ca.cgagnier.wlednativeandroid.service.soundsync

import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SoundSyncTargetsTest {

    @Test
    fun resolveSoundSyncUnicastAddresses_includesOnlineVisibleDevicesAndDeduplicates() {
        val devices = listOf(
            deviceWithState(mac = "01", address = "192.168.1.10", status = WebsocketStatus.CONNECTED),
            deviceWithState(mac = "02", address = "192.168.1.11", status = WebsocketStatus.CONNECTED),
            deviceWithState(mac = "03", address = "192.168.1.10", status = WebsocketStatus.CONNECTED),
        )

        val result = resolveSoundSyncUnicastAddresses(devices, isPaused = false)

        assertEquals(listOf("192.168.1.10", "192.168.1.11"), result)
    }

    @Test
    fun resolveSoundSyncUnicastAddresses_excludesOfflineConnectingAndHiddenDevices() {
        val devices = listOf(
            deviceWithState(mac = "01", address = "192.168.1.10", status = WebsocketStatus.CONNECTED),
            deviceWithState(mac = "02", address = "192.168.1.11", status = WebsocketStatus.DISCONNECTED),
            deviceWithState(mac = "03", address = "192.168.1.12", status = WebsocketStatus.CONNECTING),
            deviceWithState(mac = "04", address = "192.168.1.13", status = WebsocketStatus.CONNECTED, isHidden = true),
        )

        val result = resolveSoundSyncUnicastAddresses(devices, isPaused = false)

        assertEquals(listOf("192.168.1.10"), result)
    }

    @Test
    fun resolveSoundSyncUnicastAddresses_returnsNullWhenPaused() {
        val devices = listOf(
            deviceWithState(mac = "01", address = "192.168.1.10", status = WebsocketStatus.DISCONNECTED),
        )

        val result = resolveSoundSyncUnicastAddresses(devices, isPaused = true)

        assertNull(result)
    }

    @Test
    fun resolveSoundSyncUnicastAddresses_returnsNullWhileVisibleDevicesAreReconnecting() {
        val devices = listOf(
            deviceWithState(mac = "01", address = "192.168.1.10", status = WebsocketStatus.CONNECTING),
            deviceWithState(mac = "02", address = "192.168.1.11", status = WebsocketStatus.DISCONNECTED),
        )

        val result = resolveSoundSyncUnicastAddresses(devices, isPaused = false)

        assertNull(result)
    }

    @Test
    fun resolveSoundSyncUnicastAddresses_returnsEmptyListWhenAllVisibleDevicesDisconnected() {
        val devices = listOf(
            deviceWithState(mac = "01", address = "192.168.1.10", status = WebsocketStatus.DISCONNECTED),
            deviceWithState(mac = "02", address = "192.168.1.11", status = WebsocketStatus.CONNECTING, isHidden = true),
        )

        val result = resolveSoundSyncUnicastAddresses(devices, isPaused = false)

        assertEquals(emptyList(), result)
    }

    private fun deviceWithState(
        mac: String,
        address: String,
        status: WebsocketStatus,
        isHidden: Boolean = false,
    ): DeviceWithState = DeviceWithState(
        device = Device(
            macAddress = mac,
            address = address,
            isHidden = isHidden,
        ),
        websocketStatus = status,
    )
}
