import Foundation
import Shared

struct PartitionedDeviceWrappers {
    let visible: [DeviceWithState]
    let online: [DeviceWithState]
    let offline: [DeviceWithState]
}

@MainActor
final class DevicePartitionCache {
    private static let offlineGracePeriodMs: Int64 = 60_000

    private var deviceMap: [String: DeviceWithState] = [:]
    private var latestSharedDevices: [Shared.DeviceWithState] = []

    @discardableResult
    func sync(_ sharedDevices: [Shared.DeviceWithState]) -> [DeviceWithState] {
        latestSharedDevices = sharedDevices
        let newMacs = Set(sharedDevices.map { $0.device.macAddress })
        deviceMap = deviceMap.filter { newMacs.contains($0.key) }

        var updatedList: [DeviceWithState] = []
        updatedList.reserveCapacity(sharedDevices.count)

        for shared in sharedDevices {
            let mac = shared.device.macAddress
            let wrapper: DeviceWithState
            if let existing = deviceMap[mac] {
                if existing.device != shared.device {
                    existing.device = shared.device
                }
                if existing.stateInfo != shared.stateInfo {
                    existing.stateInfo = shared.stateInfo
                }
                if existing.websocketStatus != shared.websocketStatus {
                    existing.websocketStatus = shared.websocketStatus
                }
                if existing.availableUpdateVersion != shared.updateVersionTag {
                    existing.availableUpdateVersion = shared.updateVersionTag
                }
                wrapper = existing
            } else {
                let newDevice = DeviceWithState(initialDevice: shared.device)
                newDevice.stateInfo = shared.stateInfo
                newDevice.websocketStatus = shared.websocketStatus
                newDevice.availableUpdateVersion = shared.updateVersionTag
                deviceMap[mac] = newDevice
                wrapper = newDevice
            }
            updatedList.append(wrapper)
        }
        return updatedList
    }

    func partition(showHiddenDevices: Bool, currentTime: Date = Date()) -> PartitionedDeviceWrappers {
        let currentTimeMillis = Int64(currentTime.timeIntervalSince1970 * 1000.0)
        let partitioned = PartitionedDevicesKt.partitionDevices(
            devices: latestSharedDevices,
            showHiddenDevices: showHiddenDevices,
            currentTimeMillis: currentTimeMillis,
            offlineGracePeriodMs: Self.offlineGracePeriodMs
        )
        return PartitionedDeviceWrappers(
            visible: partitioned.visible.compactMap { deviceMap[$0.device.macAddress] },
            online: partitioned.online.compactMap { deviceMap[$0.device.macAddress] },
            offline: partitioned.offline.compactMap { deviceMap[$0.device.macAddress] }
        )
    }
}
