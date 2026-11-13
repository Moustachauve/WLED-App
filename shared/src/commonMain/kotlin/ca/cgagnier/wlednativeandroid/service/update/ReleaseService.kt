package ca.cgagnier.wlednativeandroid.service.update

import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.UpdateSourceRegistry
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.githubapi.Release
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.isOtaEnabled
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.util.stripVersionPrefix
import co.touchlab.kermit.Logger
import io.github.z4kn4fein.semver.toVersionOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val TAG = "updateService"
private val logger = Logger.withTag(TAG)
const val DEFAULT_REPO = ca.cgagnier.wlednativeandroid.model.Repository.DEFAULT_OWNER_REPO

/**
 * Extracts repository from device info:
 * 1. Official Illumidel controllers matched via [UpdateSourceRegistry] always track [DEFAULT_REPO]
 *    ("Illumidel/App-Releases"), even if WLED-derived firmware reports an upstream repository field.
 * 2. Otherwise use explicit `info.repository` if present, or fall back to [DEFAULT_REPO].
 */
fun getRepositoryFromInfo(info: Info): String {
    UpdateSourceRegistry.getSource(info)?.let { source ->
        return "${source.githubOwner}/${source.githubRepo}"
    }

    val repository = info.repository
    if (!repository.isNullOrBlank() && !repository.equals("unknown", ignoreCase = true)) {
        return repository
    }

    return DEFAULT_REPO
}

/**
 * Splits a repository string (e.g., "owner/name") into owner and name parts for API calls.
 * Returns a pair of (owner, name). Defaults to ("Illumidel", "App-Releases") if format is invalid.
 */
fun splitRepository(repository: String): Pair<String, String> {
    val parts = repository.split("/")
    if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
        return Pair(parts[0], parts[1])
    } else {
        logger.w { "Invalid repo format: $repository, using default" }
        val defaultParts = DEFAULT_REPO.split("/")
        return Pair(defaultParts[0], defaultParts[1])
    }
}

