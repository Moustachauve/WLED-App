import Foundation
import Shared
import SwiftUI
import Observation
import OSLog

@MainActor
@Observable
final class DeviceWebsocketListViewModel {

    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "DeviceWebsocketListViewModel"
    )

    // MARK: - Observable Properties

    // The list of devices with their live state, exposed to the UI
    var allDevicesWithState: [DeviceWithState] = []
    var visibleDevices: [DeviceWithState] = []
    var onlineDevices: [DeviceWithState] = []
    var offlineDevices: [DeviceWithState] = []

    /// Whether the local network permission has been denied by the user.
    var localNetworkDenied: Bool = false

    /// Whether device connections are currently paused (e.g. backgrounded).
    var isPaused: Bool = false

    // Preferences
    var showHiddenDevices: Bool = false {
        didSet {
            guard showHiddenDevices != oldValue else { return }
            updateFilteredDevices(currentTime: Date())
            guard !isSyncingPreferences else { return }
            let showHidden = showHiddenDevices
            persistPreference { try await $0.updateShowHiddenDevices(showHiddenDevices: showHidden) }
        }
    }

    var showOfflineDevicesLast: Bool = true {
        didSet {
            guard showOfflineDevicesLast != oldValue, !isSyncingPreferences else { return }
            let showOfflineLast = showOfflineDevicesLast
            persistPreference { try await $0.updateShowOfflineDeviceLast(showOfflineDeviceLast: showOfflineLast) }
        }
    }

    var theme: ThemeSettings = .auto {
        didSet {
            guard theme != oldValue, !isSyncingPreferences else { return }
            let selectedTheme = theme
            persistPreference { try await $0.updateThemeMode(themeSettings: selectedTheme) }
        }
    }

    /// Whether devices are discovered automatically when the app comes to the foreground.
    /// Toggling it from the UI scans right away when enabled and stops any scan in progress when disabled.
    var autoDiscovery: Bool = true {
        didSet {
            guard autoDiscovery != oldValue, !isSyncingPreferences else { return }
            let enabled = autoDiscovery
            persistPreference { try await $0.updateAutoDiscovery(autoDiscover: enabled) }
            cancelAutoDiscovery()
            if enabled {
                startDiscovery()
            } else {
                stopDiscovery()
            }
        }
    }

    var soundSyncBetaEnabled: Bool = false {
        didSet {
            guard soundSyncBetaEnabled != oldValue, !isSyncingPreferences else { return }
            let enabled = soundSyncBetaEnabled
            persistPreference { try await $0.updateSoundSyncBetaEnabled(enabled: enabled) }
        }
    }

    var holidayThemesEnabled: Bool = true {
        didSet {
            guard holidayThemesEnabled != oldValue, !isSyncingPreferences else { return }
            let enabled = holidayThemesEnabled
            persistPreference { try await $0.updateHolidayThemesEnabled(enabled: enabled) }
        }
    }

    var holidayThemeOverride: HolidayTheme? {
        didSet {
            guard holidayThemeOverride != oldValue, !isSyncingPreferences else { return }
            let selectedOverride = holidayThemeOverride
            persistPreference { try await $0.updateHolidayThemeOverride(override: selectedOverride) }
        }
    }

    private var currentDate: CalendarDate

    var activeHolidayTheme: HolidayTheme? {
        HolidayThemeResolver.shared.resolve(
            enabled: holidayThemesEnabled,
            override: holidayThemeOverride,
            date: currentDate
        )
    }

    // MARK: - Private Properties

    private let connectionManager: DeviceConnectionManager
    private let database: AppDatabase
    private let deviceFirstContactService: DeviceFirstContactService
    private let userPreferencesRepository: UserPreferencesRepository
    @ObservationIgnored private let discoveryService: any DeviceDiscovering
    @ObservationIgnored private let currentDateProvider: () -> CalendarDate

    @ObservationIgnored private let partitionCache = DevicePartitionCache()
    @ObservationIgnored private let widgetSnapshotStore: WledWidgetSnapshotStore
    @ObservationIgnored private var isSyncingPreferences = false
    @ObservationIgnored private var autoDiscoveryTask: Task<Void, Never>?

    // MARK: - Initialization

    init(
        connectionManager: DeviceConnectionManager? = nil,
        database: AppDatabase = .shared,
        discoveryService: (any DeviceDiscovering)? = nil,
        widgetSnapshotStore: WledWidgetSnapshotStore = .shared,
        currentDateProvider: @escaping () -> CalendarDate = { PlatformKt.currentLocalDate() }
    ) {
        self.database = database
        self.connectionManager = connectionManager ?? database.deviceConnectionManager
        self.deviceFirstContactService = database.deviceFirstContactService
        self.userPreferencesRepository = database.userPreferencesRepository
        self.widgetSnapshotStore = widgetSnapshotStore
        self.currentDateProvider = currentDateProvider
        self.currentDate = currentDateProvider()

        let resolvedDiscoveryService = discoveryService ?? DiscoveryService()
        self.discoveryService = resolvedDiscoveryService

        resolvedDiscoveryService.onDeviceDiscovered = { [weak self] address, macAddress in
            self?.deviceDiscovered(at: address, withMACAddress: macAddress)
        }
        resolvedDiscoveryService.onLocalNetworkPermissionChanged = { [weak self] isGranted in
            self?.localNetworkDenied = !isGranted
        }
    }

    // MARK: - Setup and loading

    /// Observes device states, user preferences, connection pause status, and offline grace-period timers
    /// for as long as the caller's structured task remains active.
    func observe() async {
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await self.observeDevices() }
            group.addTask { await self.observeShowHiddenDevices() }
            group.addTask { await self.observeShowOfflineDevicesLast() }
            group.addTask { await self.observeThemeMode() }
            group.addTask { await self.observeAutoDiscovery() }
            group.addTask { await self.observeSoundSyncBetaEnabled() }
            group.addTask { await self.observeHolidayThemesEnabled() }
            group.addTask { await self.observeHolidayThemeOverride() }
            group.addTask { await self.observePausedState() }
            group.addTask { await self.runOfflineGracePeriodTimer() }
        }
    }

    private func observeDevices() async {
        await database.waitUntilReady()
        for await devices in connectionManager.allDevicesWithState {
            guard !Task.isCancelled else { break }
            syncDevicesWithState(devices)
        }
    }

    private func observeShowHiddenDevices() async {
        await observePreference(userPreferencesRepository.showHiddenDevices) { showHiddenDevices = $0.boolValue }
    }

    private func observeShowOfflineDevicesLast() async {
        await observePreference(userPreferencesRepository.showOfflineDevicesLast) {
            showOfflineDevicesLast = $0.boolValue
        }
    }

    private func observeThemeMode() async {
        await observePreference(userPreferencesRepository.themeMode) { theme = $0 }
    }

    private func observeAutoDiscovery() async {
        await observePreference(userPreferencesRepository.autoDiscovery) { autoDiscovery = $0.boolValue }
    }

    private func observeSoundSyncBetaEnabled() async {
        await observePreference(userPreferencesRepository.soundSyncBetaEnabled) {
            soundSyncBetaEnabled = $0.boolValue
        }
    }

    private func observeHolidayThemesEnabled() async {
        await observePreference(userPreferencesRepository.holidayThemesEnabled) {
            holidayThemesEnabled = $0.boolValue
        }
    }

    private func observeHolidayThemeOverride() async {
        await observePreference(userPreferencesRepository.holidayThemeOverride) {
            holidayThemeOverride = $0
        }
    }

    /// Mirrors a persisted preference into a local property without echoing the change back to the repository.
    private func observePreference<Element>(
        _ preference: some AsyncSequence<Element, Never>,
        apply: (Element) -> Void
    ) async {
        await database.waitUntilReady()
        for await value in preference {
            guard !Task.isCancelled else { break }
            isSyncingPreferences = true
            apply(value)
            isSyncingPreferences = false
        }
    }

    /// Persists a preference change made from the UI in the background.
    private func persistPreference(
        _ update: @escaping @Sendable (UserPreferencesRepository) async throws -> Void
    ) {
        let repository = userPreferencesRepository
        Task {
            try? await update(repository)
        }
    }

    private func observePausedState() async {
        for await paused in connectionManager.isPaused {
            guard !Task.isCancelled else { break }
            isPaused = paused.boolValue
        }
    }

    private func runOfflineGracePeriodTimer() async {
        while !Task.isCancelled {
            try? await Task.sleep(for: .seconds(30))
            guard !Task.isCancelled else { break }
            updateFilteredDevices(currentTime: Date())
        }
    }

    private func syncDevicesWithState(_ sharedDevices: [Shared.DeviceWithState]) {
        self.allDevicesWithState = partitionCache.sync(sharedDevices)
        self.updateFilteredDevices(currentTime: Date())
        self.widgetSnapshotStore.sync(devices: sharedDevices)
    }
}

