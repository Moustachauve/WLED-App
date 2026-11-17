package ca.cgagnier.wlednativeandroid.service.update

import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.shared.SynchronizedObject
import ca.cgagnier.wlednativeandroid.shared.synchronized
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
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
import kotlinx.coroutines.launch

/**
 * Immutable shared snapshot of firmware browser state consumed by both Android and iOS ViewModels.
 */
data class FirmwareBrowserState(
    val device: DeviceWithState? = null,
    val repositories: List<Repository> = emptyList(),
    val selectedRepositoryId: Long? = null,
    val selectedBranch: Branch = Branch.STABLE,
    val versions: List<VersionWithAssets> = emptyList(),
    val installedTags: Set<String> = emptySet(),
    val isLoadingVersions: Boolean = true,
    val loadError: Boolean = false,
) {
    val selectedRepository: Repository?
        get() = repositories.firstOrNull { it.id == selectedRepositoryId }
}

internal class FirmwareBrowserDataSource(
    val repositoriesFlow: Flow<List<Repository>>,
    val observeVersionsForRepository: (Long) -> Flow<List<VersionWithAssets>>,
    val refreshRepositoryVersions: suspend (Long) -> Boolean,
    val onVersionsRefreshed: () -> Unit,
)

/**
 * Shared coordinator managing repository/channel selection, cached release observation,
 * one-shot empty-cache GitHub refreshes, and race-safe source switching across Android and iOS.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirmwareBrowserStateHolder internal constructor(
    private val macAddress: String,
    private val dataSource: FirmwareBrowserDataSource,
    allDevicesWithState: StateFlow<List<DeviceWithState>>,
    coroutineScope: CoroutineScope,
    initialDevice: Device? = null,
) {
    constructor(
        macAddress: String,
        githubApi: GithubApi,
        releaseService: ReleaseService,
        deviceConnectionManager: DeviceConnectionManager,
        coroutineScope: CoroutineScope,
        initialDevice: Device? = null,
    ) : this(
        macAddress = macAddress,
        dataSource = createDataSource(
            githubApi = githubApi,
            releaseService = releaseService,
            deviceConnectionManager = deviceConnectionManager,
        ),
        allDevicesWithState = deviceConnectionManager.allDevicesWithState,
        coroutineScope = coroutineScope,
        initialDevice = initialDevice,
    )
    private data class FilterState(
        val repositoryId: Long?,
        val branch: Branch,
        val versions: List<VersionWithAssets>,
        val hasLoadedCachedVersions: Boolean,
    )

    private data class RefreshStatus(val isLoading: Boolean = false, val loadError: Boolean = false)

    private val scope = CoroutineScope(
        coroutineScope.coroutineContext + SupervisorJob(coroutineScope.coroutineContext[Job]),
    )

    private val selectedRepositoryId = MutableStateFlow(initialDevice?.repositoryId)
    private val selectedBranch = MutableStateFlow(
        if (initialDevice?.branch == Branch.BETA) Branch.BETA else Branch.STABLE,
    )
    private val refreshStatus = MutableStateFlow(RefreshStatus())

    private val lock = SynchronizedObject()
    private val autoFetchedRepoIds = mutableSetOf<Long>()
    private val failedRepoIds = mutableSetOf<Long>()
    private var initializedBranch = initialDevice != null
    private var refreshJob: Job? = null
    private var activeRefreshToken = 0L

    private val deviceFlow = allDevicesWithState
        .map { devices -> devices.firstOrNull { it.device.macAddress == macAddress } }
        .distinctUntilChanged()
        .onEach { device ->
            if (device != null) {
                synchronized(lock) {
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
        }

    private val deviceAndReposFlow = combine(
        deviceFlow,
        dataSource.repositoriesFlow,
    ) { device, allRepos ->
        val currentRepoId = device?.device?.repositoryId ?: initialDevice?.repositoryId ?: Repository.DEFAULT_ID
        device to ReleaseService.getSelectableRepositories(allRepos, currentRepoId)
    }.onEach { (_, repositories) ->
        if (repositories.isNotEmpty()) {
            val jobToCancel = synchronized(lock) {
                val currentSelected = selectedRepositoryId.value
                if (currentSelected != null && repositories.none { it.id == currentSelected }) {
                    selectRepositoryLocked(repositories.first().id)
                } else {
                    null
                }
            }
            jobToCancel?.cancel()
        }
    }

    private val repoVersionsFlow = selectedRepositoryId
        .flatMapLatest { repoId ->
            if (repoId == null) {
                flowOf(null to null)
            } else {
                dataSource.observeVersionsForRepository(repoId)
                    .onEach { versions ->
                        if (versions.isEmpty()) {
                            refreshRepository(repoId, onlyIfAutoFetchNeeded = true)
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

    val state: StateFlow<FirmwareBrowserState> = combine(
        deviceAndReposFlow,
        filterStateFlow,
        refreshStatus,
    ) { (device, repositories), filterState, status ->
        val installedVersion = device?.stateInfo?.info?.version
        val installedTags = if (device == null || installedVersion.isNullOrBlank()) {
            emptySet()
        } else {
            filterState.versions
                .asSequence()
                .filter { it.matchesInstalledVersion(installedVersion, device.device.repositoryId) }
                .mapTo(mutableSetOf()) { it.version.tagName }
        }
        FirmwareBrowserState(
            device = device,
            repositories = repositories,
            selectedRepositoryId = filterState.repositoryId,
            selectedBranch = filterState.branch,
            versions = filterState.versions,
            installedTags = installedTags,
            isLoadingVersions = status.isLoading || !filterState.hasLoadedCachedVersions,
            loadError = status.loadError,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = FirmwareBrowserState(
            selectedRepositoryId = initialDevice?.repositoryId,
            selectedBranch = if (initialDevice?.branch == Branch.BETA) Branch.BETA else Branch.STABLE,
        ),
    )

    fun selectRepository(repositoryId: Long) {
        synchronized(lock) {
            selectRepositoryLocked(repositoryId)
        }?.cancel()
    }

    private fun selectRepositoryLocked(repositoryId: Long): Job? {
        if (selectedRepositoryId.value == repositoryId) return null
        activeRefreshToken++
        val previousJob = refreshJob
        refreshJob = null
        refreshStatus.value = RefreshStatus(loadError = repositoryId in failedRepoIds)
        selectedRepositoryId.value = repositoryId
        return previousJob
    }

    fun selectBranch(branch: Branch) {
        synchronized(lock) {
            initializedBranch = true
            selectedBranch.value = branch
        }
    }

    fun refreshSelectedRepository() {
        val repoId = selectedRepositoryId.value ?: return
        refreshRepository(repoId)
    }

    fun close() {
        synchronized(lock) {
            activeRefreshToken++
            refreshJob = null
        }
        scope.cancel()
    }

    private fun refreshRepository(repositoryId: Long, onlyIfAutoFetchNeeded: Boolean = false) {
        val (token, previousJob) = synchronized(lock) {
            if (selectedRepositoryId.value != repositoryId) {
                return
            }
            if (onlyIfAutoFetchNeeded && (refreshStatus.value.isLoading || repositoryId in autoFetchedRepoIds)) {
                return
            }
            val nextToken = ++activeRefreshToken
            val oldJob = refreshJob
            refreshJob = null
            refreshStatus.value = RefreshStatus(isLoading = true, loadError = false)
            nextToken to oldJob
        }
        previousJob?.cancel()
        val launchedJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            executeRefresh(repositoryId, token)
        }
        synchronized(lock) {
            if (activeRefreshToken == token) {
                if (launchedJob.isActive) {
                    refreshJob = launchedJob
                }
            } else {
                launchedJob.cancel()
            }
        }
    }

    private suspend fun executeRefresh(repositoryId: Long, token: Long) {
        try {
            val succeeded = try {
                dataSource.refreshRepositoryVersions(repositoryId)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Repository/Room failures must surface as loadError rather than crashing the UI scope.
                logger.w(e) { "Failed to refresh versions for repository $repositoryId" }
                false
            }
            val notifyRecheck = synchronized(lock) {
                if (activeRefreshToken != token) {
                    false
                } else {
                    autoFetchedRepoIds.add(repositoryId)
                    if (succeeded) {
                        failedRepoIds.remove(repositoryId)
                    } else {
                        failedRepoIds.add(repositoryId)
                    }
                    refreshStatus.value = refreshStatus.value.copy(loadError = !succeeded)
                    succeeded
                }
            }
            if (notifyRecheck) {
                dataSource.onVersionsRefreshed()
            }
        } finally {
            synchronized(lock) {
                if (activeRefreshToken == token) {
                    refreshStatus.value = refreshStatus.value.copy(isLoading = false)
                }
            }
        }
    }

    private companion object {
        private val logger = Logger.withTag("FirmwareBrowserStateHolder")

        fun createDataSource(
            githubApi: GithubApi,
            releaseService: ReleaseService,
            deviceConnectionManager: DeviceConnectionManager,
        ) = FirmwareBrowserDataSource(
            repositoriesFlow = releaseService.observeAllRepositories(),
            observeVersionsForRepository = releaseService::observeVersionsForRepository,
            refreshRepositoryVersions = { repositoryId ->
                releaseService.refreshVersionsForRepositoryId(githubApi, repositoryId)
            },
            onVersionsRefreshed = deviceConnectionManager::recheckUpdates,
        )
    }
}
