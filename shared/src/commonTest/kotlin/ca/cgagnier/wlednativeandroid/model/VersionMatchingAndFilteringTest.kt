package ca.cgagnier.wlednativeandroid.model

import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionMatchingAndFilteringTest {

    @Test
    fun filterAndSortVersionsSeparatesStableAndBetaDescendingAndExcludesNightly() {
        val versions = listOf(
            versionWithAssets("nightly", isPrerelease = true),
            versionWithAssets("0.14.4", "2024-06-01T00:00:00Z"),
            versionWithAssets("0.15.3", "2025-06-01T00:00:00Z"),
            versionWithAssets("0.15.0-b2", "2025-01-01T00:00:00Z", isPrerelease = true),
            versionWithAssets("14.5.0-beta", "2025-08-01T00:00:00Z"),
            versionWithAssets("v0.14.0.b27", "2024-04-01T00:00:00Z"),
            versionWithAssets("14.7.1", "2025-10-01T00:00:00Z"),
            versionWithAssets("custom-rev-b", "2024-01-01T00:00:00Z"),
        )

        val stableTags = VersionWithAssetsRepository.filterAndSortVersions(versions, Branch.STABLE)
            .map { it.version.tagName }
        val betaTags = VersionWithAssetsRepository.filterAndSortVersions(versions, Branch.BETA)
            .map { it.version.tagName }

        assertEquals(listOf("14.7.1", "0.15.3", "0.14.4", "custom-rev-b"), stableTags)
        assertEquals(listOf("14.5.0-beta", "0.15.0-b2", "v0.14.0.b27"), betaTags)
    }

    @Test
    fun isPreReleaseTagAndParseSemVerHandleUppercaseVPrefixesAndBuildMetadata() {
        listOf("0.16.0+rev.b1", "v0.16.0+2025.rc1", "V0.15.0", "2025.05.01.1+build.b2").forEach { tag ->
            assertFalse(Version.isPreReleaseTag(tag), "Expected stable classification for $tag")
        }
        assertEquals(Version.parseSemVer("v0.15.0"), Version.parseSemVer("V0.15.0"))
        assertTrue(Version.isPreReleaseTag("V0.14.0.b27"))
        assertTrue(Version.isPreReleaseTag("0.16.0-rc1+2025.05"))
    }

    @Test
    fun distinctReleaseNameAndDisplaySubtitleFormatExpectedText() {
        val sameAsTag = version("0.15.0", name = "v0.15.0", publishedDate = "2025-05-01T12:00:00Z")
        val namedRelease = version("0.15.0", name = " WLED 0.15.0 Kosen ", publishedDate = "2025-05-01T12:00:00Z")

        assertNull(sameAsTag.distinctReleaseName)
        assertEquals("2025-05-01", sameAsTag.displaySubtitle)
        assertEquals("WLED 0.15.0 Kosen", namedRelease.distinctReleaseName)
        assertEquals("WLED 0.15.0 Kosen • 2025-05-01", namedRelease.displaySubtitle)
    }

    @Test
    fun matchesInstalledVersionMatchesNormalizedTagOrEmbeddedAssetVersionWithinSameRepository() {
        val wledMm = versionWithAssets(
            tagName = "v0.14.1-beta.32",
            repositoryId = 3L,
            isPrerelease = true,
            assetNames = listOf("WLEDMM_0.14.1-b32.40_esp32_4MB_M.bin"),
        )
        val officialRc = versionWithAssets(
            tagName = "v0.15.0-rc.1",
            repositoryId = 1L,
            isPrerelease = true,
            assetNames = listOf(
                "WLED_0.15.0-rc1_ESP32.bin",
                "WLED_bootloader_0.15.0-rc99_ESP32.bin",
                "firmware_board_v2.0_esp32.bin",
            ),
        )

        listOf(
            Triple(wledMm, "0.14.1-b32.40" to 3L, true),
            Triple(wledMm, "0.14.1-beta.32" to 3L, true),
            Triple(wledMm, "0.14.1-b32.40" to 1L, false),
            Triple(officialRc, "0.15.0-rc1" to 1L, true),
            Triple(officialRc, "0.15.0-rc99" to 1L, false),
            Triple(officialRc, "2.0" to 1L, false),
            Triple(officialRc, null to 1L, false),
        ).forEach { (release, query, expected) ->
            assertEquals(expected, release.matchesInstalledVersion(query.first, query.second))
        }
    }

    @Test
    fun getSelectableRepositoriesIncludesCurrentDisabledRepoAndOrdersOfficialFirst() {
        val official = Repository.BUILT_IN_REPOSITORIES[0].copy(id = 1L)
        val quinled = Repository.BUILT_IN_REPOSITORIES[1].copy(id = 2L)
        val moonModules = Repository.BUILT_IN_REPOSITORIES[2].copy(id = 3L)
        val currentCustom = official.copy(
            id = 4L,
            name = "Alpha Custom",
            ownerAndRepo = "custom/fork",
            isDefault = false,
            isEnabled = false,
        )
        val hiddenCustom = official.copy(
            id = 5L,
            name = "Other",
            ownerAndRepo = "other/fork",
            isDefault = false,
            isEnabled = false,
        )

        val selectable = ReleaseService.getSelectableRepositories(
            allRepositories = listOf(moonModules, hiddenCustom, quinled, currentCustom, official),
            currentRepositoryId = 4L,
        )

        assertEquals(listOf(1L, 4L, 3L, 2L), selectable.map { it.id })
        assertTrue(selectable.first().isOfficial)
        assertFalse(selectable.drop(1).any { it.isOfficial })
    }

    private fun version(
        tagName: String,
        name: String = tagName,
        publishedDate: String = "2025-01-01T00:00:00Z",
        repositoryId: Long = 1L,
        isPrerelease: Boolean = false,
    ) = Version(
        repositoryId = repositoryId,
        tagName = tagName,
        name = name,
        description = "",
        isPrerelease = isPrerelease,
        publishedDate = publishedDate,
        htmlUrl = "",
    )

    private fun versionWithAssets(
        tagName: String,
        publishedDate: String = "2025-01-01T00:00:00Z",
        repositoryId: Long = 1L,
        isPrerelease: Boolean = false,
        assetNames: List<String> = emptyList(),
    ) = VersionWithAssets(
        version = version(
            tagName = tagName,
            publishedDate = publishedDate,
            repositoryId = repositoryId,
            isPrerelease = isPrerelease,
        ),
        assets = assetNames.map { Asset(versionId = 1L, name = it, size = 1024L, downloadUrl = "", assetId = 1) },
    )
}
