package ca.cgagnier.wlednativeandroid.ui.homeScreen.firmwareBrowser

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateService
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "FirmwareBrowserViewModel"

@OptIn(ExperimentalCoroutinesApi::class)
class FirmwareBrowserViewModel(
    private val macAddress: String,
    private val githubApi: GithubApi,
    private val releaseService: ReleaseService,
    deviceConnectionManager: DeviceConnectionManager,
) : ViewModel() {

    private data class SelectionState(
        val branch: Branch? = null,
        val loadingRepositoryIds: Set<Long> = emptySet(),
        val failedRepositoryIds: Set<Long> = emptySet(),
        val selectedVersion: SelectedFirmwareVersion? = null,
        val dialog: FirmwareBrowserDialog? = null,
    )

    private val selectedRepositoryId = MutableStateFlow<Long?>(null)
    private val selectionState = MutableStateFlow(SelectionState())
    private val refreshedRepositoryIds = mutableSetOf<Long>()

    private val deviceFlow = deviceConnectionManager.allDevicesWithState
        .map { devices -> devices.firstOrNull { it.device.macAddress == macAddress } }
        .distinctUntilChanged()

    private val allRepositoriesFlow = releaseService.observeAllRepositories()

    private val effectiveRepositoryId = combine(
        deviceFlow,
        allRepositoriesFlow,
        selectedRepositoryId,
    ) { device, allRepos, explicitRepoId ->
        val selectable = ReleaseService.getSelectableRepositories(
            allRepositories = allRepos,
            currentRepositoryId = device?.device?.repositoryId ?: Repository.DEFAULT_ID,
        )
        val candidateId = explicitRepoId ?: device?.device?.repositoryId
        if (candidateId != null && selectable.any { it.id == candidateId }) {
            candidateId
        } else {
            selectable.firstOrNull()?.id
        }
    }.distinctUntilChanged()

    private val versionsFlow = effectiveRepositoryId
        .onEach { repoId ->
            if (repoId != null && repoId !in refreshedRepositoryIds) {
                refreshRepository(repoId)
            }
        }
        .flatMapLatest { repoId ->
            if (repoId == null) {
                flowOf<Pair<Long?, List<VersionWithAssets>?>>(null to emptyList())
            } else {
                releaseService.observeVersionsForRepository(repoId)
                    .map<List<VersionWithAssets>, Pair<Long?, List<VersionWithAssets>?>> { versions ->
                        repoId to versions
                    }
                    .onStart { emit(repoId to null) }
            }
        }

    val uiState: StateFlow<FirmwareBrowserUiState> = combine(
        deviceFlow,
        allRepositoriesFlow,
        versionsFlow,
        selectionState,
    ) { device, allRepos, (activeRepoId, rawVersions), selection ->
        buildUiState(device, allRepos, activeRepoId, rawVersions, selection)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = FirmwareBrowserUiState(),
    )

    fun selectRepository(repositoryId: Long) {
        if (uiState.value.selectedRepositoryId == repositoryId) return
        selectedRepositoryId.value = repositoryId
        selectionState.update { it.copy(selectedVersion = null) }
    }

    fun selectBranch(branch: Branch) {
        if (uiState.value.selectedBranch == branch) return
        selectionState.update { it.copy(branch = branch, selectedVersion = null) }
    }

    fun refreshSelectedRepository() {
        val repoId = uiState.value.selectedRepositoryId ?: return
        refreshRepository(repoId)
    }

    fun selectVersion(versionWithAssets: VersionWithAssets) {
        selectionState.update {
            it.copy(
                selectedVersion = SelectedFirmwareVersion(
                    versionWithAssets = versionWithAssets,
                    installableAssets = DeviceUpdateService.getInstallableAssets(versionWithAssets),
                    selectedAsset = null,
                ),
            )
        }
    }

    fun selectBinaryAsset(asset: Asset) {
        selectionState.update { current ->
            current.copy(selectedVersion = current.selectedVersion?.copy(selectedAsset = asset))
        }
    }

    fun requestInstallSelectedVersion() {
        val state = uiState.value
        val details = state.selectedVersion ?: return
        val asset = details.selectedAsset ?: return
        val currentRepoId = state.device?.device?.repositoryId
        val targetRepoId = details.versionWithAssets.version.repositoryId
        val switchingToSourceName = if (currentRepoId != null && targetRepoId != currentRepoId) {
            state.repositories.firstOrNull { it.id == targetRepoId }?.name
        } else {
            null
        }
        selectionState.update {
            it.copy(
                dialog = FirmwareBrowserDialog.Disclaimer(
                    versionWithAssets = details.versionWithAssets,
                    selectedAsset = asset,
                    switchingToSourceName = switchingToSourceName,
                ),
            )
        }
    }

    fun dismissDisclaimer() {
        selectionState.update { it.copy(dialog = null) }
    }

    fun confirmDisclaimer() {
        selectionState.update { state ->
            val disclaimer = state.dialog as? FirmwareBrowserDialog.Disclaimer ?: return@update state
            state.copy(
                dialog = FirmwareBrowserDialog.Installing(
                    versionWithAssets = disclaimer.versionWithAssets,
                    selectedAsset = disclaimer.selectedAsset,
                ),
            )
        }
    }

    fun onInstallFinished() {
        selectionState.update { it.copy(dialog = null) }
    }

    private fun refreshRepository(repositoryId: Long) {
        if (repositoryId in selectionState.value.loadingRepositoryIds) return
        selectionState.update {
            it.copy(
                loadingRepositoryIds = it.loadingRepositoryIds + repositoryId,
                failedRepositoryIds = it.failedRepositoryIds - repositoryId,
            )
        }
        viewModelScope.launch {
            try {
                val success = releaseService.refreshVersionsForRepositoryId(githubApi, repositoryId)
                if (success) {
                    refreshedRepositoryIds.add(repositoryId)
                } else {
                    selectionState.update { it.copy(failedRepositoryIds = it.failedRepositoryIds + repositoryId) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Keep GitHub/database refresh errors contained to the list error state
                Log.w(TAG, "Failed to refresh versions for repository $repositoryId", e)
                selectionState.update { it.copy(failedRepositoryIds = it.failedRepositoryIds + repositoryId) }
            } finally {
                selectionState.update { it.copy(loadingRepositoryIds = it.loadingRepositoryIds - repositoryId) }
            }
        }
    }

    private fun buildUiState(
        device: DeviceWithState?,
        allRepos: List<Repository>,
        activeRepoId: Long?,
        rawVersions: List<VersionWithAssets>?,
        selection: SelectionState,
    ): FirmwareBrowserUiState {
        val branch = selection.branch
            ?: if (device?.device?.branch == Branch.BETA) Branch.BETA else Branch.STABLE
        val repositories = ReleaseService.getSelectableRepositories(
            allRepositories = allRepos,
            currentRepositoryId = device?.device?.repositoryId ?: Repository.DEFAULT_ID,
        )
        val versions = rawVersions?.let { VersionWithAssetsRepository.filterAndSortVersions(it, branch) }.orEmpty()
        val installedTags = resolveInstalledTags(device, versions)
        val matchingSelection = selection.selectedVersion?.takeIf { selected ->
            val v = selected.versionWithAssets.version
            v.repositoryId == activeRepoId && v.isBetaRelease == (branch == Branch.BETA)
        }
        val resolvedVersion = resolveSelectedVersionAsset(matchingSelection, device)
        val isRefreshingRepo = activeRepoId != null && activeRepoId in selection.loadingRepositoryIds
        return FirmwareBrowserUiState(
            device = device,
            repositories = repositories,
            selectedRepositoryId = activeRepoId,
            selectedBranch = branch,
            versions = versions,
            installedTags = installedTags,
            isLoadingVersions = rawVersions == null || isRefreshingRepo,
            loadError = activeRepoId != null && activeRepoId in selection.failedRepositoryIds,
            selectedVersion = resolvedVersion,
            dialog = selection.dialog,
        )
    }

    private fun resolveInstalledTags(device: DeviceWithState?, versions: List<VersionWithAssets>): Set<String> {
        val deviceRepoId = device?.device?.repositoryId ?: return emptySet()
        val installedVersion = device.stateInfo?.info?.version ?: return emptySet()
        return versions
            .asSequence()
            .filter { it.matchesInstalledVersion(installedVersion, deviceRepoId) }
            .map { it.version.tagName }
            .toSet()
    }

    private fun resolveSelectedVersionAsset(
        selected: SelectedFirmwareVersion?,
        device: DeviceWithState?,
    ): SelectedFirmwareVersion? {
        if (selected == null || selected.selectedAsset != null) {
            return selected
        }
        val defaultAsset = DeviceUpdateService.determineDefaultAsset(
            info = device?.stateInfo?.info,
            versionWithAssets = selected.versionWithAssets,
        ) ?: return selected
        return selected.copy(selectedAsset = defaultAsset)
    }
}
