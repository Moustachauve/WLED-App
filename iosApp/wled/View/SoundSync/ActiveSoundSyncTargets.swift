import Foundation
import Shared
import SwiftUI

/// Describes which devices are actively receiving Sound Sync audio from this device.
struct ActiveSoundSyncTargets: Equatable, Sendable {
    var macAddresses: Set<String> = []
    var includeHidden: Bool = false

    static let inactive = ActiveSoundSyncTargets()

    @MainActor
    func isDeviceReceiving(_ device: DeviceWithState) -> Bool {
        guard device.isOnline, !device.isAPMode else { return false }
        guard includeHidden || !device.device.isHidden else { return false }
        return macAddresses.contains(device.device.macAddress)
    }
}

extension EnvironmentValues {
    @Entry var activeSoundSyncTargets: ActiveSoundSyncTargets = .inactive
}
