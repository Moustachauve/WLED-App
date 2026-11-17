package ca.cgagnier.wlednativeandroid.ui.homeScreen.firmwareBrowser

import androidx.compose.runtime.Immutable
import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState

data class FirmwareBrowserUiState(
    val device: DeviceWithState? = null,
    val repositories: List<Repository> = emptyList(),
    val selectedRepositoryId: Long? = null,
    val selectedBranch: Branch = Branch.STABLE,
    val versions: List<VersionWithAssets> = emptyList(),
    val isLoadingVersions: Boolean = false,
    val loadError: Boolean = false,
    val versionDetails: FirmwareVersionDetailsState? = null,
    val disclaimerTarget: FirmwareInstallTarget? = null,
    val installingTarget: FirmwareInstallTarget? = null,
) {
    val selectedRepository: Repository?
        get() = repositories.firstOrNull { it.id == selectedRepositoryId }
}

data class FirmwareVersionDetailsState(
    val versionWithAssets: VersionWithAssets,
    val installableAssets: List<Asset>,
    val selectedAsset: Asset?,
)

@Immutable
data class FirmwareInstallTarget(val versionWithAssets: VersionWithAssets, val selectedAsset: Asset)

@Immutable
data class FirmwareBrowserActions(
    val onSelectRepository: (Long) -> Unit = {},
    val onSelectBranch: (Branch) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onVersionClick: (VersionWithAssets) -> Unit = {},
    val onDismissVersionDetails: () -> Unit = {},
    val onSelectBinaryAsset: (Asset) -> Unit = {},
    val onRequestInstall: () -> Unit = {},
    val onDismissDisclaimer: () -> Unit = {},
    val onConfirmDisclaimer: () -> Unit = {},
    val onInstallFinished: () -> Unit = {},
)
