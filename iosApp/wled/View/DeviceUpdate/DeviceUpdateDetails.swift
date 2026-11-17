import MarkdownUI
import OSLog
import Shared
import SwiftUI

private typealias State = SwiftUI.State

private struct InstallTarget: Identifiable {
    let id = UUID()
    let version: VersionWithAssets
    let selectedAsset: Asset?
}

struct DeviceUpdateDetails: View {
    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "DeviceUpdateDetails"
    )

    @Environment(\.dismiss) var dismiss
    var device: DeviceWithState
    var database: AppDatabase = .shared
    var browserTarget: (repositoryId: Int64, tagName: String)?
    var onInstallCompleted: (() -> Void)?

    @State private var showWarningDialog = false
    @State private var activeInstall: InstallTarget?
    @State private var didCompleteInstall = false
    @State private var versionWithAssets: VersionWithAssets?
    @State private var isLoadingVersion = true
    @State private var repository: Repository?
    @State private var installableAssets: [Asset] = []
    @State private var selectedAssetName: String?

    private var isBrowserSelection: Bool {
        browserTarget != nil
    }

    private var targetRepositoryId: Int64 {
        browserTarget?.repositoryId ?? device.device.repositoryId
    }

    private var targetTagName: String? {
        browserTarget?.tagName ?? device.availableUpdateVersion
    }

    private var versionTaskKey: String {
        "\(targetRepositoryId):\(targetTagName ?? "")"
    }

    private var resolvedSelectedAsset: Asset? {
        installableAssets.first { $0.name == selectedAssetName }
    }

    private var isOtaEnabled: Bool {
        device.stateInfo?.info.isOtaEnabled ?? true
    }

    private var isSwitchingSource: Bool {
        guard let versionWithAssets else { return false }
        return versionWithAssets.version.repositoryId != device.device.repositoryId
    }

    private var canInstall: Bool {
        guard device.isOnline, isOtaEnabled, versionWithAssets != nil else {
            return false
        }
        if isBrowserSelection {
            return resolvedSelectedAsset != nil
        }
        return true
    }

    private var isAlreadyInstalled: Bool {
        versionWithAssets.map { device.hasInstalled($0) } ?? false
    }

    var body: some View {
        ScrollView {
            if isLoadingVersion && versionWithAssets == nil {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 32)
            } else {
                VStack(alignment: .leading, spacing: 12) {
                    if isBrowserSelection {
                        DeviceUpdateBinaryHeader(
                            repository: repository,
                            installableAssets: installableAssets,
                            selectedAssetName: $selectedAssetName,
                            isDeviceOnline: device.isOnline,
                            isOtaEnabled: isOtaEnabled,
                            isSwitchingSource: isSwitchingSource
                        )
                    }
                    releaseNotesView
                }
                .padding()
            }
        }
        .safeAreaInset(edge: .bottom) {
            bottomBar
        }
        .navigationTitle("Version \(versionWithAssets?.version.tagName ?? targetTagName ?? "")")
        .fullScreenCover(
            item: $activeInstall,
            onDismiss: {
                if didCompleteInstall {
                    if let onInstallCompleted {
                        onInstallCompleted()
                    } else {
                        dismiss()
                    }
                }
            },
            content: { target in
                DeviceUpdateInstalling(
                    device: device,
                    version: target.version,
                    selectedAsset: target.selectedAsset,
                    database: database
                ) {
                    didCompleteInstall = true
                }
                .background(BackgroundBlurView())
            }
        )
        .task(id: versionTaskKey) {
            await loadVersion()
        }
        .onChange(of: device.stateInfo?.info.version) {
            autoSelectDefaultAssetIfNeeded()
        }
        .onChange(of: device.stateInfo?.info.releaseName) {
            autoSelectDefaultAssetIfNeeded()
        }
    }

    @ViewBuilder
    private var releaseNotesView: some View {
        if let versionWithAssets {
            let trimmed = versionWithAssets.version.descriptionText.trimmingCharacters(in: .whitespacesAndNewlines)
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
            if !isBrowserSelection {
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
                Text("update_disclaimer")
            }
        }
        .padding()
        .background(.bar)
    }

    private func autoSelectDefaultAssetIfNeeded() {
        guard isBrowserSelection,
              selectedAssetName == nil,
              let versionWithAssets,
              let info = device.stateInfo?.info else {
            return
        }
        selectedAssetName = DeviceUpdateService.companion.determineDefaultAsset(
            info: info,
            versionWithAssets: versionWithAssets
        )?.name
    }

    private func loadVersion() async {
        guard let tag = targetTagName, !tag.isEmpty else {
            if versionWithAssets == nil {
                installableAssets = []
                repository = nil
                isLoadingVersion = false
            }
            return
        }
        if versionWithAssets == nil {
            isLoadingVersion = true
        }
        let repoId = targetRepositoryId
        await database.waitUntilReady()
        guard !Task.isCancelled else { return }
        do {
            async let loadedVersionTask = database.versionWithAssetsRepository.getVersionByTag(
                repositoryId: repoId,
                tagName: tag
            )
            async let loadedRepoTask = database.repositoryDao.getRepositoryById(id: repoId)
            let (loadedVersion, loadedRepo) = try await (loadedVersionTask, loadedRepoTask)
            try Task.checkCancellation()
            versionWithAssets = loadedVersion
            installableAssets = loadedVersion.map {
                DeviceUpdateService.companion.getInstallableAssets(versionWithAssets: $0)
            } ?? []
            repository = loadedRepo
            isLoadingVersion = false
            autoSelectDefaultAssetIfNeeded()
        } catch {
            if error is CancellationError || Task.isCancelled { return }
            versionWithAssets = nil
            installableAssets = []
            isLoadingVersion = false
            Self.logger.error("Unexpected error when loading version \(tag): \(error.localizedDescription)")
        }
    }

    func skipVersion() {
        let rawTag = versionWithAssets?.version.tagName ?? device.availableUpdateVersion ?? ""
        guard !rawTag.isEmpty else {
            dismiss()
            return
        }
        let mac = device.device.macAddress
        device.device = device.device.copy(skipUpdateTag: rawTag)
        if device.availableUpdateVersion == rawTag {
            device.availableUpdateVersion = nil
        }
        let deviceRepository = database.deviceRepository
        Task {
            do {
                try await deviceRepository.updateSkipUpdateTag(macAddress: mac, skipUpdateTag: rawTag)
            } catch {
                Self.logger.error("Unresolved error saving skip version: \(error.localizedDescription)")
            }
        }
        dismiss()
    }

    func installVersion() {
        guard let versionWithAssets else { return }
        didCompleteInstall = false
        activeInstall = InstallTarget(
            version: versionWithAssets,
            selectedAsset: isBrowserSelection ? resolvedSelectedAsset : nil
        )
    }
}

private struct DeviceUpdateBinaryHeader: View {
    let repository: Repository?
    let installableAssets: [Asset]
    @Binding var selectedAssetName: String?
    let isDeviceOnline: Bool
    let isOtaEnabled: Bool
    let isSwitchingSource: Bool

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 8) {
                if let ownerAndRepo = repository?.ownerAndRepo, !ownerAndRepo.isEmpty {
                    HStack {
                        Text("Update Source")
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text(verbatim: ownerAndRepo)
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

                if isSwitchingSource, let sourceName = repository?.name, !sourceName.isEmpty {
                    Label {
                        Text(
                            "You are switching update sources to \(sourceName). Installing an incompatible firmware binary can make your device unreachable over Wi-Fi until reflashed via USB."
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
                        .accessibilityHidden(true)
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
