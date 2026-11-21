package ca.cgagnier.wlednativeandroid.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatchDeviceSnapshotTest {

    private fun device(
        macAddress: String = "AABBCCDDEEFF",
        address: String = "192.168.1.100",
        customName: String = "",
        originalName: String = "WLED-Device",
        isHidden: Boolean = false,
    ) = Device(
        macAddress = macAddress,
        address = address,
        customName = customName,
        originalName = originalName,
        isHidden = isHidden,
    )

    @Test
    fun projectsMacNameAndAddress() {
        val snapshots = listOf(device()).toWatchDeviceSnapshots(defaultName = "Default")
        assertEquals(listOf(WatchDeviceSnapshot("AABBCCDDEEFF", "WLED-Device", "192.168.1.100")), snapshots)
    }

    @Test
    fun excludesHiddenDevices() {
        val snapshots = listOf(device(isHidden = true)).toWatchDeviceSnapshots(defaultName = "Default")
        assertTrue(snapshots.isEmpty())
    }

    @Test
    fun excludesApModeDevices() {
        val snapshots = listOf(device(macAddress = AP_MODE_MAC_ADDRESS)).toWatchDeviceSnapshots(defaultName = "Default")
        assertTrue(snapshots.isEmpty())
    }

    @Test
    fun excludesDevicesWithoutAddress() {
        val snapshots = listOf(device(address = " ")).toWatchDeviceSnapshots(defaultName = "Default")
        assertTrue(snapshots.isEmpty())
    }

    @Test
    fun customNameWins() {
        val snapshots = listOf(device(customName = "Desk")).toWatchDeviceSnapshots(defaultName = "Default")
        assertEquals("Desk", snapshots.single().name)
    }

    @Test
    fun fallsBackToDefaultName() {
        val snapshots = listOf(device(originalName = "")).toWatchDeviceSnapshots(defaultName = "Default")
        assertEquals("Default", snapshots.single().name)
    }

    @Test
    fun sortsByNameIgnoringCase() {
        val devices = listOf(
            device(macAddress = "1", customName = "tv"),
            device(macAddress = "2", customName = "Desk"),
            device(macAddress = "3", customName = "attic"),
        )
        val names = devices.toWatchDeviceSnapshots(defaultName = "Default").map { it.name }
        assertEquals(listOf("attic", "Desk", "tv"), names)
    }
}
