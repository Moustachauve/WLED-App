package ca.cgagnier.wlednativeandroid.ui.homeScreen.update

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.api.ApiResponse
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateService
import ca.cgagnier.wlednativeandroid.service.update.invoke
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

private const val TAG = "UpdateInstallingViewModel"

class UpdateInstallingViewModel(
    private val deviceRepository: DeviceRepository,
    private val repositoryDao: RepositoryDao,
    private val deviceApiFactory: DeviceApiFactory,
    private val githubApi: GithubApi,
    private val deviceConnectionManager: DeviceConnectionManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val stateLock = Any()
    private val updateMutex = Mutex()
    private var updateStarted = false
    private var updateJob: Job? = null
    private var activeAttemptId = 0L
    private var currentAttempt: UpdateAttempt? = null

    private var _state = MutableStateFlow(UpdateInstallingState())
    val state = _state.asStateFlow()

    private var _device: MutableStateFlow<DeviceWithState?> = MutableStateFlow(null)
    val device = _device.asStateFlow()

    private var _version: MutableStateFlow<VersionWithAssets?> = MutableStateFlow(null)
    val version = _version.asStateFlow()

    fun toggleErrorMessage() {
        if (state.value.step !is UpdateInstallingStep.Error) {
            return
        }
        _state.update { previousState ->
            val step = previousState.step as UpdateInstallingStep.Error
            previousState.copy(
                step = step.copy(showError = !step.showError),
            )
        }
    }

    fun retryWithoutValidation() {
        val previousJob = synchronized(stateLock) {
            val step = state.value.step as? UpdateInstallingStep.Error ?: return
            if (!step.canRetryWithoutValidation) {
                return
            }
            val previousAttempt = currentAttempt ?: return
            val retryAttempt = previousAttempt.copy(
                id = ++activeAttemptId,
                skipValidation = true,
            )
            currentAttempt = retryAttempt
            _state.value = UpdateInstallingState(
                canDismiss = true,
                step = UpdateInstallingStep.Starting,
                assetName = retryAttempt.updateService.getAssetName(),
            )
            val oldJob = updateJob
            updateJob = downloadAsset(retryAttempt)
            oldJob
        }
        previousJob?.cancel()
    }

    fun resetState() {
        // Cancel any in-flight download/install and reset state on dismiss
        val activeJob = synchronized(stateLock) {
            activeAttemptId++
            updateStarted = false
            currentAttempt = null
            _device.value = null
            _version.value = null
            _state.value = UpdateInstallingState()
            val job = updateJob
            updateJob = null
            job
        }
        activeJob?.cancel()
    }

    fun startUpdate(
        device: DeviceWithState,
        version: VersionWithAssets,
        cacheDir: File,
        selectedAsset: Asset? = null,
    ) {
        synchronized(stateLock) {
            if (updateStarted) {
                Log.w(
                    TAG,
                    "Update already started, ignoring startUpdate for ${device.device.originalName}",
                )
                return
            }
            updateStarted = true
            val attemptId = ++activeAttemptId
            Log.i(TAG, "startUpdate for device ${device.device.originalName}")
            _device.value = device
            _version.value = version

            val updateService = DeviceUpdateService(
                device = device,
                versionWithAssets = version,
                cacheDir = cacheDir,
                deviceApiFactory = deviceApiFactory,
                githubApi = githubApi,
                ioDispatcher = ioDispatcher,
                selectedAsset = selectedAsset,
            )
            if (!updateService.couldDetermineAsset()) {
                _state.value = UpdateInstallingState(
                    canDismiss = true,
                    step = UpdateInstallingStep.NoCompatibleVersion,
                    assetName = updateService.getAssetName(),
                )
                return
            }

            val attempt = UpdateAttempt(
                id = attemptId,
                device = device,
                version = version,
                updateService = updateService,
            )
            currentAttempt = attempt
            _state.value = UpdateInstallingState(
                canDismiss = true,
                step = UpdateInstallingStep.Starting,
            )
            updateJob = downloadAsset(attempt)
        }
    }

    private suspend fun isActiveAttempt(attemptId: Long): Boolean =
        currentCoroutineContext().isActive && synchronized(stateLock) { attemptId == activeAttemptId }

    private suspend fun updateActiveState(
        attemptId: Long,
        transform: (UpdateInstallingState) -> UpdateInstallingState,
    ): Boolean {
        // Abort cancelled coroutines before subsequent steps such as installUpdate()
        currentCoroutineContext().ensureActive()
        return synchronized(stateLock) {
            val isActiveAttempt = attemptId == activeAttemptId
            if (isActiveAttempt) {
                _state.update(transform)
            }
            isActiveAttempt
        }
    }

    private fun downloadAsset(attempt: UpdateAttempt) = viewModelScope.launch(ioDispatcher) {
        val updateService = attempt.updateService
        updateMutex.withLock {
            currentCoroutineContext().ensureActive()
            if (updateService.isAssetFileCached()) {
                Log.d(TAG, "asset '${updateService.getAssetName()}' is already downloaded, reusing")
                installUpdate(attempt)
                return@withLock
            }

            Log.d(TAG, "Downloading asset '${updateService.getAssetName()}'")

            updateService.downloadBinary(repositoryDao).collect { downloadState ->
                when (downloadState) {
                    is DownloadState.Downloading -> {
                        Log.d(TAG, "File download Progress=${downloadState.progress}")
                        updateActiveState(attempt.id) { previousState ->
                            previousState.copy(
                                canDismiss = true,
                                step = UpdateInstallingStep.Downloading(downloadState.progress),
                                assetName = updateService.getAssetName(),
                            )
                        }
                    }

                    is DownloadState.Failed -> {
                        Log.e(TAG, "File download Fail: ${downloadState.error}")
                        updateActiveState(attempt.id) { previousState ->
                            previousState.copy(
                                canDismiss = true,
                                step = UpdateInstallingStep.Error(
                                    downloadState.error.toString(),
                                ),
                                assetName = updateService.getAssetName(),
                            )
                        }
                        this.coroutineContext.job.cancel()
                    }

                    is DownloadState.Finished -> {
                        Log.d(TAG, "File download Finished")
                        installUpdate(attempt)
                        this.coroutineContext.job.cancel()
                    }
                }
            }
        }
    }

    private suspend fun installUpdate(attempt: UpdateAttempt) {
        val updateService = attempt.updateService
        Log.d(TAG, "Uploading binary '${updateService.getAssetName()}' to device")
        updateActiveState(attempt.id) { previousState ->
            previousState.copy(
                canDismiss = false,
                step = UpdateInstallingStep.Installing,
                assetName = updateService.getAssetName(),
            )
        }
        try {
            onSoftwareUpdateResponse(
                response = updateService.installUpdate(attempt.skipValidation),
                attempt = attempt,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Catch transport and I/O failures so they can be surfaced in the UI
            onSoftwareUpdateError(e, attempt.id)
        }
    }

    private suspend fun onSoftwareUpdateResponse(response: ApiResponse<String>, attempt: UpdateAttempt) {
        if (response.isSuccessful) {
            if (!isActiveAttempt(attempt.id)) return
            updateDeviceUpdated(attempt)
            updateActiveState(attempt.id) { previousState ->
                previousState.copy(
                    canDismiss = true,
                    step = UpdateInstallingStep.Done,
                )
            }
        } else {
            Log.d(TAG, "OTA Failed, code ${response.code}")
            val plainError = DeviceUpdateService.extractPlainTextError(response).orEmpty()
            val errorString = if (plainError.isNotEmpty()) {
                "${response.code}: $plainError"
            } else {
                response.code.toString()
            }
            val canRetry = !attempt.skipValidation && DeviceUpdateService.isValidationFailure(response)
            Log.d(TAG, "OTA Failed onResponse, error $errorString, canRetry=$canRetry")
            updateActiveState(attempt.id) { previousState ->
                previousState.copy(
                    canDismiss = true,
                    step = UpdateInstallingStep.Error(
                        error = errorString,
                        showError = canRetry,
                        canRetryWithoutValidation = canRetry,
                    ),
                )
            }
        }
    }

    private suspend fun onSoftwareUpdateError(e: Exception, attemptId: Long) {
        Log.d(TAG, "OTA Failed, call failed")
        Log.e(TAG, e.toString())
        val errorString = e.toString()
        Log.d(TAG, "OTA Failed onFailure, error $errorString")
        updateActiveState(attemptId) { previousState ->
            previousState.copy(
                canDismiss = true,
                step = UpdateInstallingStep.Error(errorString),
            )
        }
    }

    /**
     * Updates the properties of a device after a software update was done.
     */
    private suspend fun updateDeviceUpdated(attempt: UpdateAttempt) {
        val mac = attempt.device.device.macAddress
        val previousRepoId = attempt.device.device.repositoryId
        val targetRepoId = attempt.version.version.repositoryId
        var repoOwnerAndRepo: String? = null

        // Apply optimistic in-memory state before Room invalidation emissions and re-apply in
        // finally once repository lookup and targeted SQLite updates complete.
        applyInMemoryStateIfActive(attempt, repoOwnerAndRepo)

        try {
            repoOwnerAndRepo = resolveRepositoryOwnerAndRepo(targetRepoId)
            if (!isActiveAttempt(attempt.id)) return
            if (targetRepoId != previousRepoId) {
                deviceRepository.updateRepositoryId(mac, targetRepoId)
            }
            Log.d(TAG, "Resetting skipUpdateTag")
            deviceRepository.updateSkipUpdateTag(mac, "")
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Log persistence failure without marking an already-flashed OTA update as failed
            Log.e(TAG, "Failed to update device metadata after install", e)
        } finally {
            applyInMemoryStateIfActive(attempt, repoOwnerAndRepo)
        }
    }

    private suspend fun applyInMemoryStateIfActive(attempt: UpdateAttempt, repoOwnerAndRepo: String?) {
        if (!isActiveAttempt(attempt.id)) return
        deviceConnectionManager.markUpdateInstalled(
            macAddress = attempt.device.device.macAddress,
            installedVersionTag = attempt.version.version.tagName,
            repositoryId = attempt.version.version.repositoryId,
            repositoryOwnerAndRepo = repoOwnerAndRepo,
        )
    }

    private suspend fun resolveRepositoryOwnerAndRepo(repositoryId: Long): String? = try {
        repositoryDao.getRepositoryById(repositoryId)?.ownerAndRepo
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        // Fall back to existing repository metadata if lookup fails
        Log.w(TAG, "Failed to look up repository $repositoryId after install", e)
        null
    }

    private data class UpdateAttempt(
        val id: Long,
        val device: DeviceWithState,
        val version: VersionWithAssets,
        val updateService: DeviceUpdateService,
        val skipValidation: Boolean = false,
    )
}
