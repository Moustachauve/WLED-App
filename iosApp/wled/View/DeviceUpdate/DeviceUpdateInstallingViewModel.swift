//
//  DeviceUpdateInstallingViewModel.swift
//  WLED
//
//  Created by Christophe Gagnier on 2025-12-22.
//

import Foundation
import Observation
import OSLog
import Shared

@MainActor
@Observable
final class DeviceUpdateInstallingViewModel {
    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "DeviceUpdateInstallingViewModel"
    )

    enum UpdateStatus: Equatable {
        case idle
        case downloading(versionName: String, progress: Int)
        case installing(versionName: String)
        case completed
        case failed(versionName: String, error: String, canRetryWithoutValidation: Bool = false)
    }

    var status: UpdateStatus = .idle

    func startUpdateProcess(
        device: DeviceWithState,
        version: VersionWithAssets,
        selectedAsset: Asset? = nil,
        repositoryOwnerAndRepo: String? = nil,
        skipValidation: Bool = false,
        database: AppDatabase = .shared
    ) async {
        status = .idle
        let updateService = DeviceUpdateService.companion.create(
            device: device.device,
            stateInfo: device.stateInfo,
            versionWithAssets: version,
            deviceApiFactory: database.deviceApiFactory,
            githubApi: database.githubApi,
            selectedAsset: selectedAsset
        )
        let assetName = updateService.getAssetName()
        let fallbackName = assetName.isEmpty ? version.version.tagName : assetName
        guard updateService.couldDetermineAsset() else {
            status = .failed(
                versionName: fallbackName,
                error: String(localized: "No Compatible Version Found")
            )
            return
        }

        let downloadFailedMessage = String(localized: "Download failed")
        do {
            if !updateService.isAssetFileCached() {
                status = .downloading(versionName: assetName, progress: 0)
                var didFinishDownload = false
                for await downloadState in updateService.downloadBinary(repositoryDao: database.repositoryDao) {
                    if let downloading = downloadState as? DownloadState.Downloading {
                        let newStatus = UpdateStatus.downloading(
                            versionName: assetName,
                            progress: Int(downloading.progress)
                        )
                        if status != newStatus {
                            status = newStatus
                        }
                        continue
                    }
                    if let failed = downloadState as? DownloadState.Failed {
                        status = .failed(
                            versionName: assetName,
                            error: failed.error?.message ?? downloadFailedMessage
                        )
                        return
                    }
                    if downloadState is DownloadState.Finished {
                        didFinishDownload = true
                        break
                    }
                }
                guard didFinishDownload else {
                    status = .failed(versionName: assetName, error: downloadFailedMessage)
                    return
                }
            }

            status = .installing(versionName: assetName)
            let response = try await updateService.installUpdate(skipValidation: skipValidation)
            guard response.isSuccessful else {
                let plainTextError = DeviceUpdateService.companion.extractPlainTextError(response: response)
                let errorMessage = if let plainTextError, !plainTextError.isEmpty {
                    "\(Int(response.code)): \(plainTextError)"
                } else {
                    String(localized: "Update failed with status code: \(Int(response.code))")
                }
                let canRetry = !skipValidation &&
                    DeviceUpdateService.companion.isValidationFailure(response: response)
                status = .failed(
                    versionName: assetName,
                    error: errorMessage,
                    canRetryWithoutValidation: canRetry
                )
                return
            }

            await persistAndApplyInstalledFirmware(
                device: device,
                version: version,
                repositoryOwnerAndRepo: repositoryOwnerAndRepo,
                database: database
            )
            status = .completed
        } catch {
            if error is CancellationError || Task.isCancelled {
                status = .idle
                return
            }
            status = .failed(versionName: assetName, error: error.localizedDescription)
        }
    }

    func persistAndApplyInstalledFirmware(
        device: DeviceWithState,
        version: VersionWithAssets,
        repositoryOwnerAndRepo: String? = nil,
        database: AppDatabase = .shared
    ) async {
        let mac = device.device.macAddress
        let previousRepoId = device.device.repositoryId
        let previousSkipUpdateTag = device.device.skipUpdateTag
        let targetRepoId = version.version.repositoryId
        var resolvedOwnerAndRepo = repositoryOwnerAndRepo

        // Apply optimistic in-memory state immediately before suspending so Room invalidations see
        // the updated firmware metadata, and re-apply in defer once the repository lookup and
        // targeted SQLite updates finish (or if cancelled mid-persistence).
        applyInstalledFirmwareState(
            device: device,
            version: version,
            repositoryOwnerAndRepo: resolvedOwnerAndRepo,
            database: database
        )
        defer {
            applyInstalledFirmwareState(
                device: device,
                version: version,
                repositoryOwnerAndRepo: resolvedOwnerAndRepo,
                database: database
            )
        }

        do {
            await database.waitUntilReady()
            resolvedOwnerAndRepo = try await resolveRepositoryOwnerAndRepo(
                explicitOwnerAndRepo: repositoryOwnerAndRepo,
                repositoryId: targetRepoId,
                database: database
            )
            if targetRepoId != previousRepoId {
                try await database.deviceRepository.updateRepositoryId(
                    macAddress: mac,
                    repositoryId: targetRepoId
                )
            }
            if !previousSkipUpdateTag.isEmpty {
                try await database.deviceRepository.updateSkipUpdateTag(
                    macAddress: mac,
                    skipUpdateTag: ""
                )
            }
        } catch is CancellationError {
            return
        } catch {
            Self.logger.error("Failed to update device metadata after install: \(error.localizedDescription)")
        }
    }

    private func resolveRepositoryOwnerAndRepo(
        explicitOwnerAndRepo: String?,
        repositoryId: Int64,
        database: AppDatabase
    ) async throws -> String? {
        if let explicitOwnerAndRepo, !explicitOwnerAndRepo.isEmpty {
            return explicitOwnerAndRepo
        }
        do {
            return try await database.versionWithAssetsRepository
                .getRepositoryById(id: repositoryId)?
                .ownerAndRepo
        } catch let error as CancellationError {
            throw error
        } catch {
            Self.logger.warning("Failed to resolve repository \(repositoryId) after install: \(error.localizedDescription)")
            return nil
        }
    }

    func applyInstalledFirmwareState(
        device: DeviceWithState,
        version: VersionWithAssets,
        repositoryOwnerAndRepo: String? = nil,
        database: AppDatabase = .shared
    ) {
        let rawTag = version.version.tagName
        let cleanedTag = Version.companion.normalizeTagOrVersion(value: rawTag)
        let targetRepoId = version.version.repositoryId
        device.device = device.device.copy(skipUpdateTag: "", repositoryId: targetRepoId)
        if let current = device.stateInfo {
            device.stateInfo = current.withInstalledFirmware(
                newVersion: cleanedTag,
                repositoryOwnerAndRepo: repositoryOwnerAndRepo
            )
        }
        device.availableUpdateVersion = nil
        database.deviceConnectionManager.markUpdateInstalled(
            macAddress: device.device.macAddress,
            installedVersionTag: rawTag,
            repositoryId: KotlinLong(value: targetRepoId),
            repositoryOwnerAndRepo: repositoryOwnerAndRepo
        )
    }
}
