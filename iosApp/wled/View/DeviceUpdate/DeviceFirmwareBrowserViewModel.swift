import Foundation
import Observation
import Shared

@MainActor
@Observable
final class DeviceFirmwareBrowserViewModel {
    let device: DeviceWithState
    private let database: AppDatabase

    var repositories: [Repository] = []
    var selectedRepositoryId: Int64 {
        didSet {
            guard !isSyncingState, oldValue != selectedRepositoryId else { return }
            pendingRepositoryId = selectedRepositoryId
            stateHolder?.selectRepository(repositoryId: selectedRepositoryId)
        }
    }
    var selectedBranch: Branch {
        didSet {
            guard !isSyncingState, oldValue != selectedBranch else { return }
            pendingBranch = selectedBranch
            stateHolder?.selectBranch(branch: selectedBranch)
        }
    }
    private(set) var filteredVersions: [VersionWithAssets] = []
    private(set) var isLoadingVersions: Bool = true
    private(set) var loadError: Bool = false

    @ObservationIgnored private var stateHolder: FirmwareBrowserStateHolder?
    @ObservationIgnored private var isSyncingState = false
    @ObservationIgnored private var pendingRepositoryId: Int64?
    @ObservationIgnored private var pendingBranch: Branch?

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
        stateHolder?.close()
    }

    func observe() async {
        let holder = await database.makeFirmwareBrowserStateHolder(device: device.device)
        defer {
            holder.close()
            if stateHolder === holder {
                stateHolder = nil
            }
        }
        guard !Task.isCancelled else { return }
        stateHolder = holder

        let targetRepoId = selectedRepositoryId
        let targetBranch = selectedBranch
        pendingRepositoryId = targetRepoId
        pendingBranch = targetBranch
        holder.selectRepository(repositoryId: targetRepoId)
        holder.selectBranch(branch: targetBranch)

        for await state in holder.state {
            guard !Task.isCancelled else { break }
            repositories = state.repositories
            filteredVersions = state.versions
            isLoadingVersions = state.isLoadingVersions
            loadError = state.loadError
            syncSelections(from: state)
        }
    }

    private func syncSelections(from state: FirmwareBrowserState) {
        isSyncingState = true
        defer { isSyncingState = false }

        if let stateRepoId = state.selectedRepositoryId?.int64Value {
            if let pending = pendingRepositoryId {
                let pendingUnavailable = !state.repositories.isEmpty &&
                    !state.repositories.contains { $0.id == pending }
                if stateRepoId == pending || pendingUnavailable {
                    pendingRepositoryId = nil
                }
            }
            if pendingRepositoryId == nil && selectedRepositoryId != stateRepoId {
                selectedRepositoryId = stateRepoId
            }
        }

        if let pending = pendingBranch, state.selectedBranch == pending {
            pendingBranch = nil
        }
        if pendingBranch == nil && selectedBranch != state.selectedBranch {
            selectedBranch = state.selectedBranch
        }
    }

    func refreshSelectedRepository() {
        stateHolder?.refreshSelectedRepository()
    }
}
