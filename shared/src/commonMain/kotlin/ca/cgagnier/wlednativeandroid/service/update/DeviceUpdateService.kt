package ca.cgagnier.wlednativeandroid.service.update

import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.service.api.ApiResponse
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.api.updateDevice
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.util.stripVersionPrefix
import co.touchlab.kermit.Logger
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

@Suppress("LongParameterList")
class DeviceUpdateService(
    val device: DeviceWithState,
    private val versionWithAssets: VersionWithAssets,
    private val cacheDir: Path,
    private val deviceApiFactory: DeviceApiFactory,
    private val githubApi: GithubApi,
    private val fs: FileSystem = defaultFileSystem,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val supportedPlatforms = listOf(
        "esp32",
    )

    private var assetName: String = ""
    private var couldDetermineAsset: Boolean = false
    private lateinit var asset: Asset

    init {
        determineAsset()
    }

    /**
     * Resolves the Illumidel firmware binary matching `v{tag}_{PLATFORM}_{lang}.bin`,
     * where `lang` is derived from [ca.cgagnier.wlednativeandroid.model.Branch.languageCode]
     * ("en" for English, "fr" for French).
     */
    private fun determineAsset() {
        val cleanTag = versionWithAssets.version.tagName.stripVersionPrefix()
        val langCode = device.device.branch.languageCode
        val rawPlatform = device.stateInfo?.info?.platformName.orEmpty()
        if (rawPlatform.isEmpty()) {
            return
        }

        val normalizedPlatform = rawPlatform.lowercase()
        val platformUpper = rawPlatform.uppercase()
        val primaryName = "v${cleanTag}_${platformUpper}_$langCode.bin"
        assetName = primaryName
        if (normalizedPlatform in supportedPlatforms) {
            findAsset(primaryName, "${cleanTag}_${platformUpper}_$langCode.bin")
        }
    }

    private fun findAsset(primaryName: String, fallbackName: String) {
        val matched = versionWithAssets.assets.firstOrNull { it.name.equals(primaryName, ignoreCase = true) }
            ?: versionWithAssets.assets.firstOrNull { it.name.equals(fallbackName, ignoreCase = true) }
        if (matched != null) {
            this.asset = matched
            this.assetName = matched.name
            couldDetermineAsset = true
        }
    }

    /**
     * Get the name of the asset to download.
     */
    fun getAssetName(): String = assetName

    fun couldDetermineAsset(): Boolean = couldDetermineAsset

    fun isAssetFileCached(): Boolean {
        if (!couldDetermineAsset) return false
        val size = fs.metadataOrNull(getPathForAsset())?.size ?: return false
        return if (asset.size > 0L) size == asset.size else size > 0L
    }

    fun downloadBinary(repoOwner: String, repoName: String): Flow<DownloadState> = flow {
        val targetPath = getPathForAsset()
        targetPath.parent?.let(fs::createDirectories)
        emitAll(githubApi.downloadReleaseBinary(asset, repoOwner, repoName, targetPath))
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(DownloadState.Failed(error))
    }

    fun downloadBinary(repositoryDao: RepositoryDao): Flow<DownloadState> = flow {
        val repo = repositoryDao.getRepositoryById(device.device.repositoryId)
        val repoStr = repo?.ownerAndRepo ?: DEFAULT_REPO
        val (repoOwner, repoName) = splitRepository(repoStr)
        emitAll(downloadBinary(repoOwner, repoName))
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(DownloadState.Failed(error))
    }

    fun getPathForAsset(): Path {
        check(::asset.isInitialized) {
            "Asset could not be determined for ${device.device.macAddress}."
        }
        return cacheDir / versionWithAssets.version.tagName / asset.name
    }

    @Throws(Exception::class)
    suspend fun installUpdate(): ApiResponse<String> {
        logger.d { "Installing software update: ${device.device.macAddress}" }
        // Longer TTL because updates can take a bit of time to fully install
        return deviceApiFactory
            .create(device.device, UPDATE_TIMEOUT_SECONDS)
            .updateDevice(getPathForAsset(), fs, ioDispatcher)
    }

    companion object
}
