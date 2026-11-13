import Foundation
import Observation
import OSLog
import Shared

@MainActor
@Observable
final class DeviceEditViewModel {
    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.illumidel",
        category: "DeviceEditViewModel"
    )

    private let database: AppDatabase
    private let deviceRepository: DeviceRepository
    private let customNameDebounceDuration: Duration

    @ObservationIgnored private var customNameDebounceTask: Task<Void, Never>?
    @ObservationIgnored private var languageSwitchTask: Task<Void, Never>?
    @ObservationIgnored private var isRevertingBranch = false

    var device: DeviceWithState

    var customName: String = "" {
        didSet {
            guard customName != oldValue else { return }
            scheduleCustomNameSave(customName)
        }
    }

    var hideDevice: Bool = false {
        didSet {
            guard hideDevice != oldValue, device.device.isHidden != hideDevice else { return }
            let isHidden = hideDevice
            device.device = device.device.copy(isHidden: isHidden)
            persist { repository, mac in
                try await repository.updateIsHidden(macAddress: mac, isHidden: isHidden)
            }
        }
    }

    var branch: Branch = .stable {
        didSet {
            let currentBranch: Branch = device.device.branch == .beta ? .beta : .stable
            guard !isRevertingBranch, branch != oldValue, currentBranch != branch else { return }
            handleLanguageChange(from: currentBranch, to: branch)
        }
    }

    var isCheckingForUpdates: Bool = false
    var isLoadingLanguageUpdate: Bool = false
    private(set) var installTargetBranch: Branch?
    var latestBetaVersion: VersionWithAssets?
    var installingVersion: VersionWithAssets?
    var showBetaDisclaimerSheet: Bool = false

    /// Non-nil when a newer prerelease Beta 2.0 firmware is available for this Illumidel controller.
    var availableBetaVersion: VersionWithAssets? {
        guard let beta = latestBetaVersion,
              ReleaseService.companion.isBetaUpdateAvailable(
                  deviceInfo: device.stateInfo?.info,
                  candidateTag: beta.version.tagName
              ) else {
            return nil
        }
        return beta
    }

    init(
        device: DeviceWithState,
        database: AppDatabase = .shared,
        customNameDebounceDuration: Duration = .milliseconds(500)
    ) {
        self.database = database
        self.deviceRepository = database.deviceRepository
        self.customNameDebounceDuration = customNameDebounceDuration
        self.device = device
        self.customName = device.device.customName
        self.hideDevice = device.device.isHidden
        self.branch = device.device.branch == .beta ? .beta : .stable
    }

    // MARK: - Form change listeners

    /// Debounces custom name edits before persisting to the repository.
    private func scheduleCustomNameSave(_ newCustomName: String) {
        customNameDebounceTask?.cancel()
        let duration = customNameDebounceDuration
        customNameDebounceTask = Task { [weak self] in
            do {
                try await Task.sleep(for: duration)
            } catch {
                return
            }
            guard let self, !Task.isCancelled else { return }
            guard self.device.device.customName != newCustomName else { return }

            self.device.device = self.device.device.copy(customName: newCustomName)
            self.persist { repository, mac in
                try await repository.updateCustomName(macAddress: mac, customName: newCustomName)
            }
        }
    }

    private func handleLanguageChange(from oldBranch: Branch, to newBranch: Branch) {
        languageSwitchTask?.cancel()
        installTargetBranch = nil

        guard device.isOnline,
              let info = device.stateInfo?.info,
              ReleaseService.companion.isDeviceEligibleForUpdate(deviceInfo: info),
              let currentVersion = info.version,
              !currentVersion.isEmpty else {
            setBranchWithoutTriggering(oldBranch)
            return
        }

        installTargetBranch = newBranch
        isLoadingLanguageUpdate = true
        languageSwitchTask = Task { [weak self] in
            guard let self else { return }
            let resolved = await self.resolveVersionForLanguageSwitch(tag: currentVersion, targetBranch: newBranch)
            guard !Task.isCancelled else { return }
            self.isLoadingLanguageUpdate = false
            if let resolved {
                self.installingVersion = resolved
            } else {
                self.revertLanguageSelectionInMemory()
            }
        }
    }

    private func resolveVersionForLanguageSwitch(tag: String, targetBranch: Branch) async -> VersionWithAssets? {
        await database.waitUntilReady()
        guard !Task.isCancelled else { return nil }
        let repoId = device.device.repositoryId
        if let cached = await lookupVersion(repositoryId: repoId, tag: tag) {
            return cached
        }
        guard !Task.isCancelled else { return nil }
        await database.refreshVersions(for: device.device.copy(branch: targetBranch))
        guard !Task.isCancelled else { return nil }
        return await lookupVersion(repositoryId: repoId, tag: tag)
    }

    private func lookupVersion(repositoryId: Int64, tag: String) async -> VersionWithAssets? {
        try? await database.versionWithAssetsRepository.getVersionByTag(
            repositoryId: repositoryId,
            tagName: tag.prefixedWithV
        )
    }

    func cancelLanguageSwitchLoading() {
        languageSwitchTask?.cancel()
        languageSwitchTask = nil
        isLoadingLanguageUpdate = false
        revertLanguageSelectionInMemory()
    }

    func syncBranchFromDevice(_ newBranch: Branch) {
        guard !isLoadingLanguageUpdate, installingVersion == nil, installTargetBranch == nil else { return }
        let normalized: Branch = newBranch == .beta ? .beta : .stable
        guard branch != normalized else { return }
        setBranchWithoutTriggering(normalized)
    }

    func handleInstallDialogDismissed(didComplete: Bool) {
        installingVersion = nil
        if didComplete {
            installTargetBranch = nil
        } else {
            revertLanguageSelectionInMemory()
        }
    }

    private func revertLanguageSelectionInMemory() {
        installTargetBranch = nil
        let currentBranch: Branch = device.device.branch == .beta ? .beta : .stable
        setBranchWithoutTriggering(currentBranch)
    }

    private func setBranchWithoutTriggering(_ targetBranch: Branch) {
        isRevertingBranch = true
        branch = targetBranch
        isRevertingBranch = false
    }

    // MARK: - Public API

    func observeBetaVersions(repositoryId: Int64) async {
        await database.waitUntilReady()
        let flow = database.versionWithAssetsRepository.getLatestBetaVersionWithAssetsFlow(
            repositoryId: repositoryId
        )
        for await betaVersion in flow {
            guard !Task.isCancelled else { break }
            latestBetaVersion = betaVersion
        }
    }

    func startBetaInstall() {
        guard let beta = availableBetaVersion else { return }
        languageSwitchTask?.cancel()
        languageSwitchTask = nil
        isLoadingLanguageUpdate = false
        revertLanguageSelectionInMemory()
        installingVersion = beta
    }

    func checkForUpdate() async {
        isCheckingForUpdates = true
        defer { isCheckingForUpdates = false }

        let mac = device.device.macAddress
        device.device = device.device.copy(skipUpdateTag: "")
        do {
            try await deviceRepository.updateSkipUpdateTag(macAddress: mac, skipUpdateTag: "")
        } catch {
            Self.logger.error("Failed to clear skipUpdateTag: \(error.localizedDescription)")
        }

        await database.refreshVersions(for: device.device)
    }

    /// Persists a single field so concurrent writers (e.g. websocket updates) can't overwrite it with a stale copy.
    private func persist(_ write: @escaping @Sendable (DeviceRepository, String) async throws -> Void) {
        let repository = deviceRepository
        let mac = device.device.macAddress
        Task {
            do {
                try await write(repository, mac)
            } catch {
                Self.logger.error("Unresolved error saving device: \(error.localizedDescription)")
            }
        }
    }
}
