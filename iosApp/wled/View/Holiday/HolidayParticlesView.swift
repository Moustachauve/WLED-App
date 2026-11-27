import Shared
import SwiftUI

private struct SplitMix64: RandomNumberGenerator {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed
    }

    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }

    mutating func nextUnitDouble() -> Double {
        Double(next() >> 11) / Double(1 << 53)
    }
}

private struct SnowflakeSpec {
    let xFraction: CGFloat
    let yPhase: Double
    let speed: Double
    let radius: CGFloat
    let swayAmplitude: CGFloat
    let swayFrequency: Double
    let alphaFraction: Double
    let isCrystal: Bool
}

private enum SnowField {
    static let count = 38
    static let crystalStrokeWidth: CGFloat = 1.15
    static let minAlpha = 0.30
    static let alphaRange = 0.32
    static let darkColor = Color(red: 0.922, green: 0.961, blue: 1.000)  // #EBF5FF
    static let lightColor = Color(red: 0.310, green: 0.549, blue: 0.788) // #4F8CC9

    static let flakes: [SnowflakeSpec] = {
        var rng = SplitMix64(seed: 20_261_225)
        return (0..<count).map { index in
            let isCrystal = index.isMultiple(of: 2)
            let xFraction = CGFloat(rng.nextUnitDouble())
            let yPhase = rng.nextUnitDouble()
            let speed = 0.045 + rng.nextUnitDouble() * 0.055
            let radius: CGFloat = isCrystal
                ? 3.0 + CGFloat(rng.nextUnitDouble()) * 3.0
                : 1.4 + CGFloat(rng.nextUnitDouble()) * 1.8
            let swayAmp = 6.0 + CGFloat(rng.nextUnitDouble()) * 10.0
            let swayFreq = 0.8 + rng.nextUnitDouble() * 0.9
            let alphaFraction = rng.nextUnitDouble()
            return SnowflakeSpec(
                xFraction: xFraction,
                yPhase: yPhase,
                speed: speed,
                radius: radius,
                swayAmplitude: swayAmp,
                swayFrequency: swayFreq,
                alphaFraction: alphaFraction,
                isCrystal: isCrystal
            )
        }
    }()
}

private enum FireworkField {
    static let burstCount = 5
    static let sparksPerBurst = 16
    static let cycleDuration = 3.6
    static let launchEndProgress = 0.16
    static let burstEndProgress = 0.80
    static let maxRadius: CGFloat = 76
    static let gravityDroop: CGFloat = 26
    static let headRadius: CGFloat = 2.5
    static let trailWidth: CGFloat = 1.6
    static let trailScale: CGFloat = 0.52
    static let rocketOffset: CGFloat = 44
    static let rocketRadius: CGFloat = 1.8

    static let paletteDark: [Color] = [
        Color(red: 1.000, green: 0.835, blue: 0.310),
        Color(red: 0.302, green: 0.816, blue: 0.882),
        Color(red: 1.000, green: 0.431, blue: 0.569),
        Color(red: 0.412, green: 0.941, blue: 0.682),
        Color(red: 1.000, green: 0.718, blue: 0.302)
    ]

    static let paletteLight: [Color] = [
        Color(red: 0.851, green: 0.510, blue: 0.000),
        Color(red: 0.000, green: 0.475, blue: 0.549),
        Color(red: 0.761, green: 0.094, blue: 0.357),
        Color(red: 0.106, green: 0.478, blue: 0.290),
        Color(red: 0.369, green: 0.208, blue: 0.694)
    ]

    static let burstTimeOffsets: [Double] = (0..<burstCount).map {
        Double($0) / Double(burstCount) * cycleDuration
    }

    static let sparks: [(angle: Double, speed: CGFloat)] = {
        var rng = SplitMix64(seed: 20_260_101)
        let twoPi = 2.0 * Double.pi
        return (0..<(burstCount * sparksPerBurst)).map { index in
            let slot = index % sparksPerBurst
            let jitter = (rng.nextUnitDouble() - 0.5) * (twoPi / Double(sparksPerBurst))
            let angle = Double(slot) / Double(sparksPerBurst) * twoPi + jitter
            let speed = CGFloat(0.55 + rng.nextUnitDouble() * 0.55)
            return (angle: angle, speed: speed)
        }
    }()
}

/// Renders non-interactive seasonal background particles behind views when a holiday theme is active.
struct HolidayParticlesView: View {
    private static let staticTime: Double = 2.4

