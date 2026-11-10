import ActivityKit
import AppIntents
import Foundation

/// ActivityKit attributes for the persistent Sound Sync Live Activity on the Lock Screen and Dynamic Island.
struct SoundSyncActivityAttributes: ActivityAttributes, Sendable {
    struct ContentState: Codable, Hashable, Sendable {}

    static let deepLinkURL = URL(string: "wled://app/soundsync")

    static func isSoundSyncDeepLink(_ url: URL) -> Bool {
        url.scheme?.caseInsensitiveCompare("wled") == .orderedSame
            && url.host(percentEncoded: false)?.caseInsensitiveCompare("app") == .orderedSame
            && url.pathComponents.filter { $0 != "/" }.map { $0.lowercased() } == ["soundsync"]
    }
}

/// Stops the active Sound Sync audio capture session from the Lock Screen or Dynamic Island Live Activity.
struct StopSoundSyncIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Stop Sound Sync"
    static let isDiscoverable = false

    func perform() async throws -> some IntentResult {
        #if !WIDGET_EXTENSION
        await SoundSyncManager.shared.stop()
        #endif
        return .result()
    }
}
