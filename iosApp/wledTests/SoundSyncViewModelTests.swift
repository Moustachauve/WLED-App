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

        #expect(await manager.getState().port == 12345)
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

    @Test func setPortUpdatesManagerAndPersistsPreference() async throws {
        let manager = FakeSoundSyncManager()
        let viewModel = makeViewModel(manager: manager)

        viewModel.setPort(15000)

        try await waitUntil {
            let managerPort = await manager.getState().port
            let persistedPort = await database.userPreferencesRepository.soundSyncPort.firstValue()?.int32Value
            return managerPort == 15000 && persistedPort == 15000
        }
        #expect(viewModel.port == 15000)
    }

    @Test func startWhenPermissionDeniedOpensSettingsAndDoesNotStart() async {
        var didOpenSettings = false
        let manager = FakeSoundSyncManager(permission: .denied)
        let viewModel = makeViewModel(manager: manager) {
            didOpenSettings = true
        }

        await viewModel.start()

        #expect(didOpenSettings)
        #expect(!viewModel.isRunning)
        #expect(await manager.startCallCount == 0)
    }

    @Test func startWhenPermissionRequestRefusedDoesNotStart() async {
        let manager = FakeSoundSyncManager(
            permission: .undetermined,
            requestPermissionResult: false
        )
        let viewModel = makeViewModel(manager: manager)

        await viewModel.start()

        #expect(!viewModel.isRunning)
        #expect(await manager.startCallCount == 0)
    }

    @Test func startWhenManagerFailsToStartSetsErrorMessageAndKeepsRunningFalse() async {
        let manager = FakeSoundSyncManager(startResult: false)
        let viewModel = makeViewModel(manager: manager)

        await viewModel.start()

        #expect(!viewModel.isRunning)
        #expect(viewModel.errorMessage == "Capture failed")
    }

    @Test func startFiltersOutHiddenDevicesAndUpdatesRunningState() async throws {
        let visibleDevice = Shared.Device(
            macAddress: "aabbccddeeff",
            address: "192.168.1.50",
            isHidden: false
        )
        let hiddenDevice = Shared.Device(
            macAddress: "112233445566",
            address: "192.168.1.51",
            isHidden: true
        )
        try await database.deviceRepository.insert(device: visibleDevice)
        try await database.deviceRepository.insert(device: hiddenDevice)

        let manager = FakeSoundSyncManager()
        let viewModel = makeViewModel(manager: manager)

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

    @Test func stopResetsRunningState() async {
        let manager = FakeSoundSyncManager()
        let viewModel = makeViewModel(manager: manager)
        await viewModel.start()

        await viewModel.stop()

        #expect(!viewModel.isRunning)
    }

    private func makeViewModel(
        manager: FakeSoundSyncManager,
        openSettings: @escaping @MainActor () -> Void = {}
    ) -> SoundSyncViewModel {
        SoundSyncViewModel(
            soundSyncManager: manager,
            userPreferencesRepository: database.userPreferencesRepository,
            deviceRepository: database.deviceRepository,
            openSettings: openSettings
        )
    }

    private func runObserveUntil(
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
