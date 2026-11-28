import Shared
import SwiftUI

enum DevicePickerSelectionMode {
    case single(selectedMac: String?, onSelect: (DeviceWithState) -> Void)
    case multi(selectedMacs: Set<String>, onSave: (Set<String>) -> Void)
}

struct DevicePickerSheet: View {
    let visibleDevices: [DeviceWithState]
    let onlineDevices: [DeviceWithState]
    let offlineDevices: [DeviceWithState]
    let showOfflineDevicesLast: Bool
    let selectionMode: DevicePickerSelectionMode
    let title: LocalizedStringKey

    @Environment(\.dismiss) private var dismiss
    @State private var draftSelection: Set<String> = []

    init(
        visibleDevices: [DeviceWithState],
        onlineDevices: [DeviceWithState],
        offlineDevices: [DeviceWithState],
        showOfflineDevicesLast: Bool,
        selectionMode: DevicePickerSelectionMode,
        title: LocalizedStringKey = "Select a Device"
    ) {
        self.visibleDevices = visibleDevices
        self.onlineDevices = onlineDevices
        self.offlineDevices = offlineDevices
        self.showOfflineDevicesLast = showOfflineDevicesLast
        self.selectionMode = selectionMode
        self.title = title
        if case let .multi(selectedMacs, _) = selectionMode {
            _draftSelection = State(initialValue: selectedMacs)
        }
    }

    private var visibleMacs: Set<String> {
        Set(visibleDevices.map { $0.device.macAddress })
    }

    private var allVisibleSelected: Bool {
        !visibleMacs.isEmpty && visibleMacs.isSubset(of: draftSelection)
    }

    private var anyVisibleSelected: Bool {
        !visibleMacs.isDisjoint(with: draftSelection)
    }

    var body: some View {
        NavigationStack {
            Group {
                if visibleDevices.isEmpty {
                    Text("You don't have any visible devices")
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(24)
                } else {
                    List {
                        if showOfflineDevicesLast {
                            if !onlineDevices.isEmpty {
                                Section {
                                    deviceRows(for: onlineDevices)
                                }
                            }
                            if !offlineDevices.isEmpty {
                                Section("Offline Devices") {
                                    deviceRows(for: offlineDevices)
                                }
                            }
                        } else {
                            deviceRows(for: visibleDevices)
                        }
                    }
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
                if case let .multi(_, onSave) = selectionMode {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") {
                            onSave(draftSelection)
                            dismiss()
                        }
                    }
                    if !visibleDevices.isEmpty {
                        ToolbarItemGroup(placement: .bottomBar) {
                            Button("Select All") {
                                draftSelection.formUnion(visibleMacs)
                            }
                            .disabled(allVisibleSelected)

                            Spacer()

                            Button("Deselect All") {
                                draftSelection.subtract(visibleMacs)
                            }
                            .disabled(!anyVisibleSelected)
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func deviceRows(for devices: [DeviceWithState]) -> some View {
        ForEach(devices) { device in
            let isSelected = isDeviceSelected(device)
            Button {
                handleTap(on: device)
            } label: {
                HStack(spacing: 12) {
                    if case .multi = selectionMode {
                        Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                            .foregroundStyle(isSelected ? AnyShapeStyle(.tint) : AnyShapeStyle(.secondary))
                            .font(.title3)
                            .accessibilityHidden(true)
                    }
                    DeviceInfoTwoRows(device: device)
                    if case .single = selectionMode, isSelected {
                        Image(systemName: "checkmark")
                            .foregroundStyle(.tint)
                            .fontWeight(.semibold)
                            .accessibilityHidden(true)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(isSelected ? .isSelected : [])
        }
    }

    private func isDeviceSelected(_ device: DeviceWithState) -> Bool {
        let mac = device.device.macAddress
        switch selectionMode {
        case let .single(selectedMac, _):
            return mac == selectedMac
        case .multi:
            return draftSelection.contains(mac)
        }
    }

    private func handleTap(on device: DeviceWithState) {
        switch selectionMode {
        case let .single(_, onSelect):
            onSelect(device)
            dismiss()
        case .multi:
            let mac = device.device.macAddress
            if draftSelection.contains(mac) {
                draftSelection.remove(mac)
            } else {
                draftSelection.insert(mac)
            }
        }
    }
}
