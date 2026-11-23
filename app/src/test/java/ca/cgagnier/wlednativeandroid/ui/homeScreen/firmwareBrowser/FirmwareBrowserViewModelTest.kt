package ca.cgagnier.wlednativeandroid.ui.homeScreen.firmwareBrowser

import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Branch
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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
                device = Device(
                    macAddress = MAC_ADDRESS,
                    address = "192.168.1.50",
                    repositoryId = 1L,
                    branch = Branch.STABLE,
                ),
                websocketStatus = WebsocketStatus.CONNECTED,
                stateInfo = DeviceStateInfo.createMock(version = "0.14.4"),
            ),
        ),
    )

    private val esp32Asset = Asset(
        versionId = 10L,
        name = "WLED_0.15.0_ESP32.bin",
        size = 1024L,
        downloadUrl = "",
        assetId = 1,
    )
    private val digUnoAsset = Asset(
        versionId = 11L,
        name = "WLED_0.15.0_Dig-Uno-V3.bin",
        size = 1024L,
        downloadUrl = "",
        assetId = 2,
    )
    private val officialVersion = VersionWithAssets(
        version = Version.getPreviewVersion().copy(id = 10L, repositoryId = 1L, tagName = "0.14.4"),
        assets = listOf(esp32Asset),
    )
    private val quinledVersion = VersionWithAssets(
        version = Version.getPreviewVersion().copy(id = 11L, repositoryId = 2L, tagName = "0.15.0"),
        assets = listOf(digUnoAsset),
    )
    private val betaVersion = VersionWithAssets(
        version = Version.getPreviewVersion().copy(
            id = 12L,
            repositoryId = 1L,
            tagName = "0.15.0-b1",
            isPrerelease = true,
        ),
        assets = listOf(esp32Asset),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { deviceConnectionManager.allDevicesWithState } returns devicesFlow
        every { releaseService.observeAllRepositories() } returns MutableStateFlow(listOf(wledRepo, quinledRepo))
        every { releaseService.observeVersionsForRepository(1L) } returns
            MutableStateFlow(listOf(officialVersion, betaVersion))
        every { releaseService.observeVersionsForRepository(2L) } returns MutableStateFlow(listOf(quinledVersion))
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

    private fun advanceToDisclaimer(
        viewModel: FirmwareBrowserViewModel,
        version: VersionWithAssets = quinledVersion,
        asset: Asset = digUnoAsset,
    ) {
        viewModel.selectRepository(version.version.repositoryId)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.selectVersion(version)
        viewModel.selectBinaryAsset(asset)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.requestInstallSelectedVersion()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun advanceToInstalling(viewModel: FirmwareBrowserViewModel) {
        advanceToDisclaimer(viewModel)
        viewModel.confirmDisclaimer()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `initial state uses device repository, branch, and installed tags`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1L, state.selectedRepositoryId)
        assertEquals(Branch.STABLE, state.selectedBranch)
        assertEquals(setOf("0.14.4"), state.installedTags)
        assertFalse(state.isLoadingVersions)
        assertFalse(state.loadError)
    }

    @Test
    fun `initial load refreshes device repository once`() = runTest(testDispatcher) {
        createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { releaseService.refreshVersionsForRepositoryId(githubApi, 1L) }
    }

    @Test
    fun `initial branch follows device beta channel preference`() = runTest(testDispatcher) {
        devicesFlow.value = listOf(
            devicesFlow.value.first().copy(
                device = devicesFlow.value.first().device.copy(branch = Branch.BETA),
            ),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(Branch.BETA, viewModel.uiState.value.selectedBranch)
        assertEquals(listOf("0.15.0-b1"), viewModel.uiState.value.versions.map { it.version.tagName })
    }

    @Test
    fun `selectRepository displays versions from selected repository and clears selection`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectVersion(officialVersion)
        advanceUntilIdle()

        viewModel.selectRepository(2L)
        advanceUntilIdle()

        assertEquals(listOf(quinledVersion), viewModel.uiState.value.versions)
        assertNull(viewModel.uiState.value.selectedVersion)
    }

    @Test
    fun `selectRepository refreshes each repository only once across re-selection`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectRepository(2L)
        advanceUntilIdle()
        viewModel.selectRepository(1L)
        advanceUntilIdle()
        viewModel.selectRepository(2L)
        advanceUntilIdle()

        coVerify(exactly = 1) { releaseService.refreshVersionsForRepositoryId(githubApi, 1L) }
        coVerify(exactly = 1) { releaseService.refreshVersionsForRepositoryId(githubApi, 2L) }
    }

    @Test
    fun `failed initial refresh sets loadError`() = runTest(testDispatcher) {
        coEvery { releaseService.refreshVersionsForRepositoryId(githubApi, 1L) } returns false
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.loadError)
    }

    @Test
    fun `manual retry clears loadError after failed refresh`() = runTest(testDispatcher) {
        coEvery { releaseService.refreshVersionsForRepositoryId(githubApi, 1L) } returns false andThen true
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.refreshSelectedRepository()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.loadError)
        coVerify(exactly = 2) { releaseService.refreshVersionsForRepositoryId(githubApi, 1L) }
    }

    @Test
    fun `selectBranch updates displayed versions and clears selection`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectVersion(officialVersion)
        advanceUntilIdle()

        viewModel.selectBranch(Branch.BETA)
        advanceUntilIdle()

        assertEquals(listOf("0.15.0-b1"), viewModel.uiState.value.versions.map { it.version.tagName })
        assertNull(viewModel.uiState.value.selectedVersion)
    }

    @Test
    fun `requestInstallSelectedVersion does not open disclaimer without a selected binary`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectVersion(officialVersion)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.selectedVersion)
        assertNull(viewModel.uiState.value.selectedVersion?.selectedAsset)

        viewModel.requestInstallSelectedVersion()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `requestInstallSelectedVersion opens Disclaimer with cross-source warning name`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        advanceToDisclaimer(viewModel)

        val disclaimer = viewModel.uiState.value.dialog as FirmwareBrowserDialog.Disclaimer
        assertEquals(quinledRepo.name, disclaimer.switchingToSourceName)
        assertEquals(digUnoAsset, disclaimer.selectedAsset)
    }

    @Test
    fun `requestInstallSelectedVersion omits switchingToSourceName for current source`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        advanceToDisclaimer(viewModel, version = officialVersion, asset = esp32Asset)

        val disclaimer = viewModel.uiState.value.dialog as FirmwareBrowserDialog.Disclaimer
        assertNull(disclaimer.switchingToSourceName)
        assertEquals(esp32Asset, disclaimer.selectedAsset)
    }

    @Test
    fun `dismissDisclaimer clears dialog while keeping selected version`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        advanceToDisclaimer(viewModel)

        viewModel.dismissDisclaimer()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
        assertEquals(quinledVersion, viewModel.uiState.value.selectedVersion?.versionWithAssets)
    }

    @Test
    fun `confirmDisclaimer advances to Installing dialog`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        advanceToInstalling(viewModel)

        assertEquals(FirmwareBrowserDialog.Installing(quinledVersion, digUnoAsset), viewModel.uiState.value.dialog)
    }

    @Test
    fun `onInstallFinished dismisses active dialog`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        advanceToInstalling(viewModel)

        viewModel.onInstallFinished()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `selectVersion resolves default asset once late device info arrives`() = runTest(testDispatcher) {
        val twoAssetVersion = officialVersion.copy(assets = listOf(digUnoAsset, esp32Asset))
        devicesFlow.value = listOf(devicesFlow.value.first().copy(stateInfo = null))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectVersion(twoAssetVersion)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedVersion?.selectedAsset)

        val esp32State = DeviceStateInfo.createMock(version = "0.14.4").let {
            it.copy(info = it.info.copy(release = "ESP32", platformName = "esp32"))
        }
        devicesFlow.value = listOf(devicesFlow.value.first().copy(stateInfo = esp32State))
        advanceUntilIdle()

        assertEquals(esp32Asset, viewModel.uiState.value.selectedVersion?.selectedAsset)
    }

    @Test
    fun `switching back during in-flight refresh keeps loading without duplicate fetch`() = runTest(testDispatcher) {
        val releaseFirstRefresh = CompletableDeferred<Boolean>()
        coEvery { releaseService.refreshVersionsForRepositoryId(githubApi, 1L) } coAnswers {
            releaseFirstRefresh.await()
        }
        val viewModel = createViewModel()
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoadingVersions)

        viewModel.selectRepository(2L)
        runCurrent()
        viewModel.selectRepository(1L)
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoadingVersions)

        releaseFirstRefresh.complete(true)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoadingVersions)
        coVerify(exactly = 1) { releaseService.refreshVersionsForRepositoryId(githubApi, 1L) }
    }

    private fun stubSuspendedRepoQuery(repositoryId: Long) {
        val gate = CompletableDeferred<Unit>()
        every { releaseService.observeVersionsForRepository(repositoryId) } returns flow {
            gate.await()
            emit(emptyList())
        }
    }

    @Test
    fun `selectRepository shows empty loading state while new repo query is pending`() = runTest(testDispatcher) {
        stubSuspendedRepoQuery(2L)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectVersion(officialVersion)
        advanceUntilIdle()
        viewModel.selectRepository(2L)
        runCurrent()

        val loadingState = viewModel.uiState.value
        assertEquals(2L, loadingState.selectedRepositoryId)
        assertTrue(loadingState.versions.isEmpty())
        assertTrue(loadingState.isLoadingVersions)
        assertNull(loadingState.selectedVersion)
    }

    @Test
    fun `selection from a previous repository is hidden for the active repository`() = runTest(testDispatcher) {
        stubSuspendedRepoQuery(2L)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectRepository(2L)
        runCurrent()
        viewModel.selectVersion(officialVersion)
        runCurrent()

        assertNull(viewModel.uiState.value.selectedVersion)
    }

    private companion object {
        const val MAC_ADDRESS = "AA:BB:CC:DD:EE:01"
    }
}
