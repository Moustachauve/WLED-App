import MarkdownUI
import OSLog
import Shared
import SwiftUI

private typealias State = SwiftUI.State

struct DeviceUpdateDetails: View {
    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "DeviceUpdateDetails"
    )

    @Environment(\.dismiss) var dismiss
    var device: DeviceWithState
    var database: AppDatabase = .shared
    var explicitVersion: VersionWithAssets?
    var repositoryName: String?
    var repositoryOwnerAndRepo: String?
    var showBinaryPicker: Bool = false
    var onInstallCompleted: (() -> Void)?

    @State private var showWarningDialog = false
    @State private var showInstallingDialog = false
    @State private var didCompleteInstall = false
    @State private var initializedVersionKey: String?
    @State private var loadedVersion: VersionWithAssets?
    @State private var selectedAssetName: String?

    private var activeVersion: VersionWithAssets? {
        explicitVersion ?? loadedVersion
    }

    private var versionTaskKey: String {
        let repoId = explicitVersion?.version.repositoryId ?? device.device.repositoryId
        let tag = explicitVersion?.version.tagName ?? device.availableUpdateVersion ?? ""
        return "\(repoId):\(tag)"
    }

    private var installableAssets: [Asset] {
        guard let activeVersion else { return [] }
        return DeviceUpdateService.companion.getInstallableAssets(
            versionWithAssets: activeVersion
        )
    }

    private var resolvedSelectedAsset: Asset? {
        installableAssets.first { $0.name == selectedAssetName }
    }

    private var isOtaEnabled: Bool {
        device.stateInfo?.info.isOtaEnabled ?? true
    }

    private var isSwitchingSource: Bool {
        guard let activeVersion else { return false }
        return activeVersion.version.repositoryId != device.device.repositoryId
    }

    private var targetSourceDisplayName: String {
        if let repositoryName, !repositoryName.isEmpty {
            return repositoryName
        }
        if let repositoryOwnerAndRepo, !repositoryOwnerAndRepo.isEmpty {
            return repositoryOwnerAndRepo
        }
        return ""
    }

    private var canInstall: Bool {
        guard device.isOnline, isOtaEnabled, activeVersion != nil else {
            return false
        }
        if showBinaryPicker {
            return resolvedSelectedAsset != nil
        }
        return true
    }

    private var isAlreadyInstalled: Bool {
        guard let activeVersion else { return false }
        return activeVersion.matchesInstalledVersion(
            installedVersion: device.stateInfo?.info.version,
            deviceRepositoryId: device.device.repositoryId
        )
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if showBinaryPicker {
                    DeviceUpdateBinaryHeader(
                        repositoryOwnerAndRepo: repositoryOwnerAndRepo,
                        installableAssets: installableAssets,
                        selectedAssetName: $selectedAssetName,
                        isDeviceOnline: device.isOnline,
                        isOtaEnabled: isOtaEnabled,
                        switchingToSourceName: isSwitchingSource ? targetSourceDisplayName : nil
                    )
                }
                releaseNotesView
            }
            .padding()
        }
        .safeAreaInset(edge: .bottom) {
            bottomBar
        }
        .navigationTitle("Version \(activeVersion?.version.tagName ?? device.availableUpdateVersion ?? "")")
        .fullScreenCover(
            isPresented: $showInstallingDialog,
            onDismiss: {
                if didCompleteInstall {
                    if let onInstallCompleted {
                        onInstallCompleted()
                    } else {
                        dismiss()
                    }
                }
            },
            content: {
                if let activeVersion {
                    DeviceUpdateInstalling(
                        device: device,
                        version: activeVersion,
                        selectedAsset: showBinaryPicker ? resolvedSelectedAsset : nil,
                        repositoryOwnerAndRepo: repositoryOwnerAndRepo,
                        database: database
                    ) {
                        didCompleteInstall = true
                    }
                    .background(BackgroundBlurView())
                }
            }
        )
        .task(id: versionTaskKey) {
            if let explicitVersion {
                initializeDefaultAssetIfNeeded(for: explicitVersion)
            } else if initializedVersionKey != versionTaskKey || loadedVersion == nil {
                await loadVersion()
            }
        }
    }

    @ViewBuilder
    private var releaseNotesView: some View {
        if let activeVersion {
            let trimmed = activeVersion.version.descriptionText.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty {
                Text("No release notes provided")
                    .italic()
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                Markdown(trimmed)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        } else {
            Markdown(String(localized: "[Unknown Error]"))
        }
    }

    private var bottomBar: some View {
        HStack {
            if !showBinaryPicker {
                Button("Skip This Version") {
                    skipVersion()
                }
            }

            Spacer()

            Button(isAlreadyInstalled ? "Reinstall" : "Install") {
                showWarningDialog = true
            }
            .buttonStyle(.borderedProminent)
            .disabled(!canInstall)
            .confirmationDialog(
                "Are you sure?",
                isPresented: $showWarningDialog
            ) {
                Button("Install Now") {
                    installVersion()
                }
            } message: {
                if isSwitchingSource, !targetSourceDisplayName.isEmpty {
                    let sourceWarning = String(
                        localized: "You are switching update sources to \(targetSourceDisplayName). Installing an incompatible firmware binary can make your device unreachable over Wi-Fi until reflashed via USB."
                    )
                    let disclaimer = String(localized: "update_disclaimer")
                    Text(verbatim: "\(sourceWarning)\n\n\(disclaimer)")
                } else {
                    Text("update_disclaimer")
                }
            }
        }
        .padding()
        .background(.bar)
    }

    private func initializeDefaultAssetIfNeeded(for version: VersionWithAssets) {
        guard initializedVersionKey != versionTaskKey else { return }
        initializedVersionKey = versionTaskKey
        let defaultAsset = DeviceUpdateService.companion.determineDefaultAsset(
            info: device.stateInfo?.info,
            versionWithAssets: version
        )
        selectedAssetName = defaultAsset?.name
    }

    private func loadVersion() async {
        guard let tag = device.availableUpdateVersion, !tag.isEmpty else {
            return
        }
        await database.waitUntilReady()
        do {
            loadedVersion = try await database.versionWithAssetsRepository.getVersionByTag(
                repositoryId: device.device.repositoryId,
                tagName: tag
            )
            if let loadedVersion {
                initializeDefaultAssetIfNeeded(for: loadedVersion)
            }
        } catch {
            loadedVersion = nil
            Self.logger.error("Unexpected error when loading version \(tag): \(error.localizedDescription)")
        }
    }

    func skipVersion() {
        let rawTag = activeVersion?.version.tagName ?? device.availableUpdateVersion ?? ""
        guard !rawTag.isEmpty else {
            dismiss()
            return
        }
        let mac = device.device.macAddress
        device.device = device.device.copy(skipUpdateTag: rawTag)
        if device.availableUpdateVersion == rawTag {
            device.availableUpdateVersion = nil
        }
        let repository = database.deviceRepository
        Task {
            do {
                try await repository.updateSkipUpdateTag(macAddress: mac, skipUpdateTag: rawTag)
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

private struct DeviceUpdateBinaryHeader: View {
    let repositoryOwnerAndRepo: String?
    let installableAssets: [Asset]
    @Binding var selectedAssetName: String?
    let isDeviceOnline: Bool
    let isOtaEnabled: Bool
    let switchingToSourceName: String?

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 8) {
                if let repositoryOwnerAndRepo, !repositoryOwnerAndRepo.isEmpty {
                    HStack {
                        Text("Update Source")
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text(verbatim: repositoryOwnerAndRepo)
                            .lineLimit(1)
                    }
                    .font(.caption)
                }

                if installableAssets.isEmpty {
                    Text("No installable firmware binaries in this release")
                        .font(.subheadline)
                        .foregroundStyle(.red)
                } else {
                    binaryMenu
                }

                if !isDeviceOnline {
                    Text("Connect to this device to install firmware")
                        .font(.caption)
                        .foregroundStyle(.red)
                } else if !isOtaEnabled {
                    Text("OTA updates are disabled on this device")
                        .font(.caption)
                        .foregroundStyle(.red)
                }

                if let switchingToSourceName, !switchingToSourceName.isEmpty {
                    Label {
                        Text(
                            "You are switching update sources to \(switchingToSourceName). Installing an incompatible firmware binary can make your device unreachable over Wi-Fi until reflashed via USB."
                        )
                    } icon: {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundStyle(.orange)
                    }
                    .font(.caption)
                }
            }
        }
    }

    private var binaryMenu: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Firmware Binary")
                .font(.caption)
                .foregroundStyle(.secondary)
            Menu {
                ForEach(installableAssets, id: \.name) { asset in
                    Button {
                        selectedAssetName = asset.name
                    } label: {
                        if asset.name == selectedAssetName {
                            Label {
                                Text(verbatim: asset.name)
                            } icon: {
                                Image(systemName: "checkmark")
                            }
                        } else {
                            Text(verbatim: asset.name)
                        }
                    }
                }
            } label: {
                HStack(spacing: 8) {
                    Group {
                        if let selectedAssetName {
                            Text(verbatim: selectedAssetName)
                                .foregroundStyle(.primary)
                        } else {
                            Text("Select a firmware binary")
                                .foregroundStyle(.secondary)
                        }
                    }
                    .font(.subheadline.weight(.medium))
                    .lineLimit(1)
                    .truncationMode(.middle)
                    .frame(maxWidth: .infinity, alignment: .leading)

                    Image(systemName: "chevron.up.chevron.down")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 10))
                .contentShape(RoundedRectangle(cornerRadius: 10))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("Firmware Binary"))
            .accessibilityValue(
                selectedAssetName.map { Text(verbatim: $0) } ?? Text("Select a firmware binary")
            )
        }
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
