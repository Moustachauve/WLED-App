import MarkdownUI
import Shared
import SwiftUI

private typealias State = SwiftUI.State

struct DeviceEditView: View {
    @State private var viewModel: DeviceEditViewModel
    @State private var shouldStartBetaInstall = false
    private var device: DeviceWithState
    private let database: AppDatabase

    init(
        device: DeviceWithState,
        database: AppDatabase = .shared
    ) {
        _viewModel = State(wrappedValue: DeviceEditViewModel(
            device: device,
            database: database
        ))

        self.device = device
        self.database = database
    }

    private var showInstallingCover: Binding<Bool> {
        Binding(
            get: { viewModel.isLoadingLanguageUpdate || viewModel.installingVersion != nil },
            set: { isPresented in
                guard !isPresented else { return }
                if viewModel.isLoadingLanguageUpdate {
                    viewModel.cancelLanguageSwitchLoading()
                } else if viewModel.installingVersion != nil {
                    viewModel.handleInstallDialogDismissed(didComplete: false)
                }
            }
        )
    }

    private var ineligibilityReason: UpdateIneligibilityReason? {
        (device.stateInfo?.info).flatMap {
            ReleaseService.companion.getIneligibilityReason(deviceInfo: $0)
        }
    }

    private var canChangeLanguage: Bool {
        device.isOnline && device.stateInfo != nil && ineligibilityReason == nil
    }

    private var languageDisabledMessage: LocalizedStringKey? {
        guard !canChangeLanguage else { return nil }
        if !device.isOnline || device.stateInfo == nil {
            return "The device must be online to change its language."
        }
        switch ineligibilityReason {
        case .notIllumidel: return "The language can only be changed on Illumidel controllers."
        case .otaDisabled: return "OTA updates are disabled on this device, so its language can't be changed."
        case nil: return nil
        }
    }

    private var updateDisabledMessage: LocalizedStringKey? {
        switch ineligibilityReason {
        case .notIllumidel: return "Firmware updates are only available for Illumidel controllers."
        case .otaDisabled: return "OTA updates are disabled on this device, so its firmware can't be updated from the app."
        case nil: return nil
        }
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

                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text("Language")
                        Spacer()
                        Picker("Language", selection: $viewModel.branch) {
                            ForEach([Branch.stable, Branch.beta]) { branch in
                                Text(LocalizedStringKey(branch.nameKey))
                                    .tag(branch)
                                    .padding()
                            }
                        }
                        .pickerStyle(.segmented)
                        .fixedSize()
                        .disabled(!canChangeLanguage)
                    }
                    if let message = languageDisabledMessage {
                        Text(message)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(.bottom)

                Card {
                    if !device.isOnline && device.stateInfo == nil {
                        DeviceOfflineUpdateStatus()
                    } else if let message = updateDisabledMessage {
                        Text(message)
                            .font(.callout)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    } else if (device.availableUpdateVersion ?? "").isEmpty {
                        DeviceNoUpdateAvailable(
                            device: device,
                            isCheckingForUpdates: viewModel.isCheckingForUpdates
                        ) {
                            await viewModel.checkForUpdate()
                        }
                    } else {
                        DeviceUpdateAvailable(device: device)
                    }
                }
                .animation(.default, value: device.availableUpdateVersion)
                .animation(.default, value: viewModel.isCheckingForUpdates)

                if let betaVersion = viewModel.availableBetaVersion {
                    BetaUpdateAvailableCard(
                        betaVersion: betaVersion,
                        isOnline: device.isOnline,
                        onTryBeta: {
                            viewModel.showBetaDisclaimerSheet = true
                        }
                    )
                    .padding(.top, 4)
                }

                Text("Mac Address: \(device.device.macAddress)")
                    .font(.caption)
                    .frame(maxWidth: .infinity, alignment: .leading)

                Spacer()
            }
            .padding()
            .padding(.bottom, 100)
        }
        .task(id: device.device.repositoryId) {
            await viewModel.observeBetaVersions(repositoryId: device.device.repositoryId)
        }
        .onChange(of: device.device.branch) { _, newBranch in
            viewModel.syncBranchFromDevice(newBranch)
        }
        .sheet(
            isPresented: $viewModel.showBetaDisclaimerSheet,
            onDismiss: {
                if shouldStartBetaInstall {
                    shouldStartBetaInstall = false
                    viewModel.startBetaInstall()
                }
            },
            content: {
                if let betaVersion = viewModel.availableBetaVersion {
                    BetaUpdateDisclaimerSheet(
                        version: betaVersion,
                        isOnline: device.isOnline,
                        onCancel: {
                            viewModel.showBetaDisclaimerSheet = false
                        },
                        onAccept: {
                            shouldStartBetaInstall = true
                            viewModel.showBetaDisclaimerSheet = false
                        }
                    )
                }
            }
        )
        .fullScreenCover(isPresented: showInstallingCover) {
            if let installingVersion = viewModel.installingVersion {
                DeviceUpdateInstalling(
                    device: device,
                    version: installingVersion,
                    targetBranch: viewModel.installTargetBranch,
                    database: database,
                    onDismissResult: { didSucceed in
                        viewModel.handleInstallDialogDismissed(didComplete: didSucceed)
                    }
                )
                .background(BackgroundBlurView())
            } else {
                LanguageUpdateLoadingModal {
                    viewModel.cancelLanguageSwitchLoading()
                }
                .background(BackgroundBlurView())
            }
        }
        .navigationTitle("Edit Device")
        .navigationBarTitleDisplayMode(.large)
    }
}

