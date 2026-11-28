import AVFAudio
import Foundation
import Shared
import SnapshotTesting
import SwiftUI
import Testing
import UIKit
@testable import WLED

@MainActor
struct SoundSyncViewSnapshotTests {

    let database: AppDatabase

    init() {
        self.database = AppDatabase(inMemory: true)
    }

    @Test func testSoundSyncLightSnapshot() async throws {
        try await assertSoundSyncSnapshot(style: .light)
    }

    @Test func testSoundSyncDarkSnapshot() async throws {
        try await assertSoundSyncSnapshot(style: .dark)
    }

    // MARK: - Helpers

    private func assertSoundSyncSnapshot(
        style: UIUserInterfaceStyle,
        fileID: StaticString = #fileID,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line,
        column: UInt = #column
    ) async throws {
        let device1 = Shared.Device(
            macAddress: "aabbccddeeff",
            address: "192.168.1.10",
            isHidden: false,
            originalName: "Living Room"
        )
        let device2 = Shared.Device(
            macAddress: "112233445566",
            address: "192.168.1.11",
            isHidden: false,
            originalName: "Desk Strip"
        )
        try await database.insertDevicesAndSetStatuses([(device1, .connected), (device2, .disconnected)])
        let viewModel = SoundSyncViewModel(
            soundSyncManager: FakeSoundSyncManager(permission: .granted),
            userPreferencesRepository: database.userPreferencesRepository,
            deviceConnectionManager: database.deviceConnectionManager
        )
        viewModel.agcEnabled = true
        viewModel.selectedDeviceMacs = ["aabbccddeeff", "112233445566"]
        let view = SoundSyncView(viewModel: viewModel)

        let deviceConfig = ViewImageConfig(
            safeArea: ViewImageConfig.iPhone13Pro.safeArea,
            size: CGSize(width: 390, height: 1000),
            traits: ViewImageConfig.iPhone13Pro.traits
        )
        assertSnapshot(
            of: view,
            as: .image(
                drawHierarchyInKeyWindow: true,
                precision: 0.99,
                layout: .device(config: deviceConfig),
                traits: UITraitCollection(userInterfaceStyle: style)
            ),
            named: "1",
            fileID: fileID,
            file: file,
            testName: testName,
            line: line,
            column: column
        )
    }
}
