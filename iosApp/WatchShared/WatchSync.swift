import Foundation

/// Keys and coding shared by the iPhone and watch sides of the device sync.
enum WatchSync {
    /// Key of the `applicationContext` entry holding the JSON encoded `[WatchDevice]`.
    static let devicesKey = "devices"
    /// Key of the message the watch sends to ask the iPhone for the device list.
    static let requestKey = "requestDevices"

    /// Keys are sorted so the same list always encodes to the same bytes, which lets the iPhone skip
    /// re-sending an unchanged list.
    static func encode(_ devices: [WatchDevice]) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        return try encoder.encode(devices)
    }

    static func decode(_ data: Data) throws -> [WatchDevice] {
        try JSONDecoder().decode([WatchDevice].self, from: data)
    }
}
