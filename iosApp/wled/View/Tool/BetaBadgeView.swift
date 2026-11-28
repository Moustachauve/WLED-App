import SwiftUI

struct BetaBadgeView: View {
    enum Style {
        case inline
        case logoOverlay
    }

    var style: Style = .inline

    var body: some View {
        switch style {
        case .inline:
            Text("BETA")
                .font(.caption2.weight(.semibold))
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(Color.accentColor.opacity(0.15), in: Capsule())
                .foregroundStyle(.tint)
        case .logoOverlay:
            Text("BETA")
                .font(.system(size: 8, weight: .bold, design: .rounded))
                .padding(.horizontal, 5)
                .padding(.vertical, 1.5)
                .foregroundStyle(.tint)
                .background(Color(.systemBackground), in: Capsule())
                .background(Color.accentColor.opacity(0.2), in: Capsule())
                .overlay(Capsule().strokeBorder(Color.accentColor.opacity(0.35), lineWidth: 0.75))
        }
    }
}
