package ca.cgagnier.wlednativeandroid.service.update

import android.util.Log
import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.githubapi.Release
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.api.github.IllumidelRepoApi
import com.vdurmont.semver4j.Semver
import java.io.File

private const val TAG = "updateService"
private const val WLED_BRAND = "ILLUMIDEL"

class ReleaseService(private val versionWithAssetsRepository: VersionWithAssetsRepository) {

    /**
     * If a new version is available, returns the version tag of it.
     *
     * @param deviceInfo Latest information about the device
     * @param branch Which branch to check for the update
     * @param ignoreVersion You can specify a version tag to be ignored as a new version. If this is
     *      set and match with the newest version, no version will be returned
     * @return The newest version if it is newer than versionName and different than ignoreVersion,
     *      otherwise an empty string.
     */
    suspend fun getNewerReleaseTag(deviceInfo: Info, branch: Branch, ignoreVersion: String): String {
        // The options bitmask at 0x01 being 0 means OTA is disabled on the device.
        if (deviceInfo.options?.and(0x01) == 0) {
            return ""
        }
        return getNewerReleaseTag(
            version = deviceInfo.version,
            brand = deviceInfo.brand,
            ignoreVersion = ignoreVersion,
            ipAddress = deviceInfo.ipAddress,
        )
    }

    suspend fun getNewerReleaseTag(
        version: String?,
        brand: String?,
        ignoreVersion: String,
        ipAddress: String? = null,
    ): String {
        if (version.isNullOrEmpty() || version == Device.UNKNOWN_VALUE) {
            return ""
        }
        // This would need some major refactoring in order to support different sources for OTA.
        if (brand != WLED_BRAND) {
            return ""
        }

        val latestVersion = versionWithAssetsRepository.getLatestStableVersionWithAssets() ?: return ""
        if (latestVersion.version.tagName == ignoreVersion) {
            return ""
        }

        Log.w(TAG, "Device $ipAddress: $version to ${latestVersion.version.tagName}")

        try {
            return if (Semver(
                    latestVersion.version.tagName.drop(1),
                    Semver.SemverType.LOOSE
                ).isGreaterThan(version)
            ) {
                latestVersion.version.tagName
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getNewerReleaseTag: " + e.message, e)
        }

        return ""
    }

    suspend fun refreshVersions(cacheDir: File) {
        val allVersions = IllumidelRepoApi(cacheDir).getAllReleases()

        if (allVersions == null) {
            Log.w(TAG, "Did not find any version")
            return
        }

        versionWithAssetsRepository.removeAll()

        val versionModels = mutableListOf<Version>()
        val assetsModels = mutableListOf<Asset>()
        for (version in allVersions) {
            versionModels.add(createVersion(version))
            assetsModels.addAll(createAssetsForVersion(version))
        }

        Log.i(
            TAG,
            "Inserting " + versionModels.count() + " versions with " +
                    assetsModels.count() + " assets"
        )
        versionWithAssetsRepository.insertMany(versionModels, assetsModels)
    }

    internal fun createVersion(version: Release): Version {
        return Version(
            version.tagName,
            version.name,
            version.body,
            isBetaRelease(version),
            version.publishedAt,
            version.htmlUrl
        )
    }

    internal fun isBetaRelease(version: Release): Boolean {
        if (version.prerelease) {
            return true
        }
        val betaKeywords = listOf("beta", "bêta")
        return betaKeywords.any { keyword ->
            version.tagName.contains(keyword, ignoreCase = true) ||
                version.name.contains(keyword, ignoreCase = true) ||
                version.body.contains(keyword, ignoreCase = true)
        }
    }

    private fun createAssetsForVersion(version: Release): List<Asset> {
        val assetsModels = mutableListOf<Asset>()
        for (asset in version.assets) {
            assetsModels.add(
                Asset(
                    version.tagName,
                    asset.name,
                    asset.size,
                    asset.browserDownloadUrl,
                    asset.id,
                )
            )
        }
        return assetsModels
    }
}