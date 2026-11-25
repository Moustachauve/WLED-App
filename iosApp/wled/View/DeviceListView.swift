import StoreKit
import SwiftUI
import Shared

private typealias State = SwiftUI.State

struct DeviceListView: View {

    // MARK: - Properties
    @State private var viewModel: DeviceWebsocketListViewModel

    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.requestReview) private var requestReview
    @State private var navigation: DeviceListNavigation

    @State private var addDeviceButtonActive: Bool = false
    @State private var showSettingsSheet: Bool = false
    @State private var devicePendingDeletion: DeviceWithState?
    @State private var pendingDeepLinkMac: String?
    @State private var columnVisibility = NavigationSplitViewVisibility.doubleColumn
    @State private var updatePrompt: UpdatePrompt.Prompt?
    @State private var showUpdatePrompt = false

    @AppStorage("lastSelectedDeviceMac") private var lastSelectedDeviceMac: String = ""

    private var hasHiddenDevices: Bool {
        viewModel.allDevicesWithState.contains { $0.device.isHidden }
    }

    private var updatePromptKey: [String] {
        viewModel.allDevicesWithState.compactMap { device in
            guard device.isOnline,
                  !device.device.isHidden,
                  let tag = device.availableUpdateVersion,
                  !tag.isEmpty
            else {
                return nil
            }
            return "\(device.id):\(tag)"
        }
    }

    // Same navy as the V1 controller page (#12305A -> #0A1222 -> #070B14)
    private var illumidelGradient: RadialGradient {
        RadialGradient(
            colors: [IL.navyTop, IL.navyMid, IL.navyBottom],
            center: UnitPoint(x: 0.5, y: -0.1),
            startRadius: 0,
            endRadius: 700
        )
    }

    // MARK: - init

    init(
        viewModel: DeviceWebsocketListViewModel,
        navigation: DeviceListNavigation = DeviceListNavigation()
    ) {
        _viewModel = State(wrappedValue: viewModel)
        _navigation = State(wrappedValue: navigation)
    }

    // Allow injecting a specific database (defaulting to shared for the actual app)
    init(
        database: AppDatabase = .shared,
        connectionManager: DeviceConnectionManager? = nil
    ) {
        let viewModel = DeviceWebsocketListViewModel(
            connectionManager: connectionManager,
            database: database
        )
        self.init(viewModel: viewModel)
    }

    // MARK: - Body

    var body: some View {
        NavigationSplitView(columnVisibility: $columnVisibility) {
            list
                .toolbar { toolbar }
                .sheet(isPresented: $addDeviceButtonActive) {
                    DeviceAddView()
                }
                .sheet(isPresented: $showSettingsSheet) {
                    Settings(
                        showHiddenDevices: $viewModel.showHiddenDevices,
                        showOfflineDevicesLast: $viewModel.showOfflineDevicesLast,
                        autoDiscovery: $viewModel.autoDiscovery
                    )
                }
                .navigationBarTitleDisplayMode(.inline)
        } detail: {
            detailView
        }
        .navigationSplitViewStyle(.balanced)
        .overlay {
            if let pendingDevice = devicePendingDeletion {
                ConfirmDeleteDeviceDialog(
                    device: pendingDevice,
                    onConfirm: {
                        withAnimation {
                            confirmDelete(device: pendingDevice)
                        }
                    },
                    onCancel: {
                        withAnimation(.snappy(duration: 0.2)) {
                            devicePendingDeletion = nil
                        }
                    }
                )
                .transition(.opacity.combined(with: .scale(scale: 0.95)))
            }
        }
        .task {
            await viewModel.observe()
        }
        .task {
            // Native "Rate this app" sheet, a few seconds after the list is shown (rules in ReviewPrompt)
            try? await Task.sleep(for: .seconds(4))
            // Never stack the rating sheet on top of the update popup
            if !UpdatePrompt.shownThisSession,
               ReviewPrompt.shouldAsk(deviceCount: viewModel.allDevicesWithState.count) {
                ReviewPrompt.markAsked()
                requestReview()
            }
        }
        .onAppear(perform: appearAction)
        .alert(
            Text("Update \(updatePrompt?.tag ?? "") available"),
            isPresented: $showUpdatePrompt,
            presenting: updatePrompt
        ) { prompt in
            Button("See update") {
                if let device = prompt.devices.first {
                    openEditDevice(device)
                }
            }
            Button("Later", role: .cancel) {}
        } message: { prompt in
            if prompt.devices.count == 1, let device = prompt.devices.first {
                Text("\(device.device.displayName) can be updated.")
            } else {
                Text("\(prompt.devices.count) controllers can be updated. Open each one to install it.")
            }
        }
        .onOpenURL { url in
            handleOpenURL(url)
        }
        .onChange(of: scenePhase) { _, newPhase in
            switch newPhase {
            case .active:
                viewModel.onResume()
            case .background:
                viewModel.onPause()
            case .inactive:
                break // Don't disconnect during transient gestures (app switcher, notifications)
            @unknown default:
                break
            }
        }
        .onChange(of: updatePromptKey) { _, _ in
            checkUpdatePrompt(viewModel.allDevicesWithState)
        }
        .onChange(of: viewModel.allDevicesWithState) { _, devices in
            if let pendingMac = pendingDeepLinkMac, !devices.isEmpty {
                pendingDeepLinkMac = nil
                if selectDevice(withMacAddress: pendingMac, from: devices) {
                    return
                }
            }
            restoreLastSelection(from: devices)
        }
        .onChange(of: navigation.selection) { _, newSelection in
            if let mac = newSelection?.device.macAddress {
                lastSelectedDeviceMac = mac
            }
        }
        // Listen for layout changes (e.g. iPad rotation or window resizing)
        // If the user expands the window, we want to fill the empty space immediately.
        .onChange(of: horizontalSizeClass) { _, newSizeClass in
            restoreLastSelection(
                from: viewModel.allDevicesWithState,
                currentSizeClass: newSizeClass
            )
        }
    }

    var list: some View {
        ZStack {
            List(selection: $navigation.selection) {
                if viewModel.localNetworkDenied {
                    Section {
                        LocalNetworkWarningView(
                            onOpenSettings: viewModel.openSettings
                        )
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets())
                        .listRowSeparator(.hidden)
                    }
                }

                if !viewModel.visibleDevices.isEmpty {
                    summaryBar
                        .listRowInsets(EdgeInsets(top: 4, leading: 20, bottom: 6, trailing: 20))
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)
                }

                if viewModel.showOfflineDevicesLast {
                    if !viewModel.onlineDevices.isEmpty {
                        deviceRows(for: viewModel.onlineDevices)
                    }

                    // Offline Devices
                    if !viewModel.offlineDevices.isEmpty {
                        Section(header:
                            Text("Offline Devices")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(IL.summary)
                        ) {
                            deviceRows(for: viewModel.offlineDevices)
                        }
                    }
                } else if !viewModel.visibleDevices.isEmpty {
                    deviceRows(for: viewModel.visibleDevices)
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .refreshable(action: refreshList)
            .animation(.default, value: viewModel.localNetworkDenied)

            if viewModel.visibleDevices.isEmpty && !viewModel.localNetworkDenied {
                EmptyDeviceListView(
                    addDeviceButtonActive: $addDeviceButtonActive,
                    showHiddenDevices: $viewModel.showHiddenDevices,
                    hasHiddenDevices: hasHiddenDevices
                )
                .transition(.opacity)
            }
        }
        .background(illumidelGradient.ignoresSafeArea())
        .toolbarColorScheme(.dark, for: .navigationBar)
        .animation(.default, value: viewModel.visibleDevices)
        .animation(.default, value: viewModel.onlineDevices)
        .animation(.default, value: viewModel.offlineDevices)
        .animation(.default, value: viewModel.showOfflineDevicesLast)
        .animation(.easeInOut, value: viewModel.showHiddenDevices)
        .navigationTitle("Device List")
    }

    @ViewBuilder
    private func deviceRows(for devices: [DeviceWithState]) -> some View {
        ForEach(devices) { device in
            DeviceListItemView(
                device: device,
                isSelected: navigation.selection == device,
                onTogglePower: { isOn in
                    viewModel.setDevicePower(for: device, isOn: isOn)
                },
                onChangeBrightness: { brightness in
                    viewModel.setBrightness(for: device, brightness: brightness)
                }
            )
            .onTapGesture {
                withAnimation {
                    navigation.select(device)
                }
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
            .listRowSeparator(.hidden)
            .buttonStyle(.plain)
            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                // Intentionally omit `role: .destructive` here so UIKit does not
                // optimistically collapse and re-insert the row before the user
                // confirms deletion in `ConfirmDeleteDeviceDialog`.
                Button {
                    withAnimation(.snappy(duration: 0.2)) {
                        devicePendingDeletion = device
                    }
                } label: {
                    Label("Delete", systemImage: "trash.fill")
                }
                .tint(.red)

                Button {
                    openEditDevice(device)
                } label: {
                    Label("Edit", systemImage: "pencil")
                }
                .tint(.blue)
            }
        }
    }

    @ViewBuilder
    private var detailView: some View {
        if let device = navigation.selection {
            NavigationStack(path: navigation.detailPathBinding) {
                DeviceView(
                    device: device,
                    onNavigateHome: {
                        navigation.clearSelection()
                    }
                )
            }
        } else {
            VStack(spacing: 12) {
                Image(.illumidelLogo)
                    .resizable()
                    .scaledToFit()
                    .frame(maxWidth: 180)
                    .accessibilityHidden(true)
                Text("Select A Device")
                    .font(.title2)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(illumidelGradient.ignoresSafeArea())
        }
    }
}

