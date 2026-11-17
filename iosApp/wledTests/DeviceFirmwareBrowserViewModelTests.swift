import Foundation
import Shared
import Testing
@testable import WLED

@MainActor
struct DeviceFirmwareBrowserViewModelTests {

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
            try await database.repositoryDao.getRepositoryById(
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
        branch: Branch = .stable,
        repositoryId: Int64 = Repository.companion.DEFAULT_ID
    ) -> WLED.DeviceWithState {
        let base = Device(
            macAddress: macAddress,
            address: "192.168.1.20",
            originalName: "Strip",
            branch: branch
        )
        let device = repositoryId == Repository.companion.DEFAULT_ID ? base : base.copy(repositoryId: repositoryId)
        return DeviceWithState(initialDevice: device)
    }

    private func makeVersion(repositoryId: Int64, tagName: String, isPrerelease: Bool = false) -> Version {
        Version(
            id: 0,
            repositoryId: repositoryId,
            tagName: tagName,
            name: tagName,
            description: "",
            isPrerelease: isPrerelease,
            publishedDate: "2025-05-01T00:00:00Z",
            htmlUrl: "https://github.com/wled/WLED/releases/tag/\(tagName)"
        )
    }

    @Test func initializesSelectedRepositoryAndBranchFromDevice() {
        let wrapper = makeDeviceWrapper(branch: .beta, repositoryId: 2)
        let viewModel = DeviceFirmwareBrowserViewModel(device: wrapper, database: database)

        #expect(viewModel.selectedRepositoryId == 2)
        #expect(viewModel.selectedBranch == .beta)
    }

    private func seedQuinledBetaRelease() async throws -> Repository {
        let quinledRepo = try #require(
            try await database.repositoryDao.getRepositoryByOwnerAndRepo(
                ownerAndRepo: "intermittech/QuinLED-Firmware"
            )
        )
        try await database.versionWithAssetsRepository.updateRepository(
            repository: quinledRepo,
            versionToAssetsMap: [
                makeVersion(repositoryId: quinledRepo.id, tagName: "0.15.4"): [],
                makeVersion(repositoryId: quinledRepo.id, tagName: "0.16.0-b1", isPrerelease: true): []
            ]
        )
        return quinledRepo
    }

    @Test func switchingSelectedRepositoryUpdatesVersionsForNewSource() async throws {
        let repo = try await seedDefaultRepository { repoId in
            [makeVersion(repositoryId: repoId, tagName: "0.15.1"): []]
        }
        let quinledRepo = try await seedQuinledBetaRelease()

        let viewModel = DeviceFirmwareBrowserViewModel(
            device: makeDeviceWrapper(macAddress: "aabbcc112239", repositoryId: repo.id),
            database: database
        )
        let task = Task { await viewModel.observe() }
        defer { task.cancel() }

        try await waitUntil {
            !viewModel.isLoadingVersions && viewModel.filteredVersions.map(\.version.tagName) == ["0.15.1"]
        }

        viewModel.selectedRepositoryId = quinledRepo.id
        try await waitUntil {
            viewModel.filteredVersions.map(\.version.tagName) == ["0.15.4"] && !viewModel.loadError
        }
    }

    @Test func preservesSelectionsAppliedBeforeObserveStarts() async throws {
        let repo = try await seedDefaultRepository { repoId in
            [makeVersion(repositoryId: repoId, tagName: "0.15.1"): []]
        }
        let quinledRepo = try await seedQuinledBetaRelease()

        let viewModel = DeviceFirmwareBrowserViewModel(
            device: makeDeviceWrapper(macAddress: "aabbcc11223b", repositoryId: repo.id),
            database: database
        )
        viewModel.selectedRepositoryId = quinledRepo.id
        viewModel.selectedBranch = .beta

        let task = Task { await viewModel.observe() }
        defer { task.cancel() }

        try await waitUntil {
            viewModel.selectedRepositoryId == quinledRepo.id &&
                viewModel.selectedBranch == .beta &&
                viewModel.filteredVersions.map(\.version.tagName) == ["0.16.0-b1"]
        }
    }

    @Test func restartingObservePreservesSwitchedSourceAndChannel() async throws {
        let repo = try await seedDefaultRepository { repoId in
            [makeVersion(repositoryId: repoId, tagName: "0.15.1"): []]
        }
        let quinledRepo = try await seedQuinledBetaRelease()
        let viewModel = DeviceFirmwareBrowserViewModel(
            device: makeDeviceWrapper(macAddress: "aabbcc11223c", repositoryId: repo.id),
            database: database
        )

        let firstObserve = Task { await viewModel.observe() }
        try await waitUntil {
            !viewModel.isLoadingVersions && viewModel.filteredVersions.map(\.version.tagName) == ["0.15.1"]
        }
        viewModel.selectedRepositoryId = quinledRepo.id
        viewModel.selectedBranch = .beta
        try await waitUntil {
            viewModel.filteredVersions.map(\.version.tagName) == ["0.16.0-b1"]
        }
        firstObserve.cancel()
        _ = await firstObserve.result

        let secondObserve = Task { await viewModel.observe() }
        defer { secondObserve.cancel() }
        try await waitUntil {
            viewModel.selectedRepositoryId == quinledRepo.id &&
                viewModel.selectedBranch == .beta &&
                viewModel.filteredVersions.map(\.version.tagName) == ["0.16.0-b1"]
        }
    }
}
