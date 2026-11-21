import Foundation
import Shared
import WatchConnectivity
import os

/// Pushes the list of known WLED devices to the paired Apple Watch.
///
/// The watch is a remote control only: it never edits the device list. Whenever the Room devices change
/// (added, renamed, new IP, hidden...), the projected list is re-sent through
/// `WCSession.updateApplicationContext`, which always delivers the latest value.
@MainActor
final class WatchSyncService: NSObject {
    static let shared = WatchSyncService()

    private nonisolated static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "WatchSyncService"
    )

    private let database: AppDatabase
    private let session: any WatchContextSession
    private var observeTask: Task<Void, Never>?
    private var lastSentData: Data?
    /// Latest encoded list, readable from WCSession's delegate queue to answer the watch's requests.
    private nonisolated let latestData = OSAllocatedUnfairLock<Data?>(initialState: nil)

    init(database: AppDatabase = .shared, session: any WatchContextSession = WCSession.default) {
        self.database = database
        self.session = session
    }

    /// Activates the session and keeps the watch in sync until `stop()` is called.
    func start() {
        guard session.isSupportedOnDevice, observeTask == nil else { return }
        session.activate(delegate: self)
        observeTask = Task { [weak self] in
            await self?.observeDevices()
        }
    }

    func stop() {
        observeTask?.cancel()
        observeTask = nil
    }

    private func observeDevices() async {
        await database.waitUntilReady()
        for await devices in database.deviceRepository.allDevices {
            guard !Task.isCancelled else { break }
            update(devices: devices)
        }
    }

    /// Projects `devices` into the watch list and pushes it if it changed.
    func update(devices: [Device]) {
        let watchDevices = WatchDeviceSnapshotKt
            .toWatchDeviceSnapshots(devices, defaultName: Device.defaultDisplayName)
            .map(WatchDevice.init(snapshot:))
        do {
            let data = try WatchSync.encode(watchDevices)
            latestData.withLock { $0 = data }
            pushLatest()
        } catch {
            Self.logger.error("Could not encode the watch device list: \(error.localizedDescription)")
        }
    }

    /// Sends the latest list unless the session isn't active yet or the watch already has it.
    func pushLatest() {
        guard session.activationState == .activated,
              let data = latestData.withLock({ $0 }),
              data != lastSentData else { return }
        do {
            try session.updateApplicationContext([WatchSync.devicesKey: data])
            lastSentData = data
        } catch {
            Self.logger.error("Failed to update the watch application context: \(error.localizedDescription)")
        }
    }

    /// The watch app may have just been installed or a new watch paired: send the list again.
    func resendAfterWatchStateChange() {
        lastSentData = nil
        pushLatest()
    }

    /// Answer to a message from the watch. A device list request gets the latest list; empty data
    /// means "nothing to send yet" and is ignored by the watch.
    nonisolated func reply(to message: [String: Any]) -> [String: Any] {
        guard message[WatchSync.requestKey] != nil else { return [:] }
        return [WatchSync.devicesKey: latestData.withLock { $0 } ?? Data()]
    }
}

extension WatchSyncService: WCSessionDelegate {
    nonisolated func session(
        _ session: WCSession,
        activationDidCompleteWith activationState: WCSessionActivationState,
        error: (any Error)?
    ) {
        if let error {
            Self.logger.error("WCSession activation failed: \(error.localizedDescription)")
        }
        Task { @MainActor in
            self.pushLatest()
        }
    }

    nonisolated func session(
        _ session: WCSession,
        didReceiveMessage message: [String: Any],
        replyHandler: @escaping ([String: Any]) -> Void
    ) {
        // Answered right away from the lock-protected cache, so no actor hop is needed.
        replyHandler(reply(to: message))
    }

    nonisolated func sessionDidBecomeInactive(_ session: WCSession) {}

    nonisolated func sessionDidDeactivate(_ session: WCSession) {
        // Happens when switching to another paired watch.
        session.activate()
    }

    nonisolated func sessionWatchStateDidChange(_ session: WCSession) {
        Task { @MainActor in
            self.resendAfterWatchStateChange()
        }
    }
}
