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

    @Test func inactiveSoundSyncTargetsNeverMatches() {
        let device = makeDevice()
        #expect(!ActiveSoundSyncTargets.inactive.isDeviceReceiving(device))
    }

    @Test func activeSoundSyncTargetsMatchesSelectedOnlineDevice() {
        let targets = ActiveSoundSyncTargets(macAddresses: ["AA:BB:CC:DD:EE:01"])
        #expect(targets.isDeviceReceiving(makeDevice(mac: "AA:BB:CC:DD:EE:01")))
        #expect(!targets.isDeviceReceiving(makeDevice(mac: "AA:BB:CC:DD:EE:02")))
    }

    @Test func activeSoundSyncTargetsExcludesOfflineAndApModeDevices() {
        let targets = ActiveSoundSyncTargets(
            macAddresses: ["AA:BB:CC:DD:EE:01", DeviceKt.AP_MODE_MAC_ADDRESS]
        )
        #expect(!targets.isDeviceReceiving(makeDevice(mac: "AA:BB:CC:DD:EE:01", isOnline: false)))
        #expect(!targets.isDeviceReceiving(makeDevice(mac: DeviceKt.AP_MODE_MAC_ADDRESS)))
    }

    @Test func activeSoundSyncTargetsRespectsIncludeHidden() {
        let hidden = makeDevice(mac: "AA:BB:CC:DD:EE:01", isHidden: true)
        #expect(
            !ActiveSoundSyncTargets(macAddresses: ["AA:BB:CC:DD:EE:01"], includeHidden: false)
                .isDeviceReceiving(hidden)
        )
        #expect(
            ActiveSoundSyncTargets(macAddresses: ["AA:BB:CC:DD:EE:01"], includeHidden: true)
                .isDeviceReceiving(hidden)
        )
    }
}
