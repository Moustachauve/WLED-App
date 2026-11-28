package ca.cgagnier.wlednativeandroid.ui.soundsync

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.AgcMode
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme
import com.android.tools.screenshot.PreviewTest

class SoundSyncScreenshotTest {

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun SoundSyncLightPreview() {
        SoundSyncPreview(darkTheme = false)
    }

    @PreviewTest
    @Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun SoundSyncDarkPreview() {
        SoundSyncPreview(darkTheme = true)
    }

    @Composable
    private fun SoundSyncPreview(darkTheme: Boolean) {
        val sampleDevices = listOf(
            DeviceWithState(
                device = Device(macAddress = "01", address = "192.168.1.10", originalName = "Living Room"),
                websocketStatus = WebsocketStatus.CONNECTED,
            ),
            DeviceWithState(
                device = Device(macAddress = "02", address = "192.168.1.11", originalName = "Desk Strip"),
                websocketStatus = WebsocketStatus.DISCONNECTED,
            ),
        )
        WLEDNativeTheme(darkTheme = darkTheme, dynamicColor = false) {
            SoundSyncContent(
                uiState = SoundSyncUiState(
                    isRunning = false,
                    hasRecordAudioPermission = true,
                    gain = 1.0f,
                    effectiveGain = 1.0f,
                    agcEnabled = true,
                    agcMode = AgcMode.NORMAL,
                    squelch = 10,
                    allDevices = sampleDevices,
                    selectedDeviceMacs = setOf("01", "02"),
                ),
            )
        }
    }
}