// MARK: - Subviews, Toolbar & Actions

private extension DeviceListView {
    // "6 controllers · 5 on"   [ Turn all off ]
    var summaryBar: some View {
        let devices = viewModel.visibleDevices
        let onlineDevices = devices.filter { $0.isOnline }
        let onCount = onlineDevices.filter { $0.stateInfo?.state.isOn?.boolValue ?? false }.count
        let anyOn = onCount > 0
        return HStack {
            Text("\(devices.count) controllers · \(onCount) on")
                .font(.system(size: 13))
                .foregroundStyle(IL.summary)
            Spacer()
            Button {
                for device in onlineDevices {
                    viewModel.setDevicePower(for: device, isOn: !anyOn)
                }
            } label: {
                Label(
                    anyOn ? LocalizedStringKey("Turn all off") : LocalizedStringKey("Turn all on"),
                    systemImage: "power"
                )
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 14)
                .frame(height: 34)
                .background(Color.white.opacity(0.05), in: Capsule())
                .overlay(Capsule().stroke(Color.white.opacity(0.12), lineWidth: 1))
            }
            .buttonStyle(.plain)
            .disabled(onlineDevices.isEmpty)
        }
    }
    @ToolbarContentBuilder
    var toolbar: some ToolbarContent {
        ToolbarItem(placement: .principal) {
            Image(.illumidelLogo)
                .resizable()
                .scaledToFit()
                .padding(3)
                .frame(height: 50)
                .accessibilityHidden(true)
        }
        ToolbarItemGroup(placement: .primaryAction) {
            // 1. Add Button (Direct Access)
            Button {
                addDeviceButtonActive.toggle()
            } label: {
                Label("Add Device", systemImage: "plus")
            }

            // 2. Settings Button
            Button {
                showSettingsSheet.toggle()
            } label: {
                Label("Settings", systemImage: "ellipsis.circle")
            }
        }
    }

    @Sendable
    func refreshList() async {
        viewModel.startDiscovery()
        viewModel.refreshOfflineDevices()
    }

    func appearAction() {
        ThemeSettings.dark.applyToWindows()
        viewModel.onResume()
    }

    func handleOpenURL(_ url: URL) {
        guard let macAddress = DeepLinkParser.parseMacAddress(url) else { return }
        let devices = viewModel.allDevicesWithState
        pendingDeepLinkMac = (selectDevice(withMacAddress: macAddress, from: devices) || !devices.isEmpty)
            ? nil
            : macAddress
    }

    func selectDevice(withMacAddress macAddress: String, from devices: [DeviceWithState]) -> Bool {
        guard let matchingDevice = devices.first(where: {
            DeepLinkParser.matchesMacAddress($0.device.macAddress, macAddress)
        }) else {
            return false
        }
        withAnimation {
            navigation.select(matchingDevice)
        }
        return true
    }

    func checkUpdatePrompt(_ devices: [DeviceWithState]) {
        guard !showUpdatePrompt, let prompt = UpdatePrompt.check(devices) else { return }
        UpdatePrompt.markShown(prompt.tag)
        updatePrompt = prompt
        showUpdatePrompt = true
    }

    func openEditDevice(_ device: DeviceWithState) {
        withAnimation {
            navigation.openEdit(device)
        }
    }

    func confirmDelete(device: DeviceWithState) {
        devicePendingDeletion = nil
        deleteItems(device: device.device)
        let remainingDevices = viewModel.visibleDevices.filter { $0 != device }
        // Call restore in case the selected item was deleted, this
        // will select another device (most likely the first one)
        restoreLastSelection(from: remainingDevices)
    }

    func deleteItems(device: Device) {
        if navigation.selection?.device == device {
            navigation.clearSelection()
        }
        viewModel.deleteDevice(device)
    }

    // MARK: - Automatic device selection

    func restoreLastSelection(
        from devices: [DeviceWithState],
        currentSizeClass: UserInterfaceSizeClass? = nil
    ) {
        // Use the passed-in class if available, otherwise use the environment value
        let sizeClass = currentSizeClass ?? horizontalSizeClass
        // Only run if we are in a wide layout (Split View is active)
        // This prevents auto-navigation on iPhone or iPad narrow multitasking.
        guard sizeClass == .regular else { return }
        // IMPORTANT: Only try to restore if NOTHING is currently selected.
        // This prevents us from overriding a device the user just clicked on,
        // while ensuring we fill the "empty" screen if it appears.
        guard navigation.selection == nil else { return }
        // Ensure there are devices to select
        guard !devices.isEmpty else { return }

        // Try to find the last selected device by MAC address
        if let lastDevice = devices.first(where: { $0.device.macAddress == lastSelectedDeviceMac }) {
            navigation.select(lastDevice)
        }
        // Fallback: Auto-select the first device
        else if let firstDevice = devices.first {
            navigation.select(firstDevice)
        }
    }
}

