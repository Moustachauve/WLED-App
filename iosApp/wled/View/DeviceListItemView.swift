import SwiftUI
import Shared

private typealias State = SwiftUI.State

// IllumiDEL palette (same values as the Android card and the V1 web page)
enum IL {
    static let card = Color(red: 20 / 255, green: 28 / 255, blue: 44 / 255)        // #141C2C
    static let accent = Color(red: 46 / 255, green: 197 / 255, blue: 255 / 255)    // #2EC5FF
    static let onAccent = Color(red: 7 / 255, green: 33 / 255, blue: 51 / 255)     // #072133
    static let text2 = Color(red: 185 / 255, green: 198 / 255, blue: 216 / 255)    // #B9C6D8
    static let text3 = Color(red: 127 / 255, green: 146 / 255, blue: 173 / 255)    // #7F92AD
    static let off = Color(red: 85 / 255, green: 98 / 255, blue: 122 / 255)        // #55627A
    static let value = Color(red: 207 / 255, green: 217 / 255, blue: 232 / 255)    // #CFD9E8
    static let summary = Color(red: 143 / 255, green: 163 / 255, blue: 191 / 255)  // #8FA3BF
    static let navyTop = Color(red: 18 / 255, green: 48 / 255, blue: 90 / 255)     // #12305A
    static let navyMid = Color(red: 10 / 255, green: 18 / 255, blue: 34 / 255)     // #0A1222
    static let navyBottom = Color(red: 7 / 255, green: 11 / 255, blue: 20 / 255)   // #070B14
}

struct DeviceListItemView: View {
    var device: DeviceWithState

    var isSelected: Bool = false

    // MARK: - Actions
    var onTogglePower: (Bool) -> Void
    var onChangeBrightness: (Int) -> Void

    @State private var brightness: Double = 0.0

    private var isOn: Bool { device.stateInfo?.state.isOn?.boolValue ?? false }
    private var isLit: Bool { isOn && device.isOnline }

