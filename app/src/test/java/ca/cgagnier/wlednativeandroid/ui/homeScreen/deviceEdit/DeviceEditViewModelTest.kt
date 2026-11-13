package ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit

import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DeviceEditViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceEditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val deviceRepository: DeviceRepository = mockk(relaxed = true)
    private val versionWithAssetsRepository: VersionWithAssetsRepository = mockk(relaxed = true)
    private val githubApi: GithubApi = mockk(relaxed = true)
    private val releaseService: ReleaseService = mockk(relaxed = true)
    private val deviceConnectionManager: DeviceConnectionManager = mockk(relaxed = true)
    private lateinit var viewModel: DeviceEditViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { versionWithAssetsRepository.getLatestBetaVersionWithAssetsFlow(any()) } returns flowOf(null)
        viewModel = DeviceEditViewModel(
            deviceRepository = deviceRepository,
            versionWithAssetsRepository = versionWithAssetsRepository,
            githubApi = githubApi,
            releaseService = releaseService,
            deviceConnectionManager = deviceConnectionManager,
            ioDispatcher = testDispatcher,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val sampleDevice = Device(
        macAddress = "AA:BB:CC:DD:EE:01",
        address = "192.168.0.1",
        originalName = "Office Light",
        branch = Branch.STABLE,
    )

    private fun makeDeviceWithState(
        version: String = "1.5.2",
        branch: Branch = Branch.STABLE,
        brand: String? = "ILLUMIDEL",
        options: Int = 1,
        websocketStatus: WebsocketStatus = WebsocketStatus.CONNECTED,
    ): DeviceWithState = DeviceWithState(
        device = sampleDevice.copy(branch = branch),
        websocketStatus = websocketStatus,
        stateInfo = DeviceStateInfo(
            state = State(isOn = true, brightness = 200, transition = 7),
            info = Info(
                leds = Leds(count = 30, fps = 30, maxPower = 0, maxSegment = 1),
                wifi = Wifi(bssid = "mac", rssi = -50, signal = 100, channel = 1),
                name = "Illumidel",
                version = version,
                brand = brand,
                product = branch.languageCode,
                options = options,
            ),
        ),
    )

    private fun makeVersion(tagName: String = "1.5.3"): VersionWithAssets = VersionWithAssets(
        version = Version.getPreviewVersion().copy(tagName = tagName),
        assets = emptyList(),
    )

    private fun TestScope.collectUiState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    @Test
    fun `updateCustomName persists targeted custom name`() = runTest(testDispatcher) {
        viewModel.updateCustomName(sampleDevice, "Desk Strip")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            deviceRepository.updateCustomName("AA:BB:CC:DD:EE:01", "Desk Strip")
        }
    }

    @Test
    fun `updateDeviceHidden persists targeted hidden state`() = runTest(testDispatcher) {
        viewModel.updateDeviceHidden(sampleDevice, true)
        advanceUntilIdle()

        coVerify(exactly = 1) { deviceRepository.updateIsHidden("AA:BB:CC:DD:EE:01", true) }
    }

    @Test
    fun `updateDeviceBranch stages target branch and starts current version OTA install without mutating Room`() =
        runTest(testDispatcher) {
            collectUiState()
            val deviceWithState = makeDeviceWithState(version = "1.5.2", branch = Branch.STABLE)
            val currentVersion = makeVersion("1.5.2")
            coEvery { versionWithAssetsRepository.getVersionByTag(1L, "1.5.2") } returns currentVersion

            viewModel.updateDeviceBranch(deviceWithState, Branch.BETA)
            advanceUntilIdle()

            coVerify(exactly = 0) { deviceRepository.updateBranch(any(), any()) }
            assertEquals(currentVersion, viewModel.uiState.value.updateInstallVersion)
            assertEquals(Branch.BETA, viewModel.uiState.value.installTargetBranch)
        }

    @Test
    fun `updateDeviceBranch refreshes versions when current version is not cached`() = runTest(testDispatcher) {
        collectUiState()
        val deviceWithState = makeDeviceWithState(version = "1.5.2", branch = Branch.STABLE)
        val currentVersion = makeVersion("1.5.2")
        coEvery { versionWithAssetsRepository.getVersionByTag(1L, "1.5.2") } returnsMany listOf(null, currentVersion)

        viewModel.updateDeviceBranch(deviceWithState, Branch.BETA)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            releaseService.refreshVersionsForDevice(githubApi, sampleDevice.copy(branch = Branch.BETA))
        }
        coVerify(exactly = 0) { deviceRepository.updateBranch(any(), any()) }
        assertEquals(currentVersion, viewModel.uiState.value.updateInstallVersion)
        assertEquals(Branch.BETA, viewModel.uiState.value.installTargetBranch)
    }

    @Test
    fun `updateDeviceBranch clears staged target branch when version remains unresolved after refresh`() =
        runTest(testDispatcher) {
            collectUiState()
            val deviceWithState = makeDeviceWithState(version = "1.5.2", branch = Branch.STABLE)
            coEvery { versionWithAssetsRepository.getVersionByTag(1L, "1.5.2") } returns null

            viewModel.updateDeviceBranch(deviceWithState, Branch.BETA)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                releaseService.refreshVersionsForDevice(githubApi, sampleDevice.copy(branch = Branch.BETA))
            }
            coVerify(exactly = 0) { deviceRepository.updateBranch(any(), any()) }
            assertNull(viewModel.uiState.value.updateInstallVersion)
            assertNull(viewModel.uiState.value.installTargetBranch)
        }

    @Test
    fun `updateDeviceBranch ignores ineligible offline unknown-version or matching-language devices`() =
        runTest(testDispatcher) {
            collectUiState()
            listOf(
                makeDeviceWithState(version = "1.5.2", websocketStatus = WebsocketStatus.DISCONNECTED),
                makeDeviceWithState(version = "", websocketStatus = WebsocketStatus.CONNECTED),
                makeDeviceWithState(version = "1.5.2", brand = "WLED"),
                makeDeviceWithState(version = "1.5.2", options = 0),
                makeDeviceWithState(version = "1.5.2", branch = Branch.UNKNOWN),
            ).forEach { ineligibleDevice ->
                val targetBranch = if (ineligibleDevice.device.branch == Branch.UNKNOWN) Branch.STABLE else Branch.BETA
                viewModel.updateDeviceBranch(ineligibleDevice, targetBranch)
                advanceUntilIdle()
            }

            coVerify(exactly = 0) { versionWithAssetsRepository.getVersionByTag(any(), any()) }
            assertNull(viewModel.uiState.value.installTargetBranch)
        }

    @Test
    fun `cancelling language switch OTA install clears staged branch without writing to Room`() =
        runTest(testDispatcher) {
            collectUiState()
            val deviceWithState = makeDeviceWithState(version = "1.5.2", branch = Branch.STABLE)
            val currentVersion = makeVersion("1.5.2")
            coEvery { versionWithAssetsRepository.getVersionByTag(1L, "1.5.2") } returns currentVersion

            viewModel.updateDeviceBranch(deviceWithState, Branch.BETA)
            advanceUntilIdle()

            viewModel.stopUpdateInstall(
                device = deviceWithState.copy(device = deviceWithState.device.copy(branch = Branch.BETA)),
                wasSuccessful = false,
            )
            advanceUntilIdle()

            coVerify(exactly = 0) { deviceRepository.updateBranch(any(), any()) }
            assertNull(viewModel.uiState.value.installTargetBranch)
            assertNull(viewModel.uiState.value.updateInstallVersion)
        }

    @Test
    fun `completing language switch OTA install persists target branch and marks installed`() =
        runTest(testDispatcher) {
            collectUiState()
            val deviceWithState = makeDeviceWithState(version = "1.5.2", branch = Branch.STABLE)
            val currentVersion = makeVersion("1.5.2")
            coEvery { versionWithAssetsRepository.getVersionByTag(1L, "1.5.2") } returns currentVersion

            viewModel.updateDeviceBranch(deviceWithState, Branch.BETA)
            advanceUntilIdle()

            viewModel.stopUpdateInstall(
                device = deviceWithState.copy(device = deviceWithState.device.copy(branch = Branch.BETA)),
                wasSuccessful = true,
            )
            advanceUntilIdle()

            coVerify(exactly = 1) { deviceRepository.updateBranch("AA:BB:CC:DD:EE:01", Branch.BETA) }
            verify(exactly = 1) {
                deviceConnectionManager.markUpdateInstalled("AA:BB:CC:DD:EE:01", "1.5.2", Branch.BETA)
            }
        }

    @Test
    fun `skipUpdate persists targeted skipUpdateTag and clears details dialog`() = runTest(testDispatcher) {
        collectUiState()
        val version = makeVersion("1.5.3")
        coEvery { versionWithAssetsRepository.getVersionByTag(1L, "1.5.3") } returns version

        viewModel.showUpdateDetails(1L, "1.5.3")
        advanceUntilIdle()
        assertEquals(version, viewModel.uiState.value.updateDetailsVersion)

        viewModel.skipUpdate(sampleDevice, version)
        advanceUntilIdle()

        coVerify(exactly = 1) { deviceRepository.updateSkipUpdateTag("AA:BB:CC:DD:EE:01", "1.5.3") }
        assertNull(viewModel.uiState.value.updateDetailsVersion)
    }

    @Test
    fun `checkForUpdates clears skipUpdateTag refreshes releases and rechecks updates`() = runTest(testDispatcher) {
        collectUiState()

        viewModel.checkForUpdates(sampleDevice)
        advanceUntilIdle()

        coVerifyOrder {
            deviceRepository.updateSkipUpdateTag("AA:BB:CC:DD:EE:01", "")
            releaseService.refreshVersionsForDevice(githubApi, sampleDevice)
            deviceConnectionManager.recheckUpdates()
        }
        assertFalse(viewModel.uiState.value.isCheckingUpdates)
    }

    @Test
    fun `stopUpdateInstall marks update installed on success`() {
        val deviceWithState = makeDeviceWithState("1.5.2")
        val version = makeVersion("1.5.3")

        viewModel.startUpdateInstall(version)
        viewModel.stopUpdateInstall(device = deviceWithState, wasSuccessful = true)

        verify(exactly = 1) {
            deviceConnectionManager.markUpdateInstalled("AA:BB:CC:DD:EE:01", "1.5.3", null)
        }
    }

    @Test
    fun `stopUpdateInstall does not mark update installed when unsuccessful`() {
        val deviceWithState = makeDeviceWithState("1.5.2")
        val version = makeVersion("1.5.3")

        viewModel.startUpdateInstall(version)
        viewModel.stopUpdateInstall(device = deviceWithState, wasSuccessful = false)

        verify(exactly = 0) { deviceConnectionManager.markUpdateInstalled(any(), any(), any()) }
    }

    @Test
    fun `stopUpdateInstall does not mark update installed when device is null`() {
        val version = makeVersion("1.5.3")

        viewModel.startUpdateInstall(version)
        viewModel.stopUpdateInstall(device = null, wasSuccessful = true)

        verify(exactly = 0) { deviceConnectionManager.markUpdateInstalled(any(), any(), any()) }
    }

    @Test
    fun `startUpdateInstall sets install version`() = runTest(testDispatcher) {
        collectUiState()
        val version = makeVersion("1.5.3")

        viewModel.startUpdateInstall(version)
        advanceUntilIdle()

        assertEquals(version, viewModel.uiState.value.updateInstallVersion)
    }

    @Test
    fun `stopUpdateInstall clears install version`() = runTest(testDispatcher) {
        collectUiState()
        viewModel.startUpdateInstall(makeVersion("1.5.3"))
        advanceUntilIdle()

        viewModel.stopUpdateInstall()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.updateInstallVersion)
    }

    @Test
    fun `showUpdateDisclaimer sets disclaimer version`() = runTest(testDispatcher) {
        collectUiState()
        val version = makeVersion("2.2.1")

        viewModel.showUpdateDisclaimer(version)
        advanceUntilIdle()

        assertEquals(version, viewModel.uiState.value.updateDisclaimerVersion)
    }

    @Test
    fun `hideUpdateDisclaimer clears disclaimer version`() = runTest(testDispatcher) {
        collectUiState()
        viewModel.showUpdateDisclaimer(makeVersion("2.2.1"))
        advanceUntilIdle()

        viewModel.hideUpdateDisclaimer()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.updateDisclaimerVersion)
    }
}
