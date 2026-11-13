package ca.cgagnier.wlednativeandroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import ca.cgagnier.wlednativeandroid.ui.homeScreen.DeviceListDetail
import ca.cgagnier.wlednativeandroid.ui.homeScreen.DeviceListDetailActions
import ca.cgagnier.wlednativeandroid.ui.settingsScreen.Settings
import kotlinx.serialization.Serializable

private const val ILLUMIDEL_GRADIENT_MID_HEX = 0xFF003765
private val IllumidelGradientMidColor = Color(ILLUMIDEL_GRADIENT_MID_HEX)

@Serializable
data object DeviceListDetailKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Composable
fun MainNavHost(
    backStack: NavBackStack<NavKey> = rememberNavBackStack(DeviceListDetailKey),
    startDeviceAddress: NavigationEvent? = null,
) {
    Box(
        modifier = Modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        IllumidelGradientMidColor,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            )
            .fillMaxSize(),
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = {
                if (backStack.size > 1) {
                    backStack.removeAt(backStack.lastIndex)
                }
            },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<DeviceListDetailKey> {
                    DeviceListDetail(
                        initialDeviceMacAddress = startDeviceAddress,
                        actions = DeviceListDetailActions(
                            openSettings = {
                                backStack.add(SettingsKey)
                            },
                        ),
                    )
                }
                entry<SettingsKey> {
                    Settings(
                        navigateUp = {
                            if (backStack.size > 1) {
                                backStack.removeAt(backStack.lastIndex)
                            }
                        },
                    )
                }
            },
        )
    }
}
