import AVFAudio
import Foundation
import Shared
import Testing
@testable import WLED

@MainActor
struct SoundSyncViewModelTests {

    let database: AppDatabase

    init() {
        self.database = AppDatabase(inMemory: true)
    }

    @Test func observeLoadsPersistedGainWhenNotRunning() async throws {
        try await database.userPreferencesRepository.updateSoundSyncGain(gain: 4.5)
        let manager = FakeSoundSyncManager()
        let viewModel = makeViewModel(manager: manager)

        try await runObserveUntil(viewModel: viewModel, manager: manager) {
            viewModel.gain == 4.5
        }

        #expect(await manager.getState().gain == 4.5)
    }

    @Test func observeLoadsPersistedAgcModeWhileAgcDisabled() async throws {
        try await database.userPreferencesRepository.updateSoundSyncAgcEnabled(enabled: false)
        try await database.userPreferencesRepository.updateSoundSyncAgcMode(mode: .vivid)
        let manager = FakeSoundSyncManager()
        let viewModel = makeViewModel(manager: manager)

        try await runObserveUntil(viewModel: viewModel, manager: manager) {
            !viewModel.agcEnabled && viewModel.agcMode == .vivid
        }

        let managerState = await manager.getState()
        #expect(!managerState.agcEnabled)
        #expect(managerState.agcMode == .vivid)
    }

    @Test func observeLoadsPersistedSquelchWhenNotRunning() async throws {
        try await database.userPreferencesRepository.updateSoundSyncSquelch(squelch: 35)
        let manager = FakeSoundSyncManager()
        let viewModel = makeViewModel(manager: manager)

        try await runObserveUntil(viewModel: viewModel, manager: manager) {
            viewModel.squelch == 35
        }

        #expect(await manager.getState().squelch == 35)
    }

    @Test func observeLoadsPersistedPortWhenNotRunning() async throws {
        try await database.userPreferencesRepository.updateSoundSyncPort(port: 12345)
        let manager = FakeSoundSyncManager()
        let viewModel = makeViewModel(manager: manager)

        try await runObserveUntil(viewModel: viewModel, manager: manager) {
            viewModel.port == 12345
        }

        #expect(viewModel.portText == "12345")
    }

    @Test func observePreservesLiveGainWhenAlreadyRunning() async throws {
        try await database.userPreferencesRepository.updateSoundSyncGain(gain: 1.0)
        let runningState = SoundSyncState(isRunning: true, gain: 7.5, agcEnabled: false)
        let manager = FakeSoundSyncManager(initialState: runningState)
        let viewModel = makeViewModel(manager: manager)

        try await runObserveUntil(viewModel: viewModel, manager: manager) {
            viewModel.isRunning && viewModel.gain == 7.5
        }

        #expect(await manager.getState().gain == 7.5)
    }

    @Test func observePreservesLiveAgcModeWhenAlreadyRunning() async throws {
        try await database.userPreferencesRepository.updateSoundSyncAgcEnabled(enabled: false)
        try await database.userPreferencesRepository.updateSoundSyncAgcMode(mode: .normal)
        let runningState = SoundSyncState(isRunning: true, gain: 1.0, agcEnabled: true, agcMode: .lazy)
        let manager = FakeSoundSyncManager(initialState: runningState)
        let viewModel = makeViewModel(manager: manager)

        try await runObserveUntil(viewModel: viewModel, manager: manager) {
            viewModel.isRunning && viewModel.agcEnabled && viewModel.agcMode == .lazy
        }

        let managerState = await manager.getState()
        #expect(managerState.agcEnabled)
        #expect(managerState.agcMode == .lazy)
    }

    @Test func observePreservesLiveSquelchWhenAlreadyRunning() async throws {
        try await database.userPreferencesRepository.updateSoundSyncSquelch(squelch: 10)
        let runningState = SoundSyncState(isRunning: true, squelch: 48)
        let manager = FakeSoundSyncManager(initialState: runningState)
        let viewModel = makeViewModel(manager: manager)

        try await runObserveUntil(viewModel: viewModel, manager: manager) {
            viewModel.isRunning && viewModel.squelch == 48
        }

        #expect(await manager.getState().squelch == 48)
    }

