package ca.cgagnier.wlednativeandroid.ui.homeScreen.update

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var activeTarget: UpdateTarget? = null
    private var updateJob: Job? = null
    private var activeAttemptId = 0L
    private var currentAttempt: UpdateAttempt? = null

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
            updateJob = launchAttempt(retryAttempt, oldJob)
            oldJob
        }
        previousJob?.cancel()
    }

    fun resetState() {
        // Cancel any in-flight attempt; keep updateJob reference so a follow-up startUpdate awaits cleanup.
        val activeJob = synchronized(stateLock) {
            activeAttemptId++
            activeTarget = null
            currentAttempt = null
            _state.value = UpdateInstallingState()
            updateJob
        }
        activeJob?.cancel()
    }

    fun startUpdate(
        device: DeviceWithState,
        version: VersionWithAssets,
        cacheDir: File,
        selectedAsset: Asset? = null,
    ) {
        val target = UpdateTarget(
            macAddress = device.device.macAddress,
            repositoryId = version.version.repositoryId,
            tagName = version.version.tagName,
            selectedAssetName = selectedAsset?.name,
        )
        val previousJob = synchronized(stateLock) {
            if (activeTarget == target) {
                Log.w(TAG, "Update already started, ignoring startUpdate for ${device.device.originalName}")
                return
            }
            activeTarget = target
            val attemptId = ++activeAttemptId
            Log.i(TAG, "startUpdate for device ${device.device.originalName}")

            val updateService = DeviceUpdateService(
                device = device,
                versionWithAssets = version,
                cacheDir = cacheDir,
                deviceApiFactory = deviceApiFactory,
                githubApi = githubApi,
                ioDispatcher = ioDispatcher,
                selectedAsset = selectedAsset,
            )
            val oldJob = updateJob
            if (!updateService.couldDetermineAsset()) {
                currentAttempt = null
                _state.value = UpdateInstallingState(
                    canDismiss = true,
                    step = UpdateInstallingStep.NoCompatibleVersion,
                    assetName = updateService.getAssetName(),
                )
                updateJob = null
            } else {
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
                updateJob = launchAttempt(attempt, oldJob)
            }
            oldJob
        }
        previousJob?.cancel()
    }

    private suspend fun isActiveAttempt(attemptId: Long): Boolean =
        currentCoroutineContext().isActive && synchronized(stateLock) { attemptId == activeAttemptId }

    private suspend fun updateActiveState(
        attemptId: Long,
        transform: (UpdateInstallingState) -> UpdateInstallingState,
    ): Boolean {
        currentCoroutineContext().ensureActive()
        return synchronized(stateLock) {
            val isActiveAttempt = attemptId == activeAttemptId
            if (isActiveAttempt) {
                _state.update(transform)
            }
            isActiveAttempt
        }
    }

    private fun launchAttempt(attempt: UpdateAttempt, previousJob: Job?) = viewModelScope.launch(ioDispatcher) {
        if (previousJob != null) {
            withContext(NonCancellable) {
                previousJob.cancelAndJoin()
            }
        }
        currentCoroutineContext().ensureActive()

        val updateService = attempt.updateService
        if (updateService.isAssetFileCached()) {
            Log.d(TAG, "asset '${updateService.getAssetName()}' is already downloaded, reusing")
            installUpdate(attempt)
            return@launch
        }

        Log.d(TAG, "Downloading asset '${updateService.getAssetName()}'")
        updateService.downloadBinary(repositoryDao).collect { downloadState ->
            when (downloadState) {
                is DownloadState.Downloading -> {
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
                            step = UpdateInstallingStep.Error(downloadState.error.toString()),
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
            Log.e(TAG, "OTA install failed", e)
            updateActiveState(attempt.id) { previousState ->
                previousState.copy(
                    canDismiss = true,
                    step = UpdateInstallingStep.Error(e.toString()),
                )
            }
        }
    }

    private suspend fun onSoftwareUpdateResponse(response: ApiResponse<String>, attempt: UpdateAttempt) {
        if (response.isSuccessful) {
            if (!isActiveAttempt(attempt.id)) return
            deviceConnectionManager.completeFirmwareInstall(
                device = attempt.device.device,
                installedVersion = attempt.version.version,
                repositoryDao = repositoryDao,
            )
            updateActiveState(attempt.id) { previousState ->
                previousState.copy(
                    canDismiss = true,
                    step = UpdateInstallingStep.Done,
                )
            }
        } else {
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

    private data class UpdateTarget(
        val macAddress: String,
        val repositoryId: Long,
        val tagName: String,
        val selectedAssetName: String?,
    )

    private data class UpdateAttempt(
        val id: Long,
        val device: DeviceWithState,
        val version: VersionWithAssets,
        val updateService: DeviceUpdateService,
        val skipValidation: Boolean = false,
    )
}
