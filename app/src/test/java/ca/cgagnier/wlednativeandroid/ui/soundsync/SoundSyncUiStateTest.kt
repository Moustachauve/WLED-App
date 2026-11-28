package ca.cgagnier.wlednativeandroid.ui.soundsync

import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.soundsync.SoundSyncSendMode
import ca.cgagnier.wlednativeandroid.service.soundsync.UdpSoundSyncPacket
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.AgcMode
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.SoundSyncProcessor
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoundSyncUiStateTest {

    @Test
    fun defaultState_usesNormalAgcModeAndDefaultSquelch() {
        val uiState = SoundSyncUiState()

        assertFalse(uiState.agcEnabled)
        assertEquals(AgcMode.NORMAL, uiState.agcMode)
        assertEquals(SoundSyncProcessor.DEFAULT_SQUELCH, uiState.squelch)
        assertEquals(UdpSoundSyncPacket.DEFAULT_PORT, uiState.port)
        assertEquals(SoundSyncSendMode.UNICAST, uiState.sendMode)
        assertFalse(uiState.canStart)
    }

    @ParameterizedTest
    @CsvSource(
        "true, true, 5.5",
        "true, false, 2.0",
        "false, true, 2.0",
        "false, false, 2.0",
    )
    fun displayedGain_returnsEffectiveGainOnlyWhenRunningWithAgcEnabled(
        isRunning: Boolean,
        agcEnabled: Boolean,
        expectedDisplayedGain: Float,
    ) {
        val uiState = SoundSyncUiState(
            isRunning = isRunning,
            gain = 2.0f,
            effectiveGain = 5.5f,
            agcEnabled = agcEnabled,
        )

        assertEquals(expectedDisplayedGain, uiState.displayedGain, 0.0001f)
    }

    @Test
    fun selectedDevices_filtersByVisibleDevicesAndSortsAlphabetically() {
        val zebra = DeviceWithState(device = Device(macAddress = "01", address = "10.0.0.1", originalName = "Zebra"))
        val alpha = DeviceWithState(device = Device(macAddress = "02", address = "10.0.0.2", originalName = "Alpha"))
        val hidden = DeviceWithState(
            device = Device(macAddress = "03", address = "10.0.0.3", originalName = "Hidden", isHidden = true),
        )
        val uiState = SoundSyncUiState(
            allDevices = listOf(zebra, alpha, hidden),
            selectedDeviceMacs = setOf("01", "02", "03", "missing"),
            showHiddenDevices = false,
        )

        assertEquals(listOf("02", "01"), uiState.selectedDevices.map { it.device.macAddress })
    }

    @Test
    fun selectedDevices_placesOnlineBeforeOfflineWhenShowOfflineDevicesLastIsTrue() {
        val alphaOffline = DeviceWithState(
            device = Device(macAddress = "01", address = "10.0.0.1", originalName = "Alpha"),
            websocketStatus = WebsocketStatus.DISCONNECTED,
        )
        val betaOnline = DeviceWithState(
            device = Device(macAddress = "02", address = "10.0.0.2", originalName = "Beta"),
            websocketStatus = WebsocketStatus.CONNECTED,
        )
        val uiState = SoundSyncUiState(
            allDevices = listOf(alphaOffline, betaOnline),
            selectedDeviceMacs = setOf("01", "02"),
            showOfflineDevicesLast = true,
        )

        assertEquals(listOf("02", "01"), uiState.selectedDevices.map { it.device.macAddress })
    }

    @Test
    fun selectedDevices_preservesAlphabeticalOrderWhenShowOfflineDevicesLastIsFalse() {
        val alphaOffline = DeviceWithState(
            device = Device(macAddress = "01", address = "10.0.0.1", originalName = "Alpha"),
            websocketStatus = WebsocketStatus.DISCONNECTED,
        )
        val betaOnline = DeviceWithState(
            device = Device(macAddress = "02", address = "10.0.0.2", originalName = "Beta"),
            websocketStatus = WebsocketStatus.CONNECTED,
        )
        val uiState = SoundSyncUiState(
            allDevices = listOf(alphaOffline, betaOnline),
            selectedDeviceMacs = setOf("01", "02"),
            showOfflineDevicesLast = false,
        )

        assertEquals(listOf("01", "02"), uiState.selectedDevices.map { it.device.macAddress })
    }

    @Test
    fun canStart_requiresSelectionInUnicastAndAllowsEmptySelectionInMulticast() {
        val device = DeviceWithState(device = Device(macAddress = "01", address = "10.0.0.1", originalName = "Strip"))

        assertFalse(SoundSyncUiState(sendMode = SoundSyncSendMode.UNICAST, allDevices = listOf(device)).canStart)
        assertTrue(
            SoundSyncUiState(
                sendMode = SoundSyncSendMode.UNICAST,
                allDevices = listOf(device),
                selectedDeviceMacs = setOf("01"),
            ).canStart,
        )
        assertTrue(SoundSyncUiState(sendMode = SoundSyncSendMode.MULTICAST).canStart)
    }
}
