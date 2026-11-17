package ca.cgagnier.wlednativeandroid.ui.homeScreen.firmwareBrowser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateService
import ca.cgagnier.wlednativeandroid.service.update.FirmwareBrowserStateHolder
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class FirmwareBrowserViewModel(
    macAddress: String,
    githubApi: GithubApi,
    releaseService: ReleaseService,
    deviceConnectionManager: DeviceConnectionManager,
) : ViewModel() {

    private val stateHolder = FirmwareBrowserStateHolder(
        macAddress = macAddress,
        githubApi = githubApi,
        releaseService = releaseService,
        deviceConnectionManager = deviceConnectionManager,
        coroutineScope = viewModelScope,
    )

    private val dialog = MutableStateFlow<FirmwareBrowserDialog?>(null)

    val uiState: StateFlow<FirmwareBrowserUiState> = combine(
        stateHolder.state,
        dialog,
    ) { browserState, activeDialog ->
        val deviceInfo = browserState.device?.stateInfo?.info
        val resolvedDialog = if (activeDialog is FirmwareBrowserDialog.VersionDetails) {
            val resolvedAsset = resolveSelectedAsset(activeDialog, deviceInfo)
            if (resolvedAsset != activeDialog.selectedAsset) {
                activeDialog.copy(selectedAsset = resolvedAsset)
            } else {
                activeDialog
            }
        } else {
            activeDialog
        }
        FirmwareBrowserUiState(
            device = browserState.device,
            repositories = browserState.repositories,
            selectedRepositoryId = browserState.selectedRepositoryId,
            selectedBranch = browserState.selectedBranch,
            versions = browserState.versions,
            installedTags = browserState.installedTags,
            isLoadingVersions = browserState.isLoadingVersions,
            loadError = browserState.loadError,
            dialog = resolvedDialog,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = FirmwareBrowserUiState(),
    )

    fun selectRepository(repositoryId: Long) = stateHolder.selectRepository(repositoryId)

    fun selectBranch(branch: Branch) = stateHolder.selectBranch(branch)

    fun refreshSelectedRepository() = stateHolder.refreshSelectedRepository()

    fun showVersionDetails(versionWithAssets: VersionWithAssets) {
        val currentInfo = stateHolder.state.value.device?.stateInfo?.info
        dialog.value = FirmwareBrowserDialog.VersionDetails(
            versionWithAssets = versionWithAssets,
            installableAssets = DeviceUpdateService.getInstallableAssets(versionWithAssets),
            selectedAsset = DeviceUpdateService.determineDefaultAsset(currentInfo, versionWithAssets),
        )
    }

    fun hideVersionDetails() {
        dialog.value = null
    }

    fun selectBinaryAsset(asset: Asset) {
        dialog.update { current ->
            (current as? FirmwareBrowserDialog.VersionDetails)?.copy(selectedAsset = asset) ?: current
        }
    }

    fun requestInstallSelectedVersion() {
        val details = dialog.value as? FirmwareBrowserDialog.VersionDetails ?: return
        val state = stateHolder.state.value
        val asset = resolveSelectedAsset(details, state.device?.stateInfo?.info) ?: return
        val currentDevice = state.device?.device
        val targetRepoId = details.versionWithAssets.version.repositoryId
        val switchingToSourceName = if (currentDevice != null && targetRepoId != currentDevice.repositoryId) {
            state.repositories.firstOrNull { it.id == targetRepoId }?.name
        } else {
            null
        }
        dialog.value = FirmwareBrowserDialog.Disclaimer(
            versionWithAssets = details.versionWithAssets,
            selectedAsset = asset,
            switchingToSourceName = switchingToSourceName,
        )
    }

    private fun resolveSelectedAsset(details: FirmwareBrowserDialog.VersionDetails, info: Info?): Asset? =
        details.selectedAsset ?: DeviceUpdateService.determineDefaultAsset(info, details.versionWithAssets)

    fun dismissDisclaimer() {
        dialog.value = null
    }

    fun confirmDisclaimer() {
        val disclaimer = dialog.value as? FirmwareBrowserDialog.Disclaimer ?: return
        dialog.value = FirmwareBrowserDialog.Installing(
            versionWithAssets = disclaimer.versionWithAssets,
            selectedAsset = disclaimer.selectedAsset,
        )
    }

    fun onInstallFinished() {
        dialog.value = null
    }
}
