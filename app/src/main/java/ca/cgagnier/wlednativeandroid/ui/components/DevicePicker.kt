package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.partitionDevices

sealed interface DeviceSelectionMode {
    data class Single(val selectedMacAddress: String?, val onDeviceSelected: (DeviceWithState) -> Unit) :
        DeviceSelectionMode

    data class Multi(val selectedMacAddresses: Set<String>, val onDeviceToggled: (DeviceWithState) -> Unit) :
        DeviceSelectionMode
}

@Immutable
data class DevicePickerConfig(
    val showOfflineDevicesLast: Boolean = true,
    val showHiddenDevices: Boolean = false,
    val statusVisibility: DeviceStatusVisibility = DeviceStatusVisibility.WithoutWebsocket,
    val titleStyle: TextStyle? = null,
)

@Composable
fun DevicePickerList(
    devices: List<DeviceWithState>,
    selectionMode: DeviceSelectionMode,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    config: DevicePickerConfig = DevicePickerConfig(),
) {
    val currentTime = remember { System.currentTimeMillis() }
    val partitioned = remember(devices, config.showHiddenDevices, currentTime) {
        partitionDevices(
            devices = devices,
            showHiddenDevices = config.showHiddenDevices,
            currentTimeMillis = currentTime,
        )
    }

    if (partitioned.visible.isEmpty()) {
        EmptyDevicePickerState(
            message = stringResource(R.string.you_dont_have_any_visible_devices),
            contentPadding = contentPadding,
            modifier = modifier,
        )
        return
    }

    val rowStyle = DeviceInfoTwoRowsStyle(
        titleStyle = config.titleStyle ?: MaterialTheme.typography.titleLarge,
        statusVisibility = config.statusVisibility,
    )
    val listModifier = if (selectionMode is DeviceSelectionMode.Single) {
        modifier.selectableGroup()
    } else {
        modifier
    }

    LazyColumn(
        modifier = listModifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (config.showOfflineDevicesLast && config.statusVisibility != DeviceStatusVisibility.None) {
            pickerDeviceItems(partitioned.online, selectionMode, currentTime, rowStyle)
            if (partitioned.offline.isNotEmpty()) {
                item(key = "offline_label", contentType = "section_header") {
                    Text(
                        text = stringResource(R.string.offline_devices),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
                pickerDeviceItems(partitioned.offline, selectionMode, currentTime, rowStyle)
            }
        } else {
            pickerDeviceItems(partitioned.visible, selectionMode, currentTime, rowStyle)
        }
    }
}

@Composable
private fun EmptyDevicePickerState(message: String, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(contentPadding)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun LazyListScope.pickerDeviceItems(
    devices: List<DeviceWithState>,
    selectionMode: DeviceSelectionMode,
    currentTime: Long,
    rowStyle: DeviceInfoTwoRowsStyle,
) {
    items(
        items = devices,
        key = { it.device.macAddress },
        contentType = { "device_picker_row" },
    ) { device ->
        DevicePickerRow(
            device = device,
            selectionMode = selectionMode,
            currentTime = currentTime,
            rowStyle = rowStyle,
        )
    }
}

@Composable
private fun DevicePickerRow(
    device: DeviceWithState,
    selectionMode: DeviceSelectionMode,
    currentTime: Long,
    rowStyle: DeviceInfoTwoRowsStyle,
) {
    val isSelected = selectionMode.isSelected(device.device.macAddress)
    val nameMaxLines = if (rowStyle.statusVisibility == DeviceStatusVisibility.None) 2 else 1
    val leadingContent: (@Composable RowScope.() -> Unit)? = if (selectionMode is DeviceSelectionMode.Multi) {
        { Checkbox(checked = isSelected, onCheckedChange = null) }
    } else {
        null
    }
    val trailingContent: (@Composable RowScope.() -> Unit)? =
        if (selectionMode is DeviceSelectionMode.Single && isSelected) {
            {
                Icon(
                    painter = painterResource(R.drawable.baseline_check_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        }

    DeviceInfoCard(
        device = device,
        modifier = Modifier.fillMaxWidth(),
        style = DeviceInfoCardStyle(
            isSelected = isSelected,
            currentTime = currentTime,
            nameMaxLines = nameMaxLines,
            infoStyle = rowStyle,
            contentModifier = selectionMode.interactionModifier(device, isSelected),
        ),
        leadingContent = leadingContent,
        trailingContent = trailingContent,
    )
}

private fun DeviceSelectionMode.isSelected(mac: String): Boolean = when (this) {
    is DeviceSelectionMode.Single -> mac == selectedMacAddress
    is DeviceSelectionMode.Multi -> mac in selectedMacAddresses
}

private fun DeviceSelectionMode.interactionModifier(device: DeviceWithState, isSelected: Boolean): Modifier =
    when (this) {
        is DeviceSelectionMode.Single -> Modifier.selectable(
            selected = isSelected,
            onClick = { onDeviceSelected(device) },
            role = Role.RadioButton,
        )

        is DeviceSelectionMode.Multi -> Modifier.toggleable(
            value = isSelected,
            onValueChange = { onDeviceToggled(device) },
            role = Role.Checkbox,
        )
    }
