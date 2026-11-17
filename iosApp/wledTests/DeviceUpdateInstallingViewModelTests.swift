import Foundation
import Shared
import Testing
@testable import WLED

@MainActor
struct DeviceUpdateInstallingViewModelTests {

    let host = "wled-\(UUID().uuidString.lowercased()).test"
    let database = AppDatabase(inMemory: true, deviceApiFactory: .stubbed)

    @Test func persistAndApplyInstalledFirmwarePersistsMetadataAndResolvesOwnerAndRepo() async throws {
        await database.waitUntilReady()
        let quinledRepo = try #require(
            try await database.repositoryDao.getRepositoryByOwnerAndRepo(
                ownerAndRepo: "intermittech/QuinLED-Firmware"
            )
        )
        let initialDevice = Device(
            macAddress: "aabbcc112237",
            address: "192.168.1.24",
            originalName: "Garage",
            skipUpdateTag: "v0.14.4"
        )
        try await database.deviceRepository.insert(device: initialDevice)

        let wrapper = DeviceWithState(initialDevice: initialDevice)
        wrapper.stateInfo = DeviceStateInfo.companion.createMock(
            name: "Garage",
            version: "0.14.3",
            red: 255,
            green: 160,
            blue: 0,
            brightness: 128,
            isOn: true
        )
        wrapper.availableUpdateVersion = "v0.15.0"

        let targetVersion = VersionWithAssets(
            version: Version(
                id: 0,
                repositoryId: quinledRepo.id,
                tagName: "V0.15.0",
                name: "0.15.0",
                description: "",
                isPrerelease: false,
                publishedDate: "2025-05-01T00:00:00Z",
                htmlUrl: "https://github.com/intermittech/QuinLED-Firmware/releases/tag/v0.15.0"
            ),
            assets: []
        )
        let installingVM = DeviceUpdateInstallingViewModel()

        await installingVM.persistAndApplyInstalledFirmware(
            device: wrapper,
            version: targetVersion,
            repositoryOwnerAndRepo: nil,
            database: database
        )

        #expect(wrapper.device.repositoryId == quinledRepo.id)
        #expect(wrapper.device.skipUpdateTag.isEmpty)
        #expect(wrapper.stateInfo?.info.version == "0.15.0")
        #expect(wrapper.stateInfo?.info.repository == "intermittech/QuinLED-Firmware")
        #expect(wrapper.availableUpdateVersion == nil)

        let persisted = try #require(
            try await database.deviceRepository.findDeviceByMacAddress(address: "aabbcc112237")
        )
        #expect(persisted.repositoryId == quinledRepo.id)
        #expect(persisted.skipUpdateTag.isEmpty)
    }

    private struct StagedQuinledFixture {
        let quinledRepo: Repository
        let wrapper: WLED.DeviceWithState
        let asset: Asset
        let targetVersion: VersionWithAssets
        let cachedDirectoryURL: URL
    }

    private func stageQuinledUpdateFixture() async throws -> StagedQuinledFixture {
        await database.waitUntilReady()
        let quinledRepo = try #require(
            try await database.repositoryDao.getRepositoryByOwnerAndRepo(
                ownerAndRepo: "intermittech/QuinLED-Firmware"
            )
        )
        let token = UUID().uuidString.lowercased()
        let initialDevice = Device(
            macAddress: "mac-\(token)",
            address: host,
            originalName: "Dig2Go"
        )
        try await database.deviceRepository.insert(device: initialDevice)
        let wrapper = DeviceWithState(initialDevice: initialDevice)
        let asset = Asset(
            id: 1,
            versionId: 10,
            name: "WLED_16.0.0_Dig2Go-Audioreactive.bin",
            size: 4,
            downloadUrl: "https://example.com/WLED_16.0.0_Dig2Go-Audioreactive.bin",
            assetId: 77
        )
        let targetVersion = VersionWithAssets(
            version: Version(
                id: 10,
                repositoryId: quinledRepo.id,
                tagName: "v16.0.0-\(token)",
                name: "16.0.0",
                description: "",
                isPrerelease: false,
                publishedDate: "2025-07-08T00:00:00Z",
                htmlUrl: "https://github.com/intermittech/QuinLED-Firmware/releases/tag/v16.0.0"
            ),
            assets: [asset]
        )

        let service = DeviceUpdateService.companion.create(
            device: wrapper.device,
            stateInfo: wrapper.stateInfo,
            versionWithAssets: targetVersion,
            deviceApiFactory: database.deviceApiFactory,
            githubApi: database.githubApi,
            selectedAsset: asset
        )
        let cachedURL = URL(fileURLWithPath: service.getPathForAsset().description)
        let cachedDirectoryURL = cachedURL.deletingLastPathComponent()
        try FileManager.default.createDirectory(
            at: cachedDirectoryURL,
            withIntermediateDirectories: true
        )
        try Data([0x01, 0x02, 0x03, 0x04]).write(to: cachedURL)

        return StagedQuinledFixture(
            quinledRepo: quinledRepo,
            wrapper: wrapper,
            asset: asset,
            targetVersion: targetVersion,
            cachedDirectoryURL: cachedDirectoryURL
        )
    }

    @Test func validationFailureExposesRetryFlagAndPlainTextError() async throws {
        let fixture = try await stageQuinledUpdateFixture()
        defer { try? FileManager.default.removeItem(at: fixture.cachedDirectoryURL) }

        let validationHtml = """
        <body><h2>Update failed!</h2>
        <h4>This firmware file is missing compatibility metadata.
        Enable 'Ignore firmware validation' to proceed anyway.</h4>
        <button>Back</button></body>
        """
        StubURLProtocol.register(.http(statusCode: 500, body: validationHtml), for: host)

        let installingVM = DeviceUpdateInstallingViewModel()
        await installingVM.startUpdateProcess(
            device: fixture.wrapper,
            version: fixture.targetVersion,
            selectedAsset: fixture.asset,
            repositoryOwnerAndRepo: fixture.quinledRepo.ownerAndRepo,
            skipValidation: false,
            database: database
        )

        #expect(
            installingVM.status == .failed(
                versionName: fixture.asset.name,
                error: "500: Update failed! This firmware file is missing compatibility metadata. Enable 'Ignore firmware validation' to proceed anyway.",
                canRetryWithoutValidation: true
            )
        )
    }

    @Test func skipValidationUpdateCompletesAndPersistsTargetRepository() async throws {
        let fixture = try await stageQuinledUpdateFixture()
        defer { try? FileManager.default.removeItem(at: fixture.cachedDirectoryURL) }
        StubURLProtocol.register(.http(statusCode: 200, body: "Update successful!"), for: host)

        let installingVM = DeviceUpdateInstallingViewModel()
        await installingVM.startUpdateProcess(
            device: fixture.wrapper,
            version: fixture.targetVersion,
            selectedAsset: fixture.asset,
            repositoryOwnerAndRepo: fixture.quinledRepo.ownerAndRepo,
            skipValidation: true,
            database: database
        )

        #expect(installingVM.status == .completed)
        #expect(fixture.wrapper.device.repositoryId == fixture.quinledRepo.id)
    }
}
