package ca.cgagnier.wlednativeandroid.ui.homeScreen.update

import ca.cgagnier.wlednativeandroid.model.Asset
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
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateInstallingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val deviceRepository: DeviceRepository = mockk(relaxed = true)
    private val repositoryDao: RepositoryDao = mockk(relaxed = true)
    private val deviceApiFactory: DeviceApiFactory = mockk(relaxed = true)
    private val githubApi: GithubApi = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `resetState cancels in-flight download before installUpdate runs`(@TempDir tempDir: File) =
        runTest(testDispatcher) {
            val downloadFlow = MutableSharedFlow<DownloadState>()
            coEvery { repositoryDao.getRepositoryById(any()) } returns null
            every { githubApi.downloadReleaseBinary(any(), any(), any(), any()) } returns downloadFlow

            val viewModel = UpdateInstallingViewModel(
                deviceRepository = deviceRepository,
                repositoryDao = repositoryDao,
                deviceApiFactory = deviceApiFactory,
                githubApi = githubApi,
                ioDispatcher = testDispatcher,
            )
            val device = DeviceWithState(
                device = Device(macAddress = "AABBCCDDEEFF", address = "192.168.1.20", branch = Branch.BETA),
                stateInfo = DeviceStateInfo(
                    state = State(),
                    info = Info(leds = Leds(), wifi = Wifi(), name = "Illumidel", platformName = "esp32"),
                ),
            )
            val version = VersionWithAssets(
                version = Version.getPreviewVersion().copy(tagName = "1.5.2"),
                assets = listOf(
                    Asset(
                        versionId = 1L,
                        name = "v1.5.2_ESP32_en.bin",
                        size = 1024L,
                        downloadUrl = "https://example.com/v1.5.2_ESP32_en.bin",
                        assetId = 42,
                    ),
                ),
            )

            viewModel.startUpdate(device, version, tempDir)
            advanceUntilIdle()
            downloadFlow.emit(DownloadState.Downloading(40))
            advanceUntilIdle()
            assertEquals(UpdateInstallingStep.Downloading(40), viewModel.state.value.step)

            viewModel.resetState()
            downloadFlow.emit(DownloadState.Finished)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.step is UpdateInstallingStep.Starting)
            verify(exactly = 0) { deviceApiFactory.create(any<Device>()) }
        }
}
