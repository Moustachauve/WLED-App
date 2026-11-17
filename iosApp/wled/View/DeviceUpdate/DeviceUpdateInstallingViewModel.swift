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
        skipValidation: Bool = false,
        database: AppDatabase = .shared,
        cacheDirPath: String? = nil
    ) async {
        status = .idle
        let updateService = DeviceUpdateService.companion.create(
            device: device.device,
            stateInfo: device.stateInfo,
            versionWithAssets: version,
            deviceApiFactory: database.deviceApiFactory,
            githubApi: database.githubApi,
            selectedAsset: selectedAsset,
            cacheDirPath: cacheDirPath
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
                try Task.checkCancellation()
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

            await database.waitUntilReady()
            let resolvedOwnerAndRepo = try await database.deviceConnectionManager.completeFirmwareInstall(
                device: device.device,
                installedVersion: version.version,
                repositoryDao: database.repositoryDao
            )
            try Task.checkCancellation()

            let cleanedTag = Version.companion.normalizeTagOrVersion(value: version.version.tagName)
            device.device = device.device.copy(skipUpdateTag: "", repositoryId: version.version.repositoryId)
            if let current = device.stateInfo {
                device.stateInfo = current.withInstalledFirmware(
                    newVersion: cleanedTag,
                    repositoryOwnerAndRepo: resolvedOwnerAndRepo
                )
            }
            device.availableUpdateVersion = nil
            status = .completed
        } catch {
            if error is CancellationError || Task.isCancelled {
                status = .idle
                return
            }
            Self.logger.error("Firmware install failed: \(error.localizedDescription)")
            status = .failed(versionName: assetName, error: error.localizedDescription)
        }
    }
}
