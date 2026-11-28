import SwiftUI
import Shared

private typealias State = SwiftUI.State

/// Screens that can be pushed on top of a device's webview in the detail `NavigationStack`.
/// Routes carry the device id so that switching devices replaces the pushed screen.
enum DeviceDetailRoute: Hashable {
    case edit(deviceId: String)
    case updateDetails(deviceId: String)
}

struct DeviceView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.dismiss) private var dismiss
    var device: DeviceWithState
    var onNavigateHome: (() -> Void)?

    @State var showDownloadFinished = false
    @State var shouldWebViewRefresh = false
    @State private var isWebViewError = false
    @State private var isWebViewLoading = false
    @State private var postUpdateReloadTask: Task<Void, Never>?
    /// IllumiDEL: theme colour of the page, used for the bar at the top (nil = default bar)
    @State private var pageColor: Color?

    var body: some View {
        ZStack {
            WebView(
                url: getDeviceAddress(),
                reload: $shouldWebViewRefresh,
                isError: $isWebViewError,
                isLoading: $isWebViewLoading,
                onPageColor: { color in
                    withAnimation(.easeInOut(duration: 0.25)) {
                        pageColor = color.map { Color(uiColor: $0) }
                    }
                },
                downloadCompleted: { _ in
                    withAnimation {
                        showDownloadFinished = true
                    }
                    Task {
                        try? await Task.sleep(for: .seconds(3))
                        guard !Task.isCancelled else { return }
                        withAnimation {
                            showDownloadFinished = false
                        }
                    }
                }
            )
            .ignoresSafeArea(edges: .bottom)
            .opacity(isWebViewError ? 0 : 1)
            .accessibilityHidden(isWebViewError)
            .overlay(alignment: .top) {
                if isWebViewLoading && !isWebViewError {
                    IndeterminateProgressBar()
                }
            }

            if isWebViewError {
                DeviceErrorView(onRetry: refreshWebView)
            }

            if showDownloadFinished {
                VStack {
                    Spacer()
                    Text("Download Completed")
                        .font(.title3)
                        .padding()
                        .background(.regularMaterial)
                        .clipShape(RoundedRectangle(cornerRadius: 15))
                        .padding(.bottom)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .zIndex(1)
            }
        }
        .navigationTitle(device.device.displayName)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(horizontalSizeClass == .compact)
        .navigationDestination(for: DeviceDetailRoute.self) { route in
            switch route {
            case .edit:
                DeviceEditView(device: device)
                    .id(device.id)
            case .updateDetails:
                DeviceUpdateDetails(device: device)
                    .id(device.id)
            }
        }
        .onChange(of: device.id) {
            pageColor = nil
            postUpdateReloadTask?.cancel()
            postUpdateReloadTask = nil
        }
        .onChange(of: device.device.address) {
            isWebViewError = false
        }
        .onChange(of: isWebViewError) { _, isError in
            if isError {
                withAnimation(.easeInOut(duration: 0.25)) {
                    pageColor = nil
                }
            }
        }
        .onDisappear {
            postUpdateReloadTask?.cancel()
            postUpdateReloadTask = nil
        }
        .onReceive(NotificationCenter.default.publisher(for: .didCompleteUpdateInstall)) { notification in
            guard (notification.object as? String) == device.id,
                  (notification.userInfo?["succeeded"] as? Bool) == true else {
                return
            }
            postUpdateReloadTask?.cancel()
            postUpdateReloadTask = Task {
                try? await Task.sleep(for: .milliseconds(1500))
                guard !Task.isCancelled else { return }
                isWebViewError = false
                shouldWebViewRefresh = true
            }
        }
        .toolbar { toolbar }
        // IllumiDEL: the bars at the top and bottom take the page's theme colour, so app chrome and page look like one piece
        .background((pageColor ?? .clear).ignoresSafeArea())
        .toolbarBackground(pageColor ?? .clear, for: .navigationBar)
        .toolbarBackground(pageColor == nil ? .automatic : .visible, for: .navigationBar)
    }

    @ToolbarContentBuilder
    var toolbar: some ToolbarContent {
        if horizontalSizeClass == .compact {
            ToolbarItem(placement: .topBarLeading) {
                Button {
                    onNavigateHome?()
                    dismiss()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                        Image(systemName: "house")
                    }
                    .fontWeight(.light)   // IllumiDEL: thin strokes, like the web page's 6-button menu
                }
                .accessibilityLabel("Device List")
            }
        }
        ToolbarItem(placement: .principal) {
            Image(.illumidelLogo)
                .resizable()
                .scaledToFit()
                .padding(2)
                .frame(maxWidth: 100)
                .accessibilityHidden(true)
        }
        ToolbarItem(placement: .primaryAction) {
            NavigationLink(value: DeviceDetailRoute.edit(deviceId: device.id)) {
                Label("Settings", systemImage: "gear")
                    .fontWeight(.light)
                    .badge(getToolbarBadgeCount())
            }
        }
        ToolbarItem(placement: .automatic) {
            Button("Refresh", systemImage: "arrow.clockwise", action: refreshWebView)
                .fontWeight(.light)
        }
    }

    private func refreshWebView() {
        shouldWebViewRefresh = true
    }

    func getDeviceAddress() -> URL? {
        let deviceAddress = device.device.address
        guard let url = URL(string: "http://\(deviceAddress)") else {
            return nil
        }
        return url
    }

    func getToolbarBadgeCount() -> Int {
        return device.hasUpdateAvailable ? 1 : 0
    }
}

private struct IndeterminateProgressBar: View {
    @State private var progress: CGFloat = 0

    var body: some View {
        GeometryReader { geometry in
            let barWidth = geometry.size.width * 0.35
            ZStack(alignment: .leading) {
                Rectangle()
                    .fill(.tint.opacity(0.2))
                Capsule()
                    .fill(.tint)
                    .frame(width: barWidth)
                    .offset(x: (geometry.size.width + barWidth) * progress - barWidth)
            }
        }
        .frame(height: 3)
        .clipped()
        .onAppear {
            withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: false)) {
                progress = 1
            }
        }
        .accessibilityHidden(true)
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        DeviceView(device: PreviewData.onlineDevice)
    }
}
#endif
