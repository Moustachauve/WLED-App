package ca.cgagnier.wlednativeandroid.service.update

import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.service.api.ApiResponse
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.api.updateDevice
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import co.touchlab.kermit.Logger
import io.github.z4kn4fein.semver.toVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import okio.FileSystem
import okio.Path
import ca.cgagnier.wlednativeandroid.shared.fileSystem as defaultFileSystem

private const val TAG = "DeviceUpdateService"
private val logger = Logger.withTag(TAG)
private const val UPDATE_TIMEOUT_SECONDS = 180L

/**
 * The minimum target version from which [RELEASE_NAME_OVERRIDES] are applied.
 * Overrides only take effect when upgrading to this version or later.
 */
private val RELEASE_OVERRIDES_MIN_VERSION = "0.16.0".toVersion(strict = false)

/**
 * Maps deprecated or transitional release names to the release name that should be used
 * for OTA updates. This handles cases where the binary name changes between WLED releases.
 *
 * For example, devices running the ESP32_V4 tech-preview (WLED 0.15.x) must be updated
 * with the standard ESP32 binary when upgrading to 0.16.0 or later, as ESP32_V4 assets will
 * no longer be published in those releases. Within 0.15.x, ESP32_V4 assets still exist
 * and no remapping is needed.
 *
 * These overrides are only applied when the target version is [RELEASE_OVERRIDES_MIN_VERSION]
 * or greater.
 *
 * See: https://github.com/Moustachauve/WLED-Android/issues/129
 */
private val RELEASE_NAME_OVERRIDES = mapOf(
    "ESP32_V4" to "ESP32",
)

