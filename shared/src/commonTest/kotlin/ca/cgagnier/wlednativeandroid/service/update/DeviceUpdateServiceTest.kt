package ca.cgagnier.wlednativeandroid.service.update

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
import ca.cgagnier.wlednativeandroid.service.api.ApiResponse
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.api.github.KtorGithubApiEndpoints
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceUpdateServiceTest {

    private data class AssetResolutionCase(
        val targetVersionTag: String,
        val release: String?,
        val platformName: String? = null,
        val expectedAssetName: String,
    )

    private fun makeVersion(tagName: String, repositoryId: Long = 1L) = Version(
        tagName = tagName,
        name = "WLED $tagName",
        description = "",
        isPrerelease = tagName.contains("-"),
        publishedDate = "2024-01-01T00:00:00Z",
        htmlUrl = "https://github.com/wled/WLED",
        repositoryId = repositoryId,
    )

    private fun makeAsset(versionId: Long, assetName: String) = Asset(
        versionId = versionId,
        name = assetName,
        size = 1024L,
        downloadUrl = "https://example.com/$assetName",
        assetId = 0,
    )

    private fun newTempCacheDir(): Path =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "wled_update_test_${Random.nextLong(0, Long.MAX_VALUE)}"

    private fun makeDeviceUpdateService(
        targetVersionTag: String,
        release: String? = null,
        platformName: String? = null,
        availableAssets: List<Asset>,
        cacheDir: Path = newTempCacheDir(),
    ): DeviceUpdateService {
        val stateInfo = if (release != null || platformName != null) {
            DeviceStateInfo(
                state = State(),
                info = Info(
                    leds = Leds(),
                    wifi = Wifi(),
                    name = "Test Device",
                    release = release,
                    platformName = platformName,
                ),
            )
        } else {
            null
        }
        return makeDeviceUpdateService(
            versionWithAssets = VersionWithAssets(
                version = makeVersion(targetVersionTag),
                assets = availableAssets,
            ),
            stateInfo = stateInfo,
            cacheDir = cacheDir,
        )
    }

    private fun makeDeviceUpdateService(
        versionWithAssets: VersionWithAssets,
        stateInfo: DeviceStateInfo? = null,
        cacheDir: Path = newTempCacheDir(),
        selectedAsset: Asset? = null,
        httpClient: HttpClient = HttpClient(MockEngine { respond("", HttpStatusCode.OK) }),
    ): DeviceUpdateService {
        val device = Device(macAddress = "AA:BB:CC:DD:EE:FF", address = "192.168.1.1", repositoryId = 1L)
        return DeviceUpdateService(
            device = DeviceWithState(device, stateInfo = stateInfo),
            versionWithAssets = versionWithAssets,
            cacheDir = cacheDir,
            deviceApiFactory = DeviceApiFactory(httpClient),
            githubApi = GithubApi(KtorGithubApiEndpoints(httpClient)),
            selectedAsset = selectedAsset,
        )
    }

    private fun assertDeterminedAssets(cases: List<AssetResolutionCase>) {
        cases.forEach { case ->
            val service = makeDeviceUpdateService(
                targetVersionTag = case.targetVersionTag,
                release = case.release,
                platformName = case.platformName,
                availableAssets = listOf(makeAsset(1L, case.expectedAssetName)),
            )

            assertTrue(
                service.couldDetermineAsset(),
                "Expected asset for ${case.targetVersionTag} / ${case.release} / ${case.platformName}",
            )
            assertEquals(case.expectedAssetName, service.getAssetName())
        }
    }

    @Test
    fun determineAssetAppliesReleaseOverrideOn016AndNewer() {
        assertDeterminedAssets(
            listOf(
                AssetResolutionCase("0.16.0", "ESP32_V4", expectedAssetName = "WLED_0.16.0_ESP32.bin"),
                AssetResolutionCase("0.16.1", "ESP32_V4", expectedAssetName = "WLED_0.16.1_ESP32.bin"),
                AssetResolutionCase("0.16.0", "esp32_v4", expectedAssetName = "WLED_0.16.0_ESP32.bin"),
                AssetResolutionCase("0.16.0-b2", "ESP32_V4", expectedAssetName = "WLED_0.16.0-b2_ESP32.bin"),
                AssetResolutionCase("0.16.0+build1", "ESP32_V4", expectedAssetName = "WLED_0.16.0+build1_ESP32.bin"),
            ),
        )
    }

    @Test
    fun determineAssetPassesThroughStandardReleaseNames() {
        assertDeterminedAssets(
            listOf(
                AssetResolutionCase("0.16.0", "ESP32", expectedAssetName = "WLED_0.16.0_ESP32.bin"),
                AssetResolutionCase("0.16.0", "ESP32_S3", expectedAssetName = "WLED_0.16.0_ESP32_S3.bin"),
                AssetResolutionCase("0.16.0", "ESP8266", expectedAssetName = "WLED_0.16.0_ESP8266.bin"),
            ),
        )
    }

    @Test
    fun determineAssetPreservesEsp32V4Before016() {
        assertDeterminedAssets(
            listOf(
                AssetResolutionCase("0.15.0", "ESP32_V4", expectedAssetName = "WLED_0.15.0_ESP32_V4.bin"),
                AssetResolutionCase("0.15.1", "ESP32_V4", expectedAssetName = "WLED_0.15.1_ESP32_V4.bin"),
                AssetResolutionCase("0.15.0-b5", "ESP32_V4", expectedAssetName = "WLED_0.15.0-b5_ESP32_V4.bin"),
            ),
        )
    }

    @Test
    fun determineAssetFallsBackToLegacyPlatformWhenReleaseIsMissing() {
        assertDeterminedAssets(
            listOf(
                AssetResolutionCase(
                    targetVersionTag = "0.14.4",
                    release = null,
                    platformName = "esp32",
                    expectedAssetName = "WLED_0.14.4_ESP32.bin",
                ),
            ),
        )
    }

    @Test
    fun determineAssetDoesNotFallBackToGenericPlatformWhenReleaseIsKnown() {
        val service = makeDeviceUpdateService(
            targetVersionTag = "0.15.0",
            release = "ESP32_Ethernet",
            platformName = "esp32",
            availableAssets = listOf(makeAsset(1L, "WLED_0.15.0_ESP32.bin")),
        )

        assertFalse(service.couldDetermineAsset())
        assertEquals("WLED_0.15.0_ESP32_Ethernet.bin", service.getAssetName())
    }

    private fun withTempCacheDir(block: (Path) -> Unit) {
        val cacheDir = newTempCacheDir()
        try {
            block(cacheDir)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(cacheDir)
        }
    }

    private fun assertAssetCachedForSize(writtenBytes: Int, expectedCached: Boolean) {
        withTempCacheDir { cacheDir ->
            val asset = makeAsset(1L, "WLED_0.15.0_ESP32.bin")
            val service = makeDeviceUpdateService(
                targetVersionTag = "0.15.0",
                release = "ESP32",
                availableAssets = listOf(asset),
                cacheDir = cacheDir,
            )
            val assetPath = service.getPathForAsset()
            FileSystem.SYSTEM.createDirectories(assetPath.parent!!)
            FileSystem.SYSTEM.write(assetPath) {
                write(ByteArray(writtenBytes))
            }

            assertEquals(expectedCached, service.isAssetFileCached())
        }
    }

    @Test
    fun isAssetFileCachedDoesNotCreateCacheDirectory() {
        withTempCacheDir { cacheDir ->
            val service = makeDeviceUpdateService(
                targetVersionTag = "0.15.0",
                release = "ESP32",
                availableAssets = listOf(makeAsset(1L, "WLED_0.15.0_ESP32.bin")),
                cacheDir = cacheDir,
            )

            assertFalse(service.isAssetFileCached())
            assertFalse(FileSystem.SYSTEM.exists(cacheDir))
        }
    }

    @Test
    fun isAssetFileCachedReturnsTrueWhenFileSizeMatchesAssetMetadata() {
        assertAssetCachedForSize(writtenBytes = 1024, expectedCached = true)
    }

    @Test
    fun isAssetFileCachedReturnsFalseWhenFileSizeMismatchesAssetMetadata() {
        // Simulate a stale 1,538-byte GitHub JSON metadata file cached instead of the 1,024-byte binary.
        assertAssetCachedForSize(writtenBytes = 1538, expectedCached = false)
    }

    @Test
    fun resolveReleaseOverrideFallsBackToRawReleaseForNonSemVerTag() {
        assertEquals(
            "ESP32_V4",
            DeviceUpdateService.resolveReleaseOverride("ESP32_V4", "invalid_version_string"),
        )
    }

    @Test
    fun selectedAssetOverridesAutomaticMatchingForCrossForkInstall() {
        val quinledAsset = makeAsset(1L, "WLED_0.15.4_Dig-Uno-V3.bin")
        val cacheDir = newTempCacheDir()
        val stateInfo = DeviceStateInfo(
            state = State(),
            info = Info(leds = Leds(), wifi = Wifi(), name = "Strip", release = "ESP32"),
        )
        val service = makeDeviceUpdateService(
            versionWithAssets = VersionWithAssets(
                version = makeVersion("0.15.4", repositoryId = 2L),
                assets = listOf(quinledAsset),
            ),
            stateInfo = stateInfo,
            cacheDir = cacheDir,
            selectedAsset = quinledAsset,
        )

        assertTrue(service.couldDetermineAsset())
        assertEquals("WLED_0.15.4_Dig-Uno-V3.bin", service.getAssetName())
        assertEquals(cacheDir / "2" / "0.15.4" / "WLED_0.15.4_Dig-Uno-V3.bin", service.getPathForAsset())
    }

    @Test
    fun getInstallableAssetsFiltersNonFirmwareAndBootloaderFilesAndSortsByName() {
        val versionWithAssets = VersionWithAssets(
            version = makeVersion("0.15.4"),
            assets = listOf(
                makeAsset(1L, "WLED_0.15.4_ESP32.bin"),
                makeAsset(1L, "esp32_bootloader_v4.bin"),
                makeAsset(1L, "S3_partitions_4M.bin"),
                makeAsset(1L, "WLED_0.15.4_ESP02.bin.gz"),
                makeAsset(1L, "checksums.sha256"),
                makeAsset(1L, "source.zip"),
            ),
        )

        val installable = DeviceUpdateService.getInstallableAssets(versionWithAssets).map { it.name }

        assertEquals(
            listOf("WLED_0.15.4_ESP02.bin.gz", "WLED_0.15.4_ESP32.bin"),
            installable,
        )
    }

    @Test
    fun determineDefaultAssetReturnsNullWhenSwitchingToForkWithDifferentBoardNames() {
        val info = Info(leds = Leds(), wifi = Wifi(), name = "Strip", release = "ESP32")
        val quinledVersion = VersionWithAssets(
            version = makeVersion("0.15.4", repositoryId = 2L),
            assets = listOf(
                makeAsset(1L, "WLED_0.15.4_Dig-Uno-V3.bin"),
                makeAsset(1L, "WLED_0.15.4_Dig-Quad-V3.bin"),
            ),
        )

        assertNull(DeviceUpdateService.determineDefaultAsset(info, quinledVersion))
    }

    @Test
    fun determineDefaultAssetMatchesExactBoardWhenTagDiffersFromAssetVersion() {
        val info = Info(leds = Leds(), wifi = Wifi(), name = "Matrix", release = "esp32_4MB_M")
        val wledMmVersion = VersionWithAssets(
            version = makeVersion("v0.14.1-beta.32", repositoryId = 3L),
            assets = listOf(
                makeAsset(1L, "WLEDMM_0.14.1-b32.40_athom_music_esp32_4MB_M.bin"),
                makeAsset(1L, "WLEDMM_0.14.1-b32.40_esp32_4MB_M.bin"),
            ),
        )

        assertEquals(
            "WLEDMM_0.14.1-b32.40_esp32_4MB_M.bin",
            DeviceUpdateService.determineDefaultAsset(info, wledMmVersion)?.name,
        )
    }

    @Test
    fun determineDefaultAssetPrefersUncompressedBinAndFallsBackToBinGz() {
        val info = Info(leds = Leds(), wifi = Wifi(), name = "Mini", release = "esp01_1MB_S")
        val bothVersion = VersionWithAssets(
            version = makeVersion("v0.14.0-beta.28", repositoryId = 3L),
            assets = listOf(
                makeAsset(1L, "WLEDMM_0.14.0-b28.34_esp01_1MB_S.bin.gz"),
                makeAsset(1L, "WLEDMM_0.14.0-b28.34_esp01_1MB_S.bin"),
            ),
        )
        val gzOnlyVersion = VersionWithAssets(
            version = makeVersion("v0.14.0-beta.28", repositoryId = 3L),
            assets = listOf(
                makeAsset(1L, "WLEDMM_0.14.0-b28.34_esp01_1MB_S.bin.gz"),
            ),
        )

        assertEquals(
            "WLEDMM_0.14.0-b28.34_esp01_1MB_S.bin",
            DeviceUpdateService.determineDefaultAsset(info, bothVersion)?.name,
        )
        assertEquals(
            "WLEDMM_0.14.0-b28.34_esp01_1MB_S.bin.gz",
            DeviceUpdateService.determineDefaultAsset(info, gzOnlyVersion)?.name,
        )
    }

    @Test
    fun determineDefaultAssetIgnoresBootloaderAndVersionSubstringCollisions() {
        val info = Info(leds = Leds(), wifi = Wifi(), name = "Strip", release = "ESP32")
        val versionWithCollisions = VersionWithAssets(
            version = makeVersion("0.15.0"),
            assets = listOf(
                makeAsset(1L, "WLED_bootloader_0.15.0_ESP32.bin"),
                makeAsset(1L, "WLED10.15.0_ESP32.bin"),
            ),
        )

        assertNull(DeviceUpdateService.determineDefaultAsset(info, versionWithCollisions))
    }

    @Test
    fun downloadBinaryResolvesTargetVersionRepositoryInsteadOfDeviceRepository() = runTest {
        val cacheDir = newTempCacheDir()
        try {
            var requestedPath: String? = null
            val httpClient = HttpClient(
                MockEngine { request ->
                    requestedPath = request.url.encodedPath
                    respond("firmware-bytes", HttpStatusCode.OK)
                },
            )
            val quinledAsset = Asset(
                versionId = 1L,
                name = "WLED_0.15.4_Dig-Uno-V3.bin",
                size = 14L,
                downloadUrl = "https://example.com/WLED_0.15.4_Dig-Uno-V3.bin",
                assetId = 42,
            )
            val service = makeDeviceUpdateService(
                versionWithAssets = VersionWithAssets(
                    version = makeVersion("0.15.4", repositoryId = 2L),
                    assets = listOf(quinledAsset),
                ),
                cacheDir = cacheDir,
                selectedAsset = quinledAsset,
                httpClient = httpClient,
            )
            val fakeDao = fakeRepositoryDao(
                Repository(id = 1L, name = "WLED", ownerAndRepo = "wled/WLED", description = "", htmlUrl = ""),
                Repository(
                    id = 2L,
                    name = "QuinLED",
                    ownerAndRepo = "intermittech/QuinLED-Firmware",
                    description = "",
                    htmlUrl = "",
                ),
            )

            val states = service.downloadBinary(fakeDao).toList()

            assertTrue(states.any { it is DownloadState.Finished })
            assertEquals("/repos/intermittech/QuinLED-Firmware/releases/assets/42", requestedPath)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(cacheDir)
        }
    }

    @Test
    fun installUpdateForwardsSkipValidationFieldBeforeBinary() = runTest {
        val cacheDir = newTempCacheDir()
        try {
            var multipartText = ""
            val httpClient = HttpClient(
                MockEngine { request ->
                    multipartText = request.body.toByteArray().decodeToString()
                    respond("OK", HttpStatusCode.OK)
                },
            )
            val asset = makeAsset(1L, "WLED_16.0.0_Dig2Go-Audioreactive.bin")
            val service = makeDeviceUpdateService(
                versionWithAssets = VersionWithAssets(
                    version = makeVersion("16.0.0", repositoryId = 2L),
                    assets = listOf(asset),
                ),
                cacheDir = cacheDir,
                selectedAsset = asset,
                httpClient = httpClient,
            )
            val binaryPath = service.getPathForAsset()
            binaryPath.parent?.let(FileSystem.SYSTEM::createDirectories)
            FileSystem.SYSTEM.write(binaryPath) { writeUtf8("BIN") }

            val response = service.installUpdate(skipValidation = true)

            assertTrue(response.isSuccessful)
            val skipIndex = multipartText.indexOf("name=\"skipValidation\"")
            val fileIndex = multipartText.indexOf("filename=\"WLED_16.0.0_Dig2Go-Audioreactive.bin\"")
            assertTrue(skipIndex in 0..<fileIndex)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(cacheDir)
        }
    }

    @Test
    fun isValidationFailureDetectsWledMetadataRejectionOnly() {
        val validationResponse = ApiResponse<String>(code = 500, errorBody = SAMPLE_VALIDATION_ERROR_HTML)
        val spaceErrorResponse = ApiResponse<String>(
            code = 500,
            errorBody = "<body><h2>Update failed!</h2><h4>Not enough space</h4></body>",
        )
        val successResponse = ApiResponse<String>(code = 200, body = "Update successful!")

        assertTrue(DeviceUpdateService.isValidationFailure(validationResponse))
        assertFalse(DeviceUpdateService.isValidationFailure(spaceErrorResponse))
        assertFalse(DeviceUpdateService.isValidationFailure(successResponse))
    }

    @Test
    fun extractPlainTextErrorStripsHtmlJunkAndDecodesEntities() {
        val validationResponse = ApiResponse<String>(code = 500, errorBody = SAMPLE_VALIDATION_ERROR_HTML)

        assertEquals(
            "Update failed! This firmware file is missing compatibility metadata. " +
                "Enable 'Ignore firmware validation' to proceed anyway.",
            DeviceUpdateService.extractPlainTextError(validationResponse),
        )
    }

    private fun fakeRepositoryDao(vararg repos: Repository): RepositoryDao = object : RepositoryDao {
        override suspend fun insert(repository: Repository): Long = 0L
        override suspend fun insertMany(repositories: List<Repository>) = Unit
        override suspend fun update(repository: Repository) = Unit
        override suspend fun delete(repository: Repository) = Unit
        override suspend fun deleteAll() = Unit
        override suspend fun getRepositoryById(id: Long): Repository? = repos.firstOrNull { it.id == id }
        override suspend fun getRepositoryByOwnerAndRepo(ownerAndRepo: String): Repository? =
            repos.firstOrNull { it.ownerAndRepo.equals(ownerAndRepo, ignoreCase = true) }
        override fun getAllRepositories(): Flow<List<Repository>> = emptyFlow()
    }

    companion object {
        private val SAMPLE_VALIDATION_ERROR_HTML = """
            <!DOCTYPE html><html><head><title>WLED</title><style>body{}</style></head>
            <body><h2>Update failed!</h2>
            <h4>This firmware file is missing compatibility metadata.
            Enable &#39;Ignore firmware validation&#39; to proceed anyway.</h4>
            <button onclick="history.back()">Back</button></body></html>
        """.trimIndent()
    }
}
