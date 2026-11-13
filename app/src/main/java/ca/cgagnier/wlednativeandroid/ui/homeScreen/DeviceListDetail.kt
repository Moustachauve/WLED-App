package ca.cgagnier.wlednativeandroid.ui.homeScreen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.model.AP_MODE_MAC_ADDRESS
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.getApModeDeviceWithState
import ca.cgagnier.wlednativeandroid.ui.NavigationEvent
import ca.cgagnier.wlednativeandroid.ui.homeScreen.detail.DeviceDetail
import ca.cgagnier.wlednativeandroid.ui.homeScreen.detail.DeviceDetailActions
import ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceAdd.DeviceAdd
import ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit.DeviceEdit
import ca.cgagnier.wlednativeandroid.ui.homeScreen.list.DeviceList
import ca.cgagnier.wlednativeandroid.ui.homeScreen.list.DeviceListActions
import ca.cgagnier.wlednativeandroid.ui.homeScreen.list.DeviceWebsocketListViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private const val POST_OTA_WEBVIEW_RELOAD_DELAY_MS = 1500L

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Suppress("LongMethod") // Navigator requires type inference, limiting extraction options
@Composable
fun DeviceListDetail(
    modifier: Modifier = Modifier,
    initialDeviceMacAddress: NavigationEvent? = null,
    actions: DeviceListDetailActions,
    viewModel: DeviceListDetailViewModel = koinViewModel(),
    deviceWebsocketListViewModel: DeviceWebsocketListViewModel = koinViewModel(),
) {
    val coroutineScope = rememberCoroutineScope()
    val defaultScaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
    val customScaffoldDirective = defaultScaffoldDirective.copy(
        horizontalPartitionSpacerSize = 0.dp,
    )
    val navigator =
        rememberListDetailPaneScaffoldNavigator<Any>(scaffoldDirective = customScaffoldDirective)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val localNetworkPermission = rememberLocalNetworkPermissionController(
        onPermissionGranted = {
            deviceWebsocketListViewModel.refreshOfflineDevices()
            viewModel.onLocalNetworkPermissionGranted()
        },
    )

    LaunchedEffect(initialDeviceMacAddress) {
        if (initialDeviceMacAddress != null) {
            navigator.navigateTo(
                pane = ListDetailPaneScaffoldRole.Detail,
                contentKey = initialDeviceMacAddress.macAddress,
            )
        }
    }

    val devices by deviceWebsocketListViewModel.allDevicesWithState.collectAsStateWithLifecycle()
    val selectedDeviceMacAddress = navigator.currentDestination?.contentKey as? String
    val selectedDevice = rememberSelectedDevice(devices, selectedDeviceMacAddress)

    val showHiddenDevices by viewModel.showHiddenDevices.collectAsStateWithLifecycle()
    val isWLEDCaptivePortal by viewModel.isWLEDCaptivePortal.collectAsStateWithLifecycle()
    val isAddDeviceDialogVisible by viewModel.isAddDeviceDialogVisible.collectAsStateWithLifecycle()
    var pendingWebViewReloadMac by rememberSaveable { mutableStateOf<String?>(null) }

    val navigateToDetail: (DeviceWithState) -> Unit = { device ->
        coroutineScope.launch {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, device.device.macAddress)
        }
    }
    val navigateToEdit: (DeviceWithState) -> Unit = { device ->
        coroutineScope.launch {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Extra, device.device.macAddress)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                DrawerSheetContent(
                    coroutineScope = coroutineScope,
                    drawerState = drawerState,
                    showHiddenDevices = showHiddenDevices,
                    viewModel = viewModel,
                    actions = actions,
                )
            }
        },
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) { innerPadding ->
            NavigableListDetailPaneScaffold(
                modifier = modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding(),
                navigator = navigator,
                defaultBackBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange,
                listPane = {
                    AnimatedPane {
                        DeviceList(
                            selectedDevice = selectedDevice,
                            isWLEDCaptivePortal = isWLEDCaptivePortal,
                            isLocalNetworkDenied = localNetworkPermission.isDenied,
                            actions = DeviceListActions(
                                onItemClick = navigateToDetail,
                                onAddDevice = { viewModel.showAddDeviceDialog() },
                                onShowHiddenDevices = { viewModel.toggleShowHiddenDevices() },
                                onRequestLocalNetworkAccess = localNetworkPermission.requestLocalNetworkAccess,
                                onRefresh = { viewModel.startDiscoveryServiceTimed() },
                                onItemEdit = {
                                    navigateToDetail(it)
                                    navigateToEdit(it)
                                },
                                onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                            ),
                        )
                    }
                },
                detailPane = {
                    AnimatedPane {
                        selectedDevice?.let { device ->
                            val deviceMac = device.device.macAddress
                            LaunchedEffect(deviceMac) {
                                if (pendingWebViewReloadMac != null && pendingWebViewReloadMac != deviceMac) {
                                    pendingWebViewReloadMac = null
                                }
                            }
                            DeviceDetail(
                                device = device,
                                canNavigateBack = navigator.canNavigateBack(),
                                shouldReloadWebView = pendingWebViewReloadMac == deviceMac,
                                actions = DeviceDetailActions(
                                    onItemEdit = { navigateToEdit(device) },
                                    navigateUp = { coroutineScope.launch { navigator.navigateBack() } },
                                    onWebViewReloadConsumed = {
                                        if (pendingWebViewReloadMac == deviceMac) {
                                            pendingWebViewReloadMac = null
                                        }
                                    },
                                ),
                            )
                        } ?: SelectDeviceView()
                    }
                },
                extraPane = {
                    AnimatedPane {
                        selectedDevice?.let { device ->
                            val deviceMac = device.device.macAddress
                            DeviceEdit(
                                device = device,
                                canNavigateBack = navigator.canNavigateBack(),
                                navigateUp = { coroutineScope.launch { navigator.navigateBack() } },
                                reloadWebView = {
                                    coroutineScope.launch {
                                        delay(POST_OTA_WEBVIEW_RELOAD_DELAY_MS)
                                        pendingWebViewReloadMac = deviceMac
                                    }
                                },
                            )
                        }
                    }
                },
            )
        }

        BackHandler(enabled = drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        }
    }

    if (isAddDeviceDialogVisible) {
        DeviceAdd(onDismissRequest = { viewModel.hideAddDeviceDialog() })
    }
}

@Composable
private fun rememberSelectedDevice(
    devices: List<DeviceWithState>,
    selectedDeviceMacAddress: String?,
): DeviceWithState? {
    return remember(devices, selectedDeviceMacAddress) {
        if (selectedDeviceMacAddress == AP_MODE_MAC_ADDRESS) {
            return@remember getApModeDeviceWithState()
        }
        devices.firstOrNull { it.device.macAddress == selectedDeviceMacAddress }
    }
}

@Composable
private fun DrawerSheetContent(
    coroutineScope: CoroutineScope,
    drawerState: DrawerState,
    showHiddenDevices: Boolean,
    viewModel: DeviceListDetailViewModel,
    actions: DeviceListDetailActions,
) {
    DrawerContent(
        showHiddenDevices = showHiddenDevices,
        addDevice = {
            coroutineScope.launch {
                viewModel.showAddDeviceDialog()
                drawerState.close()
            }
        },
        toggleShowHiddenDevices = {
            coroutineScope.launch {
                viewModel.toggleShowHiddenDevices()
                drawerState.close()
            }
        },
        openSettings = {
            coroutineScope.launch {
                actions.openSettings()
                drawerState.close()
            }
        },
    )
}

data class DeviceListDetailActions(val openSettings: () -> Unit)
