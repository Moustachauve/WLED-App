//
//  UpdatePrompt.swift
//  IllumiDEL
//
//  Popup "Update 1.6 available" when one or more controllers can get a newer
//  IllumiDEL V1 firmware. It uses the update detection that already exists
//  (`availableUpdateVersion`, which respects the device channel and "Skip this version").
//   - shown once per launch at most, only for online controllers,
//   - after "Later", the same version is shown again only after 7 days,
//   - a new version is shown right away,
//   - versions 2.x and up are ignored (that is the move to the new cloud app,
//     which already has its own warning card).
//

import Foundation
import Shared

@MainActor
enum UpdatePrompt {
    struct Prompt {
        let tag: String
        let devices: [DeviceWithState]
    }

    private static let lastTagKey = "updatePrompt.lastTag"
    private static let lastShownKey = "updatePrompt.lastShown"
    private static let daysBeforeShowingAgain: Double = 7

    private(set) static var shownThisSession = false

    static func check(_ devices: [DeviceWithState], defaults: UserDefaults = .standard, now: Date = Date()) -> Prompt? {
        guard !shownThisSession else { return nil }
        let candidates = devices.filter { device in
            guard device.isOnline,
                  !device.device.isHidden,
                  let tag = device.availableUpdateVersion,
                  !tag.isEmpty
            else {
                return false
            }
            return majorVersion(of: tag).map { $0 < 2 } ?? false
        }
        guard let tag = candidates.compactMap(\.availableUpdateVersion).filter({ !$0.isEmpty }).max(by: isOlder) else {
            return nil
        }

        let lastTag = defaults.string(forKey: lastTagKey)
        let lastShown = defaults.object(forKey: lastShownKey) as? Date ?? .distantPast
        if tag == lastTag && now.timeIntervalSince(lastShown) < daysBeforeShowingAgain * 24 * 60 * 60 {
            return nil
        }
        return Prompt(tag: tag, devices: candidates)
    }

    static func markShown(_ tag: String, defaults: UserDefaults = .standard) {
        shownThisSession = true
        defaults.set(tag, forKey: lastTagKey)
        defaults.set(Date(), forKey: lastShownKey)
    }

    /// "v1.6.0" -> "1.6.0"
    private static func cleanTag(_ tag: String) -> String {
        tag.trimmingCharacters(in: CharacterSet(charactersIn: "vV "))
    }

    /// "v1.6.0" -> 1
    private static func majorVersion(of tag: String) -> Int? {
        Int(cleanTag(tag).split(separator: ".").first ?? "")
    }

    private static func isOlder(_ lhs: String, _ rhs: String) -> Bool {
        cleanTag(lhs).compare(cleanTag(rhs), options: .numeric) == .orderedAscending
    }
}
