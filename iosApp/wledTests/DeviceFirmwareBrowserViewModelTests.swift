import Foundation
import Shared
import Testing
@testable import WLED

@MainActor
struct DeviceFirmwareBrowserViewModelTests {

    struct BranchFilterCase: Sendable {
        let isBeta: Bool
        let expectedTags: [String]
    }

    struct InstalledMatchCase: Sendable {
        let repositoryId: Int64
        let tagName: String
        let assetNames: [String]
        let installedVersion: String
        let expected: Bool
    }

    let database: AppDatabase

    init() {
        self.database = AppDatabase(inMemory: true)
    }

    @discardableResult
    private func seedDefaultRepository(
        versions makeVersions: (Int64) -> [Version: [Asset]]
    ) async throws -> Repository {
        await database.waitUntilReady()
        let repo = try #require(
            try await database.versionWithAssetsRepository.getRepositoryById(
                id: Repository.companion.DEFAULT_ID
            )
        )
        try await database.versionWithAssetsRepository.updateRepository(
            repository: repo,
            versionToAssetsMap: makeVersions(repo.id)
        )
        return repo
    }

    private func makeDeviceWrapper(
        macAddress: String = "aabbcc112233",
        address: String = "192.168.1.20",
        originalName: String = "Strip",
        branch: Branch = .stable,
        repositoryId: Int64 = Repository.companion.DEFAULT_ID
    ) -> WLED.DeviceWithState {
        let base = Device(
            macAddress: macAddress,
            address: address,
            originalName: originalName,
            branch: branch
        )
        let device = repositoryId == Repository.companion.DEFAULT_ID
            ? base
            : base.copy(repositoryId: repositoryId)
        return DeviceWithState(initialDevice: device)
    }

    private func makeVersion(
        repositoryId: Int64,
        tagName: String,
        isPrerelease: Bool = false,
        publishedDate: String = "2025-05-01T00:00:00Z"
    ) -> Version {
        Version(
            id: 0,
            repositoryId: repositoryId,
            tagName: tagName,
            name: tagName,
            description: "Release notes for \(tagName)",
            isPrerelease: isPrerelease,
            publishedDate: publishedDate,
            htmlUrl: "https://github.com/wled/WLED/releases/tag/\(tagName)"
        )
    }

    @Test func initializesSelectedRepositoryAndBranchFromDevice() {
        let wrapper = makeDeviceWrapper(branch: .beta, repositoryId: 2)
        let viewModel = DeviceFirmwareBrowserViewModel(device: wrapper, database: database)

        #expect(viewModel.selectedRepositoryId == 2)
        #expect(viewModel.selectedBranch == .beta)
    }

    @Test func loadsBuiltInRepositoriesAsSelectableSources() async throws {
        let wrapper = makeDeviceWrapper(macAddress: "aabbcc112234", address: "192.168.1.21")
        let viewModel = DeviceFirmwareBrowserViewModel(device: wrapper, database: database)

        let task = Task {
            await viewModel.observeRepositories()
        }
        defer { task.cancel() }

        try await waitUntil {
            viewModel.repositories.count >= 3
        }
        let ownerRepos = viewModel.repositories.map(\.ownerAndRepo)
        #expect(ownerRepos.first == "wled/WLED")
        #expect(viewModel.repositories.first?.isOfficial == true)
        #expect(viewModel.repositories.dropFirst().allSatisfy { !$0.isOfficial })
        #expect(ownerRepos.contains("intermittech/QuinLED-Firmware"))
        #expect(ownerRepos.contains("MoonModules/WLED-MM"))
    }

    @Test(
        arguments: [
            BranchFilterCase(isBeta: false, expectedTags: ["v0.15.1", "v0.14.3"]),
            BranchFilterCase(isBeta: true, expectedTags: ["v14.5.0-beta", "v0.15.0-b2", "v0.14.0.b27"])
        ]
    )
    func filtersAndSortsVersionsDescendingForChannel(_ testCase: BranchFilterCase) async throws {
        let repo = try await seedDefaultRepository { repoId in
            [
                makeVersion(repositoryId: repoId, tagName: "nightly", isPrerelease: true): [],
                makeVersion(repositoryId: repoId, tagName: "v0.14.3"): [],
                makeVersion(repositoryId: repoId, tagName: "v0.15.1"): [],
                makeVersion(repositoryId: repoId, tagName: "v0.15.0-b2", isPrerelease: true): [],
                makeVersion(repositoryId: repoId, tagName: "v0.14.0.b27", isPrerelease: false): [],
                makeVersion(repositoryId: repoId, tagName: "v14.5.0-beta", isPrerelease: false): []
            ]
        }

        let wrapper = makeDeviceWrapper(
            macAddress: "aabbcc112235",
            address: "192.168.1.22",
            branch: testCase.isBeta ? .beta : .stable,
            repositoryId: repo.id
        )
        let viewModel = DeviceFirmwareBrowserViewModel(device: wrapper, database: database)
        let task = Task {
            await viewModel.observeVersions(for: repo.id)
        }
        defer { task.cancel() }

        try await waitUntil {
            !viewModel.filteredVersions.isEmpty
        }
        #expect(viewModel.filteredVersions.map(\.version.tagName) == testCase.expectedTags)
    }

    @Test(
        arguments: [
            InstalledMatchCase(
                repositoryId: 1,
                tagName: "v0.15.0",
                assetNames: [],
                installedVersion: "0.15.0",
                expected: true
            ),
            InstalledMatchCase(
                repositoryId: 1,
                tagName: "v0.15.0-rc.1",
                assetNames: ["WLED_0.15.0-rc1_ESP32.bin"],
                installedVersion: "0.15.0-rc1",
                expected: true
            ),
            InstalledMatchCase(
                repositoryId: 2,
                tagName: "v0.15.0",
                assetNames: [],
                installedVersion: "0.15.0",
                expected: false
            ),
            InstalledMatchCase(
                repositoryId: 1,
                tagName: "v0.14.4",
                assetNames: [],
                installedVersion: "0.15.0",
                expected: false
            )
        ]
    )
    func isInstalledRequiresMatchingTagOrAssetVersionAndRepository(_ testCase: InstalledMatchCase) {
        let wrapper = makeDeviceWrapper(macAddress: "aabbcc112236", address: "192.168.1.23")
        wrapper.stateInfo = DeviceStateInfo.companion.createMock(
            name: "Kitchen",
            version: testCase.installedVersion,
            red: 255,
            green: 160,
            blue: 0,
            brightness: 128,
            isOn: true
        )
        let viewModel = DeviceFirmwareBrowserViewModel(device: wrapper, database: database)
        let assets = testCase.assetNames.enumerated().map { index, name in
            Asset(
                id: Int64(index + 1),
                versionId: 1,
                name: name,
                size: 1_000_000,
                downloadUrl: "https://example.com/\(name)",
                assetId: Int32(index + 1)
            )
        }
        let item = VersionWithAssets(
            version: makeVersion(repositoryId: testCase.repositoryId, tagName: testCase.tagName),
            assets: assets
        )

        #expect(viewModel.isInstalled(item) == testCase.expected)
    }

    @Test func changingSelectedBranchRefiltersCachedVersions() async throws {
        let repo = try await seedDefaultRepository { repoId in
            [
                makeVersion(repositoryId: repoId, tagName: "v0.15.1"): [],
                makeVersion(repositoryId: repoId, tagName: "v0.15.0-b2", isPrerelease: true): []
            ]
        }
        let wrapper = makeDeviceWrapper(
            macAddress: "aabbcc112238",
            address: "192.168.1.25",
            repositoryId: repo.id
        )
        let viewModel = DeviceFirmwareBrowserViewModel(device: wrapper, database: database)
        let task = Task {
            await viewModel.observeVersions(for: repo.id)
        }
        defer { task.cancel() }

        try await waitUntil {
            viewModel.filteredVersions.map(\.version.tagName) == ["v0.15.1"]
        }

        viewModel.selectedBranch = .beta
        #expect(viewModel.filteredVersions.map(\.version.tagName) == ["v0.15.0-b2"])
    }

    @Test func changingSelectedRepositoryIdResetsCachedVersionsAndErrorState() async throws {
        let repo = try await seedDefaultRepository { repoId in
            [makeVersion(repositoryId: repoId, tagName: "v0.15.1"): []]
        }
        let wrapper = makeDeviceWrapper(
            macAddress: "aabbcc112239",
            address: "192.168.1.26",
            repositoryId: repo.id
        )
        let viewModel = DeviceFirmwareBrowserViewModel(device: wrapper, database: database)
        let task = Task {
            await viewModel.observeVersions(for: repo.id)
        }
        defer { task.cancel() }

        try await waitUntil {
            viewModel.hasLoadedCachedVersions && !viewModel.filteredVersions.isEmpty
        }
        viewModel.loadError = true
        task.cancel()

        viewModel.selectedRepositoryId = repo.id + 1

        #expect(viewModel.filteredVersions.isEmpty)
        #expect(viewModel.hasLoadedCachedVersions == false)
        #expect(viewModel.loadError == false)
    }
}
