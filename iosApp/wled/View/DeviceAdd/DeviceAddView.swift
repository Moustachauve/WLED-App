import SwiftUI
import Shared

private typealias State = SwiftUI.State

struct DeviceAddView: View {
    @Environment(\.dismiss) var dismiss

    @State private var viewModel = DeviceAddViewModel()

    var body: some View {
        NavigationStack {
            Group {
                switch viewModel.currentStep {
                case .form(let errorMessage):
                    DeviceAddStep1FormView(
                        viewModel: viewModel,
                        errorMessage: errorMessage
                    )
                case .adding:
                    VStack {
                        DeviceAddStep2LoadingView(address: viewModel.address)
                        Spacer()
                    }
                    .padding()
                case .success(let device):
                    VStack {
                        DeviceAddStep3Success(device: device)
                        Spacer()
                    }
                    .padding()
                }
            }
            .animation(.easeInOut, value: viewModel.currentStep)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", systemImage: "xmark") {
                        dismiss()
                    }
                }
                if viewModel.currentStep.isForm {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Add", systemImage: "checkmark") {
                            withAnimation {
                                viewModel.submitCreateDevice()
                            }
                        }
                    }
                }
            }
            .navigationTitle("New Device")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium])
    }
}

// MARK: - Step 1: Form

struct DeviceAddStep1FormView: View {

    @Bindable var viewModel: DeviceAddViewModel
    @FocusState private var focusedField: Field?

    let errorMessage: String

    var body: some View {
        Form {
            Section {
                TextField("IP Address or URL", text: $viewModel.address)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.done)
                    .focused($focusedField, equals: .address)
                    .onSubmit {
                        withAnimation {
                            viewModel.submitCreateDevice()
                        }
                    }
            } header: {
                Text("IP Address or URL")
            } footer: {
                if !errorMessage.isEmpty {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                }
            }
        }
        .onAppear {
            focusedField = .address
        }
    }

    enum Field: Hashable {
        case address
    }
}

// MARK: - Step 2: Adding, Loading indicator

struct DeviceAddStep2LoadingView: View {
    let address: String

    var body: some View {
        ProgressView()
            .controlSize(ControlSize.large)
            .padding()
        Text("Adding \(address)")
    }
}

// MARK: - Step 3: Success

struct DeviceAddStep3Success: View {
    let device: Device

    var body: some View {
        Image(systemName: "checkmark.seal")
            .font(.system(size: 50))
            .foregroundStyle(.green)
            .padding()
        Text("\(device.displayName) was added")
    }
}

#Preview {
    DeviceAddView()
}
