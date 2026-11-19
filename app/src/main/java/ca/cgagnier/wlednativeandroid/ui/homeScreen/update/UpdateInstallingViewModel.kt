package ca.cgagnier.wlednativeandroid.ui.homeScreen.update

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.api.ApiResponse
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateService
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
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path.Companion.toOkioPath
import java.io.File

private const val TAG = "UpdateInstallingViewModel"

class UpdateInstallingViewModel(
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
    private var activeUpdateService: DeviceUpdateService? = null

    private var _state = MutableStateFlow(UpdateInstallingState())
    val state = _state.asStateFlow()

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

    fun resetState() {
        // Reset on dismiss so we can try again next time
        val activeJob = synchronized(stateLock) {
            activeAttemptId++
            updateStarted = false
            activeUpdateService = null
            _state.value = UpdateInstallingState()
            val job = updateJob
            updateJob = null
            job
        }
        activeJob?.cancel()
    }

    fun startUpdate(device: DeviceWithState, version: VersionWithAssets, cacheDir: File) {
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

            val updateService = DeviceUpdateService(
                device = device,
                versionWithAssets = version,
                cacheDir = cacheDir.toOkioPath(),
                deviceApiFactory = deviceApiFactory,
                githubApi = githubApi,
                ioDispatcher = ioDispatcher,
            )
            if (!updateService.couldDetermineAsset()) {
                activeUpdateService = null
                _state.value = UpdateInstallingState(
                    canDismiss = true,
                    step = UpdateInstallingStep.NoCompatibleVersion,
                    assetName = updateService.getAssetName(),
                )
                return
            }
            activeUpdateService = updateService
            _state.value = UpdateInstallingState(
                canDismiss = true,
                step = UpdateInstallingStep.Starting,
            )
            updateJob = downloadAsset(updateService, attemptId)
        }
    }

    fun retryWithoutValidation() {
        val previousJob = synchronized(stateLock) {
            val step = state.value.step as? UpdateInstallingStep.Error ?: return
            val updateService = activeUpdateService ?: return
            if (!step.canRetryWithoutValidation) {
                return
            }
            val attemptId = ++activeAttemptId
            _state.value = UpdateInstallingState(
                canDismiss = true,
                step = UpdateInstallingStep.Starting,
                assetName = updateService.getAssetName(),
            )
            val oldJob = updateJob
            updateJob = downloadAsset(updateService, attemptId, skipValidation = true)
            oldJob
        }
        previousJob?.cancel()
    }

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

    private fun downloadAsset(updateService: DeviceUpdateService, attemptId: Long, skipValidation: Boolean = false) =
        viewModelScope.launch(ioDispatcher) {
            updateMutex.withLock {
                currentCoroutineContext().ensureActive()
                if (updateService.isAssetFileCached()) {
                    Log.d(TAG, "asset '${updateService.getAssetName()}' is already downloaded, reusing")
                    installUpdate(updateService, attemptId, skipValidation)
                    return@withLock
                }

                Log.d(TAG, "Downloading asset '${updateService.getAssetName()}'")

                updateService.downloadBinary(repositoryDao).collect { downloadState ->
                    when (downloadState) {
                        is DownloadState.Downloading -> {
                            Log.d(TAG, "File download Progress=${downloadState.progress}")
                            updateActiveState(attemptId) { previousState ->
                                previousState.copy(
                                    canDismiss = true,
                                    step = UpdateInstallingStep.Downloading(downloadState.progress),
                                    assetName = updateService.getAssetName(),
                                )
                            }
                        }

                        is DownloadState.Failed -> {
                            Log.e(TAG, "File download Fail: ${downloadState.error}")
                            updateActiveState(attemptId) { previousState ->
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
                            installUpdate(updateService, attemptId, skipValidation)
                            this.coroutineContext.job.cancel()
                        }
                    }
                }
            }
        }

    private suspend fun installUpdate(
        updateService: DeviceUpdateService,
        attemptId: Long,
        skipValidation: Boolean = false,
    ) {
        Log.d(TAG, "Uploading binary '${updateService.getAssetName()}' to device")
        updateActiveState(attemptId) { previousState ->
            previousState.copy(
                canDismiss = false,
                step = UpdateInstallingStep.Installing,
                assetName = updateService.getAssetName(),
            )
        }
        try {
            onSoftwareUpdateResponse(
                response = updateService.installUpdate(skipValidation = skipValidation),
                attemptId = attemptId,
                updateService = updateService,
                skipValidation = skipValidation,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Catch transport and I/O failures so they can be surfaced in the UI
            onSoftwareUpdateError(e, attemptId)
        }
    }

    private suspend fun onSoftwareUpdateResponse(
        response: ApiResponse<String>,
        attemptId: Long,
        updateService: DeviceUpdateService,
        skipValidation: Boolean,
    ) {
        if (response.isSuccessful) {
            currentCoroutineContext().ensureActive()
            val isActive = synchronized(stateLock) { attemptId == activeAttemptId }
            if (isActive) {
                deviceConnectionManager.completeFirmwareInstall(
                    device = updateService.device.device,
                    installedVersion = updateService.versionWithAssets.version,
                    repositoryDao = repositoryDao,
                )
                updateActiveState(attemptId) { previousState ->
                    previousState.copy(
                        canDismiss = true,
                        step = UpdateInstallingStep.Done,
                    )
                }
            }
        } else {
            val plainTextError = DeviceUpdateService.extractPlainTextError(response)
            val errorString = if (!plainTextError.isNullOrEmpty()) {
                "${response.code}: $plainTextError"
            } else {
                response.code.toString()
            }
            val canRetry = !skipValidation && DeviceUpdateService.isValidationFailure(response)
            Log.d(TAG, "OTA Failed, code ${response.code}, error $errorString")
            updateActiveState(attemptId) { previousState ->
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
}
