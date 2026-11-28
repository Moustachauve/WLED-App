import Foundation
import Shared
@testable import WLED

extension AppDatabase {
    @MainActor
    func insertDevicesAndSetStatuses(
        _ entries: [(device: Shared.Device, status: Shared.WebsocketStatus)]
    ) async throws {
        for (device, _) in entries {
            try await deviceRepository.insert(device: device)
        }
        try await waitUntil { self.deviceConnectionManager.allDevicesWithState.value.count == entries.count }
        for (device, status) in entries {
            deviceConnectionManager.updateDeviceState(
                deviceWithState: Shared.DeviceWithState(
                    device: device,
                    stateInfo: nil,
                    websocketStatus: status,
                    updateVersionTag: nil
                )
            )
        }
    }
}
