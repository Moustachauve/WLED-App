package ca.cgagnier.wlednativeandroid.service.update

import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toOkioPath
import java.io.File

// Platform factory mirrors DeviceUpdateService constructor dependencies with java.io.File cacheDir
@Suppress("LongParameterList")
operator fun DeviceUpdateService.Companion.invoke(
    device: DeviceWithState,
    versionWithAssets: VersionWithAssets,
    cacheDir: File,
    deviceApiFactory: DeviceApiFactory,
    githubApi: GithubApi,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    selectedAsset: Asset? = null,
): DeviceUpdateService = DeviceUpdateService(
    device = device,
    versionWithAssets = versionWithAssets,
    cacheDir = cacheDir.toOkioPath(),
    deviceApiFactory = deviceApiFactory,
    githubApi = githubApi,
    ioDispatcher = ioDispatcher,
    selectedAsset = selectedAsset,
)
