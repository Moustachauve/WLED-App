import Foundation
import Testing
@testable import WLED

struct WatchSyncTests {
    @Test func devicesRoundTrip() throws {
        let devices = [
            WatchDevice(id: "AA:BB:CC:DD:EE:FF", name: "Desk", address: "192.168.1.10"),
            WatchDevice(id: "11:22:33:44:55:66", name: "TV", address: "wled-tv.local")
        ]
        let data = try WatchSync.encode(devices)
        #expect(try WatchSync.decode(data) == devices)
    }

    /// The watch app may run an older or newer build than the iPhone: the JSON keys are a contract.
    @Test func decodesPinnedPayload() throws {
        let json = #"[{"id":"AA:BB:CC:DD:EE:FF","name":"Desk","address":"192.168.1.10"}]"#
        let devices = try WatchSync.decode(Data(json.utf8))
        #expect(devices == [WatchDevice(id: "AA:BB:CC:DD:EE:FF", name: "Desk", address: "192.168.1.10")])
    }

    @Test func invalidDataThrows() {
        #expect(throws: DecodingError.self) {
            try WatchSync.decode(Data("nope".utf8))
        }
    }
}