class ReleaseService(
    private val versionWithAssetsRepository: VersionWithAssetsRepository,
    private val repositoryDao: RepositoryDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    // Secondary constructor for Objective-C/Swift interop, which does not export default arguments.
    constructor(
        versionWithAssetsRepository: VersionWithAssetsRepository,
        repositoryDao: RepositoryDao,
    ) : this(versionWithAssetsRepository, repositoryDao, Dispatchers.IO)

    /**
     * If a newer stable version is available for an Illumidel device, returns its version tag.
     *
     * Regular update notifications strictly consider stable releases (`!isPrerelease`), while
     * opt-in Beta 2.0 migrations are offered separately in Device Edit via [isBetaUpdateAvailable].
     */
    suspend fun getNewerReleaseTag(deviceInfo: Info, ignoreVersion: String): String? {
        if (!isDeviceEligibleForUpdate(deviceInfo)) {
            return null
        }

        val repositoryStr = getRepositoryFromInfo(deviceInfo)
        val repository = repositoryDao.getRepositoryByOwnerAndRepo(repositoryStr)

        val latestVersion = repository?.let {
            versionWithAssetsRepository.getLatestStableVersionWithAssets(it.id)
        }

        return latestVersion?.version?.tagName?.takeIf {
            shouldOfferUpdate(deviceInfo, it, ignoreVersion)
        }
    }

    private fun shouldOfferUpdate(deviceInfo: Info, latestTagName: String, ignoreVersion: String): Boolean {
        val currentVersion = deviceInfo.version?.stripVersionPrefix() ?: return false
        val cleanLatest = latestTagName.stripVersionPrefix()
        val cleanIgnored = ignoreVersion.stripVersionPrefix()

        if (cleanLatest == cleanIgnored || cleanLatest == currentVersion) {
            return false
        }

        logger.d {
            "Checking update eligibility for device ${deviceInfo.ipAddress}: " +
                "current $currentVersion -> latest $cleanLatest"
        }

        return isVersionNewer(cleanLatest, currentVersion)
    }

    /**
     * Synchronizes the built-in Illumidel repository into the database,
     * preserving any user-configured enablement flags.
     */
    suspend fun syncBuiltInRepositories() = withContext(ioDispatcher) {
        val builtInOwnerAndRepos = Repository.BUILT_IN_REPOSITORIES.map { it.ownerAndRepo }

        Repository.BUILT_IN_REPOSITORIES.forEach { builtin ->
            val existing = repositoryDao.getRepositoryByOwnerAndRepo(builtin.ownerAndRepo)
            if (existing == null) {
                repositoryDao.insert(builtin)
                logger.d { "Inserted built-in repository: ${builtin.ownerAndRepo}" }
            } else {
                // Update names and descriptions if they changed, but preserve user booleans
                val updated = existing.copy(
                    name = builtin.name,
                    description = builtin.description,
                    htmlUrl = builtin.htmlUrl,
                    isDefault = true,
                )
                if (existing != updated) {
                    repositoryDao.update(updated)
                    logger.d { "Updated built-in repository: ${builtin.ownerAndRepo}" }
                }
            }
        }

        // Force all non-default repository to be false, in case they used to be default
        val allRepos = repositoryDao.getAllRepositories().first()
        allRepos
            .filter { repo ->
                repo.isDefault && builtInOwnerAndRepos.none { it.equals(repo.ownerAndRepo, ignoreCase = true) }
            }
            .forEach { repo ->
                repositoryDao.update(repo.copy(isDefault = false))
                logger.d { "Removed default status from repository: ${repo.ownerAndRepo}" }
            }
    }

    /**
     * Refreshes versions for all enabled repositories that are currently used by at least one device.
     *
     * @return `true` if at least one repository was refreshed and all refreshes succeeded;
     *      `false` if no enabled used repositories exist yet or if any repository refresh failed.
     */
    suspend fun refreshVersionsForUsedRepositories(githubApi: GithubApi, deviceRepository: DeviceRepository): Boolean =
        withContext(ioDispatcher) {
            val usedRepoIds = deviceRepository.getUsedRepositoryIds().toSet()
            val allRepos = repositoryDao.getAllRepositories().first()
            val repositories = allRepos
                .filter { it.isEnabled && it.isUpdateEnabled && usedRepoIds.contains(it.id) }
                .map { it.ownerAndRepo }
                .toSet()

            if (repositories.isEmpty()) {
                logger.i { "No used enabled repositories found, skipping update check." }
                return@withContext false
            }

            logger.i { "Refreshing versions from ${repositories.size} repositories: $repositories" }
            refreshVersions(githubApi, repositories)
        }

    /**
     * Refreshes versions for the repository associated with a single [device].
     */
    suspend fun refreshVersionsForDevice(githubApi: GithubApi, device: Device): Boolean = withContext(ioDispatcher) {
        val repo = repositoryDao.getRepositoryById(device.repositoryId)
        val repoStr = repo?.ownerAndRepo ?: DEFAULT_REPO
        refreshVersions(githubApi, setOf(repoStr))
    }

    /**
     * Refreshes versions from multiple repositories.
     * Gets a list of unique repositories, then fetches releases for each.
     *
     * @return `true` if all repositories were refreshed without error, `false` if any failed.
     */
    suspend fun refreshVersions(githubApi: GithubApi, repositories: Set<String>): Boolean = withContext(ioDispatcher) {
        var failureCount = 0
        for (repository in repositories) {
            val (repoOwner, repoName) = splitRepository(repository)
            logger.i { "Fetching releases from $repository" }
            val releases = githubApi.getAllReleases(repoOwner, repoName).getOrElse { exception ->
                logger.w(exception) { "Failed to refresh versions from $repository" }
                failureCount++
                null
            } ?: continue

            if (releases.isEmpty()) {
                logger.w { "GitHub returned 0 releases for $repository." }
            } else {
                val repoModel = Repository(
                    name = repoName,
                    ownerAndRepo = repository,
                    description = "",
                    htmlUrl = "https://github.com/$repository",
                )

                // We need to return pair of version with its assets so the repository
                // can map the new autogenerated IDs
                val versionAndAssetsMap = releases.associate { release ->
                    createVersion(release, 0L) to createAssetsForVersion(release, 0L)
                }
                logger.i { "Updating ${versionAndAssetsMap.size} versions and assets for $repository" }
                versionWithAssetsRepository.updateRepository(repoModel, versionAndAssetsMap)
            }
        }
        failureCount == 0
    }

    private fun createVersion(version: Release, repositoryId: Long): Version = Version(
        repositoryId = repositoryId,
        tagName = sanitizeTagName(version.tagName),
        name = version.name,
        description = version.body,
        isPrerelease = version.prerelease,
        publishedDate = version.publishedAt,
        htmlUrl = version.htmlUrl,
    )

    private fun createAssetsForVersion(version: Release, versionId: Long): List<Asset> {
        val assetsModels = mutableListOf<Asset>()
        for (asset in version.assets) {
            assetsModels.add(
                Asset(
                    versionId = versionId,
                    name = asset.name,
                    size = asset.size,
                    downloadUrl = asset.browserDownloadUrl,
                    assetId = asset.id,
                ),
            )
        }
        return assetsModels
    }

    /**
     * Removes any leading `'v'` or `'V'` from version tags (e.g., `"v0.14.0"` or `"V1.6.0"` -> `"1.6.0"`).
     * Leaves other tags (like `"nightly"`) untouched.
     */
    private fun sanitizeTagName(tagName: String): String = tagName.stripVersionPrefix()

    companion object {
        fun isDeviceEligibleForUpdate(deviceInfo: Info): Boolean = getIneligibilityReason(deviceInfo) == null

        /** Explains why [deviceInfo] can't be updated or switched to another language, or null if it can. */
        fun getIneligibilityReason(deviceInfo: Info): UpdateIneligibilityReason? = when {
            deviceInfo.brand != UpdateSourceRegistry.ILLUMIDEL_BRAND || deviceInfo.version.isNullOrEmpty() ->
                UpdateIneligibilityReason.NOT_ILLUMIDEL

            !deviceInfo.isOtaEnabled -> UpdateIneligibilityReason.OTA_DISABLED

            else -> null
        }

        fun isBetaUpdateAvailable(deviceInfo: Info?, candidateTag: String): Boolean {
            if (deviceInfo == null || !isDeviceEligibleForUpdate(deviceInfo)) {
                return false
            }
            return isVersionNewer(candidateTag, deviceInfo.version.orEmpty())
        }

        fun isVersionNewer(candidateTag: String, currentVersion: String): Boolean {
            if (candidateTag.isBlank() || currentVersion.isBlank()) {
                return false
            }
            val candidateSemver = candidateTag.stripVersionPrefix()
                .toVersionOrNull(strict = false) ?: return false
            val currentSemver = currentVersion.stripVersionPrefix()
                .toVersionOrNull(strict = false) ?: return false
            return candidateSemver > currentSemver
        }
    }
}

/** Why a device can't receive firmware updates or a language switch. */
enum class UpdateIneligibilityReason {
    /** The device isn't an Illumidel controller, or doesn't report a firmware version. */
    NOT_ILLUMIDEL,

    /** OTA updates are turned off on the device. */
    OTA_DISABLED,
}
