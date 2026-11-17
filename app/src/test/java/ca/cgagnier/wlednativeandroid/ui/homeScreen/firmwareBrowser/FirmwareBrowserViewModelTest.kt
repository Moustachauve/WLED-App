package ca.cgagnier.wlednativeandroid.ui.homeScreen.firmwareBrowser

import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import com.diffplug.selfie.Selfie.expectSelfie
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirmwareBrowserViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val versionWithAssetsRepository: VersionWithAssetsRepository = mockk(relaxed = true)
    private val githubApi: GithubApi = mockk(relaxed = true)
    private val releaseService: ReleaseService = mockk(relaxed = true)
    private val deviceConnectionManager: DeviceConnectionManager = mockk(relaxed = true)

    private val devicesFlow = MutableStateFlow<List<DeviceWithState>>(emptyList())
    private val repositoriesFlow = MutableStateFlow<List<Repository>>(emptyList())
    private val wledVersionsFlow = MutableStateFlow<List<VersionWithAssets>>(emptyList())
    private val quinledVersionsFlow = MutableStateFlow<List<VersionWithAssets>>(emptyList())

    private val wledRepo = Repository(
        id = 1L,
        ownerAndRepo = "wled/WLED",
        name = "WLED",
        description = "Official WLED",
        htmlUrl = "https://github.com/wled/WLED",
        isDefault = true,
        isEnabled = true,
    )
    private val quinledRepo = Repository(
        id = 2L,
        ownerAndRepo = "intermittech/QuinLED-Firmware",
        name = "QuinLED",
        description = "QuinLED Firmware",
        htmlUrl = "https://github.com/intermittech/QuinLED-Firmware",
        isDefault = false,
        isEnabled = true,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repositoriesFlow.value = listOf(wledRepo, quinledRepo)
        every { deviceConnectionManager.allDevicesWithState } returns devicesFlow
        every { versionWithAssetsRepository.observeAllRepositories() } returns repositoriesFlow
        coEvery { versionWithAssetsRepository.getRepositoryById(1L) } returns wledRepo
        coEvery { versionWithAssetsRepository.getRepositoryById(2L) } returns quinledRepo
        every { versionWithAssetsRepository.observeVersionsForRepository(1L) } returns wledVersionsFlow
        every { versionWithAssetsRepository.observeVersionsForRepository(2L) } returns quinledVersionsFlow
        coEvery { releaseService.refreshVersionsForRepository(any(), any()) } returns true
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(macAddress: String = MAC_ADDRESS): FirmwareBrowserViewModel = FirmwareBrowserViewModel(
        macAddress = macAddress,
        versionWithAssetsRepository = versionWithAssetsRepository,
        githubApi = githubApi,
        releaseService = releaseService,
        deviceConnectionManager = deviceConnectionManager,
        ioDispatcher = testDispatcher,
    )

    private fun TestScope.collectUiState(viewModel: FirmwareBrowserViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    private fun makeDevice(
        repositoryId: Long = 1L,
        branch: Branch = Branch.STABLE,
        version: String = "0.14.4",
        release: String? = "ESP32",
    ): DeviceWithState = DeviceWithState(
        device = Device(
            macAddress = MAC_ADDRESS,
            address = "192.168.1.50",
            originalName = "Desk WLED",
            branch = branch,
            repositoryId = repositoryId,
        ),
        websocketStatus = WebsocketStatus.CONNECTED,
        stateInfo = DeviceStateInfo(
            state = State(isOn = true, brightness = 128, transition = 7),
            info = Info(
                leds = Leds(count = 60),
                wifi = Wifi(rssi = -55, signal = 90),
                name = "Desk WLED",
                version = version,
                release = release,
                platformName = "esp32",
            ),
        ),
    )

    private fun makeVersion(
        id: Long,
        repositoryId: Long,
        tagName: String,
        isPrerelease: Boolean = false,
        assetNames: List<String> = emptyList(),
    ): VersionWithAssets = VersionWithAssets(
        version = Version(
            id = id,
            repositoryId = repositoryId,
            tagName = tagName,
            name = tagName,
            description = "Release notes for $tagName",
            isPrerelease = isPrerelease,
            publishedDate = "2025-05-01T00:00:00Z",
            htmlUrl = "https://github.com/wled/WLED/releases/tag/$tagName",
        ),
        assets = assetNames.mapIndexed { index, name ->
            Asset(
                id = id * 100 + index,
                versionId = id,
                name = name,
                size = 1_000_000L,
                downloadUrl = "https://example.com/$name",
                assetId = (id * 100 + index).toInt(),
            )
        },
    )

    @Test
    fun `initializes repository and branch defaults from target device`() = runTest(testDispatcher) {
        devicesFlow.value = listOf(makeDevice(repositoryId = 2L, branch = Branch.BETA))
        quinledVersionsFlow.value = listOf(
            makeVersion(id = 1L, repositoryId = 2L, tagName = "v0.15.0-b1", isPrerelease = true),
        )
        val viewModel = createViewModel()
        collectUiState(viewModel)

        advanceUntilIdle()

        assertEquals(2L, viewModel.uiState.value.selectedRepositoryId)
        assertEquals(Branch.BETA, viewModel.uiState.value.selectedBranch)
    }

    @Test
    fun `filters and sorts versions descending per selected branch`() = runTest(testDispatcher) {
        devicesFlow.value = listOf(makeDevice(repositoryId = 1L, branch = Branch.STABLE))
        wledVersionsFlow.value = listOf(
            makeVersion(id = 1L, repositoryId = 1L, tagName = "v0.14.3"),
            makeVersion(id = 2L, repositoryId = 1L, tagName = "v0.15.1"),
            makeVersion(id = 3L, repositoryId = 1L, tagName = "v0.15.0-b2", isPrerelease = true),
            makeVersion(id = 4L, repositoryId = 1L, tagName = "v0.14.4"),
        )
        val viewModel = createViewModel()
        collectUiState(viewModel)
        advanceUntilIdle()

        val stableTags = viewModel.uiState.value.versions.joinToString(", ") { it.version.tagName }
        expectSelfie(stableTags).toBe("v0.15.1, v0.14.4, v0.14.3")
    }

    @Test
    fun `selecting repository with empty cache automatically refreshes from GitHub`() = runTest(testDispatcher) {
        devicesFlow.value = listOf(makeDevice(repositoryId = 1L))
        wledVersionsFlow.value = listOf(makeVersion(id = 1L, repositoryId = 1L, tagName = "v0.15.0"))
        coEvery { releaseService.refreshVersionsForRepository(githubApi, quinledRepo) } returns false
        val viewModel = createViewModel()
        collectUiState(viewModel)
        advanceUntilIdle()

        viewModel.selectRepository(2L)
        advanceUntilIdle()

        coVerify(exactly = 1) { releaseService.refreshVersionsForRepository(githubApi, quinledRepo) }
        assertTrue(viewModel.uiState.value.loadError)
    }

    @Test
    fun `showVersionDetails preselects matching binary asset when compatible`() = runTest(testDispatcher) {
        devicesFlow.value = listOf(makeDevice(repositoryId = 1L, release = "ESP32"))
        val version = makeVersion(
            id = 1L,
            repositoryId = 1L,
            tagName = "v0.15.0",
            assetNames = listOf(
                "WLED_0.15.0_ESP8266.bin",
                "WLED_0.15.0_ESP32.bin",
                "WLED_0.15.0_ESP32_bootloader.bin",
            ),
        )
        wledVersionsFlow.value = listOf(version)
        val viewModel = createViewModel()
        collectUiState(viewModel)
        advanceUntilIdle()

        viewModel.showVersionDetails(version)
        advanceUntilIdle()

        val details = viewModel.uiState.value.versionDetails
        expectSelfie(details?.installableAssets?.joinToString(", ") { it.name }.orEmpty())
            .toBe("WLED_0.15.0_ESP32.bin, WLED_0.15.0_ESP8266.bin")
        assertEquals("WLED_0.15.0_ESP32.bin", details?.selectedAsset?.name)
    }

    @Test
    fun `showVersionDetails leaves selectedAsset null for cross-fork naming until chosen`() = runTest(testDispatcher) {
        devicesFlow.value = listOf(makeDevice(repositoryId = 1L, release = "ESP32"))
        val quinledVersion = makeVersion(
            id = 10L,
            repositoryId = 2L,
            tagName = "v0.15.0",
            assetNames = listOf(
                "WLED_0.15.0_Dig-Uno-V3.bin",
                "WLED_0.15.0_Dig-Quad-V3.bin",
            ),
        )
        wledVersionsFlow.value = listOf(makeVersion(id = 1L, repositoryId = 1L, tagName = "v0.14.4"))
        val viewModel = createViewModel()
        collectUiState(viewModel)
        advanceUntilIdle()

        viewModel.showVersionDetails(quinledVersion)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.versionDetails?.selectedAsset)
    }

    @Test
    fun `confirmDisclaimer moves disclaimerTarget to installingTarget`() = runTest(testDispatcher) {
        val quinledVersion = makeVersion(
            id = 10L,
            repositoryId = 2L,
            tagName = "v0.15.0",
            assetNames = listOf("WLED_0.15.0_Dig-Uno-V3.bin"),
        )
        val viewModel = prepareViewModelAtDisclaimer(quinledVersion)

        viewModel.confirmDisclaimer()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.disclaimerTarget)
        assertEquals(quinledVersion, viewModel.uiState.value.installingTarget?.versionWithAssets)
    }

    @Test
    fun `onInstallFinished clears installingTarget`() = runTest(testDispatcher) {
        val quinledVersion = makeVersion(
            id = 10L,
            repositoryId = 2L,
            tagName = "v0.15.0",
            assetNames = listOf("WLED_0.15.0_Dig-Uno-V3.bin"),
        )
        val viewModel = prepareViewModelAtDisclaimer(quinledVersion)
        viewModel.confirmDisclaimer()
        advanceUntilIdle()

        viewModel.onInstallFinished()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.installingTarget)
    }

    private fun TestScope.prepareViewModelAtDisclaimer(targetVersion: VersionWithAssets): FirmwareBrowserViewModel {
        devicesFlow.value = listOf(makeDevice(repositoryId = 1L))
        wledVersionsFlow.value = listOf(makeVersion(id = 1L, repositoryId = 1L, tagName = "v0.14.4"))
        val viewModel = createViewModel()
        collectUiState(viewModel)
        advanceUntilIdle()
        viewModel.showVersionDetails(targetVersion)
        viewModel.selectBinaryAsset(targetVersion.assets.first())
        viewModel.requestInstallSelectedVersion()
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `switching source during auto-refresh keeps loading state for newly selected source`() =
        runTest(testDispatcher) {
            val wledGate = CompletableDeferred<Boolean>()
            val quinledGate = CompletableDeferred<Boolean>()
            coEvery { releaseService.refreshVersionsForRepository(githubApi, wledRepo) } coAnswers {
                wledGate.await()
            }
            coEvery { releaseService.refreshVersionsForRepository(githubApi, quinledRepo) } coAnswers {
                quinledGate.await()
            }
            devicesFlow.value = listOf(makeDevice(repositoryId = 1L))
            val viewModel = createViewModel()
            collectUiState(viewModel)
            runCurrent()

            viewModel.selectRepository(2L)
            runCurrent()

            assertTrue(viewModel.uiState.value.isLoadingVersions)
            quinledGate.complete(true)
            wledGate.complete(true)
        }

    @Test
    fun `switching back to source whose initial auto-refresh failed retries fetch`() = runTest(testDispatcher) {
        coEvery { releaseService.refreshVersionsForRepository(githubApi, wledRepo) } returnsMany listOf(false, true)
        quinledVersionsFlow.value = listOf(makeVersion(id = 10L, repositoryId = 2L, tagName = "v0.15.0"))
        devicesFlow.value = listOf(makeDevice(repositoryId = 1L))
        val viewModel = createViewModel()
        collectUiState(viewModel)
        advanceUntilIdle()

        viewModel.selectRepository(2L)
        advanceUntilIdle()
        viewModel.selectRepository(1L)
        advanceUntilIdle()

        coVerify(exactly = 2) { releaseService.refreshVersionsForRepository(githubApi, wledRepo) }
    }

    @Test
    fun `switching back to source whose initial auto-refresh was cancelled retries fetch`() = runTest(testDispatcher) {
        val wledGate = CompletableDeferred<Boolean>()
        coEvery { releaseService.refreshVersionsForRepository(githubApi, wledRepo) } coAnswers {
            wledGate.await()
        }
        quinledVersionsFlow.value = listOf(makeVersion(id = 10L, repositoryId = 2L, tagName = "v0.15.0"))
        devicesFlow.value = listOf(makeDevice(repositoryId = 1L))
        val viewModel = createViewModel()
        collectUiState(viewModel)
        runCurrent()

        viewModel.selectRepository(2L)
        advanceUntilIdle()
        wledGate.complete(true)

        viewModel.selectRepository(1L)
        advanceUntilIdle()

        coVerify(exactly = 2) { releaseService.refreshVersionsForRepository(githubApi, wledRepo) }
    }

    companion object {
        private const val MAC_ADDRESS = "AA:BB:CC:DD:EE:01"
    }
}
