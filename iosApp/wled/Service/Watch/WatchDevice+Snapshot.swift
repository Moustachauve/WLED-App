import Shared

extension WatchDevice {
    init(snapshot: WatchDeviceSnapshot) {
        self.init(id: snapshot.macAddress, name: snapshot.name, address: snapshot.address)
    }
}
