package ca.cgagnier.wlednativeandroid.service.update

import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.githubapi.Release
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReleaseServiceTest {

    private val versionWithAssetsRepository: VersionWithAssetsRepository = mockk()
    private val repositoryDao: RepositoryDao = mockk()
    private val githubApi: GithubApi = mockk()
    private lateinit var releaseService: ReleaseService

    private val defaultRepo = Repository(
        id = 1L,
        name = "Illumidel",
        ownerAndRepo = "Illumidel/App-Releases",
        description = "Official Illumidel Firmware",
        htmlUrl = "https://github.com/Illumidel/App-Releases",
    )

    @BeforeEach
    fun setUp() {
        releaseService = ReleaseService(
            versionWithAssetsRepository,
            repositoryDao,
            UnconfinedTestDispatcher(),
        )
        coEvery { repositoryDao.getRepositoryByOwnerAndRepo("Illumidel/App-Releases") } returns defaultRepo
    }

    @Test
    fun `getNewerReleaseTag offers newer stable release and ignores upstream repo field`() = runTest {
        val deviceInfo = createInfo(version = "1.5.2", repository = "wled/WLED")
        coEvery {
            versionWithAssetsRepository.getLatestStableVersionWithAssets(1L)
        } returns createVersionWithAssets("1.5.3")

        val result = releaseService.getNewerReleaseTag(deviceInfo, ignoreVersion = "")

        assertEquals("1.5.3", result)
        coVerify(exactly = 0) { versionWithAssetsRepository.getLatestBetaVersionWithAssets(any()) }
    }

    @Test
    fun `getNewerReleaseTag returns null for non-ILLUMIDEL brand`() = runTest {
        val deviceInfo = createInfo(version = "0.15.2", brand = "WLED")

        val result = releaseService.getNewerReleaseTag(deviceInfo, ignoreVersion = "")

        assertNull(result)
    }

    @Test
    fun `getNewerReleaseTag returns null when device is already up to date`() = runTest {
        val deviceInfo = createInfo(version = "1.5.3")
        coEvery {
            versionWithAssetsRepository.getLatestStableVersionWithAssets(1L)
        } returns createVersionWithAssets("1.5.3")

        val result = releaseService.getNewerReleaseTag(deviceInfo, ignoreVersion = "")

        assertNull(result)
    }

    @Test
    fun `getNewerReleaseTag returns null when newer release matches ignored version`() = runTest {
        val deviceInfo = createInfo(version = "1.5.2")
        coEvery {
            versionWithAssetsRepository.getLatestStableVersionWithAssets(1L)
        } returns createVersionWithAssets("1.5.3")

        assertNull(releaseService.getNewerReleaseTag(deviceInfo, ignoreVersion = "1.5.3"))
        assertNull(releaseService.getNewerReleaseTag(deviceInfo, ignoreVersion = "v1.5.3"))
        assertNull(releaseService.getNewerReleaseTag(deviceInfo, ignoreVersion = "V1.5.3"))
    }

    @Test
    fun `getNewerReleaseTag returns null when OTA is disabled`() = runTest {
        val deviceInfo = createInfo(version = "1.5.2", options = 0x00)
        coEvery {
            versionWithAssetsRepository.getLatestStableVersionWithAssets(1L)
        } returns createVersionWithAssets("1.5.3")

        val result = releaseService.getNewerReleaseTag(deviceInfo, ignoreVersion = "")

        assertNull(result)
    }

    @Test
    fun `getNewerReleaseTag returns null when device has a newer version than repo`() = runTest {
        val deviceInfo = createInfo(version = "2.2.1")
        coEvery {
            versionWithAssetsRepository.getLatestStableVersionWithAssets(1L)
        } returns createVersionWithAssets("1.5.3")

        val result = releaseService.getNewerReleaseTag(deviceInfo, ignoreVersion = "")

        assertNull(result)
    }

    @Test
    fun `getNewerReleaseTag returns null when deviceInfo version is null`() = runTest {
        val deviceInfo = createInfo(version = null)

        val result = releaseService.getNewerReleaseTag(deviceInfo, ignoreVersion = "")

        assertNull(result)
    }

    @Test
    fun `isBetaUpdateAvailable requires ILLUMIDEL brand, OTA enabled, and newer SemVer`() {
        assertTrue(ReleaseService.isBetaUpdateAvailable(createInfo(version = "1.5.2"), "v2.0.0-beta.1"))
        assertFalse(ReleaseService.isBetaUpdateAvailable(null, "v2.0.0-beta.1"))
        assertFalse(
            ReleaseService.isBetaUpdateAvailable(createInfo(version = "1.5.2", brand = "WLED"), "v2.0.0-beta.1"),
        )
        assertFalse(
            ReleaseService.isBetaUpdateAvailable(createInfo(version = "1.5.2", options = 0x00), "v2.0.0-beta.1"),
        )
        assertFalse(ReleaseService.isBetaUpdateAvailable(createInfo(version = "2.0.0"), "v2.0.0-beta.1"))
    }

    @Test
    fun `isVersionNewer compares SemVer tags stripping leading v`() {
        assertTrue(ReleaseService.isVersionNewer("v2.2.1", "1.5.2"))
        assertTrue(ReleaseService.isVersionNewer("V2.2.1", "V1.5.2"))
        assertTrue(ReleaseService.isVersionNewer("2.2.1", "v1.5.2"))
        assertFalse(ReleaseService.isVersionNewer("v2.2.1", "2.2.1"))
        assertFalse(ReleaseService.isVersionNewer("1.5.2", "2.2.1"))
    }

    @Test
    fun `syncBuiltInRepositories inserts missing built-in repositories`() = runTest {
        coEvery { repositoryDao.getRepositoryByOwnerAndRepo(any()) } returns null
        coEvery { repositoryDao.insert(any()) } returns 1L
        every { repositoryDao.getAllRepositories() } returns flowOf(emptyList())

        releaseService.syncBuiltInRepositories()

        Repository.BUILT_IN_REPOSITORIES.forEach { builtin ->
            coVerify { repositoryDao.insert(builtin) }
        }
    }

    @Test
    fun `syncBuiltInRepositories clears default flag on non-built-in repositories`() = runTest {
        val staleCustomRepo = Repository(
            id = 99L,
            name = "OldDefault",
            ownerAndRepo = "wled/WLED",
            description = "Legacy default",
            htmlUrl = "https://github.com/wled/WLED",
            isDefault = true,
        )
        coEvery { repositoryDao.getRepositoryByOwnerAndRepo(any()) } returns defaultRepo
        coEvery { repositoryDao.update(any()) } returns Unit
        every { repositoryDao.getAllRepositories() } returns flowOf(listOf(staleCustomRepo))

        releaseService.syncBuiltInRepositories()

        coVerify { repositoryDao.update(staleCustomRepo.copy(isDefault = false)) }
    }

    @Test
    fun `refreshVersions returns false when a repository fails and still updates successful ones`() = runTest {
        val release = mockk<Release>(relaxed = true) {
            every { tagName } returns "v1.5.2"
            every { name } returns "1.5.2"
            every { body } returns "Notes"
            every { prerelease } returns false
            every { publishedAt } returns "2025-01-01T00:00:00Z"
            every { htmlUrl } returns "https://github.com/Illumidel/App-Releases/releases/tag/v1.5.2"
            every { assets } returns emptyList()
        }
        coEvery { githubApi.getAllReleases("Illumidel", "App-Releases") } returns Result.success(listOf(release))
        coEvery { githubApi.getAllReleases("broken", "fork") } returns Result.failure(Exception("Network error"))
        coEvery { versionWithAssetsRepository.updateRepository(any(), any()) } returns Unit

        val succeeded = releaseService.refreshVersions(githubApi, setOf("Illumidel/App-Releases", "broken/fork"))

        assertFalse(succeeded)
        coVerify(exactly = 1) {
            versionWithAssetsRepository.updateRepository(
                match { it.ownerAndRepo == "Illumidel/App-Releases" },
                any(),
            )
        }
    }

    @Test
    fun `refreshVersionsForUsedRepositories only refreshes enabled and used repositories`() = runTest {
        val deviceRepository: DeviceRepository = mockk()
        val usedEnabledRepo = defaultRepo.copy(id = 1L, isEnabled = true, isUpdateEnabled = true)
        val unusedRepo = defaultRepo.copy(id = 2L, ownerAndRepo = "unused/Repo", isEnabled = true)
        val disabledRepo = defaultRepo.copy(id = 3L, ownerAndRepo = "disabled/Repo", isEnabled = false)

        coEvery { deviceRepository.getUsedRepositoryIds() } returns listOf(1L, 3L)
        every { repositoryDao.getAllRepositories() } returns flowOf(
            listOf(usedEnabledRepo, unusedRepo, disabledRepo),
        )
        coEvery { githubApi.getAllReleases("Illumidel", "App-Releases") } returns Result.success(emptyList())

        val succeeded = releaseService.refreshVersionsForUsedRepositories(githubApi, deviceRepository)

        assertTrue(succeeded)
        coVerify(exactly = 1) { githubApi.getAllReleases("Illumidel", "App-Releases") }
        coVerify(exactly = 0) { githubApi.getAllReleases("unused", "Repo") }
        coVerify(exactly = 0) { githubApi.getAllReleases("disabled", "Repo") }
    }

    @Test
    fun `getIneligibilityReason distinguishes non-Illumidel, OTA-disabled, and eligible devices`() {
        assertEquals(
            UpdateIneligibilityReason.NOT_ILLUMIDEL,
            ReleaseService.getIneligibilityReason(createInfo(version = "0.14.4", brand = "WLED", options = 0x01)),
        )
        assertEquals(
            UpdateIneligibilityReason.OTA_DISABLED,
            ReleaseService.getIneligibilityReason(createInfo(version = "1.5.2", brand = "ILLUMIDEL", options = 0x00)),
        )
        assertNull(
            ReleaseService.getIneligibilityReason(createInfo(version = "1.5.2", brand = "ILLUMIDEL", options = 0x01)),
        )
    }

    private fun createInfo(
        version: String?,
        options: Int = 0x01,
        brand: String = "ILLUMIDEL",
        repository: String? = null,
    ): Info = Info(
        leds = Leds(count = 30, fps = 30, maxPower = 0, maxSegment = 1),
        wifi = Wifi(bssid = "mac", rssi = -50, signal = 100, channel = 1),
        name = "Test Device",
        version = version,
        options = options,
        brand = brand,
        repository = repository,
    )

    private fun createVersionWithAssets(tagName: String, isPrerelease: Boolean = false): VersionWithAssets =
        VersionWithAssets(
            version = Version(
                id = 10L,
                repositoryId = 1L,
                tagName = tagName,
                name = "Release $tagName",
                description = "Notes",
                isPrerelease = isPrerelease,
                publishedDate = "2026-01-15T00:00:00Z",
                htmlUrl = "https://github.com/Illumidel/App-Releases/releases/tag/$tagName",
            ),
            assets = emptyList(),
        )
}
