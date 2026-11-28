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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

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
    fun `Selected excludes offline devices even if MAC matches`() {
        val targets = ActiveSoundSyncTargets.Selected(setOf("AA:BB:CC:DD:EE:01"))

        assertFalse(targets.isDeviceReceiving(device(mac = "AA:BB:CC:DD:EE:01", isOnline = false)))
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

    @ParameterizedTest(name = "isRunning={0}, sendMode={1} -> {2}")
    @MethodSource("resolveTargetCases")
    fun `resolveActiveSoundSyncTargets maps stopped and running modes`(
        isRunning: Boolean,
        sendMode: SoundSyncSendMode,
        showHiddenDevices: Boolean,
        expected: ActiveSoundSyncTargets,
    ) {
        assertEquals(
            expected,
            resolveActiveSoundSyncTargets(
                isRunning = isRunning,
                sendMode = sendMode,
                selectedDeviceMacs = setOf("01"),
                showHiddenDevices = showHiddenDevices,
            ),
        )
    }

    companion object {
        @JvmStatic
        fun resolveTargetCases(): List<Arguments> = listOf(
            Arguments.of(false, SoundSyncSendMode.UNICAST, false, ActiveSoundSyncTargets.None),
            Arguments.of(true, SoundSyncSendMode.MULTICAST, false, ActiveSoundSyncTargets.AllOnline),
            Arguments.of(
                true,
                SoundSyncSendMode.UNICAST,
                true,
                ActiveSoundSyncTargets.Selected(macAddresses = setOf("01"), includeHidden = true),
            ),
        )
    }
}
