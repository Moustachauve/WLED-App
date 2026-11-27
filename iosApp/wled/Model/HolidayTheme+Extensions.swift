import Shared
import SwiftUI
import UIKit

extension Shared.HolidayTheme: @retroactive Identifiable {
    public var id: Self { self }

    var localizedName: LocalizedStringResource {
        switch self {
        case .winterHolidays:
            "Winter Holidays"
        case .newYear:
            "New Year"
        }
    }

    var accentColor: Color {
        switch self {
        case .winterHolidays:
            Color(uiColor: UIColor { traits in
                traits.userInterfaceStyle == .dark
                    ? UIColor(red: 0.298, green: 0.780, blue: 0.573, alpha: 1)
                    : UIColor(red: 0.106, green: 0.420, blue: 0.290, alpha: 1)
            })
        case .newYear:
            Color(uiColor: UIColor { traits in
                traits.userInterfaceStyle == .dark
                    ? UIColor(red: 0.624, green: 0.659, blue: 1.000, alpha: 1)
                    : UIColor(red: 0.224, green: 0.286, blue: 0.671, alpha: 1)
            })
        }
    }
}

extension EnvironmentValues {
    @Entry var activeHolidayTheme: HolidayTheme?
    /// Fixed timestamp in seconds for deterministic previews and snapshot tests; `nil` runs live animation.
    @Entry var holidayAnimationStaticTime: Double?
}
