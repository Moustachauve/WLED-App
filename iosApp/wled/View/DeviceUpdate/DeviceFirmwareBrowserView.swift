import Shared
import SwiftUI

private typealias State = SwiftUI.State

struct DeviceFirmwareBrowserView: View {
    @State private var viewModel: DeviceFirmwareBrowserViewModel
    private let device: DeviceWithState

    init(
        device: DeviceWithState,
        database: AppDatabase = .shared
    ) {
        _viewModel = State(wrappedValue: DeviceFirmwareBrowserViewModel(
            device: device,
            database: database
        ))
        self.device = device
    }

    var body: some View {
        List {
            Section {
                updateSourceRow

                if let selectedRepository = viewModel.selectedRepository,
                   selectedRepository.id != device.device.repositoryId {
                    Label {
                        Text(
                            "You are viewing firmware from \(selectedRepository.name) instead of your current update source. Make sure the binary you select matches your exact controller hardware before installing."
                        )
                        .font(.caption)
                    } icon: {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundStyle(.orange)
                    }
                    .padding(.vertical, 2)
                }

                Picker("Update Channel", selection: $viewModel.selectedBranch) {
                    ForEach([Branch.stable, Branch.beta]) { branch in
                        Text(LocalizedStringKey(branch.nameKey))
                            .tag(branch)
                    }
                }
                .pickerStyle(.segmented)
            }

            Section {
                versionsContent
            }
        }
        .navigationTitle("Firmware Versions")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                if viewModel.isLoadingVersions && !viewModel.filteredVersions.isEmpty {
                    ProgressView()
                } else {
                    Button(
                        "Refresh",
                        systemImage: "arrow.clockwise",
                        action: viewModel.refreshSelectedRepository
                    )
                    .disabled(viewModel.isLoadingVersions)
                }
            }
        }
        .task {
            await viewModel.observe()
        }
    }

    @ViewBuilder
    private var updateSourceRow: some View {
        if viewModel.repositories.isEmpty {
            LabeledContent("Update Source") {
                ProgressView()
            }
        } else {
            LabeledContent("Update Source") {
                Menu {
                    ForEach(viewModel.repositories, id: \.id) { repo in
                        Button {
                            viewModel.selectedRepositoryId = repo.id
                        } label: {
                            if repo.id == viewModel.selectedRepositoryId {
                                Label {
                                    Text(verbatim: repo.name)
                                    Text(verbatim: repositoryMenuSubtitle(for: repo))
                                } icon: {
                                    Image(systemName: "checkmark")
                                }
                            } else {
                                Text(verbatim: repo.name)
                                Text(verbatim: repositoryMenuSubtitle(for: repo))
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 6) {
                        if let selected = viewModel.selectedRepository {
                            Text(verbatim: selected.name)
                                .lineLimit(1)
                                .layoutPriority(1)
                            if selected.isOfficial {
                                OfficialSourceBadge()
                            }
                            if selected.id == device.device.repositoryId {
                                InstalledSourceBadge()
                            }
                        }
                        Image(systemName: "chevron.up.chevron.down")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.secondary)
                            .accessibilityHidden(true)
                    }
                }
            }
        }
    }

    private func repositoryMenuSubtitle(for repo: Repository) -> String {
        var parts: [String] = []
        if repo.isOfficial {
            parts.append(String(localized: "Official"))
        }
        if repo.id == device.device.repositoryId {
            parts.append(String(localized: "Installed"))
        }
        parts.append(repo.ownerAndRepo)
        return parts.joined(separator: " • ")
    }

    @ViewBuilder
    private var versionsContent: some View {
        if viewModel.isLoadingVersions && viewModel.filteredVersions.isEmpty {
            HStack {
                Spacer()
                ProgressView("Loading versions…")
                    .padding(.vertical, 16)
                Spacer()
            }
        } else if viewModel.loadError && viewModel.filteredVersions.isEmpty {
            VStack(spacing: 12) {
                Text("Could not load versions from GitHub")
                    .multilineTextAlignment(.center)
                Button("Retry", action: viewModel.refreshSelectedRepository)
                    .buttonStyle(.bordered)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
        } else if viewModel.filteredVersions.isEmpty {
            Text("No releases found for this channel")
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.vertical, 16)
        } else {
            ForEach(viewModel.filteredVersions, id: \.browserRowId) { item in
                let installed = device.hasInstalled(item)
                NavigationLink(
                    value: DeviceDetailRoute.firmwareVersionDetails(
                        deviceId: device.id,
                        repositoryId: item.version.repositoryId,
                        tagName: item.version.tagName
                    )
                ) {
                    FirmwareVersionRow(
                        version: item.version,
                        isInstalled: installed
                    )
                }
            }
        }
    }
}

private struct FirmwareVersionRow: View {
    let version: Version
    let isInstalled: Bool

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: version.tagName)
                    .font(isInstalled ? .body.weight(.semibold) : .body)
                let subtitle = version.displaySubtitle
                if !subtitle.isEmpty {
                    Text(verbatim: subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer()

            if isInstalled {
                InstalledSourceBadge(showIcon: true)
            }
        }
        .padding(.vertical, 2)
    }
}

private struct OfficialSourceBadge: View {
    var body: some View {
        Text("Official")
            .lineLimit(1)
            .fixedSize()
            .font(.caption.weight(.medium))
            .foregroundStyle(.secondary)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(.secondary.opacity(0.15), in: Capsule())
    }
}

private struct InstalledSourceBadge: View {
    var showIcon: Bool = false

    var body: some View {
        Group {
            if showIcon {
                Label("Installed", systemImage: "checkmark.circle.fill")
            } else {
                Text("Installed")
            }
        }
        .lineLimit(1)
        .fixedSize()
        .font(.caption.weight(.medium))
        .foregroundStyle(.tint)
        .padding(.horizontal, showIcon ? 10 : 8)
        .padding(.vertical, showIcon ? 4 : 3)
        .background(.tint.opacity(0.15), in: Capsule())
    }
}

private extension VersionWithAssets {
    var browserRowId: String {
        "\(version.repositoryId):\(version.tagName)"
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        DeviceFirmwareBrowserView(
            device: PreviewData.deviceWithUpdate,
            database: PreviewData.database
        )
    }
}
#endif
