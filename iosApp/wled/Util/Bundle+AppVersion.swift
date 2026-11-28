import Foundation

extension Bundle {
    var appVersionName: String {
        if let custom = infoDictionary?["WLEDVersionName"] as? String,
           !custom.isEmpty,
           !custom.hasPrefix("$(") {
            return custom
        }
        return infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
    }

    var appBuildNumber: String {
        infoDictionary?["CFBundleVersion"] as? String ?? "1"
    }

    static func isBetaVersion(_ version: String) -> Bool {
        version.localizedCaseInsensitiveContains("-beta")
    }
}