// Injectable FileSystem, CoroutineDispatcher, and selectedAsset support testing and cross-fork OTA
@Suppress("LongParameterList")
class DeviceUpdateService(
    val device: DeviceWithState,
    private val versionWithAssets: VersionWithAssets,
    private val cacheDir: Path,
    private val deviceApiFactory: DeviceApiFactory,
    private val githubApi: GithubApi,
    private val fs: FileSystem = defaultFileSystem,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    selectedAsset: Asset? = null,
) {
    private val assetResolution: AssetResolution = if (selectedAsset != null) {
        AssetResolution(matchedAsset = selectedAsset, expectedAssetName = selectedAsset.name)
    } else {
        resolveDefaultAsset(device.stateInfo?.info, versionWithAssets)
    }
    private val resolvedAsset: Asset? = assetResolution.matchedAsset

    /**
     * Get the name of the asset to download.
     */
    fun getAssetName(): String = resolvedAsset?.name ?: assetResolution.expectedAssetName

    fun couldDetermineAsset(): Boolean = resolvedAsset != null

    fun isAssetFileCached(): Boolean {
        val asset = resolvedAsset ?: return false
        val metadata = fs.metadataOrNull(getPathForAsset()) ?: return false
        val cachedSize = metadata.size
        return metadata.isRegularFile &&
            cachedSize != null &&
            if (asset.size > 0L) cachedSize == asset.size else cachedSize > 0L
    }

    fun downloadBinary(repositoryDao: RepositoryDao): Flow<DownloadState> = flow {
        val asset = checkNotNull(resolvedAsset) {
            "Asset could not be determined for ${device.device.macAddress}."
        }
        val targetRepoId = versionWithAssets.version.repositoryId
        val repoStr = checkNotNull(repositoryDao.getRepositoryById(targetRepoId)) {
            "Repository $targetRepoId not found for ${versionWithAssets.version.tagName}"
        }.ownerAndRepo
        val (repoOwner, repoName) = splitRepository(repoStr)
        val targetPath = getPathForAsset()
        targetPath.parent?.let(fs::createDirectories)
        emitAll(githubApi.downloadReleaseBinary(asset, repoOwner, repoName, targetPath))
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(DownloadState.Failed(error))
    }

    fun getPathForAsset(): Path {
        val asset = checkNotNull(resolvedAsset) {
            "Asset could not be determined for ${device.device.macAddress}."
        }
        return cacheDir /
            versionWithAssets.version.repositoryId.toString() /
            versionWithAssets.version.tagName /
            asset.name
    }

    @Throws(Exception::class)
    suspend fun installUpdate(): ApiResponse<String> = installUpdate(skipValidation = false)

    @Throws(Exception::class)
    suspend fun installUpdate(skipValidation: Boolean): ApiResponse<String> {
        logger.d {
            "Installing software update: ${device.device.macAddress} (skipValidation=$skipValidation)"
        }
        // Longer TTL because updates can take a bit of time to fully install
        return deviceApiFactory
            .create(device.device, UPDATE_TIMEOUT_SECONDS)
            .updateDevice(getPathForAsset(), fs, ioDispatcher, skipValidation)
    }

    private data class AssetResolution(val matchedAsset: Asset?, val expectedAssetName: String)

    companion object {
        private const val VALIDATION_OVERRIDE_HINT = "Ignore firmware validation"
        private val HTML_BODY_REGEX = Regex(
            "<body[^>]*>(.*?)</body>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        private val HTML_JUNK_TAG_REGEX = Regex(
            "<(button|script|style)\\b[^>]*>.*?</\\1>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        private val HTML_BLOCK_BREAK_REGEX = Regex(
            "<br\\s*/?>|</(h[1-6]|p|div|li)>",
            RegexOption.IGNORE_CASE,
        )
        private val HTML_TAG_REGEX = Regex("<[^>]+>")
        private val WHITESPACE_REGEX = Regex("\\s+")

        private val SUPPORTED_LEGACY_PLATFORMS = setOf(
            "esp01",
            "esp02",
            "esp32",
            "esp8266",
        )

        /**
         * Returns `true` when [response] failed because WLED's OTA compatibility validator rejected
         * the firmware binary (missing metadata, release variant mismatch, or minimum version check)
         * and can be retried with `skipValidation = true`.
         */
        fun isValidationFailure(response: ApiResponse<String>): Boolean {
            if (response.isSuccessful) return false
            val plainText = extractPlainTextError(response) ?: return false
            return plainText.contains(VALIDATION_OVERRIDE_HINT, ignoreCase = true)
        }

        /**
         * Extracts a normalized plain-text message from WLED's `/update` HTML or plain-text error
         * response, removing scripts, styles, and navigation buttons.
         */
        fun extractPlainTextError(response: ApiResponse<String>): String? {
            val raw = response.body ?: response.errorBody ?: return null
            val bodyContent = HTML_BODY_REGEX.find(raw)?.groupValues?.get(1) ?: raw
            val withoutJunk = HTML_JUNK_TAG_REGEX.replace(bodyContent, " ")
            val spacedBlocks = HTML_BLOCK_BREAK_REGEX.replace(withoutJunk, " ")
            val strippedTags = HTML_TAG_REGEX.replace(spacedBlocks, "")
            val decoded = decodeBasicHtmlEntities(strippedTags)
            return decoded.trim().replace(WHITESPACE_REGEX, " ").ifEmpty { null }
        }

        private fun decodeBasicHtmlEntities(text: String): String = text
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#x27;", "'", ignoreCase = true)
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")

        /**
         * Returns all installable OTA binary assets (`.bin` and `.bin.gz`, excluding bootloaders)
         * published for [versionWithAssets], sorted alphabetically by filename.
         */
        fun getInstallableAssets(versionWithAssets: VersionWithAssets): List<Asset> = versionWithAssets.assets
            .filter { VersionWithAssets.isInstallableAssetName(it.name) }
            .sortedBy { it.name.lowercase() }

        /**
         * Attempts to automatically match a binary asset in [versionWithAssets] against [info]'s
         * `release` (WLED 0.15+) or legacy `platformName` (`arch`).
         *
         * Returns `null` when switching forks where board/variant names do not match automatically.
         */
        fun determineDefaultAsset(info: Info?, versionWithAssets: VersionWithAssets): Asset? =
            resolveDefaultAsset(info, versionWithAssets).matchedAsset

        private fun resolveDefaultAsset(info: Info?, versionWithAssets: VersionWithAssets): AssetResolution {
            val rawRelease = info?.release
            val target = if (!rawRelease.isNullOrEmpty()) {
                // Never swap to generic build if release name is known
                resolveReleaseOverride(rawRelease, versionWithAssets.version.tagName)
            } else {
                val platformName = info?.platformName
                if (platformName == null || !SUPPORTED_LEGACY_PLATFORMS.contains(platformName)) {
                    return AssetResolution(matchedAsset = null, expectedAssetName = "")
                }
                // Legacy fallback for WLED older than 0.15.0
                platformName.uppercase()
            }

            val candidateAssets = versionWithAssets.assets.filter {
                VersionWithAssets.isInstallableAssetName(it.name)
            }
            val versionWithTarget = formatVersionWithTarget(versionWithAssets.version.tagName, target)
            val expectedName = "WLED_$versionWithTarget.bin"
            val matched = findAssetByTarget(candidateAssets, versionWithTarget, target)
            return AssetResolution(matchedAsset = matched, expectedAssetName = expectedName)
        }

        private fun formatVersionWithTarget(tagName: String, target: String): String =
            "${Version.normalizeTagOrVersion(tagName)}_$target"

        private fun findAssetByTarget(assets: List<Asset>, versionWithTarget: String, target: String): Asset? =
            VersionWithAssets.FIRMWARE_EXTENSIONS.firstNotNullOfOrNull { ext ->
                assets.firstOrNull { it.name.endsWith("_$versionWithTarget$ext", ignoreCase = true) }
            } ?: VersionWithAssets.FIRMWARE_EXTENSIONS.firstNotNullOfOrNull { ext ->
                // Fallback when the embedded binary version differs from the GitHub tag (e.g. WLEDMM):
                // check extension + exact board segment rather than substring matching.
                assets.firstOrNull {
                    it.name.endsWith(ext, ignoreCase = true) &&
                        VersionWithAssets.parseFirmwareAssetFilename(it.name)
                            ?.boardSegment
                            ?.equals(target, ignoreCase = true) == true
                }
            }

        fun resolveReleaseOverride(rawRelease: String, targetVersionTag: String): String {
            val targetVersion = Version.parseSemVer(targetVersionTag)
            if (targetVersion == null) {
                logger.w {
                    "Could not parse target version '$targetVersionTag', skipping release name overrides"
                }
                return rawRelease
            }
            val baseVersion = targetVersion.copy(preRelease = null, buildMetadata = null)
            return if (baseVersion >= RELEASE_OVERRIDES_MIN_VERSION) {
                RELEASE_NAME_OVERRIDES[rawRelease.uppercase()] ?: rawRelease
            } else {
                rawRelease
            }
        }
    }
}
