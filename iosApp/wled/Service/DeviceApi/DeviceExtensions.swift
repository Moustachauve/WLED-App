import Foundation
import Shared

extension WifiSignalLevel {
    var symbolName: String {
        self == .offline ? "wifi.slash" : "wifi"
    }

    var variableValue: Double {
        switch self {
        case .good:
            1.0
        case .fair:
            0.64
        case .weak:
            0.33
        case .none, .offline:
            0.0
        }
    }
}

extension String {
    /// Returns the tag without a leading `"v"` or `"V"` prefix.
    var strippingLeadingV: String {
        hasPrefix("v") || hasPrefix("V") ? String(dropFirst()) : self
    }

    /// Formats a version tag with a single leading lowercase `"v"` (e.g., `"v2.0.0-beta.1"`).
    var prefixedWithV: String {
        "v\(strippingLeadingV)"
    }
}

enum DeepLinkParser {
    static let scheme = "illumidel"

    /// Parses `illumidel://device/<mac>` or `illumidel://<mac>` into a trimmed MAC address.
    static func parseMacAddress(_ url: URL) -> String? {
        guard url.scheme?.caseInsensitiveCompare(scheme) == .orderedSame,
              let host = url.host(percentEncoded: false),
              !host.isEmpty else {
            return nil
        }

        let segments = url.pathComponents.filter { $0 != "/" }
        if host.caseInsensitiveCompare("device") == .orderedSame {
            guard segments.count == 1 else { return nil }
            let mac = segments[0].trimmingCharacters(in: .whitespacesAndNewlines)
            return mac.isEmpty ? nil : mac
        }

        guard segments.isEmpty else { return nil }
        let mac = host.trimmingCharacters(in: .whitespacesAndNewlines)
        return mac.isEmpty ? nil : mac
    }

    /// Compares two MAC address strings case-insensitively while ignoring `:` and `-` separators.
    static func matchesMacAddress(_ lhs: String, _ rhs: String) -> Bool {
        let normalizedLhs = canonicalMac(lhs)
        let normalizedRhs = canonicalMac(rhs)
        return !normalizedLhs.isEmpty && normalizedLhs == normalizedRhs
    }

    private static func canonicalMac(_ mac: String) -> String {
        mac.replacingOccurrences(of: ":", with: "")
            .replacingOccurrences(of: "-", with: "")
            .lowercased()
    }
}

extension Device {
    static var defaultDisplayName: String {
        String(localized: "(New Device)")
    }

    var displayName: String {
        WidgetSnapshotKt.resolveDeviceName(device: self, defaultName: Self.defaultDisplayName)
    }

    /// Swift-friendly initializer mirroring the Kotlin default arguments, which aren't exported to Swift.
    convenience init(
        macAddress: String,
        address: String,
        isHidden: Bool = false,
        originalName: String = "",
        customName: String = "",
        skipUpdateTag: String = "",
        branch: Branch = .unknown,
        lastSeen: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.init(
            macAddress: macAddress,
            address: address,
            isHidden: isHidden,
            originalName: originalName,
            customName: customName,
            skipUpdateTag: skipUpdateTag,
            branch: branch,
            lastSeen: lastSeen,
            repositoryId: Repository.companion.DEFAULT_ID
        )
    }

    func copy(
        macAddress: String? = nil,
        address: String? = nil,
        isHidden: Bool? = nil,
        originalName: String? = nil,
        customName: String? = nil,
        skipUpdateTag: String? = nil,
        branch: Branch? = nil,
        lastSeen: Int64? = nil,
        repositoryId: Int64? = nil
    ) -> Device {
        Device(
            macAddress: macAddress ?? self.macAddress,
            address: address ?? self.address,
            isHidden: isHidden ?? self.isHidden,
            originalName: originalName ?? self.originalName,
            customName: customName ?? self.customName,
            skipUpdateTag: skipUpdateTag ?? self.skipUpdateTag,
            branch: branch ?? self.branch,
            lastSeen: lastSeen ?? self.lastSeen,
            repositoryId: repositoryId ?? self.repositoryId
        )
    }

    func getColor(state: State?) -> Int64 {
        if let argb = getColorFromState(state: state) {
            return Int64(argb.int32Value) & 0xFFFFFF
        }
        return 0x808080
    }
}
