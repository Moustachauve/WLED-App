package ca.cgagnier.wlednativeandroid.ui.homeScreen.firmwareBrowser

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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.withContext

private const val STOP_TIMEOUT_MILLIS = 5_000L

@OptIn(ExperimentalCoroutinesApi::class)
class FirmwareBrowserViewModel(
    private val macAddress: String,
    private val versionWithAssetsRepository: VersionWithAssetsRepository,
    private val githubApi: GithubApi,
    private val releaseService: ReleaseService,
    private val deviceConnectionManager: DeviceConnectionManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val selectedRepositoryId = MutableStateFlow<Long?>(null)
    private val selectedBranch = MutableStateFlow(Branch.STABLE)
    private val isLoadingVersions = MutableStateFlow(false)
    private val loadError = MutableStateFlow(false)
    private val versionDetails = MutableStateFlow<FirmwareVersionDetailsState?>(null)
    private val disclaimerTarget = MutableStateFlow<FirmwareInstallTarget?>(null)
    private val installingTarget = MutableStateFlow<FirmwareInstallTarget?>(null)

    private val autoFetchedRepoIds = mutableSetOf<Long>()
    private var initializedBranch = false
    private var refreshJob: Job? = null
    private var activeRefreshToken = 0L

    private val deviceFlow = deviceConnectionManager.allDevicesWithState
        .map { devices ->
            devices.firstOrNull { it.device.macAddress == macAddress }
        }
        .distinctUntilChanged()
        .onEach { device ->
            if (device != null) {
                if (selectedRepositoryId.value == null) {
                    selectedRepositoryId.value = device.device.repositoryId
                }
                if (!initializedBranch) {
                    initializedBranch = true
                    selectedBranch.value = if (device.device.branch == Branch.BETA) {
                        Branch.BETA
                    } else {
                        Branch.STABLE
                    }
                }
            }
        }

    private val deviceAndReposFlow = combine(
        deviceFlow,
        versionWithAssetsRepository.observeAllRepositories(),
    ) { device, allRepos ->
        val currentRepoId = device?.device?.repositoryId ?: Repository.DEFAULT_ID
        device to ReleaseService.getSelectableRepositories(allRepos, currentRepoId)
    }.onEach { (_, repositories) ->
        val currentSelected = selectedRepositoryId.value
        if (currentSelected != null &&
            repositories.isNotEmpty() &&
            repositories.none { it.id == currentSelected }
        ) {
            selectRepository(repositories.first().id)
        }
    }

    private data class FilterState(
        val repositoryId: Long?,
        val branch: Branch,
        val versions: List<VersionWithAssets>,
        val hasLoadedCachedVersions: Boolean,
    )

    private val repoVersionsFlow = selectedRepositoryId
        .flatMapLatest { repoId ->
            if (repoId == null) {
                flowOf(null to null)
            } else {
                versionWithAssetsRepository.observeVersionsForRepository(repoId)
                    .onEach { versions ->
                        if (versions.isEmpty() && !isLoadingVersions.value && repoId !in autoFetchedRepoIds) {
                            refreshRepository(repoId)
                        }
                    }
                    .map<List<VersionWithAssets>, Pair<Long?, List<VersionWithAssets>?>> { versions ->
                        repoId to versions
                    }
                    .onStart { emit(repoId to null) }
            }
        }

    private val filterStateFlow = combine(
        repoVersionsFlow,
        selectedBranch,
    ) { (repoId, rawVersions), branch ->
        FilterState(
            repositoryId = repoId,
            branch = branch,
            versions = rawVersions?.let { VersionWithAssetsRepository.filterAndSortVersions(it, branch) }.orEmpty(),
            hasLoadedCachedVersions = rawVersions != null,
        )
    }

    private val statusStateFlow = combine(
        isLoadingVersions,
        loadError,
    ) { isLoading, hasError ->
        isLoading to hasError
    }

    private val dialogStateFlow = combine(
        versionDetails,
        disclaimerTarget,
        installingTarget,
    ) { details, disclaimer, installing ->
        Triple(details, disclaimer, installing)
    }

    val uiState: StateFlow<FirmwareBrowserUiState> = combine(
        deviceAndReposFlow,
        filterStateFlow,
        statusStateFlow,
        dialogStateFlow,
    ) { (device, repositories), filterState, statusState, dialogState ->
        FirmwareBrowserUiState(
            device = device,
            repositories = repositories,
            selectedRepositoryId = filterState.repositoryId,
            selectedBranch = filterState.branch,
            versions = filterState.versions,
            isLoadingVersions = statusState.first || !filterState.hasLoadedCachedVersions,
            loadError = statusState.second,
            versionDetails = dialogState.first,
            disclaimerTarget = dialogState.second,
            installingTarget = dialogState.third,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = FirmwareBrowserUiState(isLoadingVersions = true),
    )

    fun selectRepository(repositoryId: Long) {
        if (selectedRepositoryId.value == repositoryId) return
        activeRefreshToken++
        refreshJob?.cancel()
        refreshJob = null
        isLoadingVersions.value = false
        loadError.value = false
        selectedRepositoryId.value = repositoryId
    }

    fun selectBranch(branch: Branch) {
        initializedBranch = true
        selectedBranch.value = branch
    }

    fun refreshSelectedRepository() {
        val repoId = selectedRepositoryId.value ?: return
        refreshRepository(repoId)
    }

    fun showVersionDetails(versionWithAssets: VersionWithAssets) {
        val currentDevice = uiState.value.device
        val installableAssets = DeviceUpdateService.getInstallableAssets(versionWithAssets)
        val defaultAsset = currentDevice?.let {
            DeviceUpdateService.determineDefaultAsset(it, versionWithAssets)
        }
        versionDetails.value = FirmwareVersionDetailsState(
            versionWithAssets = versionWithAssets,
            installableAssets = installableAssets,
            selectedAsset = defaultAsset,
        )
    }

    fun hideVersionDetails() {
        versionDetails.value = null
    }

    fun selectBinaryAsset(asset: Asset) {
        versionDetails.update { current ->
            current?.copy(selectedAsset = asset)
        }
    }

    fun requestInstallSelectedVersion() {
        val details = versionDetails.value ?: return
        val asset = details.selectedAsset ?: return
        versionDetails.value = null
        disclaimerTarget.value = FirmwareInstallTarget(
            versionWithAssets = details.versionWithAssets,
            selectedAsset = asset,
        )
    }

    fun dismissDisclaimer() {
        disclaimerTarget.value = null
    }

    fun confirmDisclaimer() {
        val target = disclaimerTarget.value ?: return
        disclaimerTarget.value = null
        installingTarget.value = target
    }

    fun onInstallFinished() {
        installingTarget.value = null
    }

    private fun refreshRepository(repositoryId: Long) {
        val token = ++activeRefreshToken
        refreshJob?.cancel()
        isLoadingVersions.value = true
        loadError.value = false
        refreshJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val succeeded = withContext(ioDispatcher) {
                    val repo = versionWithAssetsRepository.getRepositoryById(repositoryId)
                        ?: return@withContext false
                    releaseService.refreshVersionsForRepository(githubApi, repo)
                }
                if (activeRefreshToken == token) {
                    loadError.value = !succeeded
                    if (succeeded) {
                        autoFetchedRepoIds.add(repositoryId)
                        deviceConnectionManager.recheckUpdates()
                    }
                }
            } finally {
                if (activeRefreshToken == token) {
                    isLoadingVersions.value = false
                }
            }
        }
    }
}
