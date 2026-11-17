package ca.cgagnier.wlednativeandroid.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import ca.cgagnier.wlednativeandroid.ui.homeScreen.DeviceListDetail
import ca.cgagnier.wlednativeandroid.ui.homeScreen.DeviceListDetailActions
import ca.cgagnier.wlednativeandroid.ui.homeScreen.firmwareBrowser.FirmwareBrowserScreen
import ca.cgagnier.wlednativeandroid.ui.settingsScreen.Settings
import ca.cgagnier.wlednativeandroid.ui.soundsync.SoundSyncScreen
import kotlinx.serialization.Serializable

@Serializable
data object DeviceListDetailKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object SoundSyncKey : NavKey

@Serializable
data class FirmwareBrowserKey(val macAddress: String) : NavKey

fun NavBackStack<NavKey>.navigateToSoundSync() {
    if (lastOrNull() != SoundSyncKey) {
        add(SoundSyncKey)
    }
}

fun NavBackStack<NavKey>.navigateToFirmwareBrowser(macAddress: String) {
    val key = FirmwareBrowserKey(macAddress)
    if (lastOrNull() != key) {
        add(key)
    }
}

private fun NavBackStack<NavKey>.popIfPossible() {
    if (size > 1) {
        removeAt(lastIndex)
    }
}

@Composable
fun MainNavHost(
    backStack: NavBackStack<NavKey> = rememberNavBackStack(DeviceListDetailKey),
    startDeviceAddress: NavigationEvent? = null,
    openChangelogs: () -> Unit = {},
) {
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.popIfPossible() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<DeviceListDetailKey> {
                DeviceListDetail(
                    initialDeviceMacAddress = startDeviceAddress,
                    actions = DeviceListDetailActions(
                        openSettings = { backStack.add(SettingsKey) },
                        openSoundSync = { backStack.navigateToSoundSync() },
                        openChangelogs = openChangelogs,
                        openFirmwareBrowser = { macAddress ->
                            backStack.navigateToFirmwareBrowser(macAddress)
                        },
                    ),
                )
            }
            entry<FirmwareBrowserKey> { key ->
                FirmwareBrowserScreen(
                    macAddress = key.macAddress,
                    navigateUp = { backStack.popIfPossible() },
                )
            }
            entry<SettingsKey> {
                Settings(navigateUp = { backStack.popIfPossible() })
            }
            entry<SoundSyncKey> {
                SoundSyncScreen(navigateUp = { backStack.popIfPossible() })
            }
        },
    )
}
