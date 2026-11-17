import Foundation
import Shared
import Testing
@testable import WLED

@MainActor
struct DeviceUpdateInstallingViewModelTests {

    let host = "wled-\(UUID().uuidString.lowercased()).test"
    let database = AppDatabase(inMemory: true, deviceApiFactory: .stubbed)

    private struct StagedQuinledFixture {
        let quinledRepo: Repository
        let wrapper: WLED.DeviceWithState
        let asset: Asset
        let targetVersion: VersionWithAssets
        let cacheDirectoryURL: URL
    }

    private func stageQuinledUpdateFixture() async throws -> StagedQuinledFixture {
        await database.waitUntilReady()
        let quinledRepo = try #require(
            try await database.repositoryDao.getRepositoryByOwnerAndRepo(
                ownerAndRepo: "intermittech/QuinLED-Firmware"
            )
        )
        let token = UUID().uuidString.lowercased()
        let tagName = "0.15.0-\(token)"
        let initialDevice = Device(macAddress: "mac-\(token)", address: host, originalName: "Dig2Go")
        try await database.deviceRepository.insert(device: initialDevice)
        let wrapper = DeviceWithState(initialDevice: initialDevice)
        let asset = Asset(
            id: 1,
            versionId: 10,
            name: "WLED_0.15.0_Dig2Go-Audioreactive.bin",
            size: 4,
            downloadUrl: "https://example.com/WLED_0.15.0_Dig2Go-Audioreactive.bin",
            assetId: 77
        )
        let targetVersion = VersionWithAssets(
            version: Version(
                id: 10,
                repositoryId: quinledRepo.id,
                tagName: tagName,
                name: "0.15.0",
                description: "",
                isPrerelease: false,
                publishedDate: "2025-05-01T00:00:00Z",
                htmlUrl: "https://github.com/intermittech/QuinLED-Firmware/releases/tag/v0.15.0"
            ),
            assets: [asset]
        )

        let cacheDirectoryURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("wled-ota-test-\(token)", isDirectory: true)
        let binaryDirectory = cacheDirectoryURL
            .appendingPathComponent("\(quinledRepo.id)", isDirectory: true)
            .appendingPathComponent(tagName, isDirectory: true)
        try FileManager.default.createDirectory(at: binaryDirectory, withIntermediateDirectories: true)
        try Data([0x01, 0x02, 0x03, 0x04]).write(to: binaryDirectory.appendingPathComponent(asset.name))

        return StagedQuinledFixture(
            quinledRepo: quinledRepo,
            wrapper: wrapper,
            asset: asset,
            targetVersion: targetVersion,
            cacheDirectoryURL: cacheDirectoryURL
        )
    }

    @Test func validationFailureExposesRetryFlagAndPlainTextError() async throws {
        let fixture = try await stageQuinledUpdateFixture()
        defer { try? FileManager.default.removeItem(at: fixture.cacheDirectoryURL) }

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
            skipValidation: false,
            database: database,
            cacheDirPath: fixture.cacheDirectoryURL.path
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
        defer { try? FileManager.default.removeItem(at: fixture.cacheDirectoryURL) }
        StubURLProtocol.register(.http(statusCode: 200, body: "Update successful!"), for: host)

        let installingVM = DeviceUpdateInstallingViewModel()
        await installingVM.startUpdateProcess(
            device: fixture.wrapper,
            version: fixture.targetVersion,
            selectedAsset: fixture.asset,
            skipValidation: true,
            database: database,
            cacheDirPath: fixture.cacheDirectoryURL.path
        )

        #expect(installingVM.status == .completed)
        #expect(fixture.wrapper.device.repositoryId == fixture.quinledRepo.id)
        let persisted = try await database.deviceRepository.findDeviceByMacAddress(
            address: fixture.wrapper.device.macAddress
        )
        #expect(persisted?.repositoryId == fixture.quinledRepo.id)
        let requests = StubURLProtocol.requests(for: host)
        #expect(requests.count == 1)
        let bodyString = requests.first?.httpBody.flatMap { String(data: $0, encoding: .utf8) } ?? ""
        #expect(bodyString.contains("name=\"skipValidation\""))
    }

    @Test func skipValidationPreventsInfiniteRetryPromptOnSecondValidationFailure() async throws {
        let fixture = try await stageQuinledUpdateFixture()
        defer { try? FileManager.default.removeItem(at: fixture.cacheDirectoryURL) }
        let validationHtml = """
        <body><h2>Update failed!</h2>
        <h4>This firmware file is missing compatibility metadata.
        Enable 'Ignore firmware validation' to proceed anyway.</h4></body>
        """
        StubURLProtocol.register(.http(statusCode: 500, body: validationHtml), for: host)

        let installingVM = DeviceUpdateInstallingViewModel()
        await installingVM.startUpdateProcess(
            device: fixture.wrapper,
            version: fixture.targetVersion,
            selectedAsset: fixture.asset,
            skipValidation: true,
            database: database,
            cacheDirPath: fixture.cacheDirectoryURL.path
        )

        #expect(
            installingVM.status == .failed(
                versionName: fixture.asset.name,
                error: "500: Update failed! This firmware file is missing compatibility metadata. Enable 'Ignore firmware validation' to proceed anyway.",
                canRetryWithoutValidation: false
            )
        )
    }

    @Test func nonValidationServerErrorDoesNotOfferRetryWithoutValidation() async throws {
        let fixture = try await stageQuinledUpdateFixture()
        defer { try? FileManager.default.removeItem(at: fixture.cacheDirectoryURL) }
        StubURLProtocol.register(
            .http(statusCode: 500, body: "<body><h2>Update failed!</h2><h4>Not enough space</h4></body>"),
            for: host
        )

        let installingVM = DeviceUpdateInstallingViewModel()
        await installingVM.startUpdateProcess(
            device: fixture.wrapper,
            version: fixture.targetVersion,
            selectedAsset: fixture.asset,
            skipValidation: false,
            database: database,
            cacheDirPath: fixture.cacheDirectoryURL.path
        )

        #expect(
            installingVM.status == .failed(
                versionName: fixture.asset.name,
                error: "500: Update failed! Not enough space",
                canRetryWithoutValidation: false
            )
        )
    }
}
