import SwiftUI
import Shared
import MarkdownUI
import OSLog

private typealias State = SwiftUI.State

private struct InstallRequest: Identifiable {
    let version: VersionWithAssets
    let asset: Asset?

    var id: String {
        "\(version.version.repositoryId):\(version.version.tagName):\(asset?.name ?? "")"
    }
}

struct DeviceUpdateDetails: View {
    enum Target: Hashable {
        case availableUpdate
        case specificVersion(repositoryId: Int64, tagName: String)
    }

    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "DeviceUpdateDetails"
    )

    @Environment(\.dismiss) private var dismiss
    var device: DeviceWithState
    var target: Target = .availableUpdate
    var database: AppDatabase = .shared

    @State private var showWarningDialog = false
    @State private var activeInstall: InstallRequest?
    @State private var didCompleteInstall = false
    @State private var versionWithAssets: VersionWithAssets?
    @State private var targetRepository: Repository?
    @State private var selectedAsset: Asset?
    @State private var isLoadingVersion = true

    private var isBinaryPickerMode: Bool {
        if case .specificVersion = target { return true }
        return false
    }

    private var targetRepositoryId: Int64 {
        switch target {
        case .availableUpdate: device.device.repositoryId
        case .specificVersion(let repositoryId, _): repositoryId
        }
    }

    private var targetTagName: String? {
        switch target {
        case .availableUpdate: device.availableUpdateVersion
        case .specificVersion(_, let tagName): tagName
        }
    }

    private var loadTaskId: String {
        "\(targetRepositoryId):\(targetTagName ?? "")"
    }

    private var installableAssets: [Asset] {
        guard let versionWithAssets else { return [] }
        return DeviceUpdateService.companion.getInstallableAssets(versionWithAssets: versionWithAssets)
    }

    private var isSwitchingSource: Bool {
        isBinaryPickerMode && targetRepositoryId != device.device.repositoryId
    }

    private var releaseSubtitle: String? {
        guard isBinaryPickerMode, let version = versionWithAssets?.version else { return nil }
        let parts = [version.distinctReleaseName, targetRepository?.ownerAndRepo]
            .compactMap { $0 }
            .filter { !$0.isEmpty }
        return parts.isEmpty ? nil : parts.joined(separator: " • ")
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let releaseSubtitle {
                    Text(releaseSubtitle)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                releaseNotesBody
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
        }
        .updateFooterBar {
            footerBar
        }
        .navigationTitle("Version \(versionWithAssets?.version.tagName ?? targetTagName ?? "")")
        .fullScreenCover(
            item: $activeInstall,
            onDismiss: {
                if didCompleteInstall {
                    dismiss()
                }
            },
            content: { request in
                DeviceUpdateInstalling(
                    device: device,
                    version: request.version,
                    selectedAsset: request.asset,
                    database: database
                ) {
                    didCompleteInstall = true
                }
                .presentationBackground(.ultraThinMaterial)
            }
        )
        .task(id: loadTaskId) {
            await loadVersion()
        }
        .onChange(of: device.stateInfo) {
            resolveDefaultAssetIfNeeded()
        }
    }

    @ViewBuilder
    private var releaseNotesBody: some View {
        if let versionWithAssets {
            let notes = versionWithAssets.version.descriptionText
                .trimmingCharacters(in: .whitespacesAndNewlines)
            if notes.isEmpty {
                Text("No release notes provided")
                    .italic()
                    .foregroundStyle(.secondary)
            } else {
                Markdown(notes)
            }
        } else if !isLoadingVersion {
            Text("[Unknown Error]")
                .foregroundStyle(.secondary)
        }
    }

    @ViewBuilder
    private var footerBar: some View {
        if isBinaryPickerMode {
            binaryPickerFooter
        } else {
            casualFooter
        }
    }

    private var casualFooter: some View {
        HStack {
            Button("Skip This Version") {
                skipVersion()
            }
            .adaptiveGlassButtonStyle()

            Spacer()

            installConfirmationButton(
                titleKey: "Install",
                isEnabled: device.isOnline && versionWithAssets != nil
            )
        }
    }

    private var binaryPickerFooter: some View {
        let isOtaEnabled = device.stateInfo?.info.isOtaEnabled ?? true
        let canInstall = device.isOnline && isOtaEnabled && selectedAsset != nil
        let isAlreadyInstalled = versionWithAssets?.matchesInstalledVersion(
            installedVersion: device.stateInfo?.info.version,
            deviceRepositoryId: device.device.repositoryId
        ) ?? false

        return VStack(alignment: .leading, spacing: 8) {
            if !device.isOnline {
                Text("Connect to this device to install firmware")
                    .font(.caption)
                    .foregroundStyle(.red)
            } else if !isOtaEnabled {
                Text("OTA updates are disabled on this device")
                    .font(.caption)
                    .foregroundStyle(.red)
            }

            HStack(spacing: 12) {
                if versionWithAssets != nil && installableAssets.isEmpty {
                    Text("No installable firmware binaries in this release")
                        .font(.subheadline)
                        .foregroundStyle(.red)
                        .frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    binaryPickerMenu
                }

                installConfirmationButton(
                    titleKey: isAlreadyInstalled ? "Reinstall" : "Install",
                    isEnabled: canInstall
                )
            }
        }
    }

    private func installConfirmationButton(titleKey: LocalizedStringKey, isEnabled: Bool) -> some View {
        Button(titleKey) {
            showWarningDialog = true
        }
        .adaptiveGlassProminentButtonStyle()
        .disabled(!isEnabled)
        .confirmationDialog("Are you sure?", isPresented: $showWarningDialog) {
            Button("Install Now", role: isSwitchingSource ? .destructive : nil) {
                installVersion()
            }
        } message: {
            disclaimerMessage
        }
    }

    private var selectedAssetNameBinding: Binding<String?> {
        Binding(
            get: { selectedAsset?.name },
            set: { name in
                selectedAsset = installableAssets.first { $0.name == name }
            }
        )
    }

    private var binaryPickerMenu: some View {
        Menu {
            Picker("Firmware Binary", selection: selectedAssetNameBinding) {
                ForEach(installableAssets, id: \.name) { asset in
                    Text(asset.name)
                        .tag(Optional(asset.name))
                }
            }
            .labelsHidden()
        } label: {
            HStack(spacing: 6) {
                Text(selectedAsset?.name ?? String(localized: "Select a firmware binary"))
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .foregroundStyle(selectedAsset == nil ? .secondary : .primary)
                Spacer(minLength: 4)
                Image(systemName: "chevron.up.chevron.down")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
            .adaptiveGlassPickerBackground()
        }
        .disabled(installableAssets.isEmpty)
        .accessibilityLabel("Firmware Binary")
        .accessibilityValue(selectedAsset?.name ?? String(localized: "Select a firmware binary"))
    }

    @ViewBuilder
    private var disclaimerMessage: some View {
        if isSwitchingSource, let sourceName = targetRepository?.name {
            let sourceWarning = String(localized: "You are switching update sources to \(sourceName). Installing an incompatible firmware binary can make your device unreachable over Wi-Fi until reflashed via USB.")
            let baseDisclaimer = String(localized: "update_disclaimer")
            Text(verbatim: "\(sourceWarning)\n\n\(baseDisclaimer)")
        } else {
            Text("update_disclaimer")
        }
    }

    private func loadVersion() async {
        guard activeInstall == nil && !didCompleteInstall else { return }
        if let loaded = versionWithAssets?.version,
           loaded.repositoryId == targetRepositoryId,
           loaded.tagName == targetTagName {
            return
        }
        isLoadingVersion = true
        selectedAsset = nil
        defer { isLoadingVersion = false }

        guard let tag = targetTagName, !tag.isEmpty else {
            versionWithAssets = nil
            targetRepository = nil
            return
        }
        await database.waitUntilReady()
        guard !Task.isCancelled else { return }
        do {
            versionWithAssets = try await database.versionWithAssetsRepository.getVersionByTag(
                repositoryId: targetRepositoryId,
                tagName: tag
            )
            if isBinaryPickerMode {
                targetRepository = try await database.repositoryDao.getRepositoryById(id: targetRepositoryId)
            }
            resolveDefaultAssetIfNeeded()
        } catch is CancellationError {
            return
        } catch {
            versionWithAssets = nil
            targetRepository = nil
            Self.logger.error("Unexpected error when loading version \(tag): \(error.localizedDescription)")
        }
    }

    private func resolveDefaultAssetIfNeeded() {
        guard isBinaryPickerMode, selectedAsset == nil, let versionWithAssets else { return }
        selectedAsset = DeviceUpdateService.companion.determineDefaultAsset(
            info: device.stateInfo?.info,
            versionWithAssets: versionWithAssets
        )
    }

    func skipVersion() {
        let tag = device.availableUpdateVersion ?? ""
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
        guard let versionWithAssets else { return }
        activeInstall = InstallRequest(
            version: versionWithAssets,
            asset: selectedAsset
        )
    }
}

