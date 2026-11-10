package ca.cgagnier.wlednativeandroid.service.update

import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.githubapi.Author
import ca.cgagnier.wlednativeandroid.model.githubapi.Release
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.repository.AssetDao
import ca.cgagnier.wlednativeandroid.repository.VersionDao
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseServiceTest {

    private class FakeVersionDao(
        private val stableVersion: VersionWithAssets? = null,
        private val betaVersion: VersionWithAssets? = null,
    ) : VersionDao {
        override suspend fun insert(version: Version) = Unit
        override suspend fun insertMany(version: List<Version>) = Unit
        override suspend fun update(version: Version) = Unit
        override suspend fun delete(version: Version) = Unit
        override suspend fun deleteAll() = Unit
        override suspend fun getLatestStableVersionWithAssets(): VersionWithAssets? = stableVersion
        override fun getLatestBetaVersionWithAssetsFlow(): Flow<VersionWithAssets?> = flowOf(betaVersion)
        override suspend fun getVersionByTagName(tagName: String): VersionWithAssets? = null
        override fun getVersionsWithAsset(): Flow<List<VersionWithAssets>> = flowOf(emptyList())
        override fun removeAll() = Unit
    }

    private class FakeAssetDao : AssetDao {
        override suspend fun insert(asset: Asset) = Unit
        override suspend fun insertMany(asset: List<Asset>) = Unit
        override suspend fun update(asset: Asset) = Unit
        override suspend fun delete(asset: Asset) = Unit
        override suspend fun deleteAll() = Unit
    }

    private fun createRelease(
        tagName: String = "v1.5.2",
        name: String = tagName,
        prerelease: Boolean = false,
        body: String = "# Français\n1. Intégration lumières COB.",
    ): Release {
        return Release(
            url = "",
            assetsUrl = "",
            uploadUrl = "",
            htmlUrl = "https://github.com/Illumidel/App-Releases/releases/tag/$tagName",
            id = 1,
            author = Author(
                login = "test",
                id = 1,
                nodeId = "",
                avatarUrl = "",
                gravatarId = "",
                url = "",
                htmlUrl = "",
                followersUrl = "",
                followingUrl = "",
                gistsUrl = "",
                starredUrl = "",
                subscriptionsUrl = "",
                organizationsUrl = "",
                reposUrl = "",
                eventsUrl = "",
                receivedEventsUrl = "",
                type = "User",
                siteAdmin = false,
            ),
            nodeId = "",
            tagName = tagName,
            targetCommitish = "main",
            name = name,
            draft = false,
            prerelease = prerelease,
            createdAt = "2026-11-01T00:00:00Z",
            publishedAt = "2026-11-01T00:00:00Z",
            assets = emptyList(),
            tarballUrl = "",
            zipballUrl = "",
            body = body,
            reactions = null,
            mentionsCount = null,
        )
    }

    private fun createDeviceInfo(
        version: String = "1.5.2",
        brand: String = "ILLUMIDEL",
        options: Int = 1,
    ): Info {
        return Info(
            leds = Leds(),
            wifi = Wifi(),
            version = version,
            name = "Illumidel Controller",
            options = options,
            brand = brand,
        )
    }

    @Test
    fun isBetaRelease_detectsPrereleaseFlagAndBetaDescriptions() {
        val service = ReleaseService(VersionWithAssetsRepository(FakeVersionDao(), FakeAssetDao()))

        val stableRelease = createRelease(
            tagName = "v1.5.2",
            prerelease = false,
            body = "# Français\n1. Intégration lumières COB.\n# English\n1. COB Lights integration."
        )
        assertFalse(service.isBetaRelease(stableRelease))
        assertFalse(service.createVersion(stableRelease).isPrerelease)

        val prereleaseFlagged = createRelease(
            tagName = "v2.3.0",
            prerelease = true,
            body = "Release notes without keyword"
        )
        assertTrue(service.isBetaRelease(prereleaseFlagged))
        assertTrue(service.createVersion(prereleaseFlagged).isPrerelease)

        val v221BetaBody = createRelease(
            tagName = "v2.2.1",
            prerelease = false,
            body = "🚨 IMPORTANT — READ BEFORE INSTALLING\nIllumiDEL 2.0 · Beta\nThis update moves your controller to the new IllumiDEL app."
        )
        assertTrue(service.isBetaRelease(v221BetaBody))
        assertTrue(service.createVersion(v221BetaBody).isPrerelease)

        val frenchBetaBody = createRelease(
            tagName = "v2.2.2",
            prerelease = false,
            body = "IllumiDEL 2.0 · Version bêta"
        )
        assertTrue(service.isBetaRelease(frenchBetaBody))
    }

    @Test
    fun getNewerReleaseTag_onlyOffersNewerStableVersionForBothLanguages() = runBlocking {
        val stable152 = VersionWithAssets(
            version = Version(
                tagName = "v1.5.2",
                name = "v1.5.2",
                description = "Stable",
                isPrerelease = false,
                publishedDate = "2025-05-14T18:21:41Z",
                htmlUrl = "",
            ),
            assets = emptyList(),
        )
        val beta221 = VersionWithAssets(
            version = Version(
                tagName = "v2.2.1",
                name = "v2.2.1",
                description = "IllumiDEL 2.0 · Beta",
                isPrerelease = true,
                publishedDate = "2026-11-01T20:28:45Z",
                htmlUrl = "",
            ),
            assets = emptyList(),
        )
        val service = ReleaseService(
            VersionWithAssetsRepository(
                FakeVersionDao(stableVersion = stable152, betaVersion = beta221),
                FakeAssetDao()
            )
        )

        // Device already on 1.5.2 should NOT be offered 2.2.1 beta, regardless of language (STABLE=fr, BETA=en)
        assertEquals("", service.getNewerReleaseTag(createDeviceInfo("1.5.2"), Branch.STABLE, ""))
        assertEquals("", service.getNewerReleaseTag(createDeviceInfo("1.5.2"), Branch.BETA, ""))

        // Device on 1.5.1 SHOULD be offered 1.5.2 stable for both languages
        assertEquals("v1.5.2", service.getNewerReleaseTag(createDeviceInfo("1.5.1"), Branch.STABLE, ""))
        assertEquals("v1.5.2", service.getNewerReleaseTag(createDeviceInfo("1.5.1"), Branch.BETA, ""))

        // Ignored version is respected
        assertEquals("", service.getNewerReleaseTag(createDeviceInfo("1.5.1"), Branch.STABLE, "v1.5.2"))
    }
}
