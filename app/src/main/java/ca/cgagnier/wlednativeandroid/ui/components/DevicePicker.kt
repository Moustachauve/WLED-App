package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.partitionDevices

private const val SELECTED_ROW_BACKGROUND_ALPHA = 0.35f

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
            modifier = modifier,
        )
        return
    }

    val resolvedTitleStyle = config.titleStyle ?: MaterialTheme.typography.titleLarge
    val listModifier = if (selectionMode is DeviceSelectionMode.Single) {
        modifier.selectableGroup()
    } else {
        modifier
    }

    LazyColumn(modifier = listModifier, contentPadding = contentPadding) {
        if (config.showOfflineDevicesLast) {
            pickerDeviceItems(partitioned.online, selectionMode, currentTime, resolvedTitleStyle)
            if (partitioned.offline.isNotEmpty()) {
                item(key = "offline_label", contentType = "section_header") {
                    Text(
                        text = stringResource(R.string.offline_devices),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                pickerDeviceItems(partitioned.offline, selectionMode, currentTime, resolvedTitleStyle)
            }
        } else {
            pickerDeviceItems(partitioned.visible, selectionMode, currentTime, resolvedTitleStyle)
        }
    }
}

@Composable
private fun EmptyDevicePickerState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(24.dp),
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
    titleStyle: TextStyle,
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
            titleStyle = titleStyle,
        )
        HorizontalDivider()
    }
}

@Composable
private fun DevicePickerRow(
    device: DeviceWithState,
    selectionMode: DeviceSelectionMode,
    currentTime: Long,
    titleStyle: TextStyle,
) {
    val isSelected = selectionMode.isSelected(device.device.macAddress)
    val backgroundColor = if (isSelected && selectionMode is DeviceSelectionMode.Single) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = SELECTED_ROW_BACKGROUND_ALPHA)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .then(selectionMode.interactionModifier(device, isSelected))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode is DeviceSelectionMode.Multi) {
            Checkbox(checked = isSelected, onCheckedChange = null)
            Spacer(modifier = Modifier.width(12.dp))
        }
        DeviceInfoTwoRows(
            modifier = Modifier.weight(1f),
            device = device,
            currentTime = currentTime,
            nameMaxLines = 1,
            titleStyle = titleStyle,
        )
        if (selectionMode is DeviceSelectionMode.Single && isSelected) {
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                painter = painterResource(R.drawable.baseline_check_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
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
