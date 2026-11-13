import Testing
import Foundation
import Shared
@testable import Illumidel

@MainActor
struct DeviceEditViewModelTests {

    let database: AppDatabase
    var repository: DeviceRepository { database.deviceRepository }

    init() {
        self.database = AppDatabase(inMemory: true)
    }

    @Test func debouncesCustomNameUpdatesAndPersistsFinalValue() async throws {
        let initial = Device(
            macAddress: "aabbcc000001",
            address: "192.168.1.10",
            originalName: "Living Room",
            customName: ""
        )
        try await repository.insert(device: initial)
        let wrapper = DeviceWithState(initialDevice: initial)
        let viewModel = DeviceEditViewModel(
            device: wrapper,
            database: database,
            customNameDebounceDuration: .milliseconds(40)
        )

        viewModel.customName = "First"
        viewModel.customName = "Final Name"

        try await waitUntil {
            try await repository.findDeviceByMacAddress(address: "aabbcc000001")?.customName == "Final Name"
        }
        #expect(wrapper.device.customName == "Final Name")
    }

    @Test func togglingHideDevicePersistsImmediately() async throws {
        let initial = Device(
            macAddress: "aabbcc000002",
            address: "192.168.1.11",
            isHidden: false,
            originalName: "Kitchen"
        )
        try await repository.insert(device: initial)
        let wrapper = DeviceWithState(initialDevice: initial)
        let viewModel = DeviceEditViewModel(device: wrapper, database: database)

        viewModel.hideDevice = true

        try await waitUntil {
            try await repository.findDeviceByMacAddress(address: "aabbcc000002")?.isHidden == true
        }
        #expect(wrapper.device.isHidden == true)
    }

    struct BetaEligibilityCase: Sendable, CustomTestStringConvertible {
        let label: String
        let brand: String?
        let version: String
        let options: Int32?
        let expectedTag: String?

        var testDescription: String { label }
    }

    @Test func changingBranchWhenIneligibleRevertsWithoutPersisting() async throws {
        let initial = Device(
            macAddress: "aabbcc000003",
            address: "192.168.1.12",
            originalName: "Desk",
            skipUpdateTag: "1.5.2",
            branch: .stable
        )
        try await repository.insert(device: initial)

        let offlineWrapper = DeviceWithState(initialDevice: initial)
        let nonIllumidelWrapper = DeviceWithState(initialDevice: initial)
        nonIllumidelWrapper.websocketStatus = .connected
        nonIllumidelWrapper.stateInfo = DeviceStateInfo.companion.createMock(
            name: "WLED Strip",
            version: "1.5.2",
            red: 255,
            green: 255,
            blue: 255,
            brightness: 128,
            isOn: true
        ).withBrand(brand: "WLED")

        let otaLockedWrapper = DeviceWithState(initialDevice: initial)
        otaLockedWrapper.websocketStatus = .connected
        otaLockedWrapper.stateInfo = DeviceStateInfo.companion.createMock(
            name: "Illumidel Locked",
            version: "1.5.2",
            red: 255,
            green: 255,
            blue: 255,
            brightness: 128,
            isOn: true
        ).withBrand(brand: "ILLUMIDEL").withOptions(options: KotlinInt(int: 0))

        for wrapper in [offlineWrapper, nonIllumidelWrapper, otaLockedWrapper] {
            let viewModel = DeviceEditViewModel(device: wrapper, database: database)
            viewModel.branch = .beta

            #expect(viewModel.branch == .stable)
            #expect(viewModel.installTargetBranch == nil)
            #expect(wrapper.device.branch == .stable)
        }

        let stored = try await repository.findDeviceByMacAddress(address: "aabbcc000003")
        #expect(stored?.branch == .stable)
        #expect(stored?.skipUpdateTag == "1.5.2")
    }

    @Test(arguments: [
        BetaEligibilityCase(
            label: "non-Illumidel brand is ineligible",
            brand: "WLED",
            version: "1.0.2",
            options: 1,
            expectedTag: nil
        ),
        BetaEligibilityCase(
            label: "OTA-disabled Illumidel device is ineligible",
            brand: "ILLUMIDEL",
            version: "1.0.2",
            options: 0,
            expectedTag: nil
        ),
        BetaEligibilityCase(
            label: "older OTA-enabled Illumidel device returns beta",
            brand: "ILLUMIDEL",
            version: "1.0.2",
            options: 1,
            expectedTag: "v2.0.0-beta.1"
        ),
        BetaEligibilityCase(
            label: "already-updated Illumidel device is ineligible",
            brand: "ILLUMIDEL",
            version: "2.0.0",
            options: 1,
            expectedTag: nil
        )
    ])
    func availableBetaVersionChecksEligibility(_ testCase: BetaEligibilityCase) {
        let initial = Device(
            macAddress: "aabbcc000005",
            address: "192.168.1.14",
            originalName: "Illumidel Patio"
        )
        let wrapper = DeviceWithState(initialDevice: initial)
        let viewModel = DeviceEditViewModel(device: wrapper, database: database)
        viewModel.latestBetaVersion = VersionWithAssets(
            version: Version(
                id: 10,
                repositoryId: Repository.companion.DEFAULT_ID,
                tagName: "v2.0.0-beta.1",
                name: "2.0.0 Beta 1",
                description: "Beta release",
                isPrerelease: true,
                publishedDate: "2026-01-01T00:00:00Z",
                htmlUrl: "https://github.com/Illumidel/App-Releases/releases/tag/v2.0.0-beta.1"
            ),
            assets: []
        )

        var stateInfo = DeviceStateInfo.companion.createMock(
            name: "Illumidel Patio",
            version: testCase.version,
            red: 255,
            green: 255,
            blue: 255,
            brightness: 128,
            isOn: true
        ).withBrand(brand: testCase.brand)
        if let options = testCase.options {
            stateInfo = stateInfo.withOptions(options: KotlinInt(int: options))
        }
        wrapper.stateInfo = stateInfo

        #expect(viewModel.availableBetaVersion?.version.tagName == testCase.expectedTag)
    }

