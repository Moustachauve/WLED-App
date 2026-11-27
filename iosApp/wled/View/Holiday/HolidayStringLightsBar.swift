import Shared
import SwiftUI

private enum StringLightsConstants {
    static let height: CGFloat = 18
    static let bulbSpacing: CGFloat = 32
    static let minBulbs = 6
    static let maxBulbs = 30
    static let wireY: CGFloat = 1.5
    static let wireStrokeWidth: CGFloat = 1.25
    static let socketSize = CGSize(width: 3.6, height: 2.4)
    static let socketTop: CGFloat = 0.9
    static let socketCornerRadius: CGFloat = 0.9
    static let bulbSize = CGSize(width: 5.4, height: 7.4)
    static let bulbTop: CGFloat = 2.8
    static let glowRadius: CGFloat = 10.5
    static let staticTime: Double = 2.4
    static let twinkleSpeed = 2.4
    static let bulbPhaseStep = 1.15

    static let wireColor = Color(red: 0.420, green: 0.447, blue: 0.502)
    static let socketColorDark = Color(red: 0.216, green: 0.255, blue: 0.318)
    static let socketColorLight = Color(red: 0.294, green: 0.333, blue: 0.388)

    private static let red = Color(red: 1.000, green: 0.118, blue: 0.153)    // #FF1E27
    private static let green = Color(red: 0.000, green: 0.784, blue: 0.325)  // #00C853
    private static let orange = Color(red: 1.000, green: 0.427, blue: 0.000) // #FF6D00
    private static let blue = Color(red: 0.161, green: 0.475, blue: 1.000)   // #2979FF
    private static let warmWhiteDark = Color(red: 1.000, green: 0.945, blue: 0.757)  // #FFF1C1
    private static let warmYellowLight = Color(red: 0.902, green: 0.690, blue: 0.000) // #E6B000

    static let bulbColorsDark: [Color] = [red, green, orange, blue, warmWhiteDark]
    static let bulbColorsLight: [Color] = [red, green, orange, blue, warmYellowLight]
}

/// Renders a slim decorative strand of twinkling classic C5 string lights when a holiday theme is active.
struct HolidayStringLightsBar: View {
    @Environment(\.activeHolidayTheme) private var activeHolidayTheme
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.holidayAnimationStaticTime) private var staticTime

    var body: some View {
        if activeHolidayTheme != nil {
            let isPaused = reduceMotion || staticTime != nil
            TimelineView(.animation(paused: isPaused)) { context in
                let elapsed = staticTime ?? (reduceMotion ? StringLightsConstants.staticTime : context.date.timeIntervalSinceReferenceDate)
                Canvas { graphicsContext, size in
                    drawLights(in: &graphicsContext, size: size, elapsed: elapsed)
                }
                .frame(height: StringLightsConstants.height)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            }
        }
    }

    private func drawLights(in context: inout GraphicsContext, size: CGSize, elapsed: Double) {
        guard size.width > 0 else { return }
        let isDark = colorScheme == .dark
        let wireOpacity = isDark ? 0.55 : 0.45
        let wireColor = StringLightsConstants.wireColor.opacity(wireOpacity)
        let socketColor = isDark ? StringLightsConstants.socketColorDark : StringLightsConstants.socketColorLight
        let glowMaxAlpha = isDark ? 0.60 : 0.42

        var wirePath = Path()
        wirePath.move(to: CGPoint(x: 0, y: StringLightsConstants.wireY))
        wirePath.addLine(to: CGPoint(x: size.width, y: StringLightsConstants.wireY))
        context.stroke(wirePath, with: .color(wireColor), lineWidth: StringLightsConstants.wireStrokeWidth)

        let rawCount = Int(size.width / StringLightsConstants.bulbSpacing)
        let bulbCount = min(max(rawCount, StringLightsConstants.minBulbs), StringLightsConstants.maxBulbs)
        let step = size.width / CGFloat(bulbCount)

        let bulbColors = isDark ? StringLightsConstants.bulbColorsDark : StringLightsConstants.bulbColorsLight

        for index in 0..<bulbCount {
            let x = (CGFloat(index) + 0.5) * step
            let wave = sin(elapsed * StringLightsConstants.twinkleSpeed + Double(index) * StringLightsConstants.bulbPhaseStep)
            let twinkle = 0.48 + 0.52 * (0.5 + 0.5 * wave)
            let color = bulbColors[index % bulbColors.count]

            let glowCenter = CGPoint(
                x: x,
                y: StringLightsConstants.bulbTop + StringLightsConstants.bulbSize.height * 0.5
            )
            let glowRect = CGRect(
                x: glowCenter.x - StringLightsConstants.glowRadius,
                y: glowCenter.y - StringLightsConstants.glowRadius,
                width: StringLightsConstants.glowRadius * 2,
                height: StringLightsConstants.glowRadius * 2
            )
            let glowOpacity = twinkle * glowMaxAlpha
            let gradient = Gradient(stops: [
                .init(color: color.opacity(0.70 * glowOpacity), location: 0),
                .init(color: color.opacity(0.24 * glowOpacity), location: 0.48),
                .init(color: .clear, location: 1)
            ])
            context.fill(
                Path(ellipseIn: glowRect),
                with: .radialGradient(gradient, center: glowCenter, startRadius: 0, endRadius: StringLightsConstants.glowRadius)
            )

            let socketRect = CGRect(
                x: x - StringLightsConstants.socketSize.width * 0.5,
                y: StringLightsConstants.socketTop,
                width: StringLightsConstants.socketSize.width,
                height: StringLightsConstants.socketSize.height
            )
            context.fill(
                Path(roundedRect: socketRect, cornerRadius: StringLightsConstants.socketCornerRadius),
                with: .color(socketColor)
            )

            let bulbRect = CGRect(
                x: x - StringLightsConstants.bulbSize.width * 0.5,
                y: StringLightsConstants.bulbTop,
                width: StringLightsConstants.bulbSize.width,
                height: StringLightsConstants.bulbSize.height
            )
            context.fill(
                Path(ellipseIn: bulbRect),
                with: .color(color.opacity(0.72 + 0.28 * twinkle))
            )

            let specularRect = CGRect(
                x: bulbRect.minX + bulbRect.width * 0.20,
                y: bulbRect.minY + bulbRect.height * 0.16,
                width: bulbRect.width * 0.28,
                height: bulbRect.height * 0.34
            )
            context.fill(
                Path(ellipseIn: specularRect),
                with: .color(.white.opacity(0.65 * twinkle))
            )
        }
    }
}