// MARK: - Adaptive Liquid Glass Helpers

extension View {
    @ViewBuilder
    fileprivate func updateFooterBar<BarContent: View>(@ViewBuilder content: () -> BarContent) -> some View {
        if #available(iOS 26.0, *) {
            safeAreaBar(edge: .bottom) {
                content()
                    .padding(.horizontal)
                    .padding(.vertical, 8)
            }
        } else {
            safeAreaInset(edge: .bottom) {
                content()
                    .padding()
                    .background(.bar)
            }
        }
    }

    @ViewBuilder
    fileprivate func adaptiveGlassPickerBackground() -> some View {
        if #available(iOS 26.0, *) {
            glassEffect(.regular.interactive(), in: .capsule)
        } else {
            background(
                RoundedRectangle(cornerRadius: 8)
                    .strokeBorder(.quaternary, lineWidth: 1)
            )
        }
    }

    @ViewBuilder
    fileprivate func adaptiveGlassButtonStyle() -> some View {
        if #available(iOS 26.0, *) {
            buttonStyle(.glass)
        } else {
            self
        }
    }

    @ViewBuilder
    fileprivate func adaptiveGlassProminentButtonStyle() -> some View {
        if #available(iOS 26.0, *) {
            buttonStyle(.glassProminent)
        } else {
            buttonStyle(.borderedProminent)
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
