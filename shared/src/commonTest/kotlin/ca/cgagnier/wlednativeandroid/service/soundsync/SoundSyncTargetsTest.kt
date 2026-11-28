package ca.cgagnier.wlednativeandroid.service.soundsync

import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SoundSyncTargetsTest {

    @Test
    fun resolveSoundSyncUnicastAddresses_includesSelectedOnlineVisibleDevicesAndDeduplicates() {
        val devices = listOf(
            deviceWithState(mac = "01", address = "192.168.1.10", status = WebsocketStatus.CONNECTED),
            deviceWithState(mac = "02", address = "192.168.1.11", status = WebsocketStatus.CONNECTED),
            deviceWithState(mac = "03", address = "192.168.1.10", status = WebsocketStatus.CONNECTED),
            deviceWithState(mac = "04", address = "192.168.1.12", status = WebsocketStatus.CONNECTED),
        )

        val result = resolveSoundSyncUnicastAddresses(
            devices = devices,
            isPaused = false,
            selectedDeviceMacs = setOf("01", "02", "03"),
        )

        assertEquals(listOf("192.168.1.10", "192.168.1.11"), result)
    }

    @Test
    fun resolveSoundSyncUnicastAddresses_excludesOfflineConnectingAndHiddenDevicesWhenAnotherVisibleIsOnline() {
        val devices = listOf(
            deviceWithState(mac = "01", address = "192.168.1.10", status = WebsocketStatus.CONNECTED),
            deviceWithState(mac = "02", address = "192.168.1.11", status = WebsocketStatus.DISCONNECTED),
            deviceWithState(mac = "03", address = "192.168.1.12", status = WebsocketStatus.CONNECTING),
            deviceWithState(mac = "04", address = "192.168.1.13", status = WebsocketStatus.CONNECTED, isHidden = true),
        )

        val result = resolveSoundSyncUnicastAddresses(
            devices = devices,
            isPaused = false,
            selectedDeviceMacs = setOf("01", "02", "03", "04"),
        )

        assertEquals(listOf("192.168.1.10"), result)
    }

    @Test
    fun resolveSoundSyncUnicastAddresses_includesSelectedHiddenDeviceWhenShowHiddenIsTrue() {
        val devices = listOf(
            deviceWithState(mac = "01", address = "192.168.1.10", status = WebsocketStatus.CONNECTED, isHidden = true),
        )

        val result = resolveSoundSyncUnicastAddresses(
            devices = devices,
            isPaused = false,
            selectedDeviceMacs = setOf("01"),
            showHiddenDevices = true,
        )

        assertEquals(listOf("192.168.1.10"), result)
    }

    @Test
    fun resolveSoundSyncUnicastAddresses_returnsEmptyListWhenSelectionIsEmptyEvenIfPausedOrOffline() {
        val devices = listOf(
            deviceWithState(mac = "01", address = "192.168.1.10", status = WebsocketStatus.CONNECTED),
        )

        listOf(false, true).forEach { paused ->
            val result = resolveSoundSyncUnicastAddresses(
                devices = devices,
                isPaused = paused,
                selectedDeviceMacs = emptySet(),
            )
            assertEquals(emptyList(), result, "paused=$paused")
        }
    }

    @Test
    fun resolveSoundSyncUnicastAddresses_returnsNullWhenPausedWithSelection() {
        val devices = listOf(
            deviceWithState(mac = "01", address = "192.168.1.10", status = WebsocketStatus.DISCONNECTED),
        )

        val result = resolveSoundSyncUnicastAddresses(
            devices = devices,
            isPaused = true,
            selectedDeviceMacs = setOf("01"),
        )

        assertNull(result)
    }

    @Test
    fun resolveSoundSyncUnicastAddresses_returnsNullWhenNoVisibleDeviceIsOnlineWithSelection() {
        val cases = listOf(
            listOf(
                deviceWithState(mac = "01", address = "192.168.1.10", status = WebsocketStatus.CONNECTING),
                deviceWithState(mac = "02", address = "192.168.1.11", status = WebsocketStatus.DISCONNECTED),
            ),
            listOf(
                deviceWithState(mac = "01", address = "192.168.1.10", status = WebsocketStatus.DISCONNECTED),
                deviceWithState(
                    mac = "02",
                    address = "192.168.1.11",
                    status = WebsocketStatus.CONNECTED,
                    isHidden = true,
                ),
            ),
            emptyList(),
        )

        cases.forEach { devices ->
            assertNull(
                resolveSoundSyncUnicastAddresses(
                    devices = devices,
                    isPaused = false,
                    selectedDeviceMacs = setOf("01"),
                ),
                "devices=$devices",
            )
        }
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
