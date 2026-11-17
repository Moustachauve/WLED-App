import SwiftUI
import Shared

private typealias State = SwiftUI.State

struct DeviceUpdateInstalling: View {
    @Environment(\.dismiss) private var dismiss

    @State private var viewModel = DeviceUpdateInstallingViewModel()
    @State private var retryWithoutValidation = false
    var device: DeviceWithState
    let version: VersionWithAssets
    var selectedAsset: Asset?
    var database: AppDatabase = .shared
    var onInstallCompleted: () -> Void = {}

    var body: some View {
        ZStack {
            Color(.clear)
            VStack {
                Text("Updating \(device.device.displayName)")
                    .font(.title2)
                    .bold()
                    .padding(.top)
                    .padding(.trailing)
                    .padding(.leading)

                switch viewModel.status {
                case .idle:
                    UpdateProgressStatusView(statusString: String(localized: "Starting Up"))
                case .downloading(let versionName, let progress):
                    UpdateProgressStatusView(
                        statusString: String(localized: "Downloading Version"),
                        versionName: versionName,
                        progress: progress
                    )
                case .installing(let versionName):
                    UpdateProgressStatusView(
                        statusString: String(localized: "Installing Update"),
                        versionName: versionName
                    )
                case .completed:
                    SuccessView()
                case .failed(let versionName, let error, let canRetryWithoutValidation):
                    FailureView(
                        errorMessage: error,
                        versionName: versionName,
                        canRetryWithoutValidation: canRetryWithoutValidation
                    )
                }

                actionButtons
                    .padding(.top)
            }
            .fixedSize(horizontal: false, vertical: true)
            .padding()
            .background(Color(UIColor.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .shadow(radius: 20)
            .padding(.horizontal)
            .task(id: retryWithoutValidation) {
                await viewModel.startUpdateProcess(
                    device: device,
                    version: version,
                    selectedAsset: selectedAsset,
                    skipValidation: retryWithoutValidation,
                    database: database
                )
            }
        }
    }

    @ViewBuilder
    private var actionButtons: some View {
        if case .failed(_, _, let canRetry) = viewModel.status, canRetry {
            HStack(spacing: 16) {
                Button("Cancel") {
                    dismiss()
                }
                Button("Install Anyway") {
                    retryWithoutValidation = true
                }
                .buttonStyle(.borderedProminent)
                .tint(.red)
            }
        } else {
            Button {
                if viewModel.status == .completed {
                    onInstallCompleted()
                }
                dismiss()
            } label: {
                Text(dismissButtonText)
            }
            .disabled(!canDismiss)
        }
    }

    var canDismiss: Bool {
        switch viewModel.status {
        case .completed, .failed:
            return true
        default:
            return false
        }
    }

    var dismissButtonText: LocalizedStringKey {
        switch viewModel.status {
        case .completed, .failed:
            return LocalizedStringKey("Done")
        default:
            return LocalizedStringKey("Cancel")
        }
    }
}

// MARK: - Update progress status view

struct UpdateProgressStatusView: View {
    let statusString: String
    var versionName: String = ""
    var progress: Int?

    var body: some View {
        if let progress {
            let clampedProgress = min(max(progress, 0), 100)
            ProgressView(value: Double(clampedProgress), total: 100) {
                Text(clampedProgress, format: .percent)
                    .font(.caption)
                    .monospacedDigit()
                    .frame(maxWidth: .infinity, alignment: .center)
            }
            .progressViewStyle(.linear)
            .padding(.horizontal)
            .padding(.bottom, 5)
        } else {
            ProgressView()
                .controlSize(.large)
                .padding(.bottom, 5)
        }

        Text(statusString)
            .font(.title3)
            .bold()

        if !versionName.isEmpty {
            Text(versionName)
                .font(.callout)
        }

        Text("Please do not close the app or turn off the device.")
            .multilineTextAlignment(.center)
            .padding()
    }
}

// MARK: - Success view

struct SuccessView: View {
    var body: some View {
        Image(systemName: "checkmark.seal.fill")
            .resizable()
            .foregroundColor(.green)
            .frame(width: 32.0, height: 32.0)
            .padding(.bottom, 5)

        Text("Update Completed!")
            .font(.title3)
            .bold()
    }
}

// MARK: - Failure view

struct FailureView: View {
    let errorMessage: String
    var versionName: String = ""
    var canRetryWithoutValidation: Bool = false

    var body: some View {
        Image(systemName: "exclamationmark.octagon.fill")
            .resizable()
            .foregroundColor(.red)
            .frame(width: 32.0, height: 32.0)
            .padding(.bottom, 5)

        Text("Update Failed")
            .font(.title3)
            .bold()

        if !versionName.isEmpty {
            Text(versionName)
                .font(.callout)
        }

        Text(errorMessage)
            .multilineTextAlignment(.center)
            .padding()

        if canRetryWithoutValidation {
            Text(
                "Bypassing firmware validation can make your device unreachable over Wi-Fi if this binary does not match your hardware."
            )
            .font(.caption)
            .foregroundStyle(.red)
            .multilineTextAlignment(.center)
            .padding(.horizontal)
        }
    }
}

#if DEBUG
#Preview {
    DeviceUpdateInstalling(
        device: PreviewData.deviceWithUpdate,
        version: PreviewData.previewVersion,
        database: PreviewData.database
    )
}
#endif
