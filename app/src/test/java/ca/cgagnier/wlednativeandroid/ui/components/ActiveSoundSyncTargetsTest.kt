package ca.cgagnier.wlednativeandroid.ui.components

import ca.cgagnier.wlednativeandroid.model.AP_MODE_MAC_ADDRESS
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.soundsync.SoundSyncSendMode
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActiveSoundSyncTargetsTest {

    private fun device(
        mac: String = "AA:BB:CC:DD:EE:01",
        isOnline: Boolean = true,
        isHidden: Boolean = false,
    ): DeviceWithState = DeviceWithState(
        device = Device(
            macAddress = mac,
            address = "192.168.1.10",
            originalName = "WLED",
            isHidden = isHidden,
        ),
        websocketStatus = if (isOnline) WebsocketStatus.CONNECTED else WebsocketStatus.DISCONNECTED,
    )

    @Test
    fun `None never reports any device as receiving`() {
        assertFalse(ActiveSoundSyncTargets.None.isDeviceReceiving(device()))
    }

    @Test
    fun `AllOnline includes online and hidden devices`() {
        assertTrue(ActiveSoundSyncTargets.AllOnline.isDeviceReceiving(device()))
        assertTrue(ActiveSoundSyncTargets.AllOnline.isDeviceReceiving(device(isHidden = true)))
    }

    @Test
    fun `AllOnline excludes offline and AP mode devices`() {
        assertFalse(ActiveSoundSyncTargets.AllOnline.isDeviceReceiving(device(isOnline = false)))
        assertFalse(ActiveSoundSyncTargets.AllOnline.isDeviceReceiving(device(mac = AP_MODE_MAC_ADDRESS)))
    }

    @Test
    fun `Selected matches online selected device and excludes unselected`() {
        val targets = ActiveSoundSyncTargets.Selected(setOf("AA:BB:CC:DD:EE:01"))

        assertTrue(targets.isDeviceReceiving(device(mac = "AA:BB:CC:DD:EE:01")))
        assertFalse(targets.isDeviceReceiving(device(mac = "AA:BB:CC:DD:EE:02")))
    }

    @Test
    fun `Selected excludes offline and AP mode devices even if MAC matches`() {
        val targets = ActiveSoundSyncTargets.Selected(setOf("AA:BB:CC:DD:EE:01", AP_MODE_MAC_ADDRESS))

        assertFalse(targets.isDeviceReceiving(device(mac = "AA:BB:CC:DD:EE:01", isOnline = false)))
        assertFalse(targets.isDeviceReceiving(device(mac = AP_MODE_MAC_ADDRESS)))
    }

    @Test
    fun `Selected respects includeHidden flag`() {
        val hidden = device(mac = "AA:BB:CC:DD:EE:01", isHidden = true)

        assertFalse(
            ActiveSoundSyncTargets.Selected(setOf("AA:BB:CC:DD:EE:01"), includeHidden = false)
                .isDeviceReceiving(hidden),
        )
        assertTrue(
            ActiveSoundSyncTargets.Selected(setOf("AA:BB:CC:DD:EE:01"), includeHidden = true)
                .isDeviceReceiving(hidden),
        )
    }

    @Test
    fun `resolveActiveSoundSyncTargets maps stopped and running modes`() {
        assertEquals(
            ActiveSoundSyncTargets.None,
            resolveActiveSoundSyncTargets(
                isRunning = false,
                sendMode = SoundSyncSendMode.UNICAST,
                selectedDeviceMacs = setOf("01"),
                showHiddenDevices = false,
            ),
        )
        assertEquals(
            ActiveSoundSyncTargets.AllOnline,
            resolveActiveSoundSyncTargets(
                isRunning = true,
                sendMode = SoundSyncSendMode.MULTICAST,
                selectedDeviceMacs = setOf("01"),
                showHiddenDevices = false,
            ),
        )
        assertEquals(
            ActiveSoundSyncTargets.Selected(macAddresses = setOf("01"), includeHidden = true),
            resolveActiveSoundSyncTargets(
                isRunning = true,
                sendMode = SoundSyncSendMode.UNICAST,
                selectedDeviceMacs = setOf("01"),
                showHiddenDevices = true,
            ),
        )
    }
}
