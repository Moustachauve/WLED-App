import WatchConnectivity

/// The part of `WCSession` used by `WatchSyncService`, so tests can replace it.
protocol WatchContextSession: AnyObject {
    /// `false` on devices that can't pair with an Apple Watch (iPad).
    var isSupportedOnDevice: Bool { get }
    var activationState: WCSessionActivationState { get }
    func activate(delegate: any WCSessionDelegate)
    func updateApplicationContext(_ applicationContext: [String: Any]) throws
}

extension WCSession: WatchContextSession {
    var isSupportedOnDevice: Bool {
        WCSession.isSupported()
    }

    func activate(delegate: any WCSessionDelegate) {
        self.delegate = delegate
        activate()
    }
}