    @Test func observePreservesLivePortWhenAlreadyRunning() async throws {
        try await database.userPreferencesRepository.updateSoundSyncPort(port: 11988)
        let runningState = SoundSyncState(isRunning: true, port: 14000)
        let manager = FakeSoundSyncManager(initialState: runningState)
        let viewModel = makeViewModel(manager: manager)

        try await runObserveUntil(viewModel: viewModel, manager: manager) {
            viewModel.isRunning && viewModel.port == 14000
        }

        #expect(await manager.getState().port == 14000)
    }

    @Test func setAgcModeUpdatesManagerAndPersistsPreference() async throws {
        let manager = FakeSoundSyncManager()
        let viewModel = makeViewModel(manager: manager)

        viewModel.setAgcMode(.lazy)

        try await waitUntil {
            let managerMode = await manager.getState().agcMode
            let persistedMode = await database.userPreferencesRepository.soundSyncAgcMode.firstValue()
            return managerMode == .lazy && persistedMode == .lazy
        }
        #expect(viewModel.agcMode == .lazy)
    }

    @Test func stepSquelchUpdatesManagerAndPersistsPreference() async throws {
        let manager = FakeSoundSyncManager()
        let viewModel = makeViewModel(manager: manager)

        viewModel.stepSquelch(to: 42)

        try await waitUntil {
            let managerSquelch = await manager.getState().squelch
            let persistedSquelch = await database.userPreferencesRepository.soundSyncSquelch.firstValue()?.int32Value
            return managerSquelch == 42 && persistedSquelch == 42
        }
        #expect(viewModel.squelch == 42)
    }

    @Test func saveSquelchPersistsLiveSquelchValue() async throws {
        let manager = FakeSoundSyncManager()
        let viewModel = makeViewModel(manager: manager)

        viewModel.updateLiveSquelch(65)
        viewModel.saveSquelch()

        try await waitUntil {
            let managerSquelch = await manager.getState().squelch
            let persistedSquelch = await database.userPreferencesRepository.soundSyncSquelch.firstValue()?.int32Value
            return managerSquelch == 65 && persistedSquelch == 65
        }
        #expect(viewModel.squelch == 65)
    }

    @Test func setPortPersistsPreference() async throws {
        let manager = FakeSoundSyncManager()
        let viewModel = makeViewModel(manager: manager)

        viewModel.setPort(15000)

        try await waitUntil {
            let persistedPort = await database.userPreferencesRepository.soundSyncPort.firstValue()?.int32Value
            return persistedPort == 15000
        }
        #expect(viewModel.port == 15000)
        #expect(viewModel.portText == "15000")
    }

    @Test(
        arguments: [
            ("abc123456", "12345", true, Int32(12345)),
            ("70000", "70000", false, UdpSoundSyncPacket.Companion.shared.DEFAULT_PORT),
            ("0", "0", false, UdpSoundSyncPacket.Companion.shared.DEFAULT_PORT),
            ("", "", false, UdpSoundSyncPacket.Companion.shared.DEFAULT_PORT)
        ]
    )
    func portTextFiltersNonDigitsAndValidatesRange(
        input: String,
        expectedText: String,
        expectedValid: Bool,
        expectedPort: Int32
    ) {
        let manager = FakeSoundSyncManager()
        let viewModel = makeViewModel(manager: manager)

        viewModel.portText = input

        #expect(viewModel.portText == expectedText)
        #expect(viewModel.isPortValid == expectedValid)
        #expect(viewModel.port == expectedPort)
    }

    @Test func startWhenPermissionDeniedOpensSettingsAndDoesNotStart() async throws {
        var didOpenSettings = false
        let manager = FakeSoundSyncManager(permission: .denied)
        let viewModel = try await makeReadyToStartViewModel(manager: manager) {
            didOpenSettings = true
        }

        await viewModel.start()

        #expect(didOpenSettings)
        #expect(!viewModel.isRunning)
        #expect(await manager.startCallCount == 0)
    }

    @Test func startWhenPermissionRequestRefusedDoesNotStart() async throws {
        let manager = FakeSoundSyncManager(
            permission: .undetermined,
            requestPermissionResult: false
        )
        let viewModel = try await makeReadyToStartViewModel(manager: manager)

        await viewModel.start()

        #expect(!viewModel.isRunning)
        #expect(await manager.startCallCount == 0)
    }

