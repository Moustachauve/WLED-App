import Shared
import Testing
@testable import WLED

@MainActor
struct DeviceInfoTwoRowsTests {

    private func makeDevice(
        mac: String = "AA:BB:CC:DD:EE:01",
        isOnline: Bool = true,
        isHidden: Bool = false
    ) -> WLED.DeviceWithState {
        let device = Device(
            macAddress: mac,
            address: "192.168.1.10",
            isHidden: isHidden,
            originalName: "WLED"
        )
        let wrapper = WLED.DeviceWithState(initialDevice: device)
        wrapper.websocketStatus = isOnline ? .connected : .disconnected
        return wrapper
    }

    @Test(arguments: [
        (0.0, "battery.0percent"),
        (10.0, "battery.0percent"),
        (25.0, "battery.25percent"),
        (35.0, "battery.25percent"),
        (50.0, "battery.50percent"),
        (60.0, "battery.50percent"),
        (75.0, "battery.75percent"),
        (85.0, "battery.75percent"),
        (90.0, "battery.100percent"),
        (100.0, "battery.100percent")
    ])
    func batteryIconNameMapsPercentageThresholds(percentage: Double, expectedIcon: String) {
        #expect(DeviceInfoTwoRows.batteryIconName(for: percentage) == expectedIcon)
    }

    @Test(arguments: [
        (ActiveSoundSyncTargets.inactive, "AA:BB:CC:DD:EE:01", true, false, false),
        (ActiveSoundSyncTargets(macAddresses: ["AA:BB:CC:DD:EE:01"]), "AA:BB:CC:DD:EE:01", true, false, true),
        (ActiveSoundSyncTargets(macAddresses: ["AA:BB:CC:DD:EE:01"]), "AA:BB:CC:DD:EE:02", true, false, false),
        (ActiveSoundSyncTargets(macAddresses: ["AA:BB:CC:DD:EE:01"]), "AA:BB:CC:DD:EE:01", false, false, false),
        (
            ActiveSoundSyncTargets(macAddresses: ["AA:BB:CC:DD:EE:01"], includeHidden: false),
            "AA:BB:CC:DD:EE:01",
            true,
            true,
            false
        ),
        (
            ActiveSoundSyncTargets(macAddresses: ["AA:BB:CC:DD:EE:01"], includeHidden: true),
            "AA:BB:CC:DD:EE:01",
            true,
            true,
            true
        )
    ])
    func activeSoundSyncTargetsEvaluatesReceivingState(
        targets: ActiveSoundSyncTargets,
        mac: String,
        isOnline: Bool,
        isHidden: Bool,
        expected: Bool
    ) {
        let device = makeDevice(mac: mac, isOnline: isOnline, isHidden: isHidden)
        #expect(targets.isDeviceReceiving(device) == expected)
    }
}