// MARK: - Lifecycle, Actions & Discovery

extension DeviceWebsocketListViewModel {
    func onPause() {
        cancelAutoDiscovery()
        discoveryService.stop()
        connectionManager.onPause()
    }

    private func refreshDate() {
        let newDate = currentDateProvider()
        guard newDate != currentDate else { return }
        currentDate = newDate
    }

    /// Resumes device connections and starts automatic discovery if the user has it enabled.
    ///
    /// - Returns: The pending auto-discovery task, so callers (tests) can await its completion.
    @discardableResult
    func onResume() -> Task<Void, Never> {
        refreshDate()
        connectionManager.onResume()
        cancelAutoDiscovery()
        let task = Task { await startAutoDiscoveryIfEnabled() }
        autoDiscoveryTask = task
        return task
    }

    // MARK: - Actions

    func refreshOfflineDevices() {
        connectionManager.refreshOfflineDevices()
    }

    func setBrightness(for deviceWrapper: DeviceWithState, brightness: Int) {
        let mac = deviceWrapper.device.macAddress
        let manager = connectionManager
        Task {
            _ = try? await manager.setBrightness(macAddress: mac, brightness: Int32(brightness))
        }
    }

    func setDevicePower(for deviceWrapper: DeviceWithState, isOn: Bool) {
        let mac = deviceWrapper.device.macAddress
        let manager = connectionManager
        Task {
            _ = try? await manager.setPower(macAddress: mac, isOn: isOn)
        }
    }

