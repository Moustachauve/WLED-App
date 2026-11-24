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
    private let performRefresh: @Sendable (Int64) async throws -> Bool

    var repositories: [Repository] = []
    var selectedRepositoryId: Int64
    var selectedBranch: Branch {
        didSet {
            guard selectedBranch != oldValue else { return }
            applyBranchFilter()
        }
    }
    var versions: [VersionWithAssets] = []
    var isLoadingVersions: Bool = false
    var loadError: Bool = false

    @ObservationIgnored private var rawVersions: [VersionWithAssets] = []
    @ObservationIgnored private var refreshedRepositoryIds: Set<Int64> = []
    @ObservationIgnored private var loadedRepositoryId: Int64?

    var selectedRepository: Repository? {
        repositories.first { $0.id == selectedRepositoryId }
    }

    var isSwitchingSource: Bool {
        selectedRepositoryId != device.device.repositoryId
    }

    init(
        device: DeviceWithState,
        database: AppDatabase = .shared,
        refreshRepositoryAction: (@Sendable (Int64) async throws -> Bool)? = nil
    ) {
        self.device = device
        self.database = database
        self.selectedRepositoryId = device.device.repositoryId
        self.selectedBranch = device.device.branch == .beta ? .beta : .stable
        self.performRefresh = refreshRepositoryAction ?? { repositoryId in
            let result = try await database.releaseService.refreshVersionsForRepositoryId(
                githubApi: database.githubApi,
                repositoryId: repositoryId
            )
            return result.boolValue
        }
    }

    func isInstalled(_ versionWithAssets: VersionWithAssets) -> Bool {
        versionWithAssets.matchesInstalledVersion(
            installedVersion: device.stateInfo?.info.version,
            deviceRepositoryId: device.device.repositoryId
        )
    }

    func observeRepositories() async {
        await database.waitUntilReady()
        for await allRepositories in database.releaseService.observeAllRepositories() {
            guard !Task.isCancelled else { break }
            let selectable = ReleaseService.companion.getSelectableRepositories(
                allRepositories: allRepositories,
                currentRepositoryId: device.device.repositoryId
            )
            repositories = selectable
            if !selectable.isEmpty && !selectable.contains(where: { $0.id == selectedRepositoryId }),
               let fallbackId = selectable.first?.id {
                selectedRepositoryId = fallbackId
            }
        }
    }

    func loadRepository() async {
        let repositoryId = selectedRepositoryId
        await database.waitUntilReady()
        guard !Task.isCancelled, selectedRepositoryId == repositoryId else { return }

        if loadedRepositoryId != repositoryId {
            loadedRepositoryId = repositoryId
            loadError = false
            rawVersions = []
            versions = []
        }
        isLoadingVersions = false

        await withTaskGroup(of: Void.self) { group in
            group.addTask { [weak self] in
                await self?.observeVersions(for: repositoryId)
            }
            group.addTask { [weak self] in
                await self?.refreshIfNeeded(for: repositoryId)
            }
        }
    }

    func refreshSelectedRepository() async {
        guard !isLoadingVersions else { return }
        await refreshRepository(selectedRepositoryId)
    }

    private func observeVersions(for repositoryId: Int64) async {
        for await cachedVersions in database.releaseService.observeVersionsForRepository(repositoryId: repositoryId) {
            guard !Task.isCancelled, selectedRepositoryId == repositoryId else { break }
            rawVersions = cachedVersions
            applyBranchFilter()
        }
    }

    private func refreshIfNeeded(for repositoryId: Int64) async {
        guard !refreshedRepositoryIds.contains(repositoryId) else { return }
        await refreshRepository(repositoryId)
    }

    private func refreshRepository(_ repositoryId: Int64) async {
        guard !Task.isCancelled, selectedRepositoryId == repositoryId else { return }
        isLoadingVersions = true
        loadError = false
        defer {
            if !Task.isCancelled, selectedRepositoryId == repositoryId {
                isLoadingVersions = false
            }
        }

        do {
            let success = try await performRefresh(repositoryId)
            guard !Task.isCancelled, selectedRepositoryId == repositoryId else { return }
            if success {
                refreshedRepositoryIds.insert(repositoryId)
            } else {
                loadError = true
            }
        } catch is CancellationError {
            return
        } catch {
            guard !Task.isCancelled, selectedRepositoryId == repositoryId else { return }
            Self.logger.error("Failed to refresh versions for repository \(repositoryId): \(error.localizedDescription)")
            loadError = true
        }
    }

    private func applyBranchFilter() {
        versions = VersionWithAssetsRepository.companion.filterAndSortVersions(
            versions: rawVersions,
            branch: selectedBranch
        )
    }
}
