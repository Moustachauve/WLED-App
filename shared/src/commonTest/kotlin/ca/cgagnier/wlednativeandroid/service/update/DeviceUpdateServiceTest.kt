package ca.cgagnier.wlednativeandroid.service.update

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
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.api.github.KtorGithubApiEndpoints
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceUpdateServiceTest {

    private fun makeVersion(tagName: String, isPrerelease: Boolean = false) = Version(
        tagName = tagName,
        name = "Illumidel $tagName",
        description = "",
        isPrerelease = isPrerelease,
        publishedDate = "2024-01-01T00:00:00Z",
        htmlUrl = "https://github.com/Illumidel/App-Releases",
        repositoryId = 1L,
    )

    private fun makeAsset(versionId: Long, assetName: String) = Asset(
        versionId = versionId,
        name = assetName,
        size = 1024L,
        downloadUrl = "https://example.com/$assetName",
        assetId = 0,
    )

    private fun newTempCacheDir(): Path =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "illumidel_update_test_${Random.nextLong(0, Long.MAX_VALUE)}"

    private fun makeDeviceUpdateService(
        targetVersionTag: String,
        branch: Branch = Branch.STABLE,
        platformName: String? = "esp32",
        availableAssets: List<Asset>,
        cacheDir: Path = newTempCacheDir(),
    ): DeviceUpdateService {
        val device = Device(
            macAddress = "AA:BB:CC:DD:EE:FF",
            address = "192.168.1.1",
            branch = branch,
        )
        val stateInfo = platformName?.let {
            DeviceStateInfo(
                state = State(),
                info = Info(
                    leds = Leds(),
                    wifi = Wifi(),
                    name = "Test Device",
                    platformName = it,
                    brand = "ILLUMIDEL",
                ),
            )
        }
        val deviceWithState = DeviceWithState(device, stateInfo = stateInfo)
        val versionWithAssets =
            VersionWithAssets(version = makeVersion(targetVersionTag), assets = availableAssets)
        val httpClient = HttpClient(MockEngine { respond("", HttpStatusCode.OK) })
        val deviceApiFactory = DeviceApiFactory(httpClient)
        val githubApi = GithubApi(KtorGithubApiEndpoints(httpClient))

        return DeviceUpdateService(
            device = deviceWithState,
            versionWithAssets = versionWithAssets,
            cacheDir = cacheDir,
            deviceApiFactory = deviceApiFactory,
            githubApi = githubApi,
        )
    }

    @Test
    fun determineAssetSelectsFrenchEsp32BinaryForStableBranch() {
        val service = makeDeviceUpdateService(
            targetVersionTag = "1.5.2",
            branch = Branch.STABLE,
            platformName = "esp32",
            availableAssets = listOf(
                makeAsset(1L, "v1.5.2_ESP32_en.bin"),
                makeAsset(1L, "v1.5.2_ESP32_fr.bin"),
            ),
        )

        assertTrue(service.couldDetermineAsset())
        assertEquals("v1.5.2_ESP32_fr.bin", service.getAssetName())
    }

    @Test
    fun determineAssetSelectsEnglishEsp32BinaryForBetaBranch() {
        val service = makeDeviceUpdateService(
            targetVersionTag = "v2.2.1",
            branch = Branch.BETA,
            platformName = "ESP32",
            availableAssets = listOf(
                makeAsset(1L, "v2.2.1_ESP32_en.bin"),
                makeAsset(1L, "v2.2.1_ESP32_fr.bin"),
            ),
        )

        assertTrue(service.couldDetermineAsset())
        assertEquals("v2.2.1_ESP32_en.bin", service.getAssetName())
    }

    @Test
    fun determineAssetRejectsUnsupportedPlatforms() {
        val service = makeDeviceUpdateService(
            targetVersionTag = "1.5.2",
            branch = Branch.STABLE,
            platformName = "esp8266",
            availableAssets = listOf(makeAsset(1L, "v1.5.2_ESP8266_fr.bin")),
        )

        assertFalse(service.couldDetermineAsset())
        assertEquals("v1.5.2_ESP8266_fr.bin", service.getAssetName())
    }

    @Test
    fun determineAssetLeavesAssetNameEmptyWhenPlatformMissing() {
        listOf(null, "").forEach { platform ->
            val service = makeDeviceUpdateService(
                targetVersionTag = "1.5.2",
                branch = Branch.STABLE,
                platformName = platform,
                availableAssets = listOf(makeAsset(1L, "v1.5.2_ESP32_fr.bin")),
            )

            assertFalse(service.couldDetermineAsset())
            assertEquals("", service.getAssetName())
        }
    }

    @Test
    fun isAssetFileCachedDoesNotCreateCacheDirectory() {
        val cacheDir = newTempCacheDir()
        try {
            val service = makeDeviceUpdateService(
                targetVersionTag = "1.5.2",
                branch = Branch.STABLE,
                platformName = "esp32",
                availableAssets = listOf(makeAsset(1L, "v1.5.2_ESP32_fr.bin")),
                cacheDir = cacheDir,
            )

            assertFalse(service.isAssetFileCached())
            assertFalse(FileSystem.SYSTEM.exists(cacheDir))
        } finally {
            FileSystem.SYSTEM.deleteRecursively(cacheDir)
        }
    }

    @Test
    fun isAssetFileCachedReturnsFalseWhenCachedFileSizeMismatchesAssetSize() {
        val cacheDir = newTempCacheDir()
        try {
            val service = makeDeviceUpdateService(
                targetVersionTag = "1.5.2",
                branch = Branch.BETA,
                platformName = "esp32",
                availableAssets = listOf(makeAsset(1L, "v1.5.2_ESP32_en.bin")),
                cacheDir = cacheDir,
            )
            val assetPath = service.getPathForAsset()
            assetPath.parent?.let(FileSystem.SYSTEM::createDirectories)
            FileSystem.SYSTEM.write(assetPath) {
                writeUtf8("""{"url":"https://api.github.com/repos/Illumidel/App-Releases/releases/assets/1"}""")
            }

            assertFalse(service.isAssetFileCached())
        } finally {
            FileSystem.SYSTEM.deleteRecursively(cacheDir)
        }
    }

    @Test
    fun isAssetFileCachedReturnsTrueWhenCachedFileSizeMatchesAssetSize() {
        val cacheDir = newTempCacheDir()
        try {
            val service = makeDeviceUpdateService(
                targetVersionTag = "1.5.2",
                branch = Branch.STABLE,
                platformName = "esp32",
                availableAssets = listOf(makeAsset(1L, "v1.5.2_ESP32_fr.bin")),
                cacheDir = cacheDir,
            )
            val assetPath = service.getPathForAsset()
            assetPath.parent?.let(FileSystem.SYSTEM::createDirectories)
            FileSystem.SYSTEM.write(assetPath) {
                write(ByteArray(1024) { 0xE9.toByte() })
            }

            assertTrue(service.isAssetFileCached())
        } finally {
            FileSystem.SYSTEM.deleteRecursively(cacheDir)
        }
    }
}
