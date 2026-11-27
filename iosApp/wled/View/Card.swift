import SwiftUI

enum CardStyle {
    case `default`
    case device(color: Color)
    case warning
}

struct Card<Content: View>: View {
    @Environment(\.activeHolidayTheme) private var activeHolidayTheme
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.colorScheme) private var colorScheme
    let style: CardStyle
    @ViewBuilder let content: () -> Content

    init(style: CardStyle = .default, @ViewBuilder content: @escaping () -> Content) {
        self.style = style
        self.content = content
    }

    private var isHolidayDeviceCard: Bool {
        guard activeHolidayTheme != nil, case .device = style else { return false }
        return true
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(backgroundView)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay {
            if isHolidayDeviceCard {
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(
                        Color.primary.opacity(colorScheme == .dark ? 0.14 : 0.08),
                        lineWidth: 0.75
                    )
            }
        }
    }

    @ViewBuilder
    private var backgroundView: some View {
        switch style {
        case .default:
            Color(UIColor.secondarySystemBackground)
        case .device(let color):
            if activeHolidayTheme != nil {
                ZStack {
                    Rectangle().fill(.ultraThinMaterial)
                    Rectangle().fill(color.opacity(colorScheme == .dark ? 0.30 : 0.24))
                }
                .opacity(reduceTransparency ? 1.0 : 0.78)
            } else {
                ZStack {
                    Rectangle().fill(color)
                    Rectangle().fill(.thickMaterial)
                }
            }
        case .warning:
            ZStack {
                Color(UIColor.secondarySystemBackground)
                Color.red.opacity(0.1)
            }
        }
    }
}

struct Card_Previews: PreviewProvider {
    static var previews: some View {
        VStack {
            Card {
                Text("Default Card")
            }
            Card(style: .device(color: .blue)) {
                Text("Device Card")
            }
            Card(style: .warning) {
                Text("Warning Card")
            }
        }
        .padding()
        .previewLayout(.sizeThatFits)
    }
}
