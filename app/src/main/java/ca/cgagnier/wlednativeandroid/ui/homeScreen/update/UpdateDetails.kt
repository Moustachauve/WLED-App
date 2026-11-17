package ca.cgagnier.wlednativeandroid.ui.homeScreen.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.wledapi.isOtaEnabled
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.ui.components.WledMarkdown
import ca.cgagnier.wlednativeandroid.ui.components.deviceName

private const val DIALOG_WIDTH_FRACTION = 0.92f
private const val DIALOG_HEIGHT_FRACTION = 0.88f
private val DIALOG_MAX_WIDTH = 640.dp
private const val SELECTED_BINARY_MAX_LINES = 2

sealed interface UpdateDetailsMode {
    data class Standard(val onSkip: () -> Unit) : UpdateDetailsMode

    data class BinaryPicker(
        val repositoryOwnerAndRepo: String?,
        val installableAssets: List<Asset>,
        val selectedAsset: Asset?,
        val onSelectAsset: (Asset) -> Unit,
    ) : UpdateDetailsMode
}

@Composable
fun UpdateDetailsDialog(
    device: DeviceWithState?,
    version: VersionWithAssets,
    onDismiss: () -> Unit,
    onInstall: (VersionWithAssets) -> Unit,
    mode: UpdateDetailsMode,
) {
    val isDeviceOnline = device?.isOnline == true
    val isOtaEnabled = device?.stateInfo?.info?.isOtaEnabled ?: true
    val hasRequiredAsset = mode !is UpdateDetailsMode.BinaryPicker || mode.selectedAsset != null
    val canInstall = isDeviceOnline && isOtaEnabled && hasRequiredAsset
    val isAlreadyInstalled = device != null &&
        version.matchesInstalledVersion(
            installedVersion = device.stateInfo?.info?.version,
            deviceRepositoryId = device.device.repositoryId,
        )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(DIALOG_WIDTH_FRACTION)
                .widthIn(max = DIALOG_MAX_WIDTH)
                .fillMaxHeight(DIALOG_HEIGHT_FRACTION),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(modifier = Modifier.fillMaxHeight()) {
                DetailsHeader(device = device, version = version, mode = mode)
                if (mode is UpdateDetailsMode.BinaryPicker) {
                    BinaryPickerSection(mode = mode)
                }
                DeviceInstallabilityNotice(
                    isDeviceOnline = isDeviceOnline,
                    isOtaEnabled = isOtaEnabled,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReleaseNotesContent(
                    markdown = version.version.description,
                    modifier = Modifier.weight(1f),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DetailsFooter(
                    mode = mode,
                    canInstall = canInstall,
                    isAlreadyInstalled = isAlreadyInstalled,
                    onDismiss = onDismiss,
                    onInstall = { onInstall(version) },
                )
            }
        }
    }
}

@Composable
fun UpdateDetailsDialog(
    device: DeviceWithState,
    version: VersionWithAssets,
    onDismiss: () -> Unit,
    onInstall: (VersionWithAssets) -> Unit,
    onSkip: () -> Unit,
) {
    UpdateDetailsDialog(
        device = device,
        version = version,
        onDismiss = onDismiss,
        onInstall = onInstall,
        mode = UpdateDetailsMode.Standard(onSkip = onSkip),
    )
}

@Composable
private fun DetailsHeader(device: DeviceWithState?, version: VersionWithAssets, mode: UpdateDetailsMode) {
    val title: String
    val subtitle: String
    when (mode) {
        is UpdateDetailsMode.Standard -> {
            title = stringResource(R.string.update_available)
            subtitle = device?.let { "${deviceName(it.device)} - ${it.device.address}" }.orEmpty()
        }

        is UpdateDetailsMode.BinaryPicker -> {
            title = version.version.tagName
            subtitle = listOfNotNull(
                version.version.distinctReleaseName,
                mode.repositoryOwnerAndRepo?.takeIf { it.isNotBlank() },
            ).joinToString(" • ")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BinaryPickerSection(mode: UpdateDetailsMode.BinaryPicker) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
    ) {
        if (mode.installableAssets.isEmpty()) {
            Text(
                text = stringResource(R.string.no_installable_binaries),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            BinaryDropdown(
                installableAssets = mode.installableAssets,
                selectedAsset = mode.selectedAsset,
                onSelectAsset = mode.onSelectAsset,
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
private fun BinaryDropdown(installableAssets: List<Asset>, selectedAsset: Asset?, onSelectAsset: (Asset) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedAsset?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            maxLines = SELECTED_BINARY_MAX_LINES,
            label = { Text(stringResource(R.string.firmware_binary)) },
            placeholder = { Text(stringResource(R.string.select_firmware_binary)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            installableAssets.forEach { asset ->
                DropdownMenuItem(
                    text = { Text(text = asset.name) },
                    onClick = {
                        onSelectAsset(asset)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
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
private fun DetailsFooter(
    mode: UpdateDetailsMode,
    canInstall: Boolean,
    isAlreadyInstalled: Boolean,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (mode is UpdateDetailsMode.Standard) {
            Box(modifier = Modifier.weight(1f)) {
                TextButton(onClick = mode.onSkip) {
                    Text(
                        text = stringResource(R.string.skip_this_version),
                        maxLines = 1,
                    )
                }
            }
        }
        TextButton(onClick = onDismiss) {
            Text(
                text = stringResource(
                    if (mode is UpdateDetailsMode.Standard) R.string.later else R.string.cancel,
                ),
                maxLines = 1,
            )
        }
        Button(
            onClick = onInstall,
            enabled = canInstall,
        ) {
            Text(
                text = stringResource(if (isAlreadyInstalled) R.string.reinstall else R.string.install),
                maxLines = 1,
            )
        }
    }
}