    @Environment(\.activeHolidayTheme) private var activeHolidayTheme
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.holidayAnimationStaticTime) private var staticTime

    var body: some View {
        if let holidayTheme = activeHolidayTheme {
            let isPaused = reduceMotion || staticTime != nil
            TimelineView(.animation(paused: isPaused)) { context in
                let elapsed = staticTime ?? (reduceMotion ? Self.staticTime : context.date.timeIntervalSinceReferenceDate)
                Canvas { graphicsContext, size in
                    guard size.width > 0, size.height > 0 else { return }
                    let isDark = colorScheme == .dark
                    switch holidayTheme {
                    case .winterHolidays:
                        drawSnow(in: &graphicsContext, size: size, elapsed: elapsed, isDark: isDark)
                    case .newYear:
                        drawFireworks(in: &graphicsContext, size: size, elapsed: elapsed, isDark: isDark)
                    }
                }
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            }
        }
    }

    private func drawSnow(in context: inout GraphicsContext, size: CGSize, elapsed: Double, isDark: Bool) {
        let baseColor = isDark ? SnowField.darkColor : SnowField.lightColor
        let twoPi = 2.0 * Double.pi

        for flake in SnowField.flakes {
            let yFraction = (flake.yPhase + flake.speed * elapsed).truncatingRemainder(dividingBy: 1.0)
            let sway = CGFloat(sin(elapsed * flake.swayFrequency + flake.yPhase * twoPi)) * flake.swayAmplitude
            let rawX = (flake.xFraction * size.width + sway).truncatingRemainder(dividingBy: size.width)
            let x = rawX < 0 ? rawX + size.width : rawX
            let y = CGFloat(yFraction) * size.height
            let alpha = SnowField.minAlpha + flake.alphaFraction * SnowField.alphaRange
            let color = baseColor.opacity(alpha)

            if flake.isCrystal {
                drawCrystalFlake(in: &context, center: CGPoint(x: x, y: y), radius: flake.radius, color: color)
            } else {
                let circleRect = CGRect(
                    x: x - flake.radius,
                    y: y - flake.radius,
                    width: flake.radius * 2,
                    height: flake.radius * 2
                )
                context.fill(Path(ellipseIn: circleRect), with: .color(color))
            }
        }
    }

    private func drawCrystalFlake(in context: inout GraphicsContext, center: CGPoint, radius: CGFloat, color: Color) {
        let stepAngle = Double.pi / 3.0
        var path = Path()
        for arm in 0..<3 {
            let angle = Double(arm) * stepAngle
            let dx = CGFloat(cos(angle)) * radius
            let dy = CGFloat(sin(angle)) * radius
            path.move(to: CGPoint(x: center.x - dx, y: center.y - dy))
            path.addLine(to: CGPoint(x: center.x + dx, y: center.y + dy))
        }
        context.stroke(
            path,
            with: .color(color),
            style: StrokeStyle(lineWidth: SnowField.crystalStrokeWidth, lineCap: .round)
        )
    }

    private func drawFireworks(in context: inout GraphicsContext, size: CGSize, elapsed: Double, isDark: Bool) {
        let palette = isDark ? FireworkField.paletteDark : FireworkField.paletteLight
        let maxAlpha = isDark ? 0.85 : 0.65

        for burst in 0..<FireworkField.burstCount {
            let shifted = elapsed + FireworkField.burstTimeOffsets[burst]
            let cycle = Int(shifted / FireworkField.cycleDuration)
            let progress = shifted.truncatingRemainder(dividingBy: FireworkField.cycleDuration) / FireworkField.cycleDuration
            if progress > FireworkField.burstEndProgress { continue }

            let xFraction = CGFloat((burst * 37 + cycle * 19) % 97) / 97.0
            let yFraction = CGFloat((burst * 53 + cycle * 37) % 97) / 97.0
            let origin = CGPoint(
                x: (0.15 + xFraction * 0.70) * size.width,
                y: (0.10 + yFraction * 0.75) * size.height
            )
            let color = palette[(burst + cycle) % palette.count]

            if progress < FireworkField.launchEndProgress {
                let launchFrac = progress / FireworkField.launchEndProgress
                let rocketY = origin.y + FireworkField.rocketOffset * CGFloat(1.0 - launchFrac)
                let rect = CGRect(
                    x: origin.x - FireworkField.rocketRadius,
                    y: rocketY - FireworkField.rocketRadius,
                    width: FireworkField.rocketRadius * 2,
                    height: FireworkField.rocketRadius * 2
                )
                context.fill(Path(ellipseIn: rect), with: .color(color.opacity(maxAlpha * launchFrac)))
            } else {
                let burstAge = (progress - FireworkField.launchEndProgress) /
                    (FireworkField.burstEndProgress - FireworkField.launchEndProgress)
                let alpha = min(max((1.0 - burstAge) * maxAlpha, 0.0), 1.0)
                drawFireworkBurst(
                    in: &context,
                    burstIndex: burst,
                    origin: origin,
                    burstAge: burstAge,
                    fillColor: color.opacity(alpha)
                )
            }
        }
    }

    private func drawFireworkBurst(
        in context: inout GraphicsContext,
        burstIndex: Int,
        origin: CGPoint,
        burstAge: Double,
        fillColor: Color
    ) {
        let invAge = 1.0 - burstAge
        let eased = CGFloat(1.0 - invAge * invAge)
        let droop = FireworkField.gravityDroop * CGFloat(burstAge * burstAge)
        let baseSparkIndex = burstIndex * FireworkField.sparksPerBurst
        let headRadius = FireworkField.headRadius * CGFloat(0.5 + 0.5 * invAge)

        var trailPath = Path()
        for spark in 0..<FireworkField.sparksPerBurst {
            let spec = FireworkField.sparks[baseSparkIndex + spark]
            let dist = FireworkField.maxRadius * spec.speed * eased
            let tailDist = dist * FireworkField.trailScale
            let cosA = CGFloat(cos(spec.angle))
            let sinA = CGFloat(sin(spec.angle))
            let start = CGPoint(x: origin.x + cosA * tailDist, y: origin.y + sinA * tailDist + droop * FireworkField.trailScale)
            let end = CGPoint(x: origin.x + cosA * dist, y: origin.y + sinA * dist + droop)
            trailPath.move(to: start)
            trailPath.addLine(to: end)
            let headRect = CGRect(
                x: end.x - headRadius,
                y: end.y - headRadius,
                width: headRadius * 2,
                height: headRadius * 2
            )
            context.fill(Path(ellipseIn: headRect), with: .color(fillColor))
        }
        context.stroke(
            trailPath,
            with: .color(fillColor.opacity(Double(FireworkField.trailScale))),
            style: StrokeStyle(lineWidth: FireworkField.trailWidth, lineCap: .round)
        )
    }
}
