package ca.cgagnier.wlednativeandroid.ui.homeScreen.firmwareBrowser

import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
    private val githubApi: GithubApi = mockk(relaxed = true)
    private val releaseService: ReleaseService = mockk(relaxed = true)
    private val deviceConnectionManager: DeviceConnectionManager = mockk(relaxed = true)

    private val wledRepo = Repository.BUILT_IN_REPOSITORIES.first().copy(id = 1L)
    private val quinledRepo = Repository.BUILT_IN_REPOSITORIES[1].copy(id = 2L)
    private val devicesFlow = MutableStateFlow(
        listOf(
            DeviceWithState(
                device = Device(macAddress = MAC_ADDRESS, address = "192.168.1.50", repositoryId = 1L),
                websocketStatus = WebsocketStatus.CONNECTED,
                stateInfo = DeviceStateInfo.createMock(version = "0.14.4"),
            ),
        ),
    )

    private val quinledAsset = Asset(
        versionId = 10L,
        name = "WLED_0.15.0_Dig-Uno-V3.bin",
        size = 1024L,
        downloadUrl = "",
        assetId = 1,
    )
    private val quinledVersion = VersionWithAssets(
        version = Version.getPreviewVersion().copy(id = 10L, repositoryId = 2L, tagName = "0.15.0"),
        assets = listOf(quinledAsset),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { deviceConnectionManager.allDevicesWithState } returns devicesFlow
        every { releaseService.observeAllRepositories() } returns MutableStateFlow(listOf(wledRepo, quinledRepo))
        every { releaseService.observeVersionsForRepository(any()) } returns MutableStateFlow(listOf(quinledVersion))
        coEvery { releaseService.refreshVersionsForRepositoryId(any(), any()) } returns true
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): FirmwareBrowserViewModel = FirmwareBrowserViewModel(
        macAddress = MAC_ADDRESS,
        githubApi = githubApi,
        releaseService = releaseService,
        deviceConnectionManager = deviceConnectionManager,
    )

    private suspend fun TestScope.advanceToDisclaimer(
        viewModel: FirmwareBrowserViewModel,
        selectAsset: Asset? = quinledAsset,
    ) {
        viewModel.showVersionDetails(quinledVersion)
        if (selectAsset != null) {
            viewModel.selectBinaryAsset(selectAsset)
        }
        viewModel.requestInstallSelectedVersion()
        advanceUntilIdle()
    }

    private suspend fun TestScope.advanceToInstalling(viewModel: FirmwareBrowserViewModel) {
        advanceToDisclaimer(viewModel)
        viewModel.confirmDisclaimer()
        advanceUntilIdle()
    }

    @Test
    fun `showVersionDetails populates installable assets without requiring collector`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showVersionDetails(quinledVersion)
        advanceUntilIdle()

        val dialog = viewModel.uiState.value.dialog as FirmwareBrowserDialog.VersionDetails
        assertEquals(listOf(quinledAsset), dialog.installableAssets)
        assertNull(dialog.selectedAsset)
    }

    @Test
    fun `requestInstallSelectedVersion advances to Disclaimer with cross-source name`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        advanceToDisclaimer(viewModel)

        val dialog = viewModel.uiState.value.dialog as FirmwareBrowserDialog.Disclaimer
        assertEquals(quinledRepo.name, dialog.switchingToSourceName)
        assertEquals(quinledAsset, dialog.selectedAsset)
    }

    @Test
    fun `requestInstallSelectedVersion stays on VersionDetails when selectedAsset is null`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        advanceToDisclaimer(viewModel, selectAsset = null)

        assertTrue(viewModel.uiState.value.dialog is FirmwareBrowserDialog.VersionDetails)
    }

    @Test
    fun `requestInstallSelectedVersion uses auto-matched asset when deviceInfo arrives later`() = runTest(
        testDispatcher,
    ) {
        val matchingAsset = Asset(
            versionId = 10L,
            name = "WLED_0.15.0_ESP32.bin",
            size = 1024L,
            downloadUrl = "",
            assetId = 2,
        )
        val versionWithEsp32 = quinledVersion.copy(assets = listOf(quinledAsset, matchingAsset))
        devicesFlow.value = listOf(
            DeviceWithState(
                device = Device(macAddress = MAC_ADDRESS, address = "192.168.1.50", repositoryId = 1L),
                websocketStatus = WebsocketStatus.CONNECTED,
                stateInfo = null,
            ),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showVersionDetails(versionWithEsp32)
        advanceUntilIdle()
        assertNull((viewModel.uiState.value.dialog as FirmwareBrowserDialog.VersionDetails).selectedAsset)

        val esp32State = DeviceStateInfo.createMock(version = "0.14.4").let { mock ->
            mock.copy(info = mock.info.copy(release = "ESP32", platformName = "esp32"))
        }
        devicesFlow.value = listOf(devicesFlow.value.first().copy(stateInfo = esp32State))
        advanceUntilIdle()
        viewModel.requestInstallSelectedVersion()
        advanceUntilIdle()

        val dialog = viewModel.uiState.value.dialog as FirmwareBrowserDialog.Disclaimer
        assertEquals(matchingAsset, dialog.selectedAsset)
    }

    @Test
    fun `confirmDisclaimer advances Disclaimer to Installing dialog`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        advanceToInstalling(viewModel)

        assertTrue(viewModel.uiState.value.dialog is FirmwareBrowserDialog.Installing)
    }

    @Test
    fun `onInstallFinished clears active Installing dialog`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        advanceToInstalling(viewModel)
        viewModel.onInstallFinished()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
    }

    companion object {
        private const val MAC_ADDRESS = "AA:BB:CC:DD:EE:01"
    }
}
