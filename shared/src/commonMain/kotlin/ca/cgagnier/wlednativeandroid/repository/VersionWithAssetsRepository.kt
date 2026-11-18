package ca.cgagnier.wlednativeandroid.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import kotlinx.coroutines.flow.Flow
import io.github.z4kn4fein.semver.Version as SemVersion

/**
 * nightly tag is not supported at the moment. Exclude it from results.
 * TODO: Add support for nightly tags. This will need special handling since the tag itself never
 *   changes. Probably need a new Branch option for it too.
 */
private const val IGNORED_TAG = "nightly"

class VersionWithAssetsRepository(
    private val database: DevicesDatabase,
    private val repositoryDao: RepositoryDao,
    private val versionDao: VersionDao,
    private val assetDao: AssetDao,
) {

    suspend fun updateRepository(repository: Repository, versionToAssetsMap: Map<Version, List<Asset>>) {
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                // Ensure repository exists and get its ID, preserving existing metadata
                // (display name, description, and flags) when refreshing a known repository.
                val existingRepo = repositoryDao.getRepositoryByOwnerAndRepo(repository.ownerAndRepo)
                val repoId = existingRepo?.id ?: run {
                    val repoToInsert = if (repository.isOfficial) {
                        repository.copy(id = Repository.DEFAULT_ID)
                    } else {
                        repository
                    }
                    repositoryDao.insert(repoToInsert)
                }

                // Get existing data for this repository
                val existingVersions = versionDao.getVersionsByRepository(repoId)
                val existingAssets = assetDao.getAssetsByRepository(repoId)

                // Find versions that were removed from GitHub
                val newVersionTags = versionToAssetsMap.keys.map { it.tagName }.toSet()
                val versionsToDelete = existingVersions.filter { it.tagName !in newVersionTags }

                // Because Assets use Version IDs, and Version IDs may change or be new,
                // it's safest to delete removed assets by name matching, or simply let CASCADE delete
                // assets of deleted versions, and then update existing versions.

                versionsToDelete.forEach { versionDao.delete(it) }

                // Insert/update current data
                // We must update the versions to have the correct repoId from the database
                val versionsToInsert = versionToAssetsMap.keys.map { it.copy(repositoryId = repoId) }
                versionDao.insertMany(versionsToInsert)

                // Refetch inserted versions to get their generated IDs to map to assets
                val insertedVersions = versionDao.getVersionsByRepository(repoId)
                val versionMap = insertedVersions.associateBy { it.tagName }

                val assetsToInsert = mutableListOf<Asset>()
                val assetKeysToKeep = mutableSetOf<Pair<Long, String>>()

                for ((version, assets) in versionToAssetsMap) {
                    val insertedVersion = versionMap[version.tagName] ?: continue
                    for (asset in assets) {
                        assetsToInsert.add(asset.copy(versionId = insertedVersion.id))
                        assetKeysToKeep.add(Pair(insertedVersion.id, asset.name))
                    }
                }

                val assetsToDelete = existingAssets.filter {
                    Pair(it.versionId, it.name) !in assetKeysToKeep
                }
                assetsToDelete.forEach { assetDao.delete(it) }

                assetDao.insertMany(assetsToInsert)
            }
        }
    }

    suspend fun getLatestStableVersionWithAssets(repositoryId: Long): VersionWithAssets? {
        val latestVersion = getLatestVersion(
            versionDao.getVersionsByRepository(repositoryId).filter {
                !it.isBetaRelease && !it.tagName.equals(IGNORED_TAG, ignoreCase = true)
            },
        )
        return latestVersion?.let { versionDao.getVersionByTagName(repositoryId, it.tagName) }
    }

    suspend fun getLatestBetaVersionWithAssets(repositoryId: Long): VersionWithAssets? {
        val latestVersion = getLatestVersion(
            versionDao.getVersionsByRepository(repositoryId).filter {
                !it.tagName.equals(IGNORED_TAG, ignoreCase = true)
            },
        )
        return latestVersion?.let { versionDao.getVersionByTagName(repositoryId, it.tagName) }
    }

    suspend fun getVersionByTag(repositoryId: Long, tagName: String): VersionWithAssets? =
        versionDao.getVersionByTagName(repositoryId, tagName)

    /**
     * Streams all releases (with their binary assets) cached for [repositoryId].
     */
    fun observeVersionsForRepository(repositoryId: Long): Flow<List<VersionWithAssets>> =
        versionDao.observeVersionsWithAssetsByRepository(repositoryId)

    companion object {
        val semVerComparator =
            Comparator<Pair<Version, SemVersion?>> { v1, v2 ->
                val semver1 = v1.second
                val semver2 = v2.second

                when {
                    semver1 != null && semver2 != null -> {
                        val cmp = semver1.compareTo(semver2)
                        if (cmp != 0) cmp else v1.first.publishedDate.compareTo(v2.first.publishedDate)
                    }

                    semver1 != null -> 1

                    semver2 != null -> -1

                    else -> v1.first.publishedDate.compareTo(v2.first.publishedDate)
                }
            }

        fun getLatestVersion(versions: List<Version>): Version? = versions.map {
            it to Version.parseSemVer(it.tagName)
        }.maxWithOrNull(semVerComparator)?.first

        /**
         * Filters [versions] to either Stable-only or Beta-only releases based on [branch]
         * (excluding unsupported `"nightly"` builds) and sorts them descending with the
         * most recent / highest version number first.
         */
        fun filterAndSortVersions(versions: List<VersionWithAssets>, branch: Branch): List<VersionWithAssets> {
            val wantBeta = branch == Branch.BETA
            return versions
                .filter { item ->
                    !item.version.tagName.equals(IGNORED_TAG, ignoreCase = true) &&
                        item.version.isBetaRelease == wantBeta
                }
                .map { item -> (item.version to Version.parseSemVer(item.version.tagName)) to item }
                .sortedWith(compareBy(semVerComparator.reversed()) { it.first })
                .map { it.second }
        }
    }
}
