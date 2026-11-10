import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit

/// Lock Screen banner and Dynamic Island Live Activity displayed while Sound Sync is capturing audio.
struct SoundSyncLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: SoundSyncActivityAttributes.self) { _ in
            HStack(spacing: 12) {
                SoundSyncAkemiIcon()
                SoundSyncTitleAndSubtitleView()
                SoundSyncStopButton()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .widgetURL(SoundSyncActivityAttributes.deepLinkURL)
            .activityBackgroundTint(Color(.secondarySystemBackground))
        } dynamicIsland: { _ in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    SoundSyncAkemiIcon()
                        .padding(.leading, 4)
                        .frame(maxHeight: .infinity, alignment: .center)
                }
                DynamicIslandExpandedRegion(.center) {
                    SoundSyncTitleAndSubtitleView()
                        .frame(maxHeight: .infinity, alignment: .center)
                }
                DynamicIslandExpandedRegion(.trailing, priority: 1) {
                    SoundSyncStopButton()
                        .frame(maxHeight: .infinity, alignment: .center)
                }
            } compactLeading: {
                SoundSyncAkemiWithWavesView(size: 22)
            } compactTrailing: {
                EmptyView()
            } minimal: {
                SoundSyncAkemiWithWavesView(size: 20)
            }
            .widgetURL(SoundSyncActivityAttributes.deepLinkURL)
        }
    }
}

private struct SoundSyncAkemiIcon: View {
    var body: some View {
        SoundSyncAkemiWithWavesView(size: 28)
            .frame(width: 40, height: 40)
            .background(Color(.tertiarySystemFill), in: Circle())
    }
}

/// Renders the 32x32 pixel-art RGB Akemi icon flanked by rainbow pixel-art sound wave bars.
private struct SoundSyncAkemiWithWavesView: View {
    private struct WaveBar {
        let xOffset: CGFloat
        let yOffset: CGFloat
        let height: CGFloat
        let gradient: LinearGradient
    }

    private static let leftWaveGradient = LinearGradient(
        colors: [
            Color(red: 0.95, green: 0.0, blue: 0.25),
            Color(red: 1.0, green: 0.45, blue: 0.0),
            Color(red: 1.0, green: 0.85, blue: 0.0)
        ],
        startPoint: .top,
        endPoint: .bottom
    )

    private static let rightWaveGradient = LinearGradient(
        colors: [
            Color(red: 0.75, green: 0.15, blue: 1.0),
            Color(red: 0.1, green: 0.3, blue: 1.0),
            Color(red: 0.0, green: 0.85, blue: 1.0)
        ],
        startPoint: .top,
        endPoint: .bottom
    )

    private static let waveBars = [
        WaveBar(xOffset: 1, yOffset: 5, height: 6, gradient: leftWaveGradient),
        WaveBar(xOffset: 5, yOffset: 2, height: 11, gradient: leftWaveGradient),
        WaveBar(xOffset: 25, yOffset: 2, height: 11, gradient: rightWaveGradient),
        WaveBar(xOffset: 29, yOffset: 5, height: 6, gradient: rightWaveGradient)
    ]

    let size: CGFloat

    var body: some View {
        let pixel = size / 32.0
        ZStack(alignment: .topLeading) {
            Image(.akemi042CheerfulRgb)
                .resizable()
                .interpolation(.none)
                .scaledToFit()
                .frame(width: size, height: size)

            ForEach(0..<Self.waveBars.count, id: \.self) { index in
                let bar = Self.waveBars[index]
                Rectangle()
                    .fill(bar.gradient)
                    .frame(width: 2 * pixel, height: bar.height * pixel)
                    .offset(x: bar.xOffset * pixel, y: bar.yOffset * pixel)
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

private struct SoundSyncTitleAndSubtitleView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("Sound Sync Active")
                .font(.subheadline.weight(.semibold))
                .lineLimit(1)

            Text("Sending audio data to WLED devices")
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct SoundSyncStopButton: View {
    var body: some View {
        Button(intent: StopSoundSyncIntent()) {
            HStack(spacing: 6) {
                Image(systemName: "stop.fill")
                    .font(.caption.weight(.bold))
                Text("Stop")
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(Color.red.opacity(0.18), in: Capsule())
            .foregroundStyle(.red)
            .fixedSize(horizontal: true, vertical: true)
        }
        .buttonStyle(.plain)
    }
}
