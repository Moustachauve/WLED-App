package ca.cgagnier.wlednativeandroid.repository

import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import com.diffplug.selfie.Selfie.expectSelfie
import io.github.z4kn4fein.semver.toVersionOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VersionWithAssetsRepositoryTest {

    @Test
    fun semVerComparator_sortsCorrectly() {
        val versions = listOf(
            createVersion("0.15.2", "2026-01-01T00:00:00Z"),
            createVersion("0.15.3", "2026-03-01T00:00:00Z"),
            createVersion("0.16.0", "2026-05-01T00:00:00Z"),
            createVersion("16.0.0", "2025-01-01T00:00:00Z"), // Older date, newer semver
            createVersion("invalid-tag", "2026-06-01T00:00:00Z"), // Fallback to date
            createVersion("invalid-old", "2026-02-01T00:00:00Z"), // Fallback to date
        ).shuffled()

        val parsedVersions = versions.map {
            it to it.tagName.toVersionOrNull(strict = false)
        }
        val sorted = parsedVersions.sortedWith(VersionWithAssetsRepository.semVerComparator).map { it.first }

        // Invalid semver tags are sorted by date and placed before valid semver tags
        expectSelfie(
            sorted.map {
                it.tagName
            }.joinToString(", "),
        ).toBe("invalid-old, invalid-tag, 0.15.2, 0.15.3, 0.16.0, 16.0.0")

        val latest = VersionWithAssetsRepository.getLatestVersion(versions)
        assertEquals("16.0.0", latest?.tagName)
    }

    @Test
    fun semVerComparator_breaksTieUsingPublishedDateWhenSemverIsEqual() {
        val v1 = createVersion("0.15.2", "2026-01-01T00:00:00Z")
        val v2 = createVersion("0.15.2", "2026-02-01T00:00:00Z")
        val pair1 = v1 to v1.tagName.toVersionOrNull(strict = false)
        val pair2 = v2 to v2.tagName.toVersionOrNull(strict = false)

        val cmp = VersionWithAssetsRepository.semVerComparator.compare(pair1, pair2)
        assertTrue(cmp < 0)
    }

    @Test
    fun filterAndSortVersions_separatesStableAndBetaDescendingAndExcludesNightly() {
        val versions = listOf(
            createVersionWithAssets("nightly", "2026-06-01T00:00:00Z", isPrerelease = true),
            createVersionWithAssets("0.14.4", "2024-06-01T00:00:00Z", isPrerelease = false),
            createVersionWithAssets("0.15.3", "2025-06-01T00:00:00Z", isPrerelease = false),
            createVersionWithAssets("0.15.0-b2", "2025-01-01T00:00:00Z", isPrerelease = true),
            // MoonModules-style beta tags (hyphen and dot-separated) where GitHub's prerelease flag was left false
            createVersionWithAssets("14.5.0-beta", "2025-08-01T00:00:00Z", isPrerelease = false),
            createVersionWithAssets("v0.14.0.b27", "2024-04-01T00:00:00Z", isPrerelease = false),
            createVersionWithAssets("14.7.1", "2025-10-01T00:00:00Z", isPrerelease = false),
            createVersionWithAssets("custom-rev-b", "2024-01-01T00:00:00Z", isPrerelease = false),
        )

        val stableTags = VersionWithAssetsRepository
            .filterAndSortVersions(versions, Branch.STABLE)
            .map { it.version.tagName }
            .joinToString(", ")
        val betaTags = VersionWithAssetsRepository
            .filterAndSortVersions(versions, Branch.BETA)
            .map { it.version.tagName }
            .joinToString(", ")

        expectSelfie(stableTags).toBe("14.7.1, 0.15.3, 0.14.4, custom-rev-b")
        expectSelfie(betaTags).toBe("14.5.0-beta, 0.15.0-b2, v0.14.0.b27")
    }

    @Test
    fun isPreReleaseTag_ignoresBuildMetadataSuffixOnStableVersions() {
        listOf("0.16.0+rev.b1", "v0.16.0+2025.rc1", "2025.05.01.1+build.b2").forEach { tag ->
            assertFalse(Version.isPreReleaseTag(tag), "Expected stable classification for $tag")
        }
        assertTrue(Version.isPreReleaseTag("0.16.0-rc1+2025.05"))
    }

    @Test
    fun distinctReleaseName_omitsBlankAndNormalizedTagDuplicates() {
        assertNull(createVersion("0.15.0", "2025-05-01T00:00:00Z").copy(name = "v0.15.0").distinctReleaseName)
        assertNull(createVersion("v0.15.0", "2025-05-01T00:00:00Z").copy(name = "0.15.0").distinctReleaseName)
        assertNull(createVersion("0.15.0", "2025-05-01T00:00:00Z").copy(name = "   ").distinctReleaseName)
        assertEquals(
            "WLED 0.15.0 Kosen",
            createVersion("0.15.0", "2025-05-01T00:00:00Z").copy(name = " WLED 0.15.0 Kosen ").distinctReleaseName,
        )
    }

    @Test
    fun matchesInstalledVersion_requiresSameRepositoryAndMatchingNormalizedTag() {
        val officialVersion = createVersion("0.15.3", "2025-06-01T00:00:00Z", repositoryId = 1L)
        val quinledVersion = createVersion("0.15.3", "2025-06-01T00:00:00Z", repositoryId = 2L)

        assertTrue(officialVersion.matchesInstalledVersion("0.15.3", deviceRepositoryId = 1L))
        assertTrue(officialVersion.matchesInstalledVersion("v0.15.3", deviceRepositoryId = 1L))
        assertFalse(quinledVersion.matchesInstalledVersion("0.15.3", deviceRepositoryId = 1L))
        assertFalse(officialVersion.matchesInstalledVersion("0.15.2", deviceRepositoryId = 1L))
        assertFalse(officialVersion.matchesInstalledVersion(null, deviceRepositoryId = 1L))
    }

    @Test
    fun matchesInstalledVersion_matchesTagOrEmbeddedAssetVersionWithinSameRepository() {
        val wledMmRelease = VersionWithAssets(
            version = createVersion("v0.14.1-beta.32", "2024-06-01T00:00:00Z", repositoryId = 3L, isPrerelease = true),
            assets = listOf(
                Asset(
                    id = 1L,
                    versionId = 1L,
                    name = "WLEDMM_0.14.1-b32.40_esp32_4MB_M.bin",
                    size = 1_200_000L,
                    downloadUrl = "https://example.com/WLEDMM_0.14.1-b32.40_esp32_4MB_M.bin",
                    assetId = 10,
                ),
            ),
        )
        val officialRcRelease = VersionWithAssets(
            version = createVersion("v0.15.0-rc.1", "2024-11-01T00:00:00Z", repositoryId = 1L, isPrerelease = true),
            assets = listOf(
                Asset(
                    id = 2L,
                    versionId = 2L,
                    name = "WLED_0.15.0-rc1_ESP32.bin",
                    size = 1_200_000L,
                    downloadUrl = "https://example.com/WLED_0.15.0-rc1_ESP32.bin",
                    assetId = 11,
                ),
                Asset(
                    id = 3L,
                    versionId = 2L,
                    name = "WLED_bootloader_0.15.0-rc99_ESP32.bin",
                    size = 32_000L,
                    downloadUrl = "https://example.com/bootloader.bin",
                    assetId = 12,
                ),
            ),
        )

        assertTrue(wledMmRelease.matchesInstalledVersion("0.14.1-b32.40", deviceRepositoryId = 3L))
        assertTrue(wledMmRelease.matchesInstalledVersion("0.14.1-beta.32", deviceRepositoryId = 3L))
        assertFalse(wledMmRelease.matchesInstalledVersion("0.14.1-b32.40", deviceRepositoryId = 1L))
        assertTrue(officialRcRelease.matchesInstalledVersion("0.15.0-rc1", deviceRepositoryId = 1L))
        assertFalse(officialRcRelease.matchesInstalledVersion("0.15.0-rc99", deviceRepositoryId = 1L))
    }

    private fun createVersionWithAssets(
        tagName: String,
        publishedDate: String,
        isPrerelease: Boolean,
    ): VersionWithAssets = VersionWithAssets(
        version = createVersion(tagName, publishedDate, isPrerelease = isPrerelease),
        assets = emptyList(),
    )

    private fun createVersion(
        tagName: String,
        publishedDate: String,
        repositoryId: Long = 0,
        isPrerelease: Boolean = false,
    ): Version = Version(
        id = 0,
        repositoryId = repositoryId,
        tagName = tagName,
        name = tagName,
        description = "",
        isPrerelease = isPrerelease,
        publishedDate = publishedDate,
        htmlUrl = "",
    )
}
