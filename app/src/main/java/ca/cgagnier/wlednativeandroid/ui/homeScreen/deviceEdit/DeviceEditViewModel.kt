package ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "DeviceEditViewModel"

/**
 * ViewModel for the device edit screen.
 *
 * Manages update-related dialog state, device property mutations,
 * language switching, and update-check operations. Exposes a single [uiState]
 * flow consumed by the UI composable.
 */
@Suppress("LongParameterList") // DI constructor requires multiple dependencies.
class DeviceEditViewModel(
    private val deviceRepository: DeviceRepository,
    private val versionWithAssetsRepository: VersionWithAssetsRepository,
    private val githubApi: GithubApi,
    private val releaseService: ReleaseService,
    private val deviceConnectionManager: DeviceConnectionManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val repositoryIdFlow = MutableStateFlow(Repository.DEFAULT_ID)
    private val updateDetailsVersion = MutableStateFlow<VersionWithAssets?>(null)
    private val updateDisclaimerVersion = MutableStateFlow<VersionWithAssets?>(null)
    private val updateInstallVersion = MutableStateFlow<VersionWithAssets?>(null)
    private val installTargetBranch = MutableStateFlow<Branch?>(null)
    private val isCheckingUpdates = MutableStateFlow(false)
    private var languageSwitchJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val latestBetaVersion = repositoryIdFlow.flatMapLatest { repoId ->
        versionWithAssetsRepository.getLatestBetaVersionWithAssetsFlow(repoId)
    }

    private data class DialogState(
        val details: VersionWithAssets?,
        val disclaimer: VersionWithAssets?,
        val install: VersionWithAssets?,
        val targetBranch: Branch?,
    )

    private val dialogStateFlow = combine(
        updateDetailsVersion,
        updateDisclaimerVersion,
        updateInstallVersion,
        installTargetBranch,
    ) { details, disclaimer, install, targetBranch ->
        DialogState(details, disclaimer, install, targetBranch)
    }

    /**
     * Single combined UI state for the device edit screen.
     *
     * The [DeviceEditUiState.updateTag] field is always `null` here because
     * the update tag comes from [DeviceWithState.updateVersionTag], which
     * is owned by the caller and merged in the composable layer.
     */
    val uiState: StateFlow<DeviceEditUiState> = combine(
        isCheckingUpdates,
        latestBetaVersion,
        dialogStateFlow,
    ) { isChecking, betaVersion, dialogState ->
        DeviceEditUiState(
            isCheckingUpdates = isChecking,
            latestBetaVersion = betaVersion,
            updateDetailsVersion = dialogState.details,
            updateDisclaimerVersion = dialogState.disclaimer,
            updateInstallVersion = dialogState.install,
            installTargetBranch = dialogState.targetBranch,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = DeviceEditUiState(),
    )

    /** Observes beta versions for [repositoryId]. */
    fun loadRepository(repositoryId: Long) {
        repositoryIdFlow.value = repositoryId
    }

    /** Persists a new custom name for the [device]. */
    fun updateCustomName(device: Device, name: String) = viewModelScope.launch(ioDispatcher) {
        Log.d(TAG, "updateCustomName: $name")
        deviceRepository.updateCustomName(device.macAddress, name)
    }

    /** Toggles the hidden-from-list flag on [device]. */
    fun updateDeviceHidden(device: Device, isHidden: Boolean) = viewModelScope.launch(ioDispatcher) {
        Log.d(TAG, "updateDeviceHidden: ${device.originalName}, isHidden: $isHidden")
        deviceRepository.updateIsHidden(device.macAddress, isHidden)
    }

    /**
     * Prepares a firmware language switch ([branch]) for [deviceWithState] and starts
     * reinstalling the device's current firmware version in the selected language.
     * Persists [branch] to Room only after the OTA installation succeeds.
     */
    fun updateDeviceBranch(deviceWithState: DeviceWithState, branch: Branch): Job {
        languageSwitchJob?.cancel()
        installTargetBranch.value = null
        return viewModelScope.launch(ioDispatcher) {
            val currentVersion = resolveEligibleLanguageSwitchVersion(deviceWithState, branch) ?: return@launch
            val device = deviceWithState.device
            ensureActive()
            Log.d(TAG, "updateDeviceBranch: ${device.originalName}, languageBranch: $branch")
            installTargetBranch.value = branch

            var version = versionWithAssetsRepository.getVersionByTag(device.repositoryId, currentVersion)
            if (version == null) {
                releaseService.refreshVersionsForDevice(githubApi, device.copy(branch = branch))
                version = versionWithAssetsRepository.getVersionByTag(device.repositoryId, currentVersion)
            }

            ensureActive()
            if (version != null) {
                updateInstallVersion.value = version
            } else {
                installTargetBranch.value = null
            }
        }.also { languageSwitchJob = it }
    }

    private fun resolveEligibleLanguageSwitchVersion(deviceWithState: DeviceWithState, targetBranch: Branch): String? {
        val currentBranch = if (deviceWithState.device.branch == Branch.BETA) Branch.BETA else Branch.STABLE
        if (!deviceWithState.isOnline || currentBranch == targetBranch) {
            return null
        }
        val info = deviceWithState.stateInfo?.info?.takeIf(ReleaseService::isDeviceEligibleForUpdate) ?: return null
        return info.version?.takeIf { it.isNotEmpty() }
    }

    /** Fetches and shows the release details for a specific [version] tag. */
    fun showUpdateDetails(repositoryId: Long, version: String) = viewModelScope.launch(ioDispatcher) {
        updateDetailsVersion.value = versionWithAssetsRepository.getVersionByTag(repositoryId, version)
    }

    /** Dismisses the update-details dialog. */
    fun hideUpdateDetails() {
        updateDetailsVersion.value = null
    }

    /** Marks the given [version] as skipped for [device] and closes the dialog. */
    fun skipUpdate(device: Device, version: VersionWithAssets) = viewModelScope.launch(ioDispatcher) {
        Log.d(TAG, "Saving skipUpdateTag")
        deviceRepository.updateSkipUpdateTag(device.macAddress, version.version.tagName)
        updateDetailsVersion.value = null
    }

    /** Shows the beta update disclaimer dialog for the given [version]. */
    fun showUpdateDisclaimer(version: VersionWithAssets) {
        updateDisclaimerVersion.value = version
    }

    /** Dismisses the update disclaimer dialog. */
    fun hideUpdateDisclaimer() {
        updateDisclaimerVersion.value = null
    }

    /** Begins the OTA install flow for [version]. */
    fun startUpdateInstall(version: VersionWithAssets) {
        languageSwitchJob?.cancel()
        installTargetBranch.value = null
        updateInstallVersion.value = version
    }

    /**
     * Called when the OTA install dialog is dismissed or completed.
     * Persists any pending language switch and updates device state when [wasSuccessful] is true.
     */
    fun stopUpdateInstall(
        device: DeviceWithState? = null,
        version: VersionWithAssets? = updateInstallVersion.value,
        wasSuccessful: Boolean = false,
    ) {
        val targetBranch = installTargetBranch.value
        installTargetBranch.value = null
        updateInstallVersion.value = null
        if (!wasSuccessful || device == null || version == null) {
            return
        }
        if (targetBranch != null) {
            viewModelScope.launch(ioDispatcher) {
                deviceRepository.updateBranch(device.device.macAddress, targetBranch)
            }
        }
        deviceConnectionManager.markUpdateInstalled(
            macAddress = device.device.macAddress,
            installedVersionTag = version.version.tagName,
            installedBranch = targetBranch,
        )
    }

    /**
     * Clears any skipped-update tag on [device] and refreshes available
     * versions from GitHub.
     */
    fun checkForUpdates(device: Device) {
        viewModelScope.launch(ioDispatcher) {
            isCheckingUpdates.value = true
            deviceRepository.updateSkipUpdateTag(device.macAddress, "")
            try {
                releaseService.refreshVersionsForDevice(githubApi, device)
                deviceConnectionManager.recheckUpdates()
            } finally {
                isCheckingUpdates.value = false
            }
        }
    }
}
