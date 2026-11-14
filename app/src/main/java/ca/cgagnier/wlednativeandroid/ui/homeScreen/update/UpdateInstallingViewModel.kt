package ca.cgagnier.wlednativeandroid.ui.homeScreen.update

import android.util.Log
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
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
import okio.Path.Companion.toOkioPath
import java.io.File
import java.util.regex.Pattern

private const val TAG = "UpdateInstallingViewModel"

class UpdateInstallingViewModel(
    private val deviceRepository: DeviceRepository,
    private val repositoryDao: RepositoryDao,
    private val deviceApiFactory: DeviceApiFactory,
    private val githubApi: GithubApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val stateLock = Any()
    private val updateMutex = Mutex()
    private var updateStarted = false
    private var updateJob: Job? = null
    private var activeAttemptId = 0L

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
                step = UpdateInstallingStep.Error(
                    step.error,
                    !step.showError,
                ),
            )
        }
    }

    fun resetState() {
        // Reset on dismiss so we can try again next time
        val activeJob = synchronized(stateLock) {
            activeAttemptId++
            updateStarted = false
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
            _device.value = device
            _version.value = version

            val updateService = DeviceUpdateService(
                device = device,
                versionWithAssets = version,
                cacheDir = cacheDir.toOkioPath(),
                deviceApiFactory = deviceApiFactory,
                githubApi = githubApi,
                ioDispatcher = ioDispatcher,
            )
            if (!updateService.couldDetermineAsset()) {
                _state.value = UpdateInstallingState(
                    canDismiss = true,
                    step = UpdateInstallingStep.NoCompatibleVersion,
                    assetName = updateService.getAssetName(),
                )
                return
            }
            _state.value = UpdateInstallingState(
                canDismiss = true,
                step = UpdateInstallingStep.Starting,
            )
            updateJob = downloadAsset(updateService, attemptId)
        }
    }

    private suspend fun updateActiveState(
        attemptId: Long,
        transform: (UpdateInstallingState) -> UpdateInstallingState,
    ) {
        val context = currentCoroutineContext()
        context.ensureActive()
        synchronized(stateLock) {
            if (context.isActive && attemptId == activeAttemptId) {
                _state.update(transform)
            }
        }
    }

    private fun downloadAsset(updateService: DeviceUpdateService, attemptId: Long) =
        viewModelScope.launch(ioDispatcher) {
            updateMutex.withLock {
                currentCoroutineContext().ensureActive()
                if (updateService.isAssetFileCached()) {
                    Log.d(TAG, "asset '${updateService.getAssetName()}' is already downloaded, reusing")
                    installUpdate(updateService, attemptId)
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
                            installUpdate(updateService, attemptId)
                            this.coroutineContext.job.cancel()
                        }
                    }
                }
            }
        }

    private suspend fun installUpdate(updateService: DeviceUpdateService, attemptId: Long) {
        Log.d(TAG, "Uploading binary '${updateService.getAssetName()}' to device")
        updateActiveState(attemptId) { previousState ->
            previousState.copy(
                canDismiss = false,
                step = UpdateInstallingStep.Installing,
                assetName = updateService.getAssetName(),
            )
        }
        try {
            onSoftwareUpdateResponse(updateService.installUpdate(), attemptId)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Catch transport and I/O failures so they can be surfaced in the UI
            onSoftwareUpdateError(e, attemptId)
        }
    }

    private suspend fun onSoftwareUpdateResponse(response: ApiResponse<String>, attemptId: Long) {
        if (response.isSuccessful) {
            updateActiveState(attemptId) { previousState ->
                previousState.copy(
                    canDismiss = true,
                    step = UpdateInstallingStep.Done,
                )
            }
            updateDeviceUpdated()
        } else {
            Log.d(TAG, "OTA Failed, code ${response.code}")
            val errorString = "${response.code}: ${getHtmlErrorMessage(response)}"
            Log.d(TAG, "OTA Failed onResponse, error $errorString")
            updateActiveState(attemptId) { previousState ->
                previousState.copy(
                    canDismiss = true,
                    step = UpdateInstallingStep.Error(errorString),
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
    private fun updateDeviceUpdated() = viewModelScope.launch(ioDispatcher) {
        val device = device.value ?: return@launch
        Log.d(TAG, "Resetting skipUpdateTag")
        deviceRepository.updateSkipUpdateTag(device.device.macAddress, "")
    }

    private fun getHtmlErrorMessage(response: ApiResponse<String>): String {
        val html = response.body ?: response.errorBody ?: ""
        // Extract the body content to ignore <head> (title, scripts, styles)
        val bodyMatcher = HTML_BODY_MATCHER.matcher(html)
        var bodyContent = if (bodyMatcher.find()) bodyMatcher.group(1) else html
        // Remove <button>, <script>, and <style> blocks entirely so their text (e.g. "Back") doesn't appear
        bodyContent = JUNK_TAG_PATTERN.matcher(bodyContent).replaceAll("")
        // Use Android's Html class to strip remaining tags (<h2>, <br>) and decode entities
        val plainText =
            HtmlCompat.fromHtml(bodyContent, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
        // Normalize whitespace (remove newlines from <br>/<h2> and extra spaces)
        return plainText.trim().replace(Regex("\\s+"), " ")
    }

    companion object {
        private val HTML_BODY_MATCHER =
            Pattern.compile("<body[^>]*>(.*?)</body>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        private val JUNK_TAG_PATTERN = Pattern.compile(
            "<(button|script|style)[^>]*>.*?</\\1>",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL,
        )
    }
}
