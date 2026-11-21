import Foundation

/// The part of a WLED device's `/json/state` the watch uses.
struct WatchDeviceState: Decodable, Sendable {
    var isOn: Bool?
    var brightness: Int64?
    var nightlight: WatchNightlightState?

    enum CodingKeys: String, CodingKey {
        case isOn = "on"
        case brightness = "bri"
        case nightlight = "nl"
    }
}

/// `nl` object of `WatchDeviceState`.
struct WatchNightlightState: Decodable, Sendable {
    var isOn: Bool?
    /// Seconds left, `-1` when unknown or inactive.
    var remainingTime: Int64?

    enum CodingKeys: String, CodingKey {
        case isOn = "on"
        case remainingTime = "rem"
    }
}
