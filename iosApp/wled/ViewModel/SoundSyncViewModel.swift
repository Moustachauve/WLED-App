import AVFAudio
import Foundation
import Observation
import Shared
import UIKit

/// View model for the Sound Sync screen.
///
/// Bridges the `SoundSyncManager` actor to SwiftUI, persists user preferences
/// via the shared `UserPreferencesRepository`, and observes the device list
/// for unicast UDP targets.
@MainActor
@Observable
final class SoundSyncViewModel {

    // MARK: - Observable Properties

    private static let maxPortDigits = 5

    var isRunning: Bool = false
    var gain: Float = 1.0
    var effectiveGain: Float = 1.0
    var agcEnabled: Bool = false
    var agcMode: AgcMode = .normal
    var squelch: Int32 = SoundSyncProcessor.Companion.shared.DEFAULT_SQUELCH
    var port: Int32 = UdpSoundSyncPacket.Companion.shared.DEFAULT_PORT {
        didSet {
            let text = String(port)
            if portText != text {
                portText = text
            }
        }
    }
    var portText: String = String(UdpSoundSyncPacket.Companion.shared.DEFAULT_PORT) {
        didSet {
            let digitsOnly = String(portText.filter(\.isNumber).prefix(Self.maxPortDigits))
            if portText != digitsOnly {
                portText = digitsOnly
                return
            }
            guard portText != oldValue else { return }
            if let parsed = parsedValidPort, port != parsed {
                setPort(parsed)
            }
        }
    }
    var fftChannels: [UInt8] = Array(repeating: 0, count: Int(UdpSoundSyncPacket.Companion.shared.FFT_CHANNEL_COUNT))
    var sampleLevel: Float = 0
    var isPeak: Bool = false
    var errorMessage: String?
    var recordPermission: AVAudioApplication.recordPermission = .undetermined

    var visibleDevices: [DeviceWithState] = []
    var onlineDevices: [DeviceWithState] = []
    var offlineDevices: [DeviceWithState] = []
    var selectedDeviceMacs: Set<String> = []
    var showOfflineDevicesLast: Bool = true
    var showHiddenDevices: Bool = false

    var displayedGain: Float {
        (isRunning && agcEnabled) ? effectiveGain : gain
    }

    var isPortValid: Bool {
        parsedValidPort != nil
    }

    var isPermissionDenied: Bool {
        recordPermission == .denied
    }

    var selectedDevices: [DeviceWithState] {
        let ordered = showOfflineDevicesLast ? (onlineDevices + offlineDevices) : visibleDevices
        return ordered.filter { selectedDeviceMacs.contains($0.device.macAddress) }
    }

    var canStart: Bool {
        !selectedDevices.isEmpty
    }

    var activeSoundSyncTargets: ActiveSoundSyncTargets {
        guard isRunning else { return .inactive }
        return ActiveSoundSyncTargets(
            macAddresses: selectedDeviceMacs,
            includeHidden: showHiddenDevices
        )
    }

    private var parsedValidPort: Int32? {
        guard let parsed = Int32(portText),
              UdpSoundSyncPacket.Companion.shared.isValidPort(port: parsed) else {
            return nil
        }
        return parsed
    }

    // MARK: - Private Properties

    private let soundSyncManager: any SoundSyncManaging
    private let userPreferencesRepository: UserPreferencesRepository
    private let deviceConnectionManager: DeviceConnectionManager
    private let openSettings: @MainActor () -> Void
    @ObservationIgnored private let partitionCache = DevicePartitionCache()
    @ObservationIgnored private(set) var deviceObservationTask: Task<Void, Never>?
    @ObservationIgnored private var gainUpdateTask: Task<Void, Never>?
    @ObservationIgnored private var squelchUpdateTask: Task<Void, Never>?
    @ObservationIgnored private var portUpdateTask: Task<Void, Never>?
    @ObservationIgnored private var targetMacsUpdateTask: Task<Void, Never>?

    // MARK: - Initialization

