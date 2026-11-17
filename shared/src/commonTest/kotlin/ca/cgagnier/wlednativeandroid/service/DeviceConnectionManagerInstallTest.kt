package ca.cgagnier.wlednativeandroid.service

import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.repository.DeviceStore
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.service.update.RepositoryIdResolver
import ca.cgagnier.wlednativeandroid.service.update.UpdateTagResolver
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceConnection
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceConnectionFactory
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceConnectionManagerInstallTest {

    private class StoreFake : DeviceStore {
        val devicesFlow = MutableStateFlow<List<Device>>(emptyList())
        override val allDevices: Flow<List<Device>> = devicesFlow
        val updatedSkipUpdateTags = mutableMapOf<String, String>()
        val updatedRepositoryIds = mutableMapOf<String, Long>()

        override suspend fun updateOriginalName(macAddress: String, originalName: String) = Unit
        override suspend fun updateBranch(macAddress: String, branch: Branch) = Unit
        override suspend fun updateLastSeen(macAddress: String, lastSeen: Long) = Unit
        override suspend fun delete(device: Device) = Unit

        override suspend fun updateSkipUpdateTag(macAddress: String, skipUpdateTag: String) {
            updatedSkipUpdateTags[macAddress] = skipUpdateTag
        }

        override suspend fun updateRepositoryId(macAddress: String, repositoryId: Long) {
            updatedRepositoryIds[macAddress] = repositoryId
        }
    }

    private data class ConnectedHarness(
        val store: StoreFake,
        val manager: DeviceConnectionManager,
        val infoFlow: MutableSharedFlow<DeviceStateInfo>,
    )

    private fun TestScope.connectDevice(
        initialRepositoryId: Long = 1L,
        skipUpdateTag: String = "",
        repositoryIdResolver: RepositoryIdResolver = RepositoryIdResolver { 1L },
    ): ConnectedHarness {
        val store = StoreFake()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        coroutineContext.job.invokeOnCompletion { scope.cancel() }
        val infoFlow = MutableSharedFlow<DeviceStateInfo>(replay = 1)
        val manager = DeviceConnectionManager(
            store = store,
            connectionFactory = object : DeviceConnectionFactory {
                override fun createConnection(address: String, scope: CoroutineScope) = object : DeviceConnection {
                    override val status: StateFlow<WebsocketStatus> = MutableStateFlow(WebsocketStatus.CONNECTED)
                    override val incomingStateInfo = infoFlow
                    override fun connect() = Unit
                    override fun pause() = Unit
                    override fun resume() = Unit
                    override fun destroy() = Unit
                    override suspend fun sendState(state: State) = true
                }
            },
            connectivity = FakeNetworkConnectivityManager(isConnected = true),
            updateTagResolver = UpdateTagResolver { _, _ -> "0.15.0" },
            repositoryIdResolver = repositoryIdResolver,
            coroutineScope = scope,
            timeProvider = { 10_000L },
            backgroundDispatcher = dispatcher,
            ioDispatcher = dispatcher,
        )
        store.devicesFlow.value = listOf(
            Device(
                macAddress = "A",
                address = "192.168.1.50",
                originalName = "Light",
                repositoryId = initialRepositoryId,
                skipUpdateTag = skipUpdateTag,
            ),
        )
        advanceUntilIdle()
        infoFlow.tryEmit(stateInfo())
        advanceUntilIdle()
        return ConnectedHarness(store, manager, infoFlow)
    }

    private fun stateInfo(version: String = "0.14.0", repository: String? = null, brand: String? = null) =
        DeviceStateInfo(
            state = State(),
            info = Info(
                version = version,
                repository = repository,
                brand = brand,
                name = "Light",
                leds = Leds(count = 30),
                wifi = Wifi(),
            ),
        )

    private fun version(tagName: String, repositoryId: Long) = Version(
        repositoryId = repositoryId,
        tagName = tagName,
        name = tagName,
        description = "",
        isPrerelease = false,
        publishedDate = "",
        htmlUrl = "",
    )

    private fun repoDao(vararg repos: Repository): RepositoryDao = object : RepositoryDao {
        override suspend fun insert(repository: Repository): Long = 0L
        override suspend fun insertMany(repositories: List<Repository>) = Unit
        override suspend fun update(repository: Repository) = Unit
        override suspend fun delete(repository: Repository) = Unit
        override suspend fun deleteAll() = Unit
        override suspend fun getRepositoryById(id: Long): Repository? = repos.firstOrNull { it.id == id }
        override suspend fun getRepositoryByOwnerAndRepo(ownerAndRepo: String): Repository? = null
        override fun getAllRepositories(): Flow<List<Repository>> = emptyFlow()
    }

    @Test
    fun markUpdateInstalledUpdatesRepositoryWhenSwitchingSource() = runTest {
        val (_, manager, _) = connectDevice()

        manager.markUpdateInstalled(
            macAddress = "A",
            installedVersionTag = "v0.15.0",
            repositoryId = 2L,
            repositoryOwnerAndRepo = "intermittech/QuinLED-Firmware",
        )

        val updated = manager.allDevicesWithState.value[0]
        assertEquals(2L, updated.device.repositoryId)
        assertEquals("intermittech/QuinLED-Firmware", updated.stateInfo?.info?.repository)
    }

    @Test
    fun completeFirmwareInstallPersistsChangedRepoAndSkipTagAndUpdatesState() = runTest {
        val (store, manager, _) = connectDevice(skipUpdateTag = "0.14.4")
        val targetVersion = version(tagName = "v0.15.0", repositoryId = 2L)
        val dao = repoDao(
            Repository(
                id = 2L,
                name = "QuinLED",
                ownerAndRepo = "intermittech/QuinLED-Firmware",
                description = "",
                htmlUrl = "",
            ),
        )

        val ownerRepo = manager.completeFirmwareInstall(manager.allDevicesWithState.value[0].device, targetVersion, dao)

        assertEquals("intermittech/QuinLED-Firmware", ownerRepo)
        assertEquals(2L, store.updatedRepositoryIds["A"])
        assertEquals("", store.updatedSkipUpdateTags["A"])
        val updated = manager.allDevicesWithState.value[0]
        assertEquals("0.15.0", updated.stateInfo?.info?.version)
        assertEquals("intermittech/QuinLED-Firmware", updated.stateInfo?.info?.repository)
        assertNull(updated.updateVersionTag)
    }

    @Test
    fun completeFirmwareInstallSkipsRedundantWritesWhenRepoAndSkipTagUnchanged() = runTest {
        val (store, manager, _) = connectDevice(skipUpdateTag = "")

        manager.completeFirmwareInstall(
            device = manager.allDevicesWithState.value[0].device,
            installedVersion = version(tagName = "0.15.0", repositoryId = 1L),
            repositoryDao = repoDao(),
        )

        assertFalse("A" in store.updatedRepositoryIds)
        assertFalse("A" in store.updatedSkipUpdateTags)
    }

    @Test
    fun incomingFrameWithoutExplicitRepositoryPreservesSwitchedRepositoryId() = runTest {
        val (store, manager, infoFlow) = connectDevice(initialRepositoryId = 2L)

        listOf(null, "unknown").forEach { reportedRepo ->
            infoFlow.tryEmit(stateInfo(version = "0.15.0", repository = reportedRepo, brand = "WLED"))
            advanceUntilIdle()
        }

        assertFalse("A" in store.updatedRepositoryIds)
        assertEquals(2L, manager.allDevicesWithState.value[0].device.repositoryId)
    }

    @Test
    fun staleInFlightIncomingFrameDoesNotOverwritePostInstallState() = runTest {
        val repoLookupGate = CompletableDeferred<Long>()
        var blockLookup = false
        val (store, manager, infoFlow) = connectDevice(
            repositoryIdResolver = { _ ->
                if (blockLookup) repoLookupGate.await() else 1L
            },
        )
        blockLookup = true
        infoFlow.tryEmit(stateInfo(version = "0.14.0", repository = "wled/WLED"))
        runCurrent()

        manager.markUpdateInstalled(
            macAddress = "A",
            installedVersionTag = "0.15.0",
            repositoryId = 2L,
            repositoryOwnerAndRepo = "intermittech/QuinLED-Firmware",
        )
        repoLookupGate.complete(1L)
        advanceUntilIdle()

        val updated = manager.allDevicesWithState.value[0]
        assertFalse("A" in store.updatedRepositoryIds)
        assertEquals(2L, updated.device.repositoryId)
        assertEquals("0.15.0", updated.stateInfo?.info?.version)
        assertNull(updated.updateVersionTag)
    }
}
