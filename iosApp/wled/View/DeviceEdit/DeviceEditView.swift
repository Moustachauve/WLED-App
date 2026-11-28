import SwiftUI
import Shared

private typealias State = SwiftUI.State

struct DeviceEditView: View {
    @State private var viewModel: DeviceEditViewModel
    private var device: DeviceWithState

    init(
        device: DeviceWithState,
        database: AppDatabase = .shared
    ) {
        _viewModel = State(wrappedValue: DeviceEditViewModel(
            device: device,
            database: database
        ))

        self.device = device
    }

    // MARK: - body

    var body: some View {
        Form {
            Section {
                DeviceInfoCard(device: device)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
            }

            Section("Custom Name") {
                TextField("Custom Name", text: $viewModel.customName)
                    .submitLabel(.done)
            }

            Section {
                Toggle("Hide this Device", isOn: $viewModel.hideDevice)

                LabeledContent("Update Channel") {
                    Picker("Update Channel", selection: $viewModel.branch) {
                        ForEach(Branch.allCases.filter { $0 != .unknown }) { branch in
                            Text(LocalizedStringKey(branch.nameKey))
                                .tag(branch)
                        }
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                    .fixedSize()
                }
            }

            Section {
                if !device.isOnline && device.stateInfo == nil {
                    DeviceOfflineUpdateStatus()
                } else if (device.availableUpdateVersion ?? "").isEmpty {
                    DeviceNoUpdateAvailable(
                        device: device,
                        isCheckingForUpdates: viewModel.isCheckingForUpdates
                    ) {
                        await viewModel.checkForUpdate()
                    }
                } else {
                    DeviceUpdateAvailable(device: device)
                    NavigationLink(value: DeviceDetailRoute.updateDetails(deviceId: device.id)) {
                        Text("See Update")
                    }
                }

                NavigationLink(value: DeviceDetailRoute.firmwareBrowser(deviceId: device.id)) {
                    Text("Other Versions & Sources")
                }

                if let repository = viewModel.repository {
                    LabeledContent("Update Source") {
                        Text(repository.ownerAndRepo)
                            .lineLimit(1)
                    }
                }
            } footer: {
                Text("Mac Address: \(device.device.macAddress)")
            }
            .animation(.default, value: device.availableUpdateVersion)
            .animation(.default, value: viewModel.isCheckingForUpdates)
        }
        .holidayFormSurface()
        .task(id: device.device.repositoryId) {
            await viewModel.loadRepository(id: device.device.repositoryId)
        }
        .navigationTitle("Edit Device")
        .navigationBarTitleDisplayMode(.large)
    }
}

// MARK: - Device Offline Update Status

struct DeviceOfflineUpdateStatus: View {
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "wifi.slash")
                .font(.title2)
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text("Unable to check for updates")
                    .font(.headline)
                Text("The device must be online to verify its current version and check for available updates.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

// MARK: - Device No Update Available

struct DeviceNoUpdateAvailable: View {

    var device: DeviceWithState
    let isCheckingForUpdates: Bool
    let onCheckForUpdate: () async -> Void

    @ViewBuilder
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("Your device is up to date")
                .font(.headline)
            Text(
                "Version \(device.stateInfo?.info.version ?? String(localized: "unknown_version"))"
            )
            .font(.subheadline)
            .foregroundStyle(.secondary)
        }
        Button {
            Task {
                await onCheckForUpdate()
            }
        } label: {
            HStack(spacing: 8) {
                if isCheckingForUpdates {
                    ProgressView()
                        .controlSize(.small)
                }
                Text(isCheckingForUpdates ? "Checking for Updates" : "Check for Update")
            }
        }
        .disabled(isCheckingForUpdates)
    }
}

// MARK: - Device Update Available

struct DeviceUpdateAvailable: View {

    var device: DeviceWithState

    private let unknownVersion = String(localized: "unknown_version")

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: getUpdateIconName())
                .resizable()
                .scaledToFit()
                .frame(width: 30.0, height: 30.0)
                .foregroundStyle(.tint)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text("Update Available")
                    .font(.headline)
                Text("From \(device.stateInfo?.info.version ?? unknownVersion) to \(device.availableUpdateVersion ?? unknownVersion)")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func getUpdateIconName() -> String {
        "arrow.down.circle.dotted"
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        DeviceEditView(device: PreviewData.onlineDevice, database: PreviewData.database)
    }
}
#endif
