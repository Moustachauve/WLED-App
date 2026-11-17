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
        ScrollView {
            VStack(spacing: 10) {
                Card(style: .device(color: device.currentColor)) {
                    DeviceInfoTwoRows(device: device)
                }
                .listRowInsets(EdgeInsets(top: 4, leading: 8, bottom: 4, trailing: 8))

                VStack(alignment: .leading) {
                    Text("Custom Name")
                    TextField("Custom Name", text: $viewModel.customName)
                        .submitLabel(.done)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                }

                Toggle("Hide this Device", isOn: $viewModel.hideDevice)
                    .padding(.trailing, 2)
                    .padding(.bottom)

                HStack {
                    Text("Update Channel")
                    Spacer()
                    Picker("Update Channel", selection: $viewModel.branch) {
                        ForEach(Branch.allCases.filter { $0 != .unknown }) { branch in
                            Text(LocalizedStringKey(branch.nameKey))
                                .tag(branch)
                                .padding()
                        }
                    }
                    .pickerStyle(.segmented)
                    .fixedSize()
                }
                .padding(.bottom)

                Card {
                    if !device.isOnline && device.stateInfo == nil {
                        DeviceOfflineUpdateStatus()
                    } else if (device.availableUpdateVersion ?? "").isEmpty {
                        DeviceNoUpdateAvailable(
                            device: device,
                            isCheckingForUpdates: viewModel.isCheckingForUpdates,
                            onCheckForUpdate: {
                                await viewModel.checkForUpdate()
                            }
                        )
                    } else {
                        DeviceUpdateAvailable(device: device)
                    }

                    if let repository = viewModel.repository {
                        Divider()
                        HStack {
                            Text("Update Source")
                            Spacer()
                            Text(repository.ownerAndRepo)
                                .lineLimit(1)
                        }
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    }
                }
                .animation(.default, value: device.availableUpdateVersion)

                Text("Mac Address: \(device.device.macAddress)")
                    .font(.caption)
                    .frame(maxWidth: .infinity, alignment: .leading)

                Spacer()
            }
            .padding()
            .padding(.bottom, 100)
        }
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

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Your device is up to date")
            Text(
                "Version \(device.stateInfo?.info.version ?? String(localized: "unknown_version"))"
            )
            VStack(spacing: 8) {
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
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(isCheckingForUpdates)

                OtherVersionsLink(deviceId: device.id)
            }
            .padding(.top, 2)
        }
    }
}

// MARK: - Device Update Available

struct DeviceUpdateAvailable: View {

    var device: DeviceWithState

    private let unknownVersion = String(localized: "unknown_version")

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Image(systemName: getUpdateIconName())
                    .resizable()
                    // TODO: Migrate legacy aspectRatio to scaledToFill in a dedicated follow-up PR
                    // swiftlint:disable:next all
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 30.0, height: 30.0)
                    .padding(.trailing)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Update Available")
                    Text("From \(device.stateInfo?.info.version ?? unknownVersion) to \(device.availableUpdateVersion ?? unknownVersion)")
                }
            }
            VStack(spacing: 8) {
                NavigationLink(value: DeviceDetailRoute.updateDetails(deviceId: device.id)) {
                    Text("See Update")
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)

                OtherVersionsLink(deviceId: device.id)
            }
        }
    }

    private func getUpdateIconName() -> String {
        "arrow.down.circle.dotted"
    }
}

private struct OtherVersionsLink: View {
    let deviceId: String

    var body: some View {
        NavigationLink(value: DeviceDetailRoute.firmwareBrowser(deviceId: deviceId)) {
            Text("Other Versions & Sources")
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 4)
                .contentShape(Rectangle())
        }
        .buttonStyle(.borderless)
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        DeviceEditView(device: PreviewData.onlineDevice, database: PreviewData.database)
    }
}
#endif
