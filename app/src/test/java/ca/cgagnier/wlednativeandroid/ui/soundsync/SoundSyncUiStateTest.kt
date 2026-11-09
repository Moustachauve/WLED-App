package ca.cgagnier.wlednativeandroid.ui.soundsync

import ca.cgagnier.wlednativeandroid.service.soundsync.UdpSoundSyncPacket
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.AgcMode
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.SoundSyncProcessor
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SoundSyncUiStateTest {

    @Test
    fun defaultState_usesNormalAgcModeAndDefaultSquelch() {
        val uiState = SoundSyncUiState()

        assertFalse(uiState.agcEnabled)
        assertEquals(AgcMode.NORMAL, uiState.agcMode)
        assertEquals(SoundSyncProcessor.DEFAULT_SQUELCH, uiState.squelch)
        assertEquals(UdpSoundSyncPacket.DEFAULT_PORT, uiState.port)
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
}
