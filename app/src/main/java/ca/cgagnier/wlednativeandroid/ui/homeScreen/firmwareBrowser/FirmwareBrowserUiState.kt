package ca.cgagnier.wlednativeandroid.ui.homeScreen.firmwareBrowser

import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateService
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState

data class FirmwareBrowserUiState(
    val device: DeviceWithState? = null,
    val repositories: List<Repository> = emptyList(),
    val selectedRepositoryId: Long? = null,
    val selectedBranch: Branch = Branch.STABLE,
    val versions: List<VersionWithAssets> = emptyList(),
    val installedTags: Set<String> = emptySet(),
    val isLoadingVersions: Boolean = true,
    val loadError: Boolean = false,
    val selectedVersion: SelectedFirmwareVersion? = null,
    val dialog: FirmwareBrowserDialog? = null,
) {
    val selectedRepository: Repository?
        get() = repositories.firstOrNull { it.id == selectedRepositoryId }
}

data class SelectedFirmwareVersion(val versionWithAssets: VersionWithAssets, val selectedAsset: Asset?) {
    val installableAssets: List<Asset> = DeviceUpdateService.getInstallableAssets(versionWithAssets)
}

sealed interface FirmwareBrowserDialog {
    data class Disclaimer(
        val versionWithAssets: VersionWithAssets,
        val selectedAsset: Asset,
        val switchingToSourceName: String?,
    ) : FirmwareBrowserDialog

    data class Installing(val versionWithAssets: VersionWithAssets, val selectedAsset: Asset) : FirmwareBrowserDialog
}
