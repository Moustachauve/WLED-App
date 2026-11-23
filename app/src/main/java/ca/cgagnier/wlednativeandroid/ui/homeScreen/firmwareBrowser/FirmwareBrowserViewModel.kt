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
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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

    private data class BrowserState(
        val branch: Branch? = null,
        val isRefreshing: Boolean = false,
        val loadError: Boolean = false,
        val selectedVersion: SelectedFirmwareVersion? = null,
        val dialog: FirmwareBrowserDialog? = null,
    )

    private val selectedRepositoryId = MutableStateFlow<Long?>(null)
    private val browserState = MutableStateFlow(BrowserState())
    private val refreshedRepositoryIds = mutableSetOf<Long>()
    private var refreshJob: Job? = null

    private val deviceFlow = deviceConnectionManager.allDevicesWithState
        .map { devices -> devices.firstOrNull { it.device.macAddress == macAddress } }
        .distinctUntilChanged()

    private val repoSelectionFlow = combine(
        deviceFlow,
        releaseService.observeAllRepositories(),
        selectedRepositoryId,
    ) { device, allRepos, explicitRepoId ->
        val selectable = ReleaseService.getSelectableRepositories(
            allRepositories = allRepos,
            currentRepositoryId = device?.device?.repositoryId ?: Repository.DEFAULT_ID,
        )
        val candidateId = explicitRepoId ?: device?.device?.repositoryId
        val activeId = candidateId?.takeIf { id -> selectable.any { it.id == id } }
            ?: selectable.firstOrNull()?.id
        selectable to activeId
    }

    private val versionsFlow = repoSelectionFlow
        .map { it.second }
        .distinctUntilChanged()
        .onEach { repoId ->
            refreshJob?.cancel()
            browserState.update { it.copy(isRefreshing = false, loadError = false) }
            if (repoId != null && repoId !in refreshedRepositoryIds) {
                refreshRepository(repoId)
            }
        }
        .flatMapLatest { repoId ->
            if (repoId == null) flowOf(emptyList()) else releaseService.observeVersionsForRepository(repoId)
        }

    val uiState: StateFlow<FirmwareBrowserUiState> = combine(
        deviceFlow,
        repoSelectionFlow,
        versionsFlow,
        browserState,
    ) { device, (repositories, activeRepoId), versionsForRepo, state ->
        buildUiState(device, repositories, activeRepoId, versionsForRepo, state)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = FirmwareBrowserUiState(),
    )

    fun selectRepository(repositoryId: Long) {
        if (uiState.value.selectedRepositoryId == repositoryId) return
        selectedRepositoryId.value = repositoryId
        browserState.update { it.copy(selectedVersion = null) }
    }

    fun selectBranch(branch: Branch) {
        if (uiState.value.selectedBranch == branch) return
        browserState.update { it.copy(branch = branch, selectedVersion = null) }
    }

    fun refreshSelectedRepository() {
        val repoId = uiState.value.selectedRepositoryId ?: return
        if (browserState.value.isRefreshing) return
        refreshRepository(repoId)
    }

    fun selectVersion(versionWithAssets: VersionWithAssets) {
        browserState.update {
            it.copy(
                selectedVersion = SelectedFirmwareVersion(
                    versionWithAssets = versionWithAssets,
                    selectedAsset = null,
                ),
            )
        }
    }

    fun selectBinaryAsset(asset: Asset) {
        browserState.update { current ->
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
        browserState.update {
            it.copy(
                dialog = FirmwareBrowserDialog.Disclaimer(
                    versionWithAssets = details.versionWithAssets,
                    selectedAsset = asset,
                    switchingToSourceName = switchingToSourceName,
                ),
            )
        }
    }

    fun dismissDialog() {
        browserState.update { it.copy(dialog = null) }
    }

    fun confirmDisclaimer() {
        browserState.update { state ->
            val disclaimer = state.dialog as? FirmwareBrowserDialog.Disclaimer ?: return@update state
            state.copy(
                dialog = FirmwareBrowserDialog.Installing(
                    versionWithAssets = disclaimer.versionWithAssets,
                    selectedAsset = disclaimer.selectedAsset,
                ),
            )
        }
    }

    private fun refreshRepository(repositoryId: Long) {
        refreshJob?.cancel()
        browserState.update { it.copy(isRefreshing = true, loadError = false) }
        refreshJob = viewModelScope.launch {
            val success = try {
                releaseService.refreshVersionsForRepositoryId(githubApi, repositoryId)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Keep GitHub/database refresh errors contained to the list error state
                Log.w(TAG, "Failed to refresh versions for repository $repositoryId", e)
                false
            }
            coroutineContext.ensureActive()
            if (success) refreshedRepositoryIds.add(repositoryId)
            browserState.update { it.copy(isRefreshing = false, loadError = !success) }
        }
    }

    private fun buildUiState(
        device: DeviceWithState?,
        repositories: List<Repository>,
        activeRepoId: Long?,
        versionsForRepo: List<VersionWithAssets>,
        state: BrowserState,
    ): FirmwareBrowserUiState {
        val branch = state.branch ?: if (device?.device?.branch == Branch.BETA) Branch.BETA else Branch.STABLE
        val versions = VersionWithAssetsRepository.filterAndSortVersions(versionsForRepo, branch)
        val installedRepoId = device?.device?.repositoryId
        val installedVersion = device?.stateInfo?.info?.version
        val installedTags = if (installedRepoId != null && installedVersion != null) {
            versions
                .asSequence()
                .filter { it.matchesInstalledVersion(installedVersion, installedRepoId) }
                .map { it.version.tagName }
                .toSet()
        } else {
            emptySet()
        }
        return FirmwareBrowserUiState(
            device = device,
            repositories = repositories,
            selectedRepositoryId = activeRepoId,
            selectedBranch = branch,
            versions = versions,
            installedTags = installedTags,
            isLoadingVersions = state.isRefreshing,
            loadError = state.loadError,
            selectedVersion = resolveSelectedVersionAsset(state.selectedVersion, device),
            dialog = state.dialog,
        )
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
