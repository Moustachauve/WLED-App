package ca.cgagnier.wlednativeandroid.ui.soundsync

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.soundsync.SoundSyncError
import ca.cgagnier.wlednativeandroid.service.soundsync.SoundSyncManager
import ca.cgagnier.wlednativeandroid.service.soundsync.SoundSyncSendMode
import ca.cgagnier.wlednativeandroid.service.soundsync.SoundSyncService
import ca.cgagnier.wlednativeandroid.service.soundsync.UdpSoundSyncPacket
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.AgcMode
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.SoundSyncProcessor
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.partitionDevices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SoundSyncViewModel(
    private val context: Context,
    private val soundSyncManager: SoundSyncManager,
    private val preferencesRepository: UserPreferencesRepository,
    private val deviceConnectionManager: DeviceConnectionManager,
) : ViewModel() {

    private val hasRecordAudioPermission = MutableStateFlow(soundSyncManager.hasRecordAudioPermission())
    val portState = TextFieldState(initialText = UdpSoundSyncPacket.DEFAULT_PORT.toString())

    private val deviceSelectionFlow = combine(
        deviceConnectionManager.allDevicesWithState,
        preferencesRepository.soundSyncTargetMacs,
        preferencesRepository.showOfflineDevicesLast,
        preferencesRepository.showHiddenDevices,
    ) { allDevices, targetMacs, showOfflineLast, showHidden ->
        DeviceSelectionSnapshot(
            allDevices = allDevices,
            selectedDeviceMacs = targetMacs,
            showOfflineDevicesLast = showOfflineLast,
            showHiddenDevices = showHidden,
        )
    }

    init {
        viewModelScope.launch {
            val savedPort = preferencesRepository.soundSyncPort.first()
            if (!soundSyncManager.state.value.isRunning) {
                soundSyncManager.setGain(preferencesRepository.soundSyncGain.first())
                soundSyncManager.setAgcEnabled(preferencesRepository.soundSyncAgcEnabled.first())
                soundSyncManager.setAgcMode(preferencesRepository.soundSyncAgcMode.first())
                soundSyncManager.setSquelch(preferencesRepository.soundSyncSquelch.first())
                soundSyncManager.setSendMode(preferencesRepository.soundSyncSendMode.first())
            }
            val initialPort = if (soundSyncManager.state.value.isRunning) {
                soundSyncManager.state.value.port
            } else {
                savedPort
            }
            if (portState.text.toString() == UdpSoundSyncPacket.DEFAULT_PORT.toString() &&
                initialPort != UdpSoundSyncPacket.DEFAULT_PORT
            ) {
                portState.setTextAndPlaceCursorAtEnd(initialPort.toString())
            }
            var lastSavedPort = savedPort
            snapshotFlow { portState.text.toString() }.collect { text ->
                if (soundSyncManager.state.value.isRunning) return@collect
                val validPort = text.toValidPortOrNull() ?: return@collect
                if (validPort != lastSavedPort) {
                    lastSavedPort = validPort
                    preferencesRepository.updateSoundSyncPort(validPort)
                }
            }
        }
    }

    val uiState: StateFlow<SoundSyncUiState> = combine(
        soundSyncManager.state,
        hasRecordAudioPermission,
        preferencesRepository.soundSyncPort,
        deviceSelectionFlow,
    ) { state, hasPermission, savedPort, selection ->
        SoundSyncUiState(
            isRunning = state.isRunning,
            gain = state.gain,
            effectiveGain = state.effectiveGain,
            agcEnabled = state.agcEnabled,
            agcMode = state.agcMode,
            squelch = state.squelch,
            port = if (state.isRunning) state.port else savedPort,
            sendMode = state.sendMode,
            error = state.error,
            hasRecordAudioPermission = hasPermission,
            allDevices = selection.allDevices,
            selectedDeviceMacs = selection.selectedDeviceMacs,
            showOfflineDevicesLast = selection.showOfflineDevicesLast,
            showHiddenDevices = selection.showHiddenDevices,
        )
    }.stateIn(
        viewModelScope,
        WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
        soundSyncManager.state.value.let { current ->
            SoundSyncUiState(
                isRunning = current.isRunning,
                gain = current.gain,
                effectiveGain = current.effectiveGain,
                agcEnabled = current.agcEnabled,
                agcMode = current.agcMode,
                squelch = current.squelch,
                port = current.port,
                sendMode = current.sendMode,
                error = current.error,
                hasRecordAudioPermission = hasRecordAudioPermission.value,
                allDevices = deviceConnectionManager.allDevicesWithState.value,
            )
        },
    )

    val latestPacket: StateFlow<UdpSoundSyncPacket> = soundSyncManager.latestPacket

    fun start() {
        if (!uiState.value.canStart) return
        if (!soundSyncManager.hasRecordAudioPermission()) {
            refreshPermissionState()
            return
        }
        val effectivePort = portState.text.toString().toValidPortOrNull() ?: uiState.value.port
        if (portState.text.toString() != effectivePort.toString()) {
            portState.setTextAndPlaceCursorAtEnd(effectivePort.toString())
        }
        val current = soundSyncManager.state.value.copy(port = effectivePort)
        val intent = SoundSyncService.startIntent(
            context = context,
            state = current,
        )
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop() {
        soundSyncManager.stop()
        context.stopService(Intent(context, SoundSyncService::class.java))
    }

    fun setGain(gain: Float) {
        soundSyncManager.setGain(gain)
    }

    fun saveGain() {
        val currentGain = soundSyncManager.state.value.gain
        viewModelScope.launch {
            preferencesRepository.updateSoundSyncGain(currentGain)
        }
    }

    fun setAgcEnabled(enabled: Boolean) {
        soundSyncManager.setAgcEnabled(enabled)
        viewModelScope.launch {
            preferencesRepository.updateSoundSyncAgcEnabled(enabled)
        }
    }

    fun setAgcMode(mode: AgcMode) {
        soundSyncManager.setAgcMode(mode)
        viewModelScope.launch {
            preferencesRepository.updateSoundSyncAgcMode(mode)
        }
    }

    fun setSquelch(squelch: Int) {
        soundSyncManager.setSquelch(squelch)
    }

    fun setSendMode(mode: SoundSyncSendMode) {
        soundSyncManager.setSendMode(mode)
        viewModelScope.launch {
            preferencesRepository.updateSoundSyncSendMode(mode)
        }
    }

    fun setSelectedDeviceMacs(macAddresses: Set<String>) {
        viewModelScope.launch {
            preferencesRepository.updateSoundSyncTargetMacs(macAddresses)
        }
    }

    fun stepSquelch(delta: Int) {
        soundSyncManager.setSquelch(soundSyncManager.state.value.squelch + delta)
        saveSquelch()
    }

    fun saveSquelch() {
        val currentSquelch = soundSyncManager.state.value.squelch
        viewModelScope.launch {
            preferencesRepository.updateSoundSyncSquelch(currentSquelch)
        }
    }

    fun resetInvalidPort() {
        if (!soundSyncManager.state.value.isRunning && portState.text.toString().toValidPortOrNull() == null) {
            portState.setTextAndPlaceCursorAtEnd(uiState.value.port.toString())
        }
    }

    fun refreshPermissionState() {
        hasRecordAudioPermission.update { soundSyncManager.hasRecordAudioPermission() }
    }

    private data class DeviceSelectionSnapshot(
        val allDevices: List<DeviceWithState>,
        val selectedDeviceMacs: Set<String>,
        val showOfflineDevicesLast: Boolean,
        val showHiddenDevices: Boolean,
    )

    private companion object {
        /** Timeout for WhileSubscribed in milliseconds. */
        const val STATE_FLOW_TIMEOUT_MS = 5000L
    }
}

data class SoundSyncUiState(
    val isRunning: Boolean = false,
    val gain: Float = 1.0f,
    val effectiveGain: Float = 1.0f,
    val agcEnabled: Boolean = false,
    val agcMode: AgcMode = AgcMode.NORMAL,
    val squelch: Int = SoundSyncProcessor.DEFAULT_SQUELCH,
    val port: Int = UdpSoundSyncPacket.DEFAULT_PORT,
    val sendMode: SoundSyncSendMode = SoundSyncSendMode.UNICAST,
    val error: SoundSyncError? = null,
    val hasRecordAudioPermission: Boolean = false,
    val allDevices: List<DeviceWithState> = emptyList(),
    val selectedDeviceMacs: Set<String> = emptySet(),
    val showOfflineDevicesLast: Boolean = true,
    val showHiddenDevices: Boolean = false,
) {
    val displayedGain: Float
        get() = if (isRunning && agcEnabled) effectiveGain else gain

    val visibleDevices: List<DeviceWithState>
        get() = partitionDevices(
            devices = allDevices,
            showHiddenDevices = showHiddenDevices,
            currentTimeMillis = 0L,
        ).visible

    val selectedDevices: List<DeviceWithState>
        get() {
            val selected = visibleDevices.filter { it.device.macAddress in selectedDeviceMacs }
            return if (showOfflineDevicesLast) {
                val (online, offline) = selected.partition { it.isOnline }
                online + offline
            } else {
                selected
            }
        }

    val canStart: Boolean
        get() = sendMode == SoundSyncSendMode.MULTICAST || selectedDevices.isNotEmpty()
}

internal fun String.toValidPortOrNull(): Int? = toIntOrNull()?.takeIf(UdpSoundSyncPacket::isValidPort)