    @Test func startWhenNoDevicesSelectedDoesNotStart() async throws {
        let device = Shared.Device(macAddress: "aabbccddeeff", address: "192.168.1.50", isHidden: false)
        try await database.insertDevicesAndSetStatuses([(device, .connected)])
        let manager = FakeSoundSyncManager()
        let viewModel = makeViewModel(manager: manager)

        await viewModel.start()

        #expect(!viewModel.canStart)
        #expect(!viewModel.isRunning)
        #expect(await manager.startCallCount == 0)
    }

    @Test func startWhenManagerFailsToStartSetsErrorMessageAndKeepsRunningFalse() async throws {
        let manager = FakeSoundSyncManager(startResult: false)
        let viewModel = try await makeReadyToStartViewModel(manager: manager)

        await viewModel.start()

        #expect(!viewModel.isRunning)
        #expect(viewModel.errorMessage == "Capture failed")
    }

    @Test func startFiltersOutUnselectedHiddenAndOfflineDevicesAndUpdatesRunningState() async throws {
        let selectedOnline = Shared.Device(
            macAddress: "aabbccddeeff",
            address: "192.168.1.50",
            isHidden: false
        )
        let unselectedOnline = Shared.Device(
            macAddress: "001122334455",
            address: "192.168.1.51",
            isHidden: false
        )
        let hiddenOnline = Shared.Device(
            macAddress: "112233445566",
            address: "192.168.1.52",
            isHidden: true
        )
        let offlineSelected = Shared.Device(
            macAddress: "665544332211",
            address: "192.168.1.53",
            isHidden: false
        )
        try await database.insertDevicesAndSetStatuses([
            (selectedOnline, .connected),
            (unselectedOnline, .connected),
            (hiddenOnline, .connected),
            (offlineSelected, .disconnected)
        ])

        let manager = FakeSoundSyncManager()
        let viewModel = makeViewModel(manager: manager)
        viewModel.setSelectedDeviceMacs(["aabbccddeeff", "112233445566", "665544332211"])

        await viewModel.start()

        #expect(viewModel.isRunning)
        #expect(await manager.lastStartedAddresses == ["192.168.1.50"])
        await viewModel.stop()
    }

    @Test(
        arguments: [
            (true, true, Float(5.5)),
            (true, false, Float(2.0)),
            (false, true, Float(2.0))
        ]
    )
    func observeReflectsDisplayedGainForRunningAndAgcStates(
        isRunning: Bool,
        agcEnabled: Bool,
        expectedDisplayedGain: Float
    ) async throws {
        try await database.userPreferencesRepository.updateSoundSyncGain(gain: 2.0)
        try await database.userPreferencesRepository.updateSoundSyncAgcEnabled(enabled: agcEnabled)
        let initialState = SoundSyncState(
            isRunning: isRunning,
            gain: 2.0,
            effectiveGain: 5.5,
            agcEnabled: agcEnabled
        )
        let manager = FakeSoundSyncManager(initialState: initialState)
        let viewModel = makeViewModel(manager: manager)

        try await runObserveUntil(viewModel: viewModel, manager: manager) {
            viewModel.isRunning == isRunning &&
                viewModel.agcEnabled == agcEnabled &&
                viewModel.displayedGain == expectedDisplayedGain
        }

        #expect(viewModel.gain == 2.0)
    }
}

@MainActor
struct SoundSyncViewModelSelectionTests {

    let database: AppDatabase

    init() {
        self.database = AppDatabase(inMemory: true)
    }

    @Test func setSelectedDeviceMacsPersistsPreference() async throws {
        let manager = FakeSoundSyncManager()
        let viewModel = makeViewModel(manager: manager)

        viewModel.setSelectedDeviceMacs(["01", "02"])

        try await waitUntil {
            let persisted = await database.userPreferencesRepository.soundSyncTargetMacs.firstValue()
            return persisted == Set(["01", "02"])
        }
    }