private struct LanguageUpdateLoadingModal: View {
    let onCancel: () -> Void

    var body: some View {
        ZStack {
            Color(.clear)
            VStack(spacing: 10) {
                ProgressView()
                Text("Loading...")
                Button("Cancel", action: onCancel)
                    .buttonStyle(.plain)
            }
            .fixedSize(horizontal: false, vertical: true)
            .padding()
            .background(Color(UIColor.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .shadow(radius: 20)
        }
    }
}

// MARK: - Beta 2.0 Migration Card & Disclaimer Sheet

struct BetaUpdateAvailableCard: View {
    let betaVersion: VersionWithAssets
    var isOnline: Bool = true
    let onTryBeta: () -> Void

    var body: some View {
        Card {
            HStack(spacing: 8) {
                ExperimentIcon()
                    .fill(Color.accentColor)
                    .frame(width: 22, height: 22)
                    .accessibilityHidden(true)
                Text(String(format: String(localized: "beta_version_available"), betaVersion.version.tagName.prefixedWithV))
                    .font(.headline)
                    .foregroundStyle(Color.accentColor)
            }

            if let url = URL(string: String(localized: "beta_cloudapp_url")) {
                Link(destination: url) {
                    HStack(spacing: 6) {
                        Text("learn_more_beta_app")
                            .underline()
                            .multilineTextAlignment(.leading)
                        Image(systemName: "link")
                    }
                    .font(.footnote)
                    .foregroundStyle(Color.accentColor)
                }
            }

            Text("beta_migration_card_warning")
                .font(.footnote)
                .foregroundStyle(.secondary)

            Button(action: onTryBeta) {
                Text("try_new_beta_version")
            }
            .buttonStyle(.bordered)
            .disabled(!isOnline)
        }
    }
}

struct BetaUpdateDisclaimerSheet: View {
    let version: VersionWithAssets
    var isOnline: Bool = true
    let onCancel: () -> Void
    let onAccept: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 8) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundStyle(.red)
                        Text(String(format: String(localized: "beta_update_disclaimer_title"), version.version.tagName.prefixedWithV))
                            .font(.title3)
                            .bold()
                    }

                    Text("beta_update_disclaimer_warning")
                        .fontWeight(.bold)
                        .foregroundStyle(.white)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.red.opacity(0.85))
                        .clipShape(RoundedRectangle(cornerRadius: 8))

                    let description = version.version.descriptionText
                    if !description.isEmpty {
                        Divider()
                        Markdown(description)
                    }
                }
                .padding()
            }
            .safeAreaInset(edge: .bottom) {
                HStack {
                    Button("Cancel", role: .cancel, action: onCancel)
                        .buttonStyle(.bordered)
                    Spacer()
                    Button(role: .destructive, action: onAccept) {
                        Text("Install Now")
                            .bold()
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.red)
                    .disabled(!isOnline)
                }
                .padding()
                .background(.bar)
            }
        }
    }
}

