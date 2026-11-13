import SwiftUI
import Shared
import MarkdownUI
import OSLog

private typealias State = SwiftUI.State

struct DeviceUpdateDetails: View {
    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.illumidel",
        category: "DeviceUpdateDetails"
    )
    @Environment(\.dismiss) var dismiss
    var device: DeviceWithState
    var database: AppDatabase = .shared

    @State var showInstallingDialog = false
    @State private var versionWithAssets: VersionWithAssets?

    private var displayTagName: String {
        let rawTag = versionWithAssets?.version.tagName ?? device.availableUpdateVersion ?? ""
        return rawTag.strippingLeadingV
    }

    var body: some View {
        ZStack {
            ScrollView {
                Markdown(versionWithAssets?.version.descriptionText ?? String(localized: "[Unknown Error]"))
                    .padding()
            }
        }
        .safeAreaInset(edge: .bottom) {
            HStack {
                Button("Skip This Version") {
                    skipVersion()
                }

                Spacer()

                Button("Install") {
                    installVersion()
                }
                .buttonStyle(.borderedProminent)
                .disabled(!device.isOnline || versionWithAssets == nil)
            }
            .padding()
            .background(.bar)
        }
        .navigationTitle("Version \(displayTagName)")
        .fullScreenCover(isPresented: $showInstallingDialog) {
            if let versionWithAssets {
                DeviceUpdateInstalling(
                    device: device,
                    version: versionWithAssets,
                    database: database
                )
                .background(BackgroundBlurView())
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .didCompleteUpdateInstall)) { notification in
            guard (notification.object as? String) == device.id else { return }
            dismiss()
        }
        .task(id: device.availableUpdateVersion) {
            await loadVersion()
        }
    }

    private func loadVersion() async {
        guard let tag = device.availableUpdateVersion, !tag.isEmpty else {
            return
        }
        await database.waitUntilReady()
        do {
            versionWithAssets = try await database.versionWithAssetsRepository.getVersionByTag(
                repositoryId: device.device.repositoryId,
                tagName: tag
            )
        } catch {
            versionWithAssets = nil
            Self.logger.error("Unexpected error when loading version \(tag): \(error.localizedDescription)")
        }
    }

    func skipVersion() {
        let tag = (device.availableUpdateVersion ?? "").strippingLeadingV
        let mac = device.device.macAddress
        device.device = device.device.copy(skipUpdateTag: tag)
        device.availableUpdateVersion = nil
        let repository = database.deviceRepository
        Task {
            do {
                try await repository.updateSkipUpdateTag(macAddress: mac, skipUpdateTag: tag)
            } catch {
                Self.logger.error("Unresolved error saving skip version: \(error.localizedDescription)")
            }
        }
        dismiss()
    }

    func installVersion() {
        showInstallingDialog = true
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        DeviceUpdateDetails(
            device: PreviewData.deviceWithUpdate,
            database: PreviewData.database
        )
    }
}
#endif