    init(
        soundSyncManager: any SoundSyncManaging = SoundSyncManager.shared,
        userPreferencesRepository: UserPreferencesRepository,
        deviceConnectionManager: DeviceConnectionManager,
        openSettings: @escaping @MainActor () -> Void = SoundSyncViewModel.defaultOpenAppSettings
    ) {
        self.soundSyncManager = soundSyncManager
        self.userPreferencesRepository = userPreferencesRepository
        self.deviceConnectionManager = deviceConnectionManager
        self.openSettings = openSettings
        self.recordPermission = soundSyncManager.recordPermissionStatus()
        syncDevicesWithState(deviceConnectionManager.allDevicesWithState.value)
    }

    // MARK: - Lifecycle

    /// Loads saved preferences, syncs current manager state, and observes live updates.
    func observe() async {
        let initialState = await soundSyncManager.getState()
        guard !Task.isCancelled else { return }
        applyState(initialState)
        await syncInitialPreferences(from: initialState)
        await loadInitialDeviceSelectionPreferences()

        guard !Task.isCancelled else { return }
        let helperTasks = [
            Task { [weak self] in await self?.observeDevices() },
            Task { [weak self] in await self?.observeTargetMacs() },
            Task { [weak self] in await self?.observeShowOfflineDevicesLast() },
            Task { [weak self] in await self?.observeShowHiddenDevices() },
            Task { [weak self] in await self?.runOfflineGracePeriodTimer() }
        ]
        defer {
            helperTasks.forEach { $0.cancel() }
        }

        let stream = await soundSyncManager.stateStream()
        for await state in stream {
            guard !Task.isCancelled else { break }
            applyState(state)
        }
    }

    // MARK: - Actions

    /// Refreshes the microphone permission state.
    func refreshPermissionState() {
        recordPermission = soundSyncManager.recordPermissionStatus()
    }

    /// Requests microphone permission (or opens Settings if previously denied) and starts sound sync if granted.
    func start() async {
        refreshPermissionState()
        if recordPermission == .denied {
            openSettings()
            return
        }
        if recordPermission != .granted {
            let granted = await soundSyncManager.requestRecordAudioPermission()
            refreshPermissionState()
            guard granted else { return }
        }

        guard canStart else { return }

        let addresses = deviceConnectionManager.currentSoundSyncUnicastAddresses(
            selectedDeviceMacs: selectedDeviceMacs,
            showHiddenDevices: showHiddenDevices
        )
        // Reset any invalid or incomplete text entry back to the last valid port before starting.
        if portText != String(port) {
            portText = String(port)
        }
        let started = await soundSyncManager.start(
            SoundSyncStartConfig(
                gain: gain,
                agcEnabled: agcEnabled,
                agcMode: agcMode,
                squelch: squelch,
                port: port,
                deviceAddresses: addresses
            )
        )
        let currentState = await soundSyncManager.getState()
        applyState(currentState)
        if started {
            startObservingDevices()
        }
    }

    /// Stops sound sync.
    func stop() async {
        await soundSyncManager.stop()
        let currentState = await soundSyncManager.getState()
        applyState(currentState)
    }

    /// Updates the manual gain in real-time while dragging.
    func updateLiveGain(_ newGain: Float) {
        gain = newGain
        gainUpdateTask?.cancel()
        gainUpdateTask = Task { [soundSyncManager] in
            await soundSyncManager.setGain(newGain)
        }
    }

    /// Persists the manual gain preference when slider editing finishes.
    func saveGain() {
        let currentGain = gain
        gainUpdateTask?.cancel()
        gainUpdateTask = Task { [soundSyncManager, userPreferencesRepository] in
            await soundSyncManager.setGain(currentGain)
            try? await userPreferencesRepository.updateSoundSyncGain(gain: currentGain)
        }
    }

    /// Updates the AGC toggle and persists the preference.
    func setAgcEnabled(_ enabled: Bool) {
        agcEnabled = enabled
        Task {
            await soundSyncManager.setAgcEnabled(enabled)
            try? await userPreferencesRepository.updateSoundSyncAgcEnabled(enabled: enabled)
        }
    }

    /// Updates the AGC preset mode and persists the preference.
    func setAgcMode(_ mode: AgcMode) {
        agcMode = mode
        Task {
            await soundSyncManager.setAgcMode(mode)
            try? await userPreferencesRepository.updateSoundSyncAgcMode(mode: mode)
        }
    }

