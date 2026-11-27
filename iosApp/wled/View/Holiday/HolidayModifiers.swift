import SwiftUI

private struct HolidaySurfaceModifier: ViewModifier {
    @Environment(\.activeHolidayTheme) private var activeHolidayTheme
    let grouped: Bool

    private var baseBackgroundColor: Color {
        grouped ? Color(uiColor: .systemGroupedBackground) : Color(uiColor: .systemBackground)
    }

    func body(content: Content) -> some View {
        content
            .scrollContentBackground(activeHolidayTheme == nil ? .automatic : .hidden)
            .background {
                if activeHolidayTheme != nil {
                    ZStack {
                        baseBackgroundColor
                        HolidayParticlesView()
                    }
                    .ignoresSafeArea()
                }
            }
            .safeAreaInset(edge: .top, spacing: 0) {
                if activeHolidayTheme != nil {
                    HolidayStringLightsBar()
                }
            }
    }
}

extension View {
    /// Adds background holiday particles and top string lights when a holiday theme is active.
    func holidayFormSurface(grouped: Bool = true) -> some View {
        modifier(HolidaySurfaceModifier(grouped: grouped))
    }
}
