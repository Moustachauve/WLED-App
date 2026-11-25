//
//  ReviewPrompt.swift
//  IllumiDEL
//
//  Decides when to show Apple's native "Rate this app" sheet (SKStoreReviewController,
//  through SwiftUI's `requestReview`). Same rules as Android:
//   - never on the first launches: only from the 5th launch,
//   - at least 3 days after the first launch,
//   - the user has at least 1 controller in the list,
//   - at most once every 120 days, and at most 3 times in total.
//  Apple also limits the sheet to 3 times per 365 days and may decide not to show it.
//  It is never shown in TestFlight builds, and in debug builds it always shows (Apple behaviour).
//

import Foundation

@MainActor
enum ReviewPrompt {
    private static let launchesKey = "reviewPrompt.launches"
    private static let firstLaunchKey = "reviewPrompt.firstLaunch"
    private static let lastAskedKey = "reviewPrompt.lastAsked"
    private static let timesAskedKey = "reviewPrompt.timesAsked"

    private static let minLaunches = 5
    private static let minDaysInstalled: Double = 3
    private static let daysBetweenAsks: Double = 120
    private static let maxAsks = 3

    private static var askedThisSession = false
    private static let day: TimeInterval = 24 * 60 * 60

    static func recordLaunch(defaults: UserDefaults = .standard) {
        defaults.set(defaults.integer(forKey: launchesKey) + 1, forKey: launchesKey)
        if defaults.object(forKey: firstLaunchKey) == nil {
            defaults.set(Date(), forKey: firstLaunchKey)
        }
    }

    static func shouldAsk(deviceCount: Int, defaults: UserDefaults = .standard, now: Date = Date()) -> Bool {
        guard !askedThisSession, deviceCount >= 1 else { return false }
        let launches = defaults.integer(forKey: launchesKey)
        let firstLaunch = defaults.object(forKey: firstLaunchKey) as? Date ?? now
        let lastAsked = defaults.object(forKey: lastAskedKey) as? Date ?? .distantPast
        let timesAsked = defaults.integer(forKey: timesAskedKey)

        return launches >= minLaunches
            && now.timeIntervalSince(firstLaunch) >= minDaysInstalled * day
            && now.timeIntervalSince(lastAsked) >= daysBetweenAsks * day
            && timesAsked < maxAsks
    }

    static func markAsked(defaults: UserDefaults = .standard) {
        askedThisSession = true
        defaults.set(Date(), forKey: lastAskedKey)
        defaults.set(defaults.integer(forKey: timesAskedKey) + 1, forKey: timesAskedKey)
    }
}
