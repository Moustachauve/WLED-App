import Foundation
import Observation
import OSLog
import Shared

@MainActor
@Observable
final class DeviceFirmwareBrowserViewModel {
    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "DeviceFirmwareBrowserViewModel"
    )

    let device: DeviceWithState
    private let database: AppDatabase

    var repositories: [Repository] = []
    var selectedRepositoryId: Int64 {
        didSet {
            guard oldValue != selectedRepositoryId else { return }
            activeRefreshToken &+= 1
            refreshTask?.cancel()
            refreshTask = nil
            hasLoadedCachedVersions = false
            isLoadingVersions = false
            loadError = false
            rawVersions = []
        }
    }
    var selectedBranch: Branch {
        didSet {
            guard oldValue != selectedBranch else { return }
            recomputeFilteredVersions()
        }
    }
    private(set) var filteredVersions: [VersionWithAssets] = []
    private(set) var hasLoadedCachedVersions: Bool = false
    var isLoadingVersions: Bool = false
    var loadError: Bool = false

    @ObservationIgnored private var rawVersions: [VersionWithAssets] = [] {
        didSet {
            recomputeFilteredVersions()
        }
    }
    @ObservationIgnored private var autoFetchedRepoIds: Set<Int64> = []
    @ObservationIgnored private var refreshTask: Task<Void, Never>?
    @ObservationIgnored private var activeRefreshToken: UInt64 = 0

    var selectedRepository: Repository? {
        repositories.first { $0.id == selectedRepositoryId }
    }

    init(
        device: DeviceWithState,
        database: AppDatabase = .shared
    ) {
        self.device = device
        self.database = database
        self.selectedRepositoryId = device.device.repositoryId
        self.selectedBranch = device.device.branch == .beta ? .beta : .stable
    }

    isolated deinit {
        refreshTask?.cancel()
    }

    func isInstalled(_ item: VersionWithAssets) -> Bool {
        item.matchesInstalledVersion(
            installedVersion: device.stateInfo?.info.version,
            deviceRepositoryId: device.device.repositoryId
        )
    }

    func observeRepositories() async {
        await database.waitUntilReady()
        for await allRepos in database.versionWithAssetsRepository.observeAllRepositories() {
            guard !Task.isCancelled else { break }
            let selectable = ReleaseService.companion.getSelectableRepositories(
                allRepositories: allRepos,
                currentRepositoryId: device.device.repositoryId
            )
            repositories = selectable
            if !selectable.isEmpty && !selectable.contains(where: { $0.id == selectedRepositoryId }) {
                selectedRepositoryId = selectable[0].id
            }
        }
    }

    func observeVersions(for repositoryId: Int64) async {
        await database.waitUntilReady()
        for await versions in database.versionWithAssetsRepository.observeVersionsForRepository(
            repositoryId: repositoryId
        ) {
            guard !Task.isCancelled, selectedRepositoryId == repositoryId else { break }
            rawVersions = versions
            hasLoadedCachedVersions = true
            if versions.isEmpty && !isLoadingVersions && !autoFetchedRepoIds.contains(repositoryId) {
                startRefresh(for: repositoryId)
            }
        }
    }

    func refreshSelectedRepository() {
        startRefresh(for: selectedRepositoryId)
    }

    private func recomputeFilteredVersions() {
        filteredVersions = VersionWithAssetsRepository.companion.filterAndSortVersions(
            versions: rawVersions,
            branch: selectedBranch
        )
    }

    private func startRefresh(for repositoryId: Int64) {
        refreshTask?.cancel()
        activeRefreshToken &+= 1
        let token = activeRefreshToken
        isLoadingVersions = true
        loadError = false

        refreshTask = Task { [weak self] in
            guard let self, !Task.isCancelled else { return }
            await self.performRefresh(for: repositoryId, token: token)
        }
    }

    private func performRefresh(
        for repositoryId: Int64,
        token: UInt64
    ) async {
        defer {
            if activeRefreshToken == token {
                isLoadingVersions = false
            }
        }

        do {
            await database.waitUntilReady()
            guard !Task.isCancelled, activeRefreshToken == token else { return }
            guard let repo = try await database.versionWithAssetsRepository.getRepositoryById(
                id: repositoryId
            ) else {
                if activeRefreshToken == token {
                    loadError = true
                }
                return
            }
            let succeeded = try await database.releaseService.refreshVersionsForRepository(
                githubApi: database.githubApi,
                repository: repo
            )
            guard !Task.isCancelled, activeRefreshToken == token else { return }
            loadError = !succeeded.boolValue
            if succeeded.boolValue {
                autoFetchedRepoIds.insert(repositoryId)
                database.deviceConnectionManager.recheckUpdates()
            }
        } catch {
            if error is CancellationError || Task.isCancelled || activeRefreshToken != token {
                return
            }
            Self.logger.error("Failed to refresh versions: \(error.localizedDescription)")
            loadError = true
        }
    }
}