    var body: some View {
        // The card is always dark, so use the dark-mode version of the device colour
        let tint = device.currentColor.fixDisplayColor(colorScheme: .dark)

        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 14) {
                colorOrb(tint: tint)

                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 8) {
                        Text(device.device.displayName)
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                        if device.hasUpdateAvailable {
                            Text("Update")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(IL.onAccent)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 2)
                                .background(IL.accent, in: Capsule())
                                .fixedSize()
                        }
                    }

                    Group {
                        if device.isOnline {
                            Text(isOn ? LocalizedStringKey("On") : LocalizedStringKey("Off"))
                        } else {
                            OfflineSinceText(device: device)
                                .minimumScaleFactor(0.7)
                        }
                    }
                    .font(.system(size: 13))
                    .foregroundStyle(IL.text2)
                    .lineLimit(1)

                    HStack(spacing: 5) {
                        WebsocketStatusIndicator(currentStatus: device.websocketStatus)
                        Text(device.device.address)
                            .lineLimit(1)
                        signalIcon
                        if device.device.isHidden {
                            Image(systemName: "eye.slash")
                                .font(.system(size: 11))
                                .accessibilityLabel("(Hidden)")
                        }
                    }
                    .font(.system(size: 12))
                    .foregroundStyle(IL.text3)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Toggle("Turn On/Off", isOn: isOnBinding)
                    .labelsHidden()
                    .tint(IL.accent)
                    .disabled(!device.isOnline)
            }

            HStack(spacing: 12) {
                Image(systemName: "sun.max")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(IL.text3)
                    .accessibilityHidden(true)
                Slider(
                    value: $brightness,
                    in: 1.0...255.0,
                    onEditingChanged: { editing in
                        // Call the brightness closure when dragging ends
                        if !editing {
                            onChangeBrightness(Int(brightness))
                        }
                    }
                )
                .tint(isLit ? tint : IL.off)
                .disabled(!device.isOnline)
                .accessibilityLabel(Text("Brightness"))
                // non-breaking space + one line: "100 %" never wraps, even with large text sizes
                Text("\(Int((brightness / 255 * 100).rounded()))\u{00A0}%")
                    .font(.system(size: 13, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(IL.value)
                    .lineLimit(1)
                    .fixedSize()
                    .frame(minWidth: 44, alignment: .trailing)
            }
        }
        .padding(EdgeInsets(top: 16, leading: 16, bottom: 14, trailing: 16))
        .background(cardBackground(tint: tint))
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(borderColor(tint: tint), lineWidth: isSelected ? 2 : 1)
        )
        .contentShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .shadow(color: .black.opacity(0.35), radius: 15, x: 0, y: 10)
        .opacity(isLit || isSelected ? 1 : 0.75)
        .animation(.easeInOut(duration: 0.25), value: isLit)
        .animation(.linear(duration: 0.3), value: tint)
        .onAppear {
            brightness = Double(device.stateInfo?.state.brightness?.intValue ?? 0)
        }
        .onChange(of: device.stateInfo?.state.brightness) {
            withAnimation(.spring()) {
                self.brightness = Double(device.stateInfo?.state.brightness?.intValue ?? 0)
            }
        }
    }

    // MARK: - Pieces

    private var isOnBinding: Binding<Bool> {
        Binding(get: {
            isOn
        }, set: { isOn in
            onTogglePower(isOn)
        })
    }

    private var signalIcon: some View {
        let signalLevel = WifiSignalLevel.companion.fromRssi(
            rssi: device.stateInfo?.info.wifi.rssi,
            isOnline: device.isOnline,
            defaultWhenNull: .offline
        )
        return Image(systemName: signalLevel.symbolName, variableValue: signalLevel.variableValue)
            .symbolRenderingMode(.hierarchical)
            .font(.system(size: 11))
            .contentTransition(.symbolEffect(.replace))
            .accessibilityHidden(true)
    }

    private func borderColor(tint: Color) -> Color {
        if isSelected { return IL.accent }
        return isLit ? tint.opacity(0.25) : Color.white.opacity(0.08)
    }

    // Dot glowing in the light's current colour (grey when off)
    private func colorOrb(tint: Color) -> some View {
        Circle()
            .fill(
                RadialGradient(
                    colors: [.white, tint, tint.opacity(0.6)],
                    center: UnitPoint(x: 0.35, y: 0.3),
                    startRadius: 0,
                    endRadius: 26
                )
            )
            .frame(width: 42, height: 42)
            .background(
                Circle()
                    .fill(tint.opacity(isLit ? 0.14 : 0))
                    .padding(-4)
            )
            .shadow(color: isLit ? tint.opacity(0.6) : .clear, radius: 11)
            .saturation(isLit ? 1 : 0.15)
            .brightness(isLit ? 0 : -0.35)
            .accessibilityHidden(true)
    }

    // Dark card with a soft tint of the light's colour from the top-left corner
    private func cardBackground(tint: Color) -> some View {
        RoundedRectangle(cornerRadius: 22, style: .continuous)
            .fill(IL.card.opacity(0.85))
            .overlay(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(
                        RadialGradient(
                            colors: [tint.opacity(isLit ? 0.18 : 0), .clear],
                            center: .topLeading,
                            startRadius: 0,
                            endRadius: 280
                        )
                    )
            )
    }
}

#if DEBUG
struct DeviceListItemView_Previews: PreviewProvider {
    static var previews: some View {
        let device = PreviewData.onlineDevice

        VStack(alignment: .leading) {
            Text("1st Selected, 2nd unselected")
            DeviceListItemView(
                device: device,
                isSelected: true,
                onTogglePower: { isOn in
                    print("Preview: Power toggled to \(isOn)")
                    if let current = device.stateInfo {
                        device.stateInfo = current.withIsOn(isOn: isOn)
                    }
                },
                onChangeBrightness: { val in
                    print("Preview: Brightness changed to \(val)")
                    if let current = device.stateInfo {
                        device.stateInfo = current.withBrightness(brightness: Int32(val))
                    }
                }
            )
            DeviceListItemView(
                device: device,
                onTogglePower: { isOn in
                    print("Preview: Power toggled to \(isOn)")
                    if let current = device.stateInfo {
                        device.stateInfo = current.withIsOn(isOn: isOn)
                    }
                },
                onChangeBrightness: { val in
                    print("Preview: Brightness changed to \(val)")
                    if let current = device.stateInfo {
                        device.stateInfo = current.withBrightness(brightness: Int32(val))
                    }
                }
            )
        }
        .padding()
        .background(IL.navyMid)
        .previewLayout(.sizeThatFits)
    }
}
#endif
