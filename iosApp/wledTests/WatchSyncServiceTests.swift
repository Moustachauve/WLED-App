import Foundation
import Shared
import Testing
import WatchConnectivity
@testable import WLED

/// Records what `WatchSyncService` sends instead of talking to a real watch.
private final class FakeWatchSession: WatchContextSession {
    var isSupportedOnDevice = true
    var activationState: WCSessionActivationState = .activated
    private(set) var activationCount = 0
    private(set) var sentContexts: [[String: Any]] = []

    func activate(delegate: any WCSessionDelegate) {
        activationCount += 1
    }

    func updateApplicationContext(_ applicationContext: [String: Any]) throws {
        sentContexts.append(applicationContext)
    }

    func sentDevices() throws -> [[WatchDevice]] {
        try sentContexts.map { context in
            try WatchSync.decode(try #require(context[WatchSync.devicesKey] as? Data))
        }
    }
}

@MainActor
struct WatchSyncServiceTests {
    private let session = FakeWatchSession()
    private let database = AppDatabase(inMemory: true)
    private var service: WatchSyncService { WatchSyncService(database: database, session: session) }

    private let desk = Device(macAddress: "AA:BB:CC:DD:EE:FF", address: "192.168.1.10", customName: "Desk")
    private let deskOnWatch = WatchDevice(id: "AA:BB:CC:DD:EE:FF", name: "Desk", address: "192.168.1.10")

    @Test func pushesDevicesStoredInDatabase() async throws {
        let service = service
        service.start()
        defer { service.stop() }
        await database.waitUntilReady()

        try await database.deviceRepository.insert(device: desk)

        try await waitUntil { (try? session.sentDevices().last) == [deskOnWatch] }
    }

    @Test func doesNothingWhenWatchIsNotSupported() {
        session.isSupportedOnDevice = false
        let service = service
        service.start()
        defer { service.stop() }

        #expect(session.activationCount == 0)
    }

    @Test func pushesProjectedList() throws {
        service.update(devices: [desk])

        #expect(try session.sentDevices() == [[deskOnWatch]])
    }

    @Test func skipsUnchangedList() {
        let service = service
        service.update(devices: [desk])
        service.update(devices: [desk])

        #expect(session.sentContexts.count == 1)
    }

    @Test func waitsForActivationBeforePushing() {
        session.activationState = .notActivated
        service.update(devices: [desk])

        #expect(session.sentContexts.isEmpty)
    }

    @Test func pushesPendingListOnceActivated() {
        session.activationState = .notActivated
        let service = service
        service.update(devices: [desk])

        session.activationState = .activated
        service.pushLatest()

        #expect(session.sentContexts.count == 1)
    }

    @Test func resendsAfterWatchStateChange() {
        let service = service
        service.update(devices: [desk])

        service.resendAfterWatchStateChange()

        #expect(session.sentContexts.count == 2)
    }

    @Test func repliesToRequestWithLatestList() throws {
        let service = service
        service.update(devices: [desk])

        let reply = service.reply(to: [WatchSync.requestKey: true])

        let data = try #require(reply[WatchSync.devicesKey] as? Data)
        #expect(try WatchSync.decode(data) == [deskOnWatch])
    }

    @Test func repliesWithEmptyDataBeforeFirstList() {
        let reply = service.reply(to: [WatchSync.requestKey: true])

        #expect(reply[WatchSync.devicesKey] as? Data == Data())
    }

    @Test func ignoresUnknownMessages() {
        #expect(service.reply(to: ["other": true]).isEmpty)
    }
}
