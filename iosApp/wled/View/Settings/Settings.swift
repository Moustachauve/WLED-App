import SwiftUI

struct Settings: View {
    @Binding var showHiddenDevices: Bool
    @Binding var showOfflineDevicesLast: Bool
    @Binding var autoDiscovery: Bool
    /// Injectable so snapshot tests don't change with every release.
    var appVersion: String = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "7.0.0"
    var buildNumber: String = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("View Options") {
                    Toggle(isOn: $showHiddenDevices) {
                        Label("Show Hidden Devices", systemImage: "eye")
                    }

                    Toggle(isOn: $showOfflineDevicesLast) {
                        Label("Show Offline Devices Last", systemImage: "wifi.slash")
                    }
                }

                Section {
                    Toggle(isOn: $autoDiscovery) {
                        Label("Automatically Discover New Devices", systemImage: "antenna.radiowaves.left.and.right")
                    }
                } footer: {
                    Text("When disabled, pull down on the device list to scan manually.")
                }

                Section {
                    if let websiteUrl = URL(string: "https://illumidel.com/") {
                        Link(destination: websiteUrl) {
                            Label("Visit Illumidel.com", systemImage: "link")
                        }
                    }

                    if let helpUrl = URL(string: String(localized: "help_url")) {
                        Link(destination: helpUrl) {
                            Label("Help", systemImage: "questionmark.circle")
                        }
                    }
                } header: {
                    Text("About")
                } footer: {
                    VStack(alignment: .center) {
                        Text("Version \(appVersion) (\(buildNumber))")
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
        .presentationDragIndicator(.visible)
    }
}

#Preview {
    Settings(
        showHiddenDevices: .constant(true),
        showOfflineDevicesLast: .constant(true),
        autoDiscovery: .constant(true)
    )
}