    /// Updates the squelch threshold in real-time while dragging.
    func updateLiveSquelch(_ newSquelch: Int32) {
        guard newSquelch != squelch else { return }
        squelch = newSquelch
        squelchUpdateTask?.cancel()
        squelchUpdateTask = Task { [soundSyncManager] in
            await soundSyncManager.setSquelch(newSquelch)
        }
    }

    /// Persists the squelch preference when slider editing finishes.
    func saveSquelch() {
        let currentSquelch = squelch
        squelchUpdateTask?.cancel()
        squelchUpdateTask = Task { [soundSyncManager, userPreferencesRepository] in
            await soundSyncManager.setSquelch(currentSquelch)
            try? await userPreferencesRepository.updateSoundSyncSquelch(squelch: currentSquelch)
        }
    }

    /// Updates the squelch threshold by a discrete step and immediately persists the preference.
    func stepSquelch(to newSquelch: Int32) {
        squelch = newSquelch
        saveSquelch()
    }

    /// Updates the UDP port and persists the preference.
    func setPort(_ newPort: Int32) {
        port = newPort
        portUpdateTask?.cancel()
        portUpdateTask = Task { [userPreferencesRepository] in
            try? await userPreferencesRepository.updateSoundSyncPort(port: newPort)
        }
    }

    /// Updates the selected Sound Sync target MAC addresses and persists the preference.
    func setSelectedDeviceMacs(_ macAddresses: Set<String>) {
        selectedDeviceMacs = macAddresses
        targetMacsUpdateTask?.cancel()
        targetMacsUpdateTask = Task { [userPreferencesRepository] in
            try? await userPreferencesRepository.updateSoundSyncTargetMacs(macAddresses: macAddresses)
        }
    }
}

// MARK: - Private Helpers

private extension SoundSyncViewModel {
    func syncInitialPreferences(from initialState: SoundSyncState) async {
        if initialState.isRunning {
            applyRunningPreferences(from: initialState)
            return
        }
        await loadSavedPreferences(fallback: initialState)
    }

    func applyRunningPreferences(from state: SoundSyncState) {
        if gain != state.gain {
            gain = state.gain
        }
        if agcEnabled != state.agcEnabled {
            agcEnabled = state.agcEnabled
        }
        if agcMode != state.agcMode {
            agcMode = state.agcMode
        }
        if squelch != state.squelch {
            squelch = state.squelch
        }
        if port != state.port {
            port = state.port
        }
    }

    func loadSavedPreferences(fallback initialState: SoundSyncState) async {
        let resolvedGain = await userPreferencesRepository.soundSyncGain.firstValue()?.floatValue
            ?? initialState.gain
        guard !Task.isCancelled else { return }
        if gain != resolvedGain {
            gain = resolvedGain
        }
        await soundSyncManager.setGain(resolvedGain)

        let resolvedAgcEnabled = await userPreferencesRepository.soundSyncAgcEnabled.firstValue()?.boolValue
            ?? initialState.agcEnabled
        guard !Task.isCancelled else { return }
        if agcEnabled != resolvedAgcEnabled {
            agcEnabled = resolvedAgcEnabled
        }
        await soundSyncManager.setAgcEnabled(resolvedAgcEnabled)

        let resolvedAgcMode = await userPreferencesRepository.soundSyncAgcMode.firstValue()
            ?? initialState.agcMode
        guard !Task.isCancelled else { return }
        if agcMode != resolvedAgcMode {
            agcMode = resolvedAgcMode
        }
        await soundSyncManager.setAgcMode(resolvedAgcMode)

        let resolvedSquelch = await userPreferencesRepository.soundSyncSquelch.firstValue()?.int32Value
            ?? initialState.squelch
        guard !Task.isCancelled else { return }
        if squelch != resolvedSquelch {
            squelch = resolvedSquelch
        }
        await soundSyncManager.setSquelch(resolvedSquelch)

        let resolvedPort = await userPreferencesRepository.soundSyncPort.firstValue()?.int32Value
            ?? initialState.port
        guard !Task.isCancelled else { return }
        if port != resolvedPort {
            port = resolvedPort
        }
    }

