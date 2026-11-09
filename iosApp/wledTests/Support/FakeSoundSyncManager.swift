import AVFAudio
import Foundation
import os
import Shared
@testable import WLED

actor FakeSoundSyncManager: SoundSyncManaging {
    private var state: SoundSyncState
    private let permissionLock: OSAllocatedUnfairLock<AVAudioApplication.recordPermission>
    private let requestPermissionResult: Bool
    private let startResult: Bool
    private var stateContinuation: AsyncStream<SoundSyncState>.Continuation?
    private(set) var startCallCount = 0
    private(set) var lastStartedAddresses: [String] = []

    init(
        initialState: SoundSyncState = SoundSyncState(),
        permission: AVAudioApplication.recordPermission = .granted,
        requestPermissionResult: Bool = true,
        startResult: Bool = true
    ) {
        self.state = initialState
        self.permissionLock = OSAllocatedUnfairLock(initialState: permission)
        self.requestPermissionResult = requestPermissionResult
        self.startResult = startResult
    }

    func getState() -> SoundSyncState {
        state
    }

    func stateStream() -> AsyncStream<SoundSyncState> {
        let (stream, continuation) = AsyncStream.makeStream(
            of: SoundSyncState.self,
            bufferingPolicy: .bufferingNewest(1)
        )
        continuation.yield(state)
        stateContinuation = continuation
        return stream
    }

    nonisolated func recordPermissionStatus() -> AVAudioApplication.recordPermission {
        permissionLock.withLock { $0 }
    }

    func requestRecordAudioPermission() async -> Bool {
        if requestPermissionResult {
            permissionLock.withLock { $0 = .granted }
        }
        return requestPermissionResult
    }

    func start(_ config: SoundSyncStartConfig) async -> Bool {
        startCallCount += 1
        lastStartedAddresses = config.deviceAddresses
        state.gain = config.gain
        state.agcEnabled = config.agcEnabled
        state.agcMode = config.agcMode
        state.squelch = config.squelch
        state.port = config.port
        if startResult {
            state.isRunning = true
            state.errorMessage = nil
        } else {
            state.isRunning = false
            state.errorMessage = "Capture failed"
        }
        stateContinuation?.yield(state)
        return startResult
    }

    func stop() async {
        state.isRunning = false
        state.fftChannels = Array(repeating: 0, count: Int(UdpSoundSyncPacket.Companion.shared.FFT_CHANNEL_COUNT))
        state.sampleLevel = 0
        state.isPeak = false
        stateContinuation?.yield(state)
    }

    func setGain(_ gain: Float) async {
        state.gain = gain
        stateContinuation?.yield(state)
    }

    func setAgcEnabled(_ enabled: Bool) async {
        state.agcEnabled = enabled
        stateContinuation?.yield(state)
    }

    func setAgcMode(_ mode: AgcMode) async {
        state.agcMode = mode
        stateContinuation?.yield(state)
    }

    func setSquelch(_ squelch: Int32) async {
        state.squelch = squelch
        stateContinuation?.yield(state)
    }

    func setPort(_ port: Int32) async {
        state.port = port
        stateContinuation?.yield(state)
    }

    func updateDeviceAddresses(_ addresses: [String]) async {
        lastStartedAddresses = addresses
    }

    func finishStream() {
        stateContinuation?.finish()
    }
}