    func deleteDevice(_ device: Device) {
        let manager = connectionManager
        let snapshotStore = widgetSnapshotStore
        Task {
            try? await manager.deleteDevice(device: device)
            snapshotStore.remove(macAddress: device.macAddress)
        }
    }

    func updateFilteredDevices(currentTime: Date = Date()) {
        let partitioned = partitionCache.partition(showHiddenDevices: showHiddenDevices, currentTime: currentTime)

        // Only update if content changed to avoid unnecessary SwiftUI view body evaluations
        if self.visibleDevices != partitioned.visible {
            self.visibleDevices = partitioned.visible
        }
        if self.onlineDevices != partitioned.online {
            self.onlineDevices = partitioned.online
        }
        if self.offlineDevices != partitioned.offline {
            self.offlineDevices = partitioned.offline
        }
    }

    // MARK: - Discovery Logic

    /// Starts a timed scan regardless of the auto-discovery preference (e.g. pull-to-refresh).
    func startDiscovery() {
        discoveryService.scan()
    }

    func stopDiscovery() {
        discoveryService.stop()
    }

    /// Starts a timed scan only if the user has automatic discovery enabled.
    ///
    /// Waits for the database to be ready so legacy preferences have been migrated before being read.
    func startAutoDiscoveryIfEnabled() async {
        await database.waitUntilReady()
        let isEnabled = await userPreferencesRepository.autoDiscovery.firstValue()?.boolValue ?? true
        guard !Task.isCancelled else { return }
        guard isEnabled else {
            Self.logger.info("Automatic discovery disabled, skipping scan")
            return
        }
        startDiscovery()
    }

    /// Cancels a pending auto-discovery (e.g. still waiting for the database) before it can start scanning.
    private func cancelAutoDiscovery() {
        autoDiscoveryTask?.cancel()
        autoDiscoveryTask = nil
    }

    /// Opens the app's Settings page where the user can toggle Local Network permission.
    func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    private func deviceDiscovered(at address: String, withMACAddress macAddress: String?) {
        Task {
            // Don't upsert discovered devices until legacy devices have been imported
            await database.waitUntilReady()
            do {
                try await deviceFirstContactService.processDiscoveredDevice(
                    rawAddress: address,
                    macAddress: macAddress
                )
            } catch {
                Self.logger.error("deviceDiscovered: Failed to upsert device: \(error.localizedDescription)")
            }
        }
    }
}
