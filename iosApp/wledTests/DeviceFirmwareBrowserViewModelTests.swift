import Foundation
import Shared
import Synchronization
import Testing
@testable import WLED

private final class RefreshRecorder: Sendable {
    private struct State: Sendable {
        var recordedIds: [Int64] = []
        var queuedResults: [Bool]
    }

    private let state: Mutex<State>

    init(results: [Bool] = [true]) {
        self.state = Mutex(State(queuedResults: results))
    }

    var recordedIds: [Int64] {
        state.withLock { $0.recordedIds }
    }

    func nextResult(for repositoryId: Int64) -> Bool {
        state.withLock {
            $0.recordedIds.append(repositoryId)
            guard $0.queuedResults.count > 1 else {
                return $0.queuedResults.first ?? true
            }
            return $0.queuedResults.removeFirst()
        }
    }
}

@MainActor
struct DeviceFirmwareBrowserViewModelTests {

    let database = AppDatabase(inMemory: true)

    private struct RepoPair {
        let official: Repository
        let quinled: Repository
    }

    private func builtInRepositories() async throws -> RepoPair {
        await database.waitUntilReady()
        let official = try #require(
            try await database.repositoryDao.getRepositoryByOwnerAndRepo(ownerAndRepo: "wled/WLED")
        )
        let quinled = try #require(
            try await database.repositoryDao.getRepositoryByOwnerAndRepo(
                ownerAndRepo: "intermittech/QuinLED-Firmware"
            )
        )
        return RepoPair(official: official, quinled: quinled)
    }

    private func makeDevice(
        repositoryId: Int64,
        branch: Branch = .stable,
        installedVersion: String = "0.14.4"
    ) -> WLED.DeviceWithState {
        let device = Device(
            macAddress: "aabbcc\(UUID().uuidString.prefix(6))",
            address: "192.168.1.50",
            isHidden: false,
            originalName: "Living Room WLED",
            customName: "",
            skipUpdateTag: "",
            branch: branch,
            lastSeen: 0,
            repositoryId: repositoryId
        )
        let wrapper = WLED.DeviceWithState(initialDevice: device)
        wrapper.websocketStatus = .connected
        wrapper.stateInfo = DeviceStateInfo.companion.createMock(
            name: "Living Room WLED",
            version: installedVersion,
            red: 255,
            green: 160,
            blue: 0,
            brightness: 128,
            isOn: true
        )
        return wrapper
    }

    private func makeVersion(
        repositoryId: Int64,
        tagName: String,
        isPrerelease: Bool = false,
        publishedDate: String = "2025-01-01T00:00:00Z"
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

    private func seedVersions(
        for repository: Repository,
        versions: [Version]
    ) async throws {
        let map = Dictionary(uniqueKeysWithValues: versions.map { ($0, [Asset]()) })
        try await database.versionWithAssetsRepository.updateRepository(
            repository: repository,
            versionToAssetsMap: map
        )
    }

    private func startObservingRepositories(_ viewModel: DeviceFirmwareBrowserViewModel) -> Task<Void, Never> {
        Task {
            await viewModel.observeRepositories()
        }
    }

    @Test func initialStateDefaultsToDeviceRepositoryAndChannel() async throws {
        let repos = try await builtInRepositories()
        let device = makeDevice(repositoryId: repos.official.id, branch: .beta)
        let viewModel = DeviceFirmwareBrowserViewModel(
            device: device,
            database: database,
            refreshRepositoryAction: { _ in true }
        )

        #expect(viewModel.selectedRepositoryId == repos.official.id)
        #expect(viewModel.selectedBranch == .beta)
    }

    @Test func isInstalledMatchesOnlyDeviceRepositoryAndVersion() async throws {
        let repos = try await builtInRepositories()
        let device = makeDevice(repositoryId: repos.official.id, installedVersion: "0.14.4")
        let viewModel = DeviceFirmwareBrowserViewModel(
            device: device,
            database: database,
            refreshRepositoryAction: { _ in true }
        )

        let installedOfficial = VersionWithAssets(
            version: makeVersion(repositoryId: repos.official.id, tagName: "0.14.4"),
            assets: []
        )
        let newerOfficial = VersionWithAssets(
            version: makeVersion(repositoryId: repos.official.id, tagName: "0.15.0"),
            assets: []
        )
        let sameTagOtherRepo = VersionWithAssets(
            version: makeVersion(repositoryId: repos.quinled.id, tagName: "0.14.4"),
            assets: []
        )

        #expect(viewModel.isInstalled(installedOfficial))
        #expect(viewModel.isInstalled(newerOfficial) == false)
        #expect(viewModel.isInstalled(sameTagOtherRepo) == false)
    }

    @Test func switchingRepositoryUpdatesVersions() async throws {
        let repos = try await builtInRepositories()
        try await seedVersions(
            for: repos.official,
            versions: [makeVersion(repositoryId: repos.official.id, tagName: "0.14.4")]
        )
        try await seedVersions(
            for: repos.quinled,
            versions: [makeVersion(repositoryId: repos.quinled.id, tagName: "0.15.0")]
        )

        let device = makeDevice(repositoryId: repos.official.id)
        let viewModel = DeviceFirmwareBrowserViewModel(
            device: device,
            database: database,
            refreshRepositoryAction: { _ in true }
        )

        viewModel.selectedRepositoryId = repos.quinled.id
        let loadTask = Task { await viewModel.loadRepository(repos.quinled.id) }
        defer { loadTask.cancel() }

        try await waitUntil {
            viewModel.versions.map(\.version.tagName) == ["0.15.0"]
        }
        #expect(viewModel.versions.map(\.version.tagName) == ["0.15.0"])
    }

    @Test func reselectingRepositoryRefreshesEachSourceOnce() async throws {
        let repos = try await builtInRepositories()
        try await seedVersions(
            for: repos.official,
            versions: [makeVersion(repositoryId: repos.official.id, tagName: "0.14.4")]
        )
        try await seedVersions(
            for: repos.quinled,
            versions: [makeVersion(repositoryId: repos.quinled.id, tagName: "0.15.0")]
        )
        let recorder = RefreshRecorder()
        let device = makeDevice(repositoryId: repos.official.id)
        let viewModel = DeviceFirmwareBrowserViewModel(
            device: device,
            database: database,
            refreshRepositoryAction: { recorder.nextResult(for: $0) }
        )

        let steps: [(Int64, String)] = [
            (repos.official.id, "0.14.4"),
            (repos.quinled.id, "0.15.0"),
            (repos.official.id, "0.14.4")
        ]
        for (repoId, expectedTag) in steps {
            viewModel.selectedRepositoryId = repoId
            let task = Task { await viewModel.loadRepository(repoId) }
            try await waitUntil {
                recorder.recordedIds.contains(repoId) &&
                    !viewModel.isLoadingVersions &&
                    viewModel.versions.map(\.version.tagName) == [expectedTag]
            }
            task.cancel()
        }

        #expect(recorder.recordedIds == [repos.official.id, repos.quinled.id])
    }

    @Test func failedRefreshSetsLoadError() async throws {
        let repos = try await builtInRepositories()
        let recorder = RefreshRecorder(results: [false])
        let device = makeDevice(repositoryId: repos.official.id)
        let viewModel = DeviceFirmwareBrowserViewModel(
            device: device,
            database: database,
            refreshRepositoryAction: { recorder.nextResult(for: $0) }
        )

        let loadTask = Task { await viewModel.loadRepository(repos.official.id) }
        defer { loadTask.cancel() }

        try await waitUntil {
            recorder.recordedIds.count == 1 && !viewModel.isLoadingVersions
        }
        #expect(viewModel.loadError)
    }

    @Test func manualRetryClearsLoadError() async throws {
        let repos = try await builtInRepositories()
        let recorder = RefreshRecorder(results: [false, true])
        let device = makeDevice(repositoryId: repos.official.id)
        let viewModel = DeviceFirmwareBrowserViewModel(
            device: device,
            database: database,
            refreshRepositoryAction: { recorder.nextResult(for: $0) }
        )

        let loadTask = Task { await viewModel.loadRepository(repos.official.id) }
        defer { loadTask.cancel() }

        try await waitUntil {
            recorder.recordedIds.count == 1 && !viewModel.isLoadingVersions
        }
        try #require(viewModel.loadError)
        await viewModel.refreshSelectedRepository()

        #expect(viewModel.loadError == false)
        #expect(recorder.recordedIds == [repos.official.id, repos.official.id])
    }

    private func mixedChannelVersions(repositoryId: Int64) -> [Version] {
        [
            makeVersion(repositoryId: repositoryId, tagName: "0.14.4", isPrerelease: false),
            makeVersion(repositoryId: repositoryId, tagName: "0.15.0", isPrerelease: false),
            makeVersion(repositoryId: repositoryId, tagName: "0.14.0.b27", isPrerelease: false),
            makeVersion(repositoryId: repositoryId, tagName: "0.15.1-beta.1", isPrerelease: false),
            makeVersion(repositoryId: repositoryId, tagName: "0.15.0-b2", isPrerelease: true)
        ]
    }

    @Test func stableBranchExcludesBetaAndDotPrereleasesSortedDescending() async throws {
        let repos = try await builtInRepositories()
        try await seedVersions(for: repos.official, versions: mixedChannelVersions(repositoryId: repos.official.id))

        let device = makeDevice(repositoryId: repos.official.id, branch: .stable)
        let viewModel = DeviceFirmwareBrowserViewModel(
            device: device,
            database: database,
            refreshRepositoryAction: { _ in true }
        )

        let loadTask = Task { await viewModel.loadRepository(repos.official.id) }
        defer { loadTask.cancel() }

        try await waitUntil { !viewModel.versions.isEmpty }
        #expect(viewModel.versions.map(\.version.tagName) == ["0.15.0", "0.14.4"])
    }

    @Test func betaBranchIncludesUnflaggedPrereleasesSortedDescending() async throws {
        let repos = try await builtInRepositories()
        try await seedVersions(for: repos.official, versions: mixedChannelVersions(repositoryId: repos.official.id))

        let device = makeDevice(repositoryId: repos.official.id, branch: .stable)
        let viewModel = DeviceFirmwareBrowserViewModel(
            device: device,
            database: database,
            refreshRepositoryAction: { _ in true }
        )

        let loadTask = Task { await viewModel.loadRepository(repos.official.id) }
        defer { loadTask.cancel() }

        try await waitUntil { !viewModel.versions.isEmpty }
        viewModel.selectedBranch = .beta

        #expect(viewModel.versions.map(\.version.tagName) == ["0.15.1-beta.1", "0.15.0-b2", "0.14.0.b27"])
    }

    @Test func isSwitchingSourceIsTrueOnlyForDifferentSelectedRepository() async throws {
        let repos = try await builtInRepositories()
        let device = makeDevice(repositoryId: repos.official.id)
        let viewModel = DeviceFirmwareBrowserViewModel(
            device: device,
            database: database,
            refreshRepositoryAction: { _ in true }
        )
        let repoTask = startObservingRepositories(viewModel)
        defer { repoTask.cancel() }

        try await waitUntil { !viewModel.repositories.isEmpty }
        #expect(viewModel.isSwitchingSource == false)

        viewModel.selectedRepositoryId = repos.quinled.id
        #expect(viewModel.isSwitchingSource)
    }
}
