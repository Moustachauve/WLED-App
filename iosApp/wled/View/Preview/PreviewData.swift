//
//  PreviewData.swift
//  WLED
//
//  Created by Christophe Gagnier on 2025-12-27.
//

#if DEBUG
import Foundation
import SwiftUI
import Shared

@MainActor
struct PreviewData {

    /// In-memory device database that preview devices are saved to, so list previews can observe them.
    static let database = AppDatabase(inMemory: true)

    static let previewVersion = VersionWithAssets(
        version: Version(
            id: 1,
            repositoryId: Repository.companion.DEFAULT_ID,
            tagName: "1.5.3",
            name: "v1.5.3",
            description: "## What's Changed\n\n* Stability improvements",
            isPrerelease: false,
            publishedDate: "2025-01-01T00:00:00Z",
            htmlUrl: "https://github.com/Illumidel/App-Releases/releases/tag/v1.5.3"
        ),
        assets: [
            Asset(
                id: 1,
                versionId: 1,
                name: "v1.5.3_ESP32_fr.bin",
                size: 1_048_576,
                downloadUrl: "https://github.com/Illumidel/App-Releases/releases/download/v1.5.3/v1.5.3_ESP32_fr.bin",
                assetId: 1
            )
        ]
    )

    // MARK: - Devices

    static var onlineDevice: DeviceWithState {
        createDevice(name: "Illumidel Beam", ip: "10.0.1.12")
    }

    static var offlineDevice: DeviceWithState {
        // Set last seen to 2 hours ago
        let twoHoursAgo = Int64(Date().addingTimeInterval(-7200).timeIntervalSince1970 * 1000)
        let device = createDevice(name: "Illumidel Strip", ip: "10.0.1.13", lastSeen: twoHoursAgo)
        device.websocketStatus = .disconnected
        return device
    }

    static var deviceWithUpdate: DeviceWithState {
        // Device is on 1.5.2, Update 1.5.3 is available in DB
        ensureVersionsExist()
        let device = createDevice(name: "Old Illumidel", ip: "10.0.1.14", version: "1.5.2", color: [50, 50, 255])
        device.availableUpdateVersion = previewVersion.version.tagName
        return device
    }

    static var hiddenDevice: DeviceWithState {
        createDevice(name: "Hidden Light", ip: "10.0.1.15", isHidden: true, color: [200, 0, 255])
    }

    // MARK: - Helpers

    private static func createDevice(
        name: String,
        ip: String,
        version: String = "1.5.3",
        isHidden: Bool = false,
        color: [Int] = [255, 160, 0],
        lastSeen: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) -> DeviceWithState {
        let macAddress = "mock:mac:\(ip)"
        let device = Device(
            macAddress: macAddress,
            address: ip,
            isHidden: isHidden,
            originalName: name,
            lastSeen: lastSeen
        )
        // Save so list previews observing the database pick it up (insert replaces on conflict)
        Task {
            try? await database.deviceRepository.insert(device: device)
        }

        let deviceWithState = DeviceWithState(initialDevice: device)
        deviceWithState.websocketStatus = .connected
        deviceWithState.stateInfo = .mock(name: name, version: version, color: color)

        return deviceWithState
    }

    private static func ensureVersionsExist() {
        let version = previewVersion.version
        let assets = previewVersion.assets
        Task {
            await database.waitUntilReady()
            guard let repo = try? await database.repositoryDao.getRepositoryByOwnerAndRepo(
                ownerAndRepo: Repository.companion.DEFAULT_OWNER_REPO
            ) else {
                return
            }
            try? await database.versionWithAssetsRepository.updateRepository(
                repository: repo,
                versionToAssetsMap: [version: assets]
            )
        }
    }
}

// MARK: - Mock Data Extensions

extension DeviceStateInfo {
    static func mock(name: String, version: String, color: [Int]) -> DeviceStateInfo {
        let r = Int32(color.indices.contains(0) ? color[0] : 255)
        let g = Int32(color.indices.contains(1) ? color[1] : 160)
        let b = Int32(color.indices.contains(2) ? color[2] : 0)

        return DeviceStateInfo.companion.createMock(
            name: name,
            version: version,
            red: r,
            green: g,
            blue: b,
            brightness: 128,
            isOn: true
        )
    }
}
#endif
