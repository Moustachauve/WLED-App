package ca.cgagnier.wlednativeandroid.ui.homeScreen.update

import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.api.github.KtorGithubApiEndpoints
import ca.cgagnier.wlednativeandroid.service.api.installDeviceHttpClientDefaults
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateInstallingViewModelTest {

    @TempDir
    lateinit var tempDir: File

    private val testDispatcher = StandardTestDispatcher()
    private val deviceConnectionManager: DeviceConnectionManager = mockk(relaxed = true)
    private val repositoryDao: RepositoryDao = mockk(relaxed = true)

    private val expectedBinary = ByteArray(1024) { (it % 256).toByte() }
    private val sampleDevice = DeviceWithState(
        device = Device(
            macAddress = "AA:BB:CC:DD:EE:FF",
            address = "192.168.1.50",
            originalName = "Office WLED",
        ),
        stateInfo = DeviceStateInfo(
            state = State(),
            info = Info(
                leds = Leds(),
                wifi = Wifi(),
                name = "Office WLED",
                release = "ESP32",
                platformName = "esp32",
            ),
        ),
    )
    private val sampleVersion = VersionWithAssets(
        version = Version(
            id = 1L,
            repositoryId = Repository.DEFAULT_ID,
            tagName = "0.15.0",
            name = "WLED 0.15.0",
            description = "",
            isPrerelease = false,
            publishedDate = "2025-01-01T00:00:00Z",
            htmlUrl = "https://github.com/wled/WLED/releases/tag/v0.15.0",
        ),
        assets = listOf(
            Asset(
                versionId = 1L,
                name = "WLED_0.15.0_ESP32.bin",
                size = expectedBinary.size.toLong(),
                downloadUrl = "https://github.com/wled/WLED/releases/download/v0.15.0/WLED_0.15.0_ESP32.bin",
                assetId = 42,
            ),
        ),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repositoryDao.getRepositoryById(Repository.DEFAULT_ID) } returns
            Repository.BUILT_IN_REPOSITORIES.first()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun cachedBinaryFile(): File = File(tempDir, "${Repository.DEFAULT_ID}/0.15.0/WLED_0.15.0_ESP32.bin")

    private fun partBinaryFile(): File = File("${cachedBinaryFile().absolutePath}.part")

    private fun writeCachedBinary(bytes: ByteArray = expectedBinary): File = cachedBinaryFile().apply {
        parentFile?.mkdirs()
        writeBytes(bytes)
    }

    private fun MockRequestHandleScope.respondWithExpectedBinary(): HttpResponseData = respond(
        content = expectedBinary,
        status = HttpStatusCode.OK,
        headers = headersOf(
            HttpHeaders.ContentType to listOf(ContentType.Application.OctetStream.toString()),
            HttpHeaders.ContentLength to listOf(expectedBinary.size.toString()),
        ),
    )

    private fun MockRequestHandleScope.respondWithBinaryStream(channel: ByteChannel): HttpResponseData = respond(
        content = channel,
        status = HttpStatusCode.OK,
        headers = headersOf(
            HttpHeaders.ContentType to listOf(ContentType.Application.OctetStream.toString()),
            HttpHeaders.ContentLength to listOf(expectedBinary.size.toString()),
        ),
    )

    private fun createViewModel(
        onDownload: () -> Unit = {},
        onUpload: () -> Unit = {},
        downloadResponder: (suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData)? = null,
        uploadResponder: (suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData)? = null,
    ): UpdateInstallingViewModel {
        val httpClient = HttpClient(MockEngine) {
            engine {
                dispatcher = testDispatcher
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/repos/wled/WLED/releases/assets/42" -> {
                            onDownload()
                            downloadResponder?.invoke(this, request) ?: respondWithExpectedBinary()
                        }

                        "/update" -> {
                            onUpload()
                            uploadResponder?.invoke(this, request) ?: respond(
                                content = "Update Success!",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
                            )
                        }

                        else -> error("Unexpected request: ${request.url}")
                    }
                }
            }
            installDeviceHttpClientDefaults()
        }
        return UpdateInstallingViewModel(
            repositoryDao = repositoryDao,
            deviceApiFactory = DeviceApiFactory(httpClient),
            githubApi = GithubApi(KtorGithubApiEndpoints(httpClient, ioDispatcher = testDispatcher)),
            deviceConnectionManager = deviceConnectionManager,
            ioDispatcher = testDispatcher,
        )
    }

    @Test
    fun `startUpdate invalidates size-mismatched cached file and downloads binary before installing`() = runTest(
        testDispatcher,
    ) {
        // Simulate a stale 1,538-byte GitHub JSON file cached on disk instead of the 1,024-byte firmware.
        val cachedFile = writeCachedBinary(ByteArray(1538))

        var downloadRequests = 0
        var uploadRequests = 0
        val viewModel = createViewModel(
            onDownload = { downloadRequests++ },
            onUpload = { uploadRequests++ },
        )

        viewModel.startUpdate(sampleDevice, sampleVersion, tempDir)
        advanceUntilIdle()

        assertEquals(1, downloadRequests)
        assertEquals(1, uploadRequests)
        assertEquals(expectedBinary.size.toLong(), cachedFile.length())
        assertEquals(UpdateInstallingStep.Done, viewModel.state.value.step)
    }

    @Test
    fun `startUpdate reuses valid cached binary without downloading`() = runTest(testDispatcher) {
        writeCachedBinary()

        var downloadRequests = 0
        var uploadRequests = 0
        val viewModel = createViewModel(
            onDownload = { downloadRequests++ },
            onUpload = { uploadRequests++ },
        )

        viewModel.startUpdate(sampleDevice, sampleVersion, tempDir)
        advanceUntilIdle()

        assertEquals(0, downloadRequests)
        assertEquals(1, uploadRequests)
        assertEquals(UpdateInstallingStep.Done, viewModel.state.value.step)
    }

    @Test
    fun `successful install completes firmware install via connection manager`() = runTest(testDispatcher) {
        writeCachedBinary()
        val viewModel = createViewModel()

        viewModel.startUpdate(sampleDevice, sampleVersion, tempDir)
        advanceUntilIdle()

        assertEquals(UpdateInstallingStep.Done, viewModel.state.value.step)
        coVerify(exactly = 1) {
            deviceConnectionManager.completeFirmwareInstall(
                sampleDevice.device,
                sampleVersion.version,
                repositoryDao,
            )
        }
    }

    @Test
    fun `startUpdate exposes retryWithoutValidation when device rejects validation`() = runTest(testDispatcher) {
        writeCachedBinary()
        val html = "<body><h2>Update failed!</h2>" +
            "<h4>Enable &#39;Ignore firmware validation&#39; to proceed anyway.</h4></body>"
        val viewModel = createViewModel(
            uploadResponder = { respond(html, HttpStatusCode.InternalServerError) },
        )

        viewModel.startUpdate(sampleDevice, sampleVersion, tempDir)
        advanceUntilIdle()

        assertEquals(
            UpdateInstallingStep.Error(
                error = "500: Update failed! Enable 'Ignore firmware validation' to proceed anyway.",
                showError = true,
                canRetryWithoutValidation = true,
            ),
            viewModel.state.value.step,
        )
    }

    @Test
    fun `retryWithoutValidation uploads skipValidation part and completes`() = runTest(testDispatcher) {
        writeCachedBinary()
        val capturedBodies = mutableListOf<String>()
        val viewModel = createViewModel(
            uploadResponder = { request ->
                val bodyText = request.body.toByteArray().decodeToString()
                capturedBodies.add(bodyText)
                if ("name=\"skipValidation\"" in bodyText) {
                    respond("Update Success!", HttpStatusCode.OK)
                } else {
                    respond("Enable 'Ignore firmware validation' to proceed", HttpStatusCode.InternalServerError)
                }
            },
        )

        viewModel.startUpdate(sampleDevice, sampleVersion, tempDir)
        advanceUntilIdle()
        viewModel.retryWithoutValidation()
        advanceUntilIdle()

        assertEquals(UpdateInstallingStep.Done, viewModel.state.value.step)
        assertEquals(2, capturedBodies.size)
        assertFalse("name=\"skipValidation\"" in capturedBodies[0])
        assertTrue("name=\"skipValidation\"" in capturedBodies[1])
    }

    @Test
    fun `retryWithoutValidation disables retry if validation fails again`() = runTest(testDispatcher) {
        writeCachedBinary()
        val viewModel = createViewModel(
            uploadResponder = {
                respond("Enable 'Ignore firmware validation' to proceed", HttpStatusCode.InternalServerError)
            },
        )

        viewModel.startUpdate(sampleDevice, sampleVersion, tempDir)
        advanceUntilIdle()
        viewModel.retryWithoutValidation()
        advanceUntilIdle()

        val step = viewModel.state.value.step as UpdateInstallingStep.Error
        assertFalse(step.canRetryWithoutValidation)
    }

    @Test
    fun `startUpdate does not offer retryWithoutValidation on non-validation error`() = runTest(testDispatcher) {
        writeCachedBinary()
        val viewModel = createViewModel(
            uploadResponder = { respond("Not enough space", HttpStatusCode.InternalServerError) },
        )

        viewModel.startUpdate(sampleDevice, sampleVersion, tempDir)
        advanceUntilIdle()

        assertEquals(
            UpdateInstallingStep.Error(
                error = "500: Not enough space",
                showError = false,
                canRetryWithoutValidation = false,
            ),
            viewModel.state.value.step,
        )
    }

    @Test
    fun `resetState restores initial UpdateInstallingState after completed update`() = runTest(testDispatcher) {
        writeCachedBinary()

        val viewModel = createViewModel()
        viewModel.startUpdate(sampleDevice, sampleVersion, tempDir)
        advanceUntilIdle()
        assertEquals(UpdateInstallingStep.Done, viewModel.state.value.step)

        viewModel.resetState()

        assertEquals(UpdateInstallingState(), viewModel.state.value)
    }

    @Test
    fun `resetState cancels in-flight download without installing update`() = runTest(testDispatcher) {
        val partialChannel = ByteChannel(autoFlush = true)
        val releaseStream = CompletableDeferred<Unit>()
        var uploadRequests = 0
        val viewModel = createViewModel(
            onUpload = { uploadRequests++ },
            downloadResponder = { respondWithBinaryStream(partialChannel) },
        )

        backgroundScope.launch {
            partialChannel.writeFully(expectedBinary, 0, 512)
            releaseStream.await()
            partialChannel.writeFully(expectedBinary, 512, expectedBinary.size)
            partialChannel.flushAndClose()
        }

        viewModel.startUpdate(sampleDevice, sampleVersion, tempDir)
        runCurrent()

        assertTrue(viewModel.state.value.step is UpdateInstallingStep.Downloading)
        assertTrue(partBinaryFile().exists())

        viewModel.resetState()
        releaseStream.complete(Unit)
        advanceUntilIdle()

        assertEquals(0, uploadRequests)
        assertEquals(UpdateInstallingState(), viewModel.state.value)
        assertFalse(cachedBinaryFile().exists())
        assertFalse(partBinaryFile().exists())
    }

    @Test
    fun `startUpdate succeeds after cancelling previous download with resetState`() = runTest(testDispatcher) {
        val partialChannel = ByteChannel(autoFlush = true)
        val releaseFirstStream = CompletableDeferred<Unit>()
        val responders = ArrayDeque<suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData>(
            listOf(
                { respondWithBinaryStream(partialChannel) },
                { respondWithExpectedBinary() },
            ),
        )
        var downloadAttempts = 0
        var uploadRequests = 0
        val viewModel = createViewModel(
            onDownload = { downloadAttempts++ },
            onUpload = { uploadRequests++ },
            downloadResponder = { request -> responders.removeFirst().invoke(this, request) },
        )

        backgroundScope.launch {
            partialChannel.writeFully(expectedBinary, 0, 512)
            releaseFirstStream.await()
            partialChannel.writeFully(expectedBinary, 512, expectedBinary.size)
            partialChannel.flushAndClose()
        }

        viewModel.startUpdate(sampleDevice, sampleVersion, tempDir)
        runCurrent()
        viewModel.resetState()
        releaseFirstStream.complete(Unit)
        advanceUntilIdle()

        viewModel.startUpdate(sampleDevice, sampleVersion, tempDir)
        advanceUntilIdle()

        assertEquals(2, downloadAttempts)
        assertEquals(1, uploadRequests)
        assertEquals(UpdateInstallingStep.Done, viewModel.state.value.step)
        assertEquals(expectedBinary.size.toLong(), cachedBinaryFile().length())
    }

    private fun stubFirstLookupCleanupPause(
        firstCleanupStarted: CompletableDeferred<Unit>,
        releaseFirstCleanup: CompletableDeferred<Unit>,
        onLookup: () -> Unit,
    ) {
        var isFirstLookup = true
        coEvery { repositoryDao.getRepositoryById(Repository.DEFAULT_ID) } coAnswers {
            onLookup()
            if (isFirstLookup) {
                isFirstLookup = false
                try {
                    CompletableDeferred<Repository?>().await()
                } finally {
                    withContext(NonCancellable) {
                        firstCleanupStarted.complete(Unit)
                        releaseFirstCleanup.await()
                    }
                }
            } else {
                Repository.BUILT_IN_REPOSITORIES.first()
            }
        }
    }

    private suspend fun TestScope.assertStartUpdateAwaitsCancelledJobCleanup(extraCancelledAttempts: Int) {
        val firstCleanupStarted = CompletableDeferred<Unit>()
        val releaseFirstCleanup = CompletableDeferred<Unit>()
        var lookupCount = 0
        var downloadAttempts = 0
        var uploadRequests = 0
        stubFirstLookupCleanupPause(firstCleanupStarted, releaseFirstCleanup) { lookupCount++ }
        val viewModel = createViewModel(
            onDownload = { downloadAttempts++ },
            onUpload = { uploadRequests++ },
        )

        viewModel.startUpdate(sampleDevice, sampleVersion, tempDir)
        runCurrent()
        assertEquals(1, lookupCount)

        viewModel.resetState()
        runCurrent()
        assertTrue(firstCleanupStarted.isCompleted)

        repeat(extraCancelledAttempts) {
            viewModel.startUpdate(sampleDevice, sampleVersion, tempDir)
            runCurrent()
            viewModel.resetState()
            runCurrent()
        }

        viewModel.startUpdate(sampleDevice, sampleVersion, tempDir)
        runCurrent()
        // The retry must wait until the original job finishes cleanup before starting a download.
        assertEquals(1, lookupCount)
        assertEquals(0, downloadAttempts)

        releaseFirstCleanup.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, lookupCount)
        assertEquals(1, downloadAttempts)
        assertEquals(1, uploadRequests)
        assertEquals(UpdateInstallingStep.Done, viewModel.state.value.step)
    }

    @Test
    fun `startUpdate awaits completion of previously cancelled download job`() = runTest(testDispatcher) {
        assertStartUpdateAwaitsCancelledJobCleanup(extraCancelledAttempts = 0)
    }

    @Test
    fun `startUpdate awaits original download job after chained resetState`() = runTest(testDispatcher) {
        assertStartUpdateAwaitsCancelledJobCleanup(extraCancelledAttempts = 1)
    }
}