    @Test func languageSwitchResolvesCachedVersionAndStagesBranchWithoutMutatingRoom() async throws {
        let wrapper = try await seedLanguageSwitchFixture(macAddress: "aabbcc000006")
        let viewModel = DeviceEditViewModel(device: wrapper, database: database)

        viewModel.branch = .beta

        try await waitUntil { viewModel.installingVersion != nil }
        #expect(viewModel.installingVersion?.version.tagName == "v1.5.3")
        #expect(viewModel.installTargetBranch == .beta)
        #expect(wrapper.device.branch == .stable)
        let stored = try await repository.findDeviceByMacAddress(address: "aabbcc000006")
        #expect(stored?.branch == .stable)
        #expect(stored?.skipUpdateTag == "1.5.0")
    }

    @Test func cancellingLanguageSwitchInstallRevertsSelectionAndPreservesSkipUpdateTag() async throws {
        let wrapper = try await seedLanguageSwitchFixture(macAddress: "aabbcc000007")
        let viewModel = DeviceEditViewModel(device: wrapper, database: database)
        viewModel.branch = .beta
        try await waitUntil { viewModel.installingVersion != nil }

        viewModel.handleInstallDialogDismissed(didComplete: false)

        #expect(viewModel.installingVersion == nil)
        #expect(viewModel.installTargetBranch == nil)
        #expect(viewModel.branch == .stable)
        #expect(wrapper.device.branch == .stable)
        let stored = try await repository.findDeviceByMacAddress(address: "aabbcc000007")
        #expect(stored?.branch == .stable)
        #expect(stored?.skipUpdateTag == "1.5.0")
    }

    @Test func cancelLanguageSwitchLoadingRevertsSelectionAndPreservesSkipUpdateTag() async throws {
        let wrapper = try await seedLanguageSwitchFixture(macAddress: "aabbcc000008")
        let viewModel = DeviceEditViewModel(device: wrapper, database: database)
        viewModel.branch = .beta

        viewModel.cancelLanguageSwitchLoading()

        #expect(!viewModel.isLoadingLanguageUpdate)
        #expect(viewModel.installTargetBranch == nil)
        #expect(viewModel.branch == .stable)
        let stored = try await repository.findDeviceByMacAddress(address: "aabbcc000008")
        #expect(stored?.branch == .stable)
        #expect(stored?.skipUpdateTag == "1.5.0")
    }

    private func seedLanguageSwitchFixture(macAddress: String) async throws -> Illumidel.DeviceWithState {
        await database.waitUntilReady()
        let repo = try #require(
            try await database.repositoryDao.getRepositoryById(id: Repository.companion.DEFAULT_ID)
        )
        let version = Version(
            id: 0,
            repositoryId: repo.id,
            tagName: "v1.5.3",
            name: "v1.5.3",
            description: "Release 1.5.3",
            isPrerelease: false,
            publishedDate: "2026-01-01T00:00:00Z",
            htmlUrl: "https://github.com/Illumidel/App-Releases/releases/tag/v1.5.3"
        )
        try await database.versionWithAssetsRepository.updateRepository(
            repository: repo,
            versionToAssetsMap: [version: []]
        )

        let initial = Device(
            macAddress: macAddress,
            address: "192.168.1.15",
            originalName: "Garage",
            skipUpdateTag: "1.5.0",
            branch: .stable
        )
        try await repository.insert(device: initial)
        let wrapper = DeviceWithState(initialDevice: initial)
        wrapper.websocketStatus = .connected
        wrapper.stateInfo = DeviceStateInfo.companion.createMock(
            name: "Garage",
            version: "1.5.3",
            red: 255,
            green: 160,
            blue: 0,
            brightness: 128,
            isOn: true
        ).withBrand(brand: "ILLUMIDEL")
        return wrapper
    }
}
