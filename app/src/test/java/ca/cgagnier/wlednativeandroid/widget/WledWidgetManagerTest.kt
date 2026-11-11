package ca.cgagnier.wlednativeandroid.widget

import android.content.Context
import androidx.glance.GlanceId
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.WifiSignalLevel
import ca.cgagnier.wlednativeandroid.model.wledapi.Segment
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.service.api.ApiResponse
import ca.cgagnier.wlednativeandroid.service.api.DeviceApi
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WledWidgetManagerTest {

    private lateinit var context: Context
    private lateinit var deviceApiFactory: DeviceApiFactory
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var widgetStorage: FakeWidgetStorage
    private lateinit var widgetManager: WledWidgetManager

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true) {
            every { getString(R.string.default_device_name) } returns "(New Device)"
        }
        deviceApiFactory = mockk()
        deviceRepository = mockk()
        widgetStorage = FakeWidgetStorage()
        widgetManager = WledWidgetManager(deviceApiFactory, deviceRepository, widgetStorage)
    }

    @Test
    fun `updateAllWidgets refreshes widgets concurrently so offline timeout does not block online widget`() = runTest {
        val offlineId =
            registerWidget(mac = "AA1122334455", address = "192.168.1.50", isOnline = true)
        val onlineId =
            registerWidget(mac = "BB1122334455", address = "192.168.1.100", isOnline = false)

        val offlineApi = mockk<DeviceApi> {
            coEvery { postJson(any()) } coAnswers {
                delay(5_000L)
                throw java.io.IOException("Connect timeout")
            }
        }
        val onlineApi = mockk<DeviceApi> {
            coEvery { postJson(any()) } returns ApiResponse(code = 200, body = onlineState(isOn = true))
        }

        every {
            deviceApiFactory.create("192.168.1.50", WledWidgetManager.WIDGET_REQUEST_TIMEOUT_SECONDS)
        } returns offlineApi
        every {
            deviceApiFactory.create("192.168.1.100", WledWidgetManager.WIDGET_REQUEST_TIMEOUT_SECONDS)
        } returns onlineApi

        val job = launch { widgetManager.updateAllWidgets(context) }

        // Run immediate tasks before the 5s timeout elapses
        runCurrent()

        // Online widget should already be updated and online while offline widget is still waiting
        val onlineSaved = widgetStorage.savedUpdates[onlineId]
        assertNotNull(onlineSaved)
        assertTrue(onlineSaved!!.isOnline)
        assertTrue(onlineSaved.isOn)
        assertNull(widgetStorage.savedUpdates[offlineId])

        // Advance virtual time past the 5s timeout so the offline widget completes
        advanceTimeBy(5_000L)
        runCurrent()
        job.join()

        val offlineSaved = widgetStorage.savedUpdates[offlineId]
        assertNotNull(offlineSaved)
        assertFalse(offlineSaved!!.isOnline)
    }

    @Test
    fun `refreshWidget creates DeviceApi with 5s timeout and marks widget offline on non-2xx response`() = runTest {
        val glanceId =
            registerWidget(mac = "AA1122334455", address = "192.168.1.10", isOn = true, isOnline = true)

        val api = mockk<DeviceApi> {
            coEvery { postJson(any()) } returns ApiResponse(code = 503, errorBody = "Unavailable")
        }
        every {
            deviceApiFactory.create("192.168.1.10", WledWidgetManager.WIDGET_REQUEST_TIMEOUT_SECONDS)
        } returns api

        widgetManager.refreshWidget(context, glanceId)

        val saved = widgetStorage.savedUpdates[glanceId]
        assertNotNull(saved)
        assertFalse(saved!!.isOnline)
        assertEquals(WifiSignalLevel.OFFLINE, saved.signalLevel)
        assertEquals(true, saved.isOn)
        coVerify(exactly = 1) { api.postJson(any()) }
    }

    @Test
    fun `refreshWidget preserves cached signalLevel when HTTP response succeeds`() = runTest {
        val glanceId = registerWidget(
            mac = "AA1122334455",
            address = "192.168.1.10",
            isOn = true,
            isOnline = true,
            signalLevel = WifiSignalLevel.FAIR,
        )

        val api = mockk<DeviceApi> {
            coEvery { postJson(any()) } returns ApiResponse(code = 200, body = onlineState(isOn = true))
        }
        every {
            deviceApiFactory.create("192.168.1.10", WledWidgetManager.WIDGET_REQUEST_TIMEOUT_SECONDS)
        } returns api

        widgetManager.refreshWidget(context, glanceId)

        val saved = widgetStorage.savedUpdates[glanceId]
        assertNotNull(saved)
        assertTrue(saved!!.isOnline)
        assertEquals(WifiSignalLevel.FAIR, saved.signalLevel)
    }

    @Test
    fun `getAssignedDeviceMac returns null for unconfigured widget`() = runTest {
        val unconfiguredId = mockk<GlanceId>()

        assertNull(widgetManager.getAssignedDeviceMac(context, unconfiguredId))
    }

    @Test
    fun `getAssignedDeviceMac returns current device MAC for configured widget`() = runTest {
        val glanceId = registerWidget(mac = "AA1122334455", address = "192.168.1.10")

        assertEquals("AA1122334455", widgetManager.getAssignedDeviceMac(context, glanceId))
    }

    @Test
    fun `configureWidget reassigns existing widget to new device without altering other widgets`() = runTest {
        val editedWidgetId = registerWidget(
            mac = "AA1122334455",
            address = "192.168.1.10",
            isOn = true,
            isOnline = true,
        )
        val untouchedWidgetId = registerWidget(
            mac = "AA1122334455",
            address = "192.168.1.10",
            isOn = true,
            isOnline = true,
        )
        val newDevice = Device(
            macAddress = "CC9988776655",
            address = "192.168.1.25",
            originalName = "wled-patio",
            customName = "Patio Strip",
        )

        widgetManager.configureWidget(context, editedWidgetId, newDevice)

        val reassigned = widgetStorage.savedUpdates[editedWidgetId]
        assertNotNull(reassigned)
        assertEquals("CC9988776655", reassigned!!.macAddress)
        assertEquals("192.168.1.25", reassigned.address)
        assertEquals("Patio Strip", reassigned.name)
        assertFalse(reassigned.isOn)
        assertNull(widgetStorage.savedUpdates[untouchedWidgetId])
        assertEquals("AA1122334455", widgetStorage.states[untouchedWidgetId]?.macAddress)
    }

    @Test
    fun `configureWidget seeds state from sibling widget already assigned to target device`() = runTest {
        val siblingState = WidgetStateData(
            macAddress = "DD1122334455",
            address = "192.168.1.40",
            name = "Old Name",
            isOn = true,
            isOnline = true,
            color = 0xFFFF8800.toInt(),
            signalLevel = WifiSignalLevel.FAIR,
        )
        registerWidget(siblingState)
        val newWidgetId = mockk<GlanceId>()
        val targetDevice = Device(
            macAddress = "DD1122334455",
            address = "192.168.1.42",
            originalName = "Kitchen Strip",
        )

        widgetManager.configureWidget(context, newWidgetId, targetDevice)

        assertEquals(
            siblingState.copy(name = "Kitchen Strip", address = "192.168.1.42"),
            widgetStorage.savedUpdates[newWidgetId],
        )
    }

    @Test
    fun `configureWidget preserves cached live state when re-selecting the already-assigned device`() = runTest {
        val existingState = WidgetStateData(
            macAddress = "EE1122334455",
            address = "192.168.1.60",
            name = "Desk Lamp",
            isOn = true,
            isOnline = true,
            color = 0xFF00FF88.toInt(),
            signalLevel = WifiSignalLevel.WEAK,
        )
        val glanceId = registerWidget(existingState)
        val sameDevice = Device(
            macAddress = "EE1122334455",
            address = "192.168.1.61",
            originalName = "Desk Lamp",
        )

        widgetManager.configureWidget(context, glanceId, sameDevice)

        assertEquals(
            existingState.copy(address = "192.168.1.61"),
            widgetStorage.savedUpdates[glanceId],
        )
    }

    private fun registerWidget(state: WidgetStateData): GlanceId {
        val glanceId = mockk<GlanceId>()
        widgetStorage.states[glanceId] = state
        coEvery { deviceRepository.findDeviceByMacAddress(state.macAddress) } returns Device(
            macAddress = state.macAddress,
            address = state.address,
            originalName = state.name,
        )
        return glanceId
    }

    private fun registerWidget(
        mac: String,
        address: String,
        isOn: Boolean = false,
        isOnline: Boolean = true,
        signalLevel: WifiSignalLevel = if (isOnline) WifiSignalLevel.GOOD else WifiSignalLevel.OFFLINE,
    ): GlanceId = registerWidget(
        WidgetStateData(
            macAddress = mac,
            address = address,
            name = "Lamp",
            isOn = isOn,
            isOnline = isOnline,
            signalLevel = signalLevel,
        ),
    )

    private fun onlineState(isOn: Boolean) = State(
        isOn = isOn,
        brightness = 255,
        segment = listOf(Segment(colors = listOf(listOf(0, 255, 0)))),
    )

    private class FakeWidgetStorage : WidgetStorage {
        val states = LinkedHashMap<GlanceId, WidgetStateData>()
        val savedUpdates = LinkedHashMap<GlanceId, WidgetStateData>()
        val clearedIds = mutableListOf<GlanceId>()

        override suspend fun getGlanceIds(context: Context): List<GlanceId> = states.keys.toList()

        override suspend fun getWidgetState(context: Context, glanceId: GlanceId): WidgetStateData? = states[glanceId]

        override suspend fun saveStateAndPush(context: Context, glanceId: GlanceId, data: WidgetStateData) {
            states[glanceId] = data
            savedUpdates[glanceId] = data
        }

        override suspend fun clearStateAndPush(context: Context, glanceId: GlanceId) {
            states.remove(glanceId)
            clearedIds.add(glanceId)
        }
    }
}