    func loadInitialDeviceSelectionPreferences() async {
        if let savedTargetMacs = await userPreferencesRepository.soundSyncTargetMacs.firstValue() {
            guard !Task.isCancelled else { return }
            selectedDeviceMacs = savedTargetMacs
        }
        if let savedShowOfflineLast = await userPreferencesRepository.showOfflineDevicesLast.firstValue()?.boolValue {
            guard !Task.isCancelled else { return }
            showOfflineDevicesLast = savedShowOfflineLast
        }
        if let savedShowHidden = await userPreferencesRepository.showHiddenDevices.firstValue()?.boolValue {
            guard !Task.isCancelled else { return }
            showHiddenDevices = savedShowHidden
        }
        updateFilteredDevices()
    }

    func observeDevices() async {
        for await devices in deviceConnectionManager.allDevicesWithState {
            guard !Task.isCancelled else { break }
            syncDevicesWithState(devices)
        }
    }

    func observeTargetMacs() async {
        for await targetMacs in userPreferencesRepository.soundSyncTargetMacs {
            guard !Task.isCancelled else { break }
            if selectedDeviceMacs != targetMacs {
                selectedDeviceMacs = targetMacs
            }
        }
    }

    func observeShowOfflineDevicesLast() async {
        for await value in userPreferencesRepository.showOfflineDevicesLast {
            guard !Task.isCancelled else { break }
            let boolVal = value.boolValue
            if showOfflineDevicesLast != boolVal {
                showOfflineDevicesLast = boolVal
            }
        }
    }

    func observeShowHiddenDevices() async {
        for await value in userPreferencesRepository.showHiddenDevices {
            guard !Task.isCancelled else { break }
            let boolVal = value.boolValue
            if showHiddenDevices != boolVal {
                showHiddenDevices = boolVal
                updateFilteredDevices()
            }
        }
    }

    func syncDevicesWithState(_ sharedDevices: [Shared.DeviceWithState]) {
        partitionCache.sync(sharedDevices)
        updateFilteredDevices()
    }

    func updateFilteredDevices(currentTime: Date = Date()) {
        let partitioned = partitionCache.partition(showHiddenDevices: showHiddenDevices, currentTime: currentTime)

        if visibleDevices != partitioned.visible {
            visibleDevices = partitioned.visible
        }
        if onlineDevices != partitioned.online {
            onlineDevices = partitioned.online
        }
        if offlineDevices != partitioned.offline {
            offlineDevices = partitioned.offline
        }
    }

    func runOfflineGracePeriodTimer() async {
        while !Task.isCancelled {
            try? await Task.sleep(for: .seconds(30))
            guard !Task.isCancelled else { break }
            updateFilteredDevices()
        }
    }

    func applyState(_ state: SoundSyncState) {
        if isRunning != state.isRunning {
            isRunning = state.isRunning
            if !state.isRunning {
                stopObservingDevices()
            }
        }
        if effectiveGain != state.effectiveGain {
            effectiveGain = state.effectiveGain
        }
        if fftChannels != state.fftChannels {
            fftChannels = state.fftChannels
        }
        if sampleLevel != state.sampleLevel {
            sampleLevel = state.sampleLevel
        }
        if isPeak != state.isPeak {
            isPeak = state.isPeak
        }
        if errorMessage != state.errorMessage {
            errorMessage = state.errorMessage
        }
    }

    func startObservingDevices() {
        deviceObservationTask?.cancel()
        deviceObservationTask = Task { [weak self] in
            guard let self else { return }
            // Wait for any in-flight target MAC save so observation doesn't briefly push previously saved targets.
            await self.targetMacsUpdateTask?.value
            guard !Task.isCancelled else { return }
            for await addresses in self.deviceConnectionManager.soundSyncUnicastAddresses(
                selectedDeviceMacs: self.userPreferencesRepository.soundSyncTargetMacs,
                showHiddenDevices: self.userPreferencesRepository.showHiddenDevices
            ) {
                guard !Task.isCancelled else { break }
                await self.soundSyncManager.updateDeviceAddresses(addresses)
            }
        }
    }

    func stopObservingDevices() {
        deviceObservationTask?.cancel()
        deviceObservationTask = nil
    }

    static func defaultOpenAppSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}
