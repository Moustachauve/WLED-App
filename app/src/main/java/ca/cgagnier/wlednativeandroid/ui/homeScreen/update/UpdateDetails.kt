package ca.cgagnier.wlednativeandroid.ui.homeScreen.update

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.wledapi.isOtaEnabled
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.ui.components.SelectableDropdownMenuItem
import ca.cgagnier.wlednativeandroid.ui.components.WledMarkdown
import ca.cgagnier.wlednativeandroid.ui.components.deviceName

private const val DIALOG_WIDTH_FRACTION = 0.92f
private const val DIALOG_HEIGHT_FRACTION = 0.88f
private val DIALOG_MAX_WIDTH = 640.dp

sealed interface UpdateDetailsMode {
    data class Standard(val onSkip: () -> Unit) : UpdateDetailsMode

    data class BinaryPicker(
        val installableAssets: List<Asset>,
        val selectedAsset: Asset?,
        val onSelectAsset: (Asset) -> Unit,
    ) : UpdateDetailsMode
}

@Composable
fun UpdateDetailsDialog(
    device: DeviceWithState,
    version: VersionWithAssets,
    onDismiss: () -> Unit,
    onInstall: (VersionWithAssets) -> Unit,
    onSkip: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(DIALOG_WIDTH_FRACTION)
                .wrapContentWidth()
                .widthIn(max = DIALOG_MAX_WIDTH)
                .fillMaxWidth()
                .fillMaxHeight(DIALOG_HEIGHT_FRACTION),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            UpdateDetailsContent(
                device = device,
                version = version,
                mode = UpdateDetailsMode.Standard(onSkip = onSkip),
                onInstall = onInstall,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
internal fun UpdateDetailsContent(
    device: DeviceWithState?,
    version: VersionWithAssets,
    mode: UpdateDetailsMode,
    onInstall: (VersionWithAssets) -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxHeight()) {
        if (mode is UpdateDetailsMode.Standard) {
            StandardDetailsHeader(device = device)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        key(version.version.repositoryId, version.version.tagName) {
            ReleaseNotesContent(
                markdown = version.version.description,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        when (mode) {
            is UpdateDetailsMode.Standard -> StandardDetailsFooter(
                mode = mode,
                onDismiss = onDismiss,
                onInstall = { onInstall(version) },
            )

            is UpdateDetailsMode.BinaryPicker -> BinaryPickerFooter(
                device = device,
                version = version,
                mode = mode,
                onDismiss = onDismiss,
                onInstall = { onInstall(version) },
            )
        }
    }
}

@Composable
private fun StandardDetailsHeader(device: DeviceWithState?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.update_available),
            style = MaterialTheme.typography.titleLarge,
        )
        if (device != null) {
            Text(
                text = "${deviceName(device.device)} - ${device.device.address}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DeviceInstallabilityNotice(isDeviceOnline: Boolean, isOtaEnabled: Boolean, modifier: Modifier = Modifier) {
    val notice = when {
        !isDeviceOnline -> stringResource(R.string.device_offline_install_notice)
        !isOtaEnabled -> stringResource(R.string.ota_disabled_notice)
        else -> null
    } ?: return
    Text(
        text = notice,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier,
    )
}

@Composable
private fun BinaryDropdown(
    installableAssets: List<Asset>,
    selectedAsset: Asset?,
    onSelectAsset: (Asset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        BinaryDropdownAnchor(
            value = selectedAsset?.name.orEmpty(),
            expanded = expanded,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            matchAnchorWidth = false,
        ) {
            installableAssets.forEach { asset ->
                val isSelected = asset.name == selectedAsset?.name
                SelectableDropdownMenuItem(
                    selected = isSelected,
                    onClick = {
                        onSelectAsset(asset)
                        expanded = false
                    },
                ) {
                    Text(
                        text = asset.name,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun BinaryDropdownAnchor(value: String, expanded: Boolean, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .padding(top = 8.dp)
            .defaultMinSize(minHeight = OutlinedTextFieldDefaults.MinHeight)
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.CenterStart,
        propagateMinConstraints = true,
    ) {
        OutlinedTextFieldDefaults.DecorationBox(
            value = value,
            innerTextField = {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            enabled = true,
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            interactionSource = interactionSource,
            label = {
                Text(
                    text = stringResource(R.string.firmware_binary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.select_firmware_binary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
    }
}

@Composable
private fun ReleaseNotesContent(markdown: String, modifier: Modifier = Modifier) {
    val trimmed = markdown.trimIndent()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (trimmed.isEmpty()) {
            Text(
                text = stringResource(R.string.no_release_notes_provided),
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
            )
        } else {
            WledMarkdown(content = trimmed)
        }
    }
}

@Composable
private fun StandardDetailsFooter(mode: UpdateDetailsMode.Standard, onDismiss: (() -> Unit)?, onInstall: () -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            TextButton(onClick = mode.onSkip) {
                Text(
                    text = stringResource(R.string.skip_this_version),
                    maxLines = 1,
                )
            }
        }
        if (onDismiss != null) {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.later),
                    maxLines = 1,
                )
            }
        }
        Button(onClick = onInstall) {
            Text(
                text = stringResource(R.string.install),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BinaryPickerFooter(
    device: DeviceWithState?,
    version: VersionWithAssets,
    mode: UpdateDetailsMode.BinaryPicker,
    onDismiss: (() -> Unit)?,
    onInstall: () -> Unit,
) {
    val isDeviceOnline = device?.isOnline == true
    val isOtaEnabled = device?.stateInfo?.info?.isOtaEnabled ?: true
    val canInstall = isDeviceOnline && isOtaEnabled && mode.selectedAsset != null
    val isAlreadyInstalled = device != null &&
        version.matchesInstalledVersion(
            installedVersion = device.stateInfo?.info?.version,
            deviceRepositoryId = device.device.repositoryId,
        )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DeviceInstallabilityNotice(isDeviceOnline = isDeviceOnline, isOtaEnabled = isOtaEnabled)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BinarySelectorSlot(mode = mode, modifier = Modifier.weight(1f))
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.cancel), maxLines = 1)
                }
            }
            Button(onClick = onInstall, enabled = canInstall) {
                Text(
                    text = stringResource(if (isAlreadyInstalled) R.string.reinstall else R.string.install),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun BinarySelectorSlot(mode: UpdateDetailsMode.BinaryPicker, modifier: Modifier = Modifier) {
    if (mode.installableAssets.isEmpty()) {
        Text(
            text = stringResource(R.string.no_installable_binaries),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
    } else {
        BinaryDropdown(
            installableAssets = mode.installableAssets,
            selectedAsset = mode.selectedAsset,
            onSelectAsset = mode.onSelectAsset,
            modifier = modifier,
        )
    }
}
