package ca.cgagnier.wlednativeandroid.service.soundsync

import android.content.Context
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.soundsync.audio.AudioSource
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.AgcMode
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.SoundSyncProcessor
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SoundSyncManagerTest {

    @Test
    fun start_whenPermissionDenied_setsPermissionErrorAndDoesNotStart() = runTest {
        val audioSource = FakeAudioSource(permissionGranted = false)
        val manager = createManager(audioSource = audioSource)

        val started = manager.start(gain = 2.0f, agcEnabled = true, agcMode = AgcMode.NORMAL)

        assertFalse(started)
        assertFalse(manager.state.value.isRunning)
        assertEquals(SoundSyncError.PERMISSION_REQUIRED, manager.state.value.error)
    }

    @Test
    fun start_whenAudioSourceFailsToStart_setsCaptureErrorAndKeepsRunningFalse() = runTest {
        val audioSource = FakeAudioSource(permissionGranted = true, startResult = false)
        val manager = createManager(audioSource = audioSource)

        val started = manager.start(gain = 1.5f, agcEnabled = false)

        assertFalse(started)
        assertFalse(manager.state.value.isRunning)
        assertEquals(SoundSyncError.CAPTURE_FAILED, manager.state.value.error)
    }

    @Test
    fun start_whenAudioSourceStarts_setsRunningWithoutError() = runTest {
        val audioSource = FakeAudioSource(permissionGranted = true, startResult = true)
        val manager = createManager(audioSource = audioSource)

        val started = manager.start(gain = 3.0f, agcEnabled = false)

        assertTrue(started)
        assertTrue(manager.state.value.isRunning)
        assertNull(manager.state.value.error)
    }

    @Test
    fun processAudioSamples_whenRunning_updatesSampleLevelAndSendsPacket() = runTest {
        val audioSource = FakeAudioSource(permissionGranted = true, startResult = true)
        val sender = FakeSoundSyncPacketSender()
        val manager = createManager(audioSource = audioSource, sender = sender)
        manager.start(gain = 3.0f, agcEnabled = false)

        val sineSamples = FloatArray(512) { i -> sin(2.0 * PI * 440.0 * i / audioSource.sampleRate).toFloat() }
        audioSource.emitSamples(sineSamples)

        assertTrue(manager.latestPacket.value.sampleSmth > 0f)
        assertEquals(1, sender.sentPackets.size)
    }

    @Test
    fun processAudioSamples_updatesLatestPacketBeforeCallingSender() = runTest {
        val audioSource = FakeAudioSource(permissionGranted = true, startResult = true)
        lateinit var manager: SoundSyncManager
        var latestSmthDuringSend = -1f
        val sender = FakeSoundSyncPacketSender(
            onSend = { latestSmthDuringSend = manager.latestPacket.value.sampleSmth },
        )
        manager = createManager(audioSource = audioSource, sender = sender)
        manager.start(gain = 3.0f, agcEnabled = false)

        val sineSamples = FloatArray(512) { i -> sin(2.0 * PI * 440.0 * i / audioSource.sampleRate).toFloat() }
        audioSource.emitSamples(sineSamples)

        assertTrue(latestSmthDuringSend > 0f)
    }

    @Test
    fun processAudioSamples_whenAgcEnabled_updatesEffectiveGainAndPreservesManualGain() = runTest {
        val audioSource = FakeAudioSource(permissionGranted = true, startResult = true)
        val manager = createManager(audioSource = audioSource)
        manager.start(gain = 1.5f, agcEnabled = true, agcMode = AgcMode.VIVID)

        val quietSineSamples = FloatArray(4096) { i ->
            0.02f * sin(2.0 * PI * 440.0 * i / audioSource.sampleRate).toFloat()
        }
        repeat(5) {
            audioSource.emitSamples(quietSineSamples)
        }

        assertEquals(1.5f, manager.state.value.gain, 0.001f)
        assertTrue(manager.state.value.effectiveGain > 2.0f)
    }

    @Test
    fun setSquelch_clampsAndUpdatesState() = runTest {
        val manager = createManager(audioSource = FakeAudioSource(permissionGranted = true))

        manager.setSquelch(150)

        assertEquals(SoundSyncProcessor.MAX_SQUELCH, manager.state.value.squelch)
    }

    @Test
    fun start_passesConfiguredPortAndSendModeToSenderFactory() = runTest {
        val audioSource = FakeAudioSource(permissionGranted = true, startResult = true)
        var capturedPort = 0
        var capturedMode: SoundSyncSendMode? = null
        val manager = createManager(
            audioSource = audioSource,
            onSenderCreated = { port, mode ->
                capturedPort = port
                capturedMode = mode
            },
        )

        val started = manager.start(gain = 1.0f, port = 12345, sendMode = SoundSyncSendMode.MULTICAST)

        assertTrue(started)
        assertEquals(12345, capturedPort)
        assertEquals(SoundSyncSendMode.MULTICAST, capturedMode)
        assertEquals(12345, manager.state.value.port)
        assertEquals(SoundSyncSendMode.MULTICAST, manager.state.value.sendMode)
    }

    @Test
    fun setSendMode_updatesStateAndForwardsToSenderWhileRunning() = runTest {
        val audioSource = FakeAudioSource(permissionGranted = true, startResult = true)
        val sender = FakeSoundSyncPacketSender()
        val manager = createManager(audioSource = audioSource, sender = sender)
        manager.start(gain = 1.0f, sendMode = SoundSyncSendMode.UNICAST)

        manager.setSendMode(SoundSyncSendMode.MULTICAST)

        assertEquals(SoundSyncSendMode.MULTICAST, manager.state.value.sendMode)
        assertEquals(SoundSyncSendMode.MULTICAST, sender.sendMode)
    }

    @Test
    fun start_observesOnlyOnlineVisibleDevicesForUnicast() = runTest {
        val onlineVisible = deviceWithState("01", "192.168.1.10", WebsocketStatus.CONNECTED, isHidden = false)
        val offlineVisible = deviceWithState("02", "192.168.1.11", WebsocketStatus.DISCONNECTED, isHidden = false)
        val onlineHidden = deviceWithState("03", "192.168.1.12", WebsocketStatus.CONNECTED, isHidden = true)
        val devicesFlow = MutableStateFlow(listOf(onlineVisible, offlineVisible, onlineHidden))
        val sender = FakeSoundSyncPacketSender()
        val manager = createManager(
            audioSource = FakeAudioSource(permissionGranted = true, startResult = true),
            sender = sender,
            devicesFlow = devicesFlow,
        )

        manager.start(gain = 1.0f)
        advanceUntilIdle()

        assertEquals(listOf("192.168.1.10"), sender.unicastAddresses)
    }

    @Test
    fun deviceListObservation_preservesTargetsWhenPausedForBackground() = runTest {
        val onlineDevice = deviceWithState("01", "192.168.1.10", WebsocketStatus.CONNECTED)
        val devicesFlow = MutableStateFlow(listOf(onlineDevice))
        val isPausedFlow = MutableStateFlow(false)
        val sender = FakeSoundSyncPacketSender()
        val manager = createManager(
            audioSource = FakeAudioSource(permissionGranted = true, startResult = true),
            sender = sender,
            devicesFlow = devicesFlow,
            isPausedFlow = isPausedFlow,
        )

        manager.start(gain = 1.0f)
        advanceUntilIdle()
        assertEquals(listOf("192.168.1.10"), sender.unicastAddresses)

        isPausedFlow.value = true
        devicesFlow.value = listOf(onlineDevice.copy(websocketStatus = WebsocketStatus.DISCONNECTED))
        advanceUntilIdle()

        assertEquals(listOf("192.168.1.10"), sender.unicastAddresses)
    }

    @Test
    fun deviceListObservation_preservesTargetsOnForegroundResumeBeforeReconnect() = runTest {
        val onlineDevice = deviceWithState("01", "192.168.1.10", WebsocketStatus.CONNECTED)
        val devicesFlow = MutableStateFlow(listOf(onlineDevice))
        val isPausedFlow = MutableStateFlow(false)
        val sender = FakeSoundSyncPacketSender()
        val manager = createManager(
            audioSource = FakeAudioSource(permissionGranted = true, startResult = true),
            sender = sender,
            devicesFlow = devicesFlow,
            isPausedFlow = isPausedFlow,
        )

        manager.start(gain = 1.0f)
        advanceUntilIdle()

        isPausedFlow.value = true
        devicesFlow.value = listOf(onlineDevice.copy(websocketStatus = WebsocketStatus.DISCONNECTED))
        advanceUntilIdle()

        isPausedFlow.value = false
        advanceUntilIdle()

        assertEquals(listOf("192.168.1.10"), sender.unicastAddresses)
    }

    @Test
    fun stop_resetsRunningAndVisualizationState() = runTest {
        val audioSource = FakeAudioSource(permissionGranted = true, startResult = true)
        val sender = FakeSoundSyncPacketSender()
        val manager = createManager(audioSource = audioSource, sender = sender)
        manager.start(gain = 2.0f, agcEnabled = false)
        val sineSamples = FloatArray(512) { i -> sin(2.0 * PI * 440.0 * i / audioSource.sampleRate).toFloat() }
        audioSource.emitSamples(sineSamples)

        manager.stop()

        assertFalse(manager.state.value.isRunning)
        assertFalse(audioSource.isRunning.value)
        assertTrue(sender.isClosed)
        assertEquals(0f, manager.latestPacket.value.sampleSmth)
        assertFalse(manager.latestPacket.value.samplePeak)
        assertTrue(manager.latestPacket.value.fftResult.all { it == 0.toByte() })
    }

    @Test
    fun audioSourceStoppingUnexpectedly_stopsManagerAndSetsCaptureError() = runTest {
        val audioSource = FakeAudioSource(permissionGranted = true, startResult = true)
        val manager = createManager(audioSource = audioSource)
        manager.start(gain = 1.0f, agcEnabled = false)
        advanceUntilIdle()

        audioSource.simulateReadFailure()
        advanceUntilIdle()

        assertFalse(manager.state.value.isRunning)
        assertEquals(SoundSyncError.CAPTURE_FAILED, manager.state.value.error)
    }

    private fun deviceWithState(
        mac: String,
        address: String,
        status: WebsocketStatus,
        isHidden: Boolean = false,
    ): DeviceWithState = DeviceWithState(
        device = Device(macAddress = mac, address = address, isHidden = isHidden),
        websocketStatus = status,
    )

    private fun TestScope.createManager(
        audioSource: AudioSource,
        sender: FakeSoundSyncPacketSender = FakeSoundSyncPacketSender(),
        devicesFlow: MutableStateFlow<List<DeviceWithState>> = MutableStateFlow(emptyList()),
        isPausedFlow: MutableStateFlow<Boolean> = MutableStateFlow(false),
        onSenderCreated: (Int, SoundSyncSendMode) -> Unit = { _, _ -> },
    ): SoundSyncManager {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val context = mockk<Context>(relaxed = true)
        val deviceConnectionManager = mockk<DeviceConnectionManager> {
            every { allDevicesWithState } returns devicesFlow.asStateFlow()
            every { isPaused } returns isPausedFlow.asStateFlow()
        }
        val manager = SoundSyncManager(
            context = context,
            deviceConnectionManager = deviceConnectionManager,
            coroutineScope = scope,
            ioDispatcher = dispatcher,
            audioSource = audioSource,
            senderFactory = { port, mode ->
                onSenderCreated(port, mode)
                sender
            },
        )
        coroutineContext.job.invokeOnCompletion {
            manager.destroy()
            scope.cancel()
        }
        return manager
    }

    private class FakeSoundSyncPacketSender(private val onSend: () -> Unit = {}) : SoundSyncPacketSender {
        val sentPackets = mutableListOf<UdpSoundSyncPacket>()
        var unicastAddresses: List<String> = emptyList()
            private set
        var sendMode: SoundSyncSendMode = SoundSyncSendMode.UNICAST
            private set
        var isClosed: Boolean = false
            private set

        override fun sendPacket(packet: UdpSoundSyncPacket) {
            onSend()
            sentPackets.add(packet)
        }

        override fun updateUnicastAddresses(addresses: List<String>) {
            unicastAddresses = addresses
        }

        override fun setSendMode(mode: SoundSyncSendMode) {
            sendMode = mode
        }

        override fun close() {
            isClosed = true
        }
    }

    private class FakeAudioSource(
        private val permissionGranted: Boolean,
        private val startResult: Boolean = true,
        override val sampleRate: Int = 44100,
    ) : AudioSource {
        private val _isRunning = MutableStateFlow(false)
        override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
        private var callback: ((FloatArray) -> Unit)? = null

        override fun hasPermission(): Boolean = permissionGranted

        override fun start(onAudioData: (FloatArray) -> Unit): Boolean {
            if (!startResult) return false
            callback = onAudioData
            _isRunning.value = true
            return true
        }

        override fun stop() {
            _isRunning.value = false
            callback = null
        }

        override fun destroy() {
            stop()
        }

        fun emitSamples(samples: FloatArray) {
            callback?.invoke(samples)
        }

        fun simulateReadFailure() {
            _isRunning.value = false
        }
    }
}
