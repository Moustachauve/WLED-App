import SwiftUI
import Shared

private typealias State = SwiftUI.State

struct DeviceFirmwareBrowserView: View {
    private var device: DeviceWithState
    @State private var viewModel: DeviceFirmwareBrowserViewModel

    init(
        device: DeviceWithState,
        database: AppDatabase = .shared,
        refreshRepositoryAction: (@Sendable (Int64) async throws -> Bool)? = nil
    ) {
        self.device = device
        _viewModel = State(
            wrappedValue: DeviceFirmwareBrowserViewModel(
                device: device,
                database: database,
                refreshRepositoryAction: refreshRepositoryAction
            )
        )
    }

    var body: some View {
        List {
            controlsSection
            versionsSection
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Firmware Versions")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button("Refresh", systemImage: "arrow.clockwise") {
                    Task {
                        await viewModel.refreshSelectedRepository()
                    }
                }
                .disabled(viewModel.isLoadingVersions)
            }
        }
        .refreshable {
            await viewModel.refreshSelectedRepository()
        }
        .task {
            await viewModel.observeRepositories()
        }
        .task(id: viewModel.selectedRepositoryId) {
            await viewModel.loadRepository(viewModel.selectedRepositoryId)
        }
    }

    private var controlsSection: some View {
        Section {
            Picker("Update Source", selection: $viewModel.selectedRepositoryId) {
                ForEach(viewModel.repositories, id: \.id) { repo in
                    VStack(alignment: .leading) {
                        Text(repo.name)
                        Text(statusSubtitle(for: repo))
                    }
                    .tag(repo.id)
                }
            }
            .pickerStyle(.menu)

            if viewModel.isSwitchingSource, let sourceName = viewModel.selectedRepository?.name {
                Label {
                    Text("You are viewing firmware from \(sourceName) instead of your current update source. Make sure the binary you select matches your exact controller hardware before installing.")
                        .font(.caption)
                } icon: {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(.orange)
                }
                .foregroundStyle(.secondary)
            }

            Picker("Update Channel", selection: $viewModel.selectedBranch) {
                ForEach([Branch.stable, Branch.beta]) { branch in
                    Text(LocalizedStringKey(branch.nameKey))
                        .tag(branch)
                }
            }
            .pickerStyle(.segmented)
        } footer: {
            if let selectedRepo = viewModel.selectedRepository {
                Text(statusSubtitle(for: selectedRepo))
            }
        }
    }

    private var versionsSection: some View {
        Section {
            if viewModel.versions.isEmpty {
                emptyVersionsView
            } else {
                ForEach(viewModel.versions, id: \.version.tagName) { item in
                    NavigationLink(
                        value: DeviceDetailRoute.firmwareVersionDetails(
                            deviceId: device.id,
                            repositoryId: item.version.repositoryId,
                            tagName: item.version.tagName
                        )
                    ) {
                        versionRow(for: item)
                    }
                }
            }
        } header: {
            HStack {
                Text("Firmware Versions")
                Spacer()
                if viewModel.isLoadingVersions && !viewModel.versions.isEmpty {
                    ProgressView()
                }
            }
        }
    }

    @ViewBuilder
    private var emptyVersionsView: some View {
        if viewModel.isLoadingVersions {
            HStack(spacing: 12) {
                Spacer()
                ProgressView("Loading versions…")
                Spacer()
            }
            .padding(.vertical, 24)
        } else if viewModel.loadError {
            ContentUnavailableView {
                Label("Could not load versions from GitHub", systemImage: "exclamationmark.triangle")
            } actions: {
                Button("Retry") {
                    Task {
                        await viewModel.refreshSelectedRepository()
                    }
                }
                .buttonStyle(.bordered)
            }
        } else {
            ContentUnavailableView(
                "No releases found for this channel",
                systemImage: "shippingbox"
            )
        }
    }

    private func versionRow(for item: VersionWithAssets) -> some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(item.version.tagName)
                    .font(.headline)
                if !item.version.displaySubtitle.isEmpty {
                    Text(item.version.displaySubtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            Spacer()
            if viewModel.isInstalled(item) {
                Text("Installed")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }

    private func statusSubtitle(for repository: Repository) -> String {
        var parts: [String] = []
        if repository.isOfficial {
            parts.append(String(localized: "Official"))
        }
        if repository.id == device.device.repositoryId {
            parts.append(String(localized: "Installed"))
        }
        parts.append(repository.ownerAndRepo)
        return parts.joined(separator: " • ")
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        DeviceFirmwareBrowserView(
            device: PreviewData.onlineDevice,
            database: PreviewData.database,
            refreshRepositoryAction: { _ in
                await PreviewData.seedVersions()
                return true
            }
        )
    }
}
#endif
