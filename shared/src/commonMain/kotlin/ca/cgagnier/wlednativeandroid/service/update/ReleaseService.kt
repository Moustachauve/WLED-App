package ca.cgagnier.wlednativeandroid.service.update

import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.UpdateSourceRegistry
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.githubapi.Release
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.isOtaEnabled
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val TAG = "updateService"
private val logger = Logger.withTag(TAG)
const val DEFAULT_REPO = ca.cgagnier.wlednativeandroid.model.Repository.DEFAULT_OWNER_REPO

/**
 * Returns the trimmed `owner/repo` string explicitly reported by [Info.repository], or `null`
 * when the field is absent, blank, or `"unknown"` (e.g. WLED builds prior to 0.15.2).
 */
fun Info.explicitRepository(): String? =
    repository?.trim()?.takeIf { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }

/**
 * Extracts repository from device info using a three-tier fallback strategy:
 * 1. First: Use the repo field if available (format: "owner/name") - added in WLED 0.15.2
 * 2. Second: Use UpdateSourceRegistry based on brand pattern matching
 * 3. Third: Default to "wled/WLED"
 */
fun getRepositoryFromInfo(info: Info): String {
    // First priority: Use original repo, if supplied and not 'unknown'
    info.explicitRepository()?.let { return it }

    // Second priority: Use brand-based registry lookup
    val source = UpdateSourceRegistry.getSource(info)
    return source?.let { "${it.githubOwner}/${it.githubRepo}" } ?: DEFAULT_REPO
}

/**
 * Splits a repository string (e.g., "owner/name") into owner and name parts for API calls.
 * Returns a pair of (owner, name). Defaults to ("wled", "WLED") if format is invalid.t
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
     * If a new version is available, returns the version tag of it.
     *
     * @param deviceInfo Latest information about the device
     * @param branch Which branch to check for the update
     * @param ignoreVersion You can specify a version tag to be ignored as a new version. If this is
     *      set and match with the newest version, no version will be returned
     * @return The newest version if it is newer than versionName and different than ignoreVersion,
     *      otherwise an empty string.
     */
    suspend fun getNewerReleaseTag(
        deviceInfo: Info,
        branch: Branch,
        ignoreVersion: String,
        repositoryId: Long? = null,
    ): String? {
        if (!isDeviceEligibleForUpdate(deviceInfo)) {
            return null
        }

        val repository = repositoryId?.let { repositoryDao.getRepositoryById(it) }
            ?: repositoryDao.getRepositoryByOwnerAndRepo(getRepositoryFromInfo(deviceInfo))

        val latestVersion = repository?.let {
            getLatestVersionWithAssets(it.id, branch)
        }

        return latestVersion?.takeIf {
            shouldOfferUpdate(deviceInfo, it, ignoreVersion, branch)
        }?.version?.tagName
    }

    private fun isDeviceEligibleForUpdate(deviceInfo: Info): Boolean =
        !deviceInfo.version.isNullOrEmpty() && deviceInfo.isOtaEnabled

    private fun shouldOfferUpdate(
        deviceInfo: Info,
        latestVersion: VersionWithAssets,
        ignoreVersion: String,
        branch: Branch,
    ): Boolean {
        val currentVersion = deviceInfo.version ?: return false
        val latestTagName = latestVersion.version.tagName

        // Don't offer ignored versions or already-installed versions (including embedded asset versions)
        if (latestTagName == ignoreVersion || latestVersion.matchesInstalledVersion(currentVersion)) {
            return false
        }

        val isDeviceOnBeta = Version.isPreReleaseTag(currentVersion)

        logger.d {
            "Checking update eligibility for device ${deviceInfo.ipAddress}: " +
                "current $currentVersion -> latest $latestTagName"
        }

        // Check branch transition first, then SemVer comparison
        // If we're on a beta branch but looking for a stable branch, always offer to "update" to
        // the stable branch.
        return isBranchTransition(branch, isDeviceOnBeta) ||
            isNewerVersion(currentVersion, latestTagName)
    }

    // Same if we are on a stable branch but looking for a beta branch, we should offer to
    // "update" to the latest beta branch, even if its older.
    private fun isBranchTransition(branch: Branch, isDeviceOnBeta: Boolean): Boolean =
        (branch == Branch.STABLE && isDeviceOnBeta) || (branch == Branch.BETA && !isDeviceOnBeta)

    private fun isNewerVersion(currentVersion: String, latestTagName: String): Boolean {
        val latestSemver = Version.parseSemVer(latestTagName)
        val currentSemver = Version.parseSemVer(currentVersion)

        return if (latestSemver != null && currentSemver != null) {
            latestSemver > currentSemver
        } else {
            logger.i {
                "Non-SemVer version detected (current: '$currentVersion', latest: '$latestTagName'), " +
                    "offering update as it differs from current."
            }
            true
        }
    }

    private suspend fun getLatestVersionWithAssets(repositoryId: Long, branch: Branch): VersionWithAssets? {
        if (branch == Branch.BETA) {
            return versionWithAssetsRepository.getLatestBetaVersionWithAssets(repositoryId)
        }

        return versionWithAssetsRepository.getLatestStableVersionWithAssets(repositoryId)
    }

    /**
     * Synchronizes built-in repositories (official WLED, QuinLED, MoonModules) into the database,
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

    fun observeAllRepositories(): Flow<List<Repository>> = repositoryDao.getAllRepositories()

    fun observeVersionsForRepository(repositoryId: Long): Flow<List<VersionWithAssets>> =
        versionWithAssetsRepository.observeVersionsForRepository(repositoryId)

    /**
     * Refreshes versions for the repository matching [repositoryId], returning `false` if missing.
     */
    suspend fun refreshVersionsForRepositoryId(githubApi: GithubApi, repositoryId: Long): Boolean =
        withContext(ioDispatcher) {
            val repo = repositoryDao.getRepositoryById(repositoryId) ?: return@withContext false
            refreshVersions(githubApi, setOf(repo.ownerAndRepo))
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
        tagName = Version.normalizeTagOrVersion(version.tagName),
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

    companion object {
        /**
         * Returns repositories selectable in the firmware version browser: all enabled repositories
         * plus the repository matching [currentRepositoryId] (so a device running an auto-discovered
         * custom fork still sees its current source in the selector), ordered with the official
         * upstream WLED repository first followed by remaining sources alphabetically.
         */
        fun getSelectableRepositories(allRepositories: List<Repository>, currentRepositoryId: Long): List<Repository> =
            allRepositories
                .filter { it.isEnabled || it.id == currentRepositoryId }
                .sortedWith(
                    compareByDescending<Repository> { it.isOfficial }
                        .thenBy { it.name.lowercase() },
                )
    }
}