// MARK: - Confirm Delete Dialog

struct ConfirmDeleteDeviceDialog: View {
    var device: DeviceWithState
    var onConfirm: () -> Void
    var onCancel: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.4)
                .ignoresSafeArea()
                .onTapGesture(perform: onCancel)
                .accessibilityHidden(true)

            Card {
                Text("Delete Device?")
                    .font(.title3.bold())

                Card(style: .device(color: device.currentColor)) {
                    DeviceInfoTwoRows(device: device)
                }

                HStack {
                    Spacer()
                    Button("Cancel", role: .cancel, action: onCancel)
                        .buttonStyle(.bordered)
                        .keyboardShortcut(.cancelAction)
                    Button("Delete", role: .destructive, action: onConfirm)
                        .buttonStyle(.borderedProminent)
                        .tint(.red)
                }
            }
            .frame(maxWidth: 360)
            .shadow(radius: 20)
            .padding(.horizontal, 24)
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isModal)
            .accessibilityAction(.escape, onCancel)
        }
    }
}

#if DEBUG
#Preview {
    // Ensure some data exists in the preview context
    // swiftlint:disable redundant_discardable_let
    let _ = PreviewData.onlineDevice
    let _ = PreviewData.offlineDevice
    let _ = PreviewData.deviceWithUpdate
    let _ = PreviewData.hiddenDevice
    // swiftlint:enable redundant_discardable_let

    DeviceListView(
        database: PreviewData.database
    )
}
#endif