    @Test func canStartBecomesTrueWhenVisibleDeviceSelected() async throws {
        let device = Shared.Device(macAddress: "01", address: "192.168.1.10", isHidden: false, originalName: "Beta")
        try await database.insertDevicesAndSetStatuses([(device, .connected)])
        let viewModel = makeViewModel(manager: FakeSoundSyncManager())

        viewModel.setSelectedDeviceMacs(["01"])

        #expect(viewModel.canStart)
    }

    @Test(arguments: [
        (true, ["01", "02"]),
        (false, ["02", "01"])
    ])
    func selectedDevicesOrdersByShowOfflineDevicesLast(
        showOfflineDevicesLast: Bool,
        expectedMacs: [String]
    ) async throws {
        let online = Shared.Device(macAddress: "01", address: "192.168.1.10", isHidden: false, originalName: "Beta")
        let offline = Shared.Device(
            macAddress: "02",
            address: "192.168.1.11",
            isHidden: false,
            originalName: "Alpha",
            lastSeen: 0
        )
        try await database.insertDevicesAndSetStatuses([(online, .connected), (offline, .disconnected)])
        let viewModel = makeViewModel(manager: FakeSoundSyncManager())
        viewModel.showOfflineDevicesLast = showOfflineDevicesLast
        viewModel.setSelectedDeviceMacs(["01", "02"])

        #expect(viewModel.selectedDevices.map { $0.device.macAddress } == expectedMacs)
    }

    @Test func stopResetsRunningState() async throws {
        let manager = FakeSoundSyncManager()
        let viewModel = try await makeReadyToStartViewModel(manager: manager)
        await viewModel.start()

        await viewModel.stop()

        #expect(!viewModel.isRunning)
    }

    @Test func externalStopCancelsDeviceObservation() async throws {
        let manager = FakeSoundSyncManager()
        let viewModel = try await makeReadyToStartViewModel(manager: manager)
        let observeTask = Task { await viewModel.observe() }
        defer { observeTask.cancel() }

        await viewModel.start()
        try await waitUntil { viewModel.isRunning }
        #expect(viewModel.deviceObservationTask != nil)

        await manager.stop()
        try await waitUntil { !viewModel.isRunning }
        #expect(viewModel.deviceObservationTask == nil)
    }

    private func makeViewModel(manager: FakeSoundSyncManager) -> SoundSyncViewModel {
        SoundSyncViewModel(
            soundSyncManager: manager,
            userPreferencesRepository: database.userPreferencesRepository,
            deviceConnectionManager: database.deviceConnectionManager
        )
    }

    private func makeReadyToStartViewModel(manager: FakeSoundSyncManager) async throws -> SoundSyncViewModel {
        let device = Shared.Device(macAddress: "aabbccddeeff", address: "192.168.1.50", isHidden: false)
        try await database.insertDevicesAndSetStatuses([(device, .connected)])
        let viewModel = makeViewModel(manager: manager)
        viewModel.setSelectedDeviceMacs(["aabbccddeeff"])
        return viewModel
    }
}

// MARK: - Helpers

private extension SoundSyncViewModelTests {
    func makeReadyToStartViewModel(
        manager: FakeSoundSyncManager,
        openSettings: @escaping @MainActor () -> Void = {}
    ) async throws -> SoundSyncViewModel {
        let device = Shared.Device(macAddress: "aabbccddeeff", address: "192.168.1.50", isHidden: false)
        try await database.insertDevicesAndSetStatuses([(device, .connected)])
        let viewModel = makeViewModel(manager: manager, openSettings: openSettings)
        viewModel.setSelectedDeviceMacs(["aabbccddeeff"])
        return viewModel
    }

    func makeViewModel(
        manager: FakeSoundSyncManager,
        openSettings: @escaping @MainActor () -> Void = {}
    ) -> SoundSyncViewModel {
        SoundSyncViewModel(
            soundSyncManager: manager,
            userPreferencesRepository: database.userPreferencesRepository,
            deviceConnectionManager: database.deviceConnectionManager,
            openSettings: openSettings
        )
    }

    func runObserveUntil(
        viewModel: SoundSyncViewModel,
        manager: FakeSoundSyncManager,
        condition: @escaping @MainActor () -> Bool
    ) async throws {
        let observeTask = Task {
            await viewModel.observe()
        }
        try await waitUntil(condition)
        await manager.finishStream()
        await observeTask.value
    }
}
