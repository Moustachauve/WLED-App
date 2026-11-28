import Shared
import SwiftUI

struct DeviceInfoCard<Leading: View, Trailing: View>: View {
    let device: DeviceWithState
    var showWebsocketStatus: Bool = true
    var isSelected: Bool = false
    @ViewBuilder let leading: () -> Leading
    @ViewBuilder let trailing: () -> Trailing

    var body: some View {
        Card(style: .device(color: device.currentColor)) {
            HStack(spacing: 12) {
                leading()
                DeviceInfoTwoRows(device: device, showWebsocketStatus: showWebsocketStatus)
                trailing()
            }
        }
        .overlay {
            if isSelected {
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(.tint, lineWidth: 1.5)
            }
        }
    }
}

extension DeviceInfoCard where Leading == EmptyView, Trailing == EmptyView {
    init(
        device: DeviceWithState,
        showWebsocketStatus: Bool = true,
        isSelected: Bool = false
    ) {
        self.device = device
        self.showWebsocketStatus = showWebsocketStatus
        self.isSelected = isSelected
        self.leading = { EmptyView() }
        self.trailing = { EmptyView() }
    }
}
