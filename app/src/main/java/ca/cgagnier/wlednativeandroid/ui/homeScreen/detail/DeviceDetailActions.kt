package ca.cgagnier.wlednativeandroid.ui.homeScreen.detail

import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState

data class DeviceDetailActions(
    val onItemEdit: (DeviceWithState) -> Unit,
    val navigateUp: () -> Unit,
    val onWebViewReloadConsumed: () -> Unit = {},
)