struct ExperimentIcon: Shape {
    func path(in rect: CGRect) -> Path {
        let scaleX = rect.width / 960.0
        let scaleY = rect.height / 960.0
        func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: rect.minX + x * scaleX, y: rect.minY + y * scaleY)
        }
        var path = Path()
        // Outer flask contour (Material Symbols `experiment` 960x960 viewport)
        path.move(to: pt(200, 840))
        path.addQuadCurve(to: pt(127.5, 794.5), control: pt(149, 840))
        path.addQuadCurve(to: pt(138, 710), control: pt(106, 749))
        path.addLine(to: pt(360, 440))
        path.addLine(to: pt(360, 200))
        path.addLine(to: pt(320, 200))
        path.addQuadCurve(to: pt(291.5, 188.5), control: pt(303, 200))
        path.addQuadCurve(to: pt(280, 160), control: pt(280, 177))
        path.addQuadCurve(to: pt(291.5, 131.5), control: pt(280, 143))
        path.addQuadCurve(to: pt(320, 120), control: pt(303, 120))
        path.addLine(to: pt(640, 120))
        path.addQuadCurve(to: pt(668.5, 131.5), control: pt(657, 120))
        path.addQuadCurve(to: pt(680, 160), control: pt(680, 143))
        path.addQuadCurve(to: pt(668.5, 188.5), control: pt(680, 177))
        path.addQuadCurve(to: pt(640, 200), control: pt(657, 200))
        path.addLine(to: pt(600, 200))
        path.addLine(to: pt(600, 440))
        path.addLine(to: pt(822, 710))
        path.addQuadCurve(to: pt(832.5, 794.5), control: pt(854, 749))
        path.addQuadCurve(to: pt(760, 840), control: pt(811, 840))
        path.addLine(to: pt(200, 840))
        path.closeSubpath()

        // Liquid fill trapezoid inside flask
        path.move(to: pt(280, 720))
        path.addLine(to: pt(680, 720))
        path.addLine(to: pt(544, 560))
        path.addLine(to: pt(416, 560))
        path.addLine(to: pt(280, 720))
        path.closeSubpath()

        // Inner cutout (counter-clockwise for non-zero winding rule)
        path.move(to: pt(200, 760))
        path.addLine(to: pt(760, 760))
        path.addLine(to: pt(520, 468))
        path.addLine(to: pt(520, 200))
        path.addLine(to: pt(440, 200))
        path.addLine(to: pt(440, 468))
        path.addLine(to: pt(200, 760))
        path.closeSubpath()

        return path
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
        Text("Your device is up to date")
        Text(
            "Version \(device.stateInfo?.info.version ?? String(localized: "unknown_version"))"
        )
        HStack {
            Button(
                action: {
                    Task {
                        await onCheckForUpdate()
                    }
                },
                label: {
                    Text(isCheckingForUpdates ? "Checking for Updates" : "Check for Update")
                }
            )
            .buttonStyle(.bordered)
            .padding(.trailing)
            .disabled(isCheckingForUpdates)
            ProgressView()
                .opacity(isCheckingForUpdates ? 1 : 0)
        }
    }
}

// MARK: - Device Update Available

struct DeviceUpdateAvailable: View {

    var device: DeviceWithState

    private let unknownVersion = String(localized: "unknown_version")

    var body: some View {
        HStack {
            Image(systemName: getUpdateIconName())
                .resizable()
                // swiftlint:disable:next all
                .aspectRatio(contentMode: .fill)
                .frame(width: 30.0, height: 30.0)
                .padding(.trailing)
            VStack(alignment: .leading) {
                Text("Update Available")
                Text("From \(device.stateInfo?.info.version ?? unknownVersion) to \(device.availableUpdateVersion ?? unknownVersion)")
                NavigationLink(value: DeviceDetailRoute.updateDetails(deviceId: device.id)) {
                    Text("See Update")
                }
            }
        }
        .buttonStyle(.borderedProminent)
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
