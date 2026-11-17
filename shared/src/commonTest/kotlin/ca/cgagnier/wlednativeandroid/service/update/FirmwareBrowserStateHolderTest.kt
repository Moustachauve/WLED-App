package ca.cgagnier.wlednativeandroid.service.update

import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FirmwareBrowserStateHolderTest {

    private val wledRepo = Repository(
        id = 1L,
        ownerAndRepo = "wled/WLED",
        name = "WLED",
        description = "Official WLED",
        htmlUrl = "https://github.com/wled/WLED",
        isDefault = true,
        isEnabled = true,
    )
    private val quinledRepo = Repository(
        id = 2L,
        ownerAndRepo = "intermittech/QuinLED-Firmware",
        name = "QuinLED",
        description = "QuinLED Firmware",
        htmlUrl = "https://github.com/intermittech/QuinLED-Firmware",
        isDefault = false,
        isEnabled = true,
    )

    private val repositoriesFlow = MutableStateFlow(listOf(wledRepo, quinledRepo))
    private val versionFlows = mapOf(
        1L to MutableStateFlow<List<VersionWithAssets>>(emptyList()),
        2L to MutableStateFlow<List<VersionWithAssets>>(emptyList()),
    )
    private val devicesFlow = MutableStateFlow<List<DeviceWithState>>(emptyList())

    private fun TestScope.createHolder(
        initialDevice: Device? = null,
        onRecheckUpdates: () -> Unit = {},
        refreshHandler: suspend (Long) -> Boolean = { true },
    ): FirmwareBrowserStateHolder {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        coroutineContext.job.invokeOnCompletion { scope.cancel() }
        return FirmwareBrowserStateHolder(
            macAddress = MAC_ADDRESS,
            dataSource = FirmwareBrowserDataSource(
                repositoriesFlow = repositoriesFlow,
                observeVersionsForRepository = { repoId -> versionFlows.getValue(repoId) },
                refreshRepositoryVersions = refreshHandler,
                onVersionsRefreshed = onRecheckUpdates,
            ),
            allDevicesWithState = devicesFlow,
            coroutineScope = scope,
            initialDevice = initialDevice,
        )
    }

    private fun makeDevice(
        repositoryId: Long = 1L,
        branch: Branch = Branch.STABLE,
        installedVersion: String = "0.14.4",
    ): DeviceWithState = DeviceWithState(
        device = Device(
            macAddress = MAC_ADDRESS,
            address = "192.168.1.50",
            originalName = "Desk WLED",
            branch = branch,
            repositoryId = repositoryId,
        ),
        websocketStatus = WebsocketStatus.CONNECTED,
        stateInfo = DeviceStateInfo.createMock(version = installedVersion),
    )

    private fun makeVersion(repoId: Long, tagName: String, isPrerelease: Boolean = false) = VersionWithAssets(
        version = Version(
            repositoryId = repoId,
            tagName = tagName,
            name = tagName,
            description = "",
            isPrerelease = isPrerelease,
            publishedDate = "2025-05-01T00:00:00Z",
            htmlUrl = "",
        ),
        assets = emptyList(),
    )

    @Test
    fun initializesDefaultsAndComputesInstalledTags() = runTest {
        devicesFlow.value = listOf(makeDevice(repositoryId = 2L, branch = Branch.BETA, installedVersion = "0.15.0-b1"))
        versionFlows.getValue(2L).value = listOf(
            makeVersion(2L, "v0.15.0-b1", isPrerelease = true),
            makeVersion(2L, "0.15.0", isPrerelease = false),
        )
        val holder = createHolder()
        advanceUntilIdle()

        assertEquals(2L, holder.state.value.selectedRepositoryId)
        assertEquals(Branch.BETA, holder.state.value.selectedBranch)
        assertEquals(listOf("v0.15.0-b1"), holder.state.value.versions.map { it.version.tagName })
        assertEquals(setOf("v0.15.0-b1"), holder.state.value.installedTags)
    }

    @Test
    fun selectBranchRefiltersVersionsToSelectedChannel() = runTest {
        devicesFlow.value = listOf(makeDevice(repositoryId = 2L, branch = Branch.BETA))
        versionFlows.getValue(2L).value = listOf(
            makeVersion(2L, "v0.15.0-b1", isPrerelease = true),
            makeVersion(2L, "0.15.0", isPrerelease = false),
        )
        val holder = createHolder()
        advanceUntilIdle()

        holder.selectBranch(Branch.STABLE)
        advanceUntilIdle()

        assertEquals(listOf("0.15.0"), holder.state.value.versions.map { it.version.tagName })
    }

    @Test
    fun emptyCacheAutoRefreshesSelectedSourceAndReportsFailure() = runTest {
        devicesFlow.value = listOf(makeDevice(repositoryId = 1L))
        var refreshCalls = 0
        var recheckCount = 0
        val holder = createHolder(
            onRecheckUpdates = { recheckCount++ },
            refreshHandler = {
                refreshCalls++
                false
            },
        )
        advanceUntilIdle()

        assertTrue(holder.state.value.loadError)
        assertEquals(1, refreshCalls)
        assertEquals(0, recheckCount)
    }

    @Test
    fun refreshSelectedRepositoryRetriesFailedSourceAndRechecksUpdates() = runTest {
        devicesFlow.value = listOf(makeDevice(repositoryId = 1L))
        var refreshCalls = 0
        var recheckCount = 0
        val holder = createHolder(
            onRecheckUpdates = { recheckCount++ },
            refreshHandler = {
                refreshCalls++
                refreshCalls == 2
            },
        )
        advanceUntilIdle()
        assertTrue(holder.state.value.loadError)

        holder.refreshSelectedRepository()
        advanceUntilIdle()

        assertFalse(holder.state.value.loadError)
        assertEquals(2, refreshCalls)
        assertEquals(1, recheckCount)
    }

    @Test
    fun refreshExceptionSetsLoadErrorWithoutCrashingScope() = runTest {
        devicesFlow.value = listOf(makeDevice(repositoryId = 1L))
        val holder = createHolder { throw IllegalStateException("Room query failed") }
        advanceUntilIdle()

        assertTrue(holder.state.value.loadError)
        assertFalse(holder.state.value.isLoadingVersions)
    }

    @Test
    fun switchingSourceDuringPendingRefreshKeepsLoadingStateForNewSource() = runTest {
        devicesFlow.value = listOf(makeDevice(repositoryId = 1L))
        val wledGate = CompletableDeferred<Boolean>()
        val quinledGate = CompletableDeferred<Boolean>()
        val holder = createHolder { repoId ->
            if (repoId == 1L) wledGate.await() else quinledGate.await()
        }
        runCurrent()

        holder.selectRepository(2L)
        runCurrent()
        assertTrue(holder.state.value.isLoadingVersions)

        wledGate.complete(false)
        runCurrent()
        assertTrue(holder.state.value.isLoadingVersions)
        assertFalse(holder.state.value.loadError)

        quinledGate.complete(true)
        advanceUntilIdle()
        assertFalse(holder.state.value.isLoadingVersions)
    }

    @Test
    fun switchingBackToFailedUnfetchedSourceRetainsLoadErrorWithRetry() = runTest {
        devicesFlow.value = listOf(makeDevice(repositoryId = 1L))
        versionFlows.getValue(2L).value = listOf(makeVersion(2L, "0.15.4"))
        val holder = createHolder { false }
        advanceUntilIdle()
        assertTrue(holder.state.value.loadError)

        holder.selectRepository(2L)
        advanceUntilIdle()
        assertFalse(holder.state.value.loadError)

        holder.selectRepository(1L)
        advanceUntilIdle()
        assertTrue(holder.state.value.loadError)
    }

    @Test
    fun cancelledManualRetryOnFailedSourceRetainsLoadErrorWhenRevisited() = runTest {
        devicesFlow.value = listOf(makeDevice(repositoryId = 1L))
        versionFlows.getValue(2L).value = listOf(makeVersion(2L, "0.15.4"))
        val retryGate = CompletableDeferred<Boolean>()
        var attempts = 0
        val holder = createHolder {
            attempts++
            if (attempts == 1) false else retryGate.await()
        }
        advanceUntilIdle()
        assertTrue(holder.state.value.loadError)

        holder.refreshSelectedRepository()
        runCurrent()
        holder.selectRepository(2L)
        advanceUntilIdle()

        holder.selectRepository(1L)
        advanceUntilIdle()
        assertTrue(holder.state.value.loadError)
        retryGate.complete(false)
    }

    companion object {
        private const val MAC_ADDRESS = "AA:BB:CC:DD:EE:01"
    }
}
