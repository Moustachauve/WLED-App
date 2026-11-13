package ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.AP_MODE_MAC_ADDRESS
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.UpdateSourceRegistry
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import ca.cgagnier.wlednativeandroid.service.update.UpdateIneligibilityReason
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import ca.cgagnier.wlednativeandroid.ui.components.DeviceVisibleSwitch
import ca.cgagnier.wlednativeandroid.ui.components.deviceName
import ca.cgagnier.wlednativeandroid.ui.homeScreen.update.UpdateDetailsDialog
import ca.cgagnier.wlednativeandroid.ui.homeScreen.update.UpdateDisclaimerDialog
import ca.cgagnier.wlednativeandroid.ui.homeScreen.update.UpdateInstallingDialog
import ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme
import ca.cgagnier.wlednativeandroid.util.openUriSafely
import ca.cgagnier.wlednativeandroid.util.stripVersionPrefix
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/**
 * ViewModel-aware entry point for the device edit screen.
 *
 * Collects state from [DeviceEditViewModel], builds a [DeviceEditUiState]
 * and [DeviceEditActions], then delegates rendering to [DeviceEditContent].
 */
@Composable
fun DeviceEdit(
    device: DeviceWithState,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    reloadWebView: () -> Unit = {},
    viewModel: DeviceEditViewModel = koinViewModel(key = "DeviceEdit:${device.device.macAddress}"),
) {
    val vmState by viewModel.uiState.collectAsState()

    LaunchedEffect(device.device.macAddress, device.device.repositoryId) {
        viewModel.loadRepository(device.device.repositoryId)
    }

    // Merge the device-owned updateTag into the VM state.
    val uiState = vmState.copy(updateTag = device.updateVersionTag)

    val actions = DeviceEditActions(
        onCustomNameChange = { viewModel.updateCustomName(device.device, it) },
        onDeviceHiddenChange = { viewModel.updateDeviceHidden(device.device, it) },
        onBranchChange = { viewModel.updateDeviceBranch(device, it) },
        onCheckForUpdates = { viewModel.checkForUpdates(device.device) },
        onSeeUpdateDetails = { tag ->
            if (tag.isNotEmpty()) {
                viewModel.showUpdateDetails(device.device.repositoryId, tag)
            }
        },
        onHideUpdateDetails = { viewModel.hideUpdateDetails() },
        onSkipUpdate = { version -> viewModel.skipUpdate(device.device, version) },
        onInstallUpdate = { version ->
            viewModel.hideUpdateDetails()
            viewModel.startUpdateInstall(version)
        },
        onTryBeta = { betaVersion ->
            viewModel.showUpdateDisclaimer(betaVersion)
        },
        onHideUpdateDisclaimer = { viewModel.hideUpdateDisclaimer() },
        onAcceptUpdateDisclaimer = { version ->
            viewModel.hideUpdateDisclaimer()
            viewModel.startUpdateInstall(version)
        },
        onInstallFinished = { wasSuccessful ->
            viewModel.stopUpdateInstall(device = device, wasSuccessful = wasSuccessful)
            if (wasSuccessful) {
                reloadWebView()
            }
        },
    )

    DeviceEditContent(
        device = device,
        uiState = uiState,
        actions = actions,
        canNavigateBack = canNavigateBack,
        navigateUp = navigateUp,
    )
}

/**
 * Stateless device edit screen content.
 *
 * Receives all data via [uiState] and all callbacks via [actions],
 * making it fully preview-friendly with no ViewModel dependency.
 */
@Composable
@Suppress("LongMethod") // Scaffold layout nesting makes this long but logically cohesive.
fun DeviceEditContent(
    device: DeviceWithState,
    uiState: DeviceEditUiState,
    actions: DeviceEditActions,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
) {
    val branchOptions = listOf(
        Pair(Branch.STABLE, stringResource(R.string.french)),
        Pair(Branch.BETA, stringResource(R.string.english)),
    )

    Scaffold(
        topBar = {
            DeviceEditAppBar(
                device = device,
                canNavigateBack = canNavigateBack,
                navigateUp = navigateUp,
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) { innerPadding ->
        Card(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 3.dp)
                .fillMaxHeight()
                .height(IntrinsicSize.Max),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                OutlinedTextField(
                    value = device.device.address,
                    enabled = false,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.ip_address_or_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CustomNameTextField(device.device, actions.onCustomNameChange)
                DeviceVisibleSwitch(
                    modifier = Modifier.padding(top = 4.dp),
                    isHidden = device.device.isHidden,
                    onCheckedChange = actions.onDeviceHiddenChange,
                )
                val rawBranch = uiState.installTargetBranch ?: device.device.branch
                val selectedBranch = if (rawBranch == Branch.BETA) Branch.BETA else Branch.STABLE
                val languageDisabledReason = languageDisabledMessage(device)
                BranchSelector(
                    options = branchOptions,
                    selectedBranch = selectedBranch,
                    enabled = languageDisabledReason == null,
                    disabledReason = languageDisabledReason?.let { stringResource(it) },
                    onBranchChange = actions.onBranchChange,
                )
                UpdateStatusCard(
                    device = device,
                    uiState = uiState,
                    onCheckForUpdates = actions.onCheckForUpdates,
                    onSeeUpdateDetails = actions.onSeeUpdateDetails,
                )
                uiState.latestBetaVersion?.let { betaVersion ->
                    if (ReleaseService.isBetaUpdateAvailable(device.stateInfo?.info, betaVersion.version.tagName)) {
                        BetaUpdateAvailableCard(
                            betaVersion = betaVersion,
                            enabled = device.isOnline,
                            onTryBeta = { actions.onTryBeta(betaVersion) },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(
                        R.string.mac_address_present,
                        device.device.macAddress,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    // Dialogs shown conditionally based on uiState.
    uiState.updateDetailsVersion?.let { versionDetails ->
        UpdateDetailsDialog(
            device = device,
            version = versionDetails,
            onDismiss = actions.onHideUpdateDetails,
            onInstall = actions.onInstallUpdate,
            onSkip = { actions.onSkipUpdate(versionDetails) },
        )
    }
    uiState.updateDisclaimerVersion?.let { versionDisclaimer ->
        UpdateDisclaimerDialog(
            version = versionDisclaimer,
            installEnabled = device.isOnline,
            onDismiss = actions.onHideUpdateDisclaimer,
            onAccept = { actions.onAcceptUpdateDisclaimer(versionDisclaimer) },
        )
    }
    uiState.updateInstallVersion?.let { version ->
        val installDevice = uiState.installTargetBranch?.let { targetBranch ->
            device.copy(device = device.device.copy(branch = targetBranch))
        } ?: device
        UpdateInstallingDialog(
            device = installDevice,
            version = version,
            onDismiss = actions.onInstallFinished,
        )
    }
}

// ---------------------------------------------------------------------------
// Private sub-composables
// ---------------------------------------------------------------------------

/** Top app bar showing the device name and an optional close button. */
@Composable
fun DeviceEditAppBar(
    modifier: Modifier = Modifier,
    device: DeviceWithState,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(
                    R.string.edit_device_with_name,
                    deviceName(device.device),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        ),
        modifier = modifier,
        navigationIcon = {
            AnimatedVisibility(visible = canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(
                            R.string.description_back_button,
                        ),
                    )
                }
            }
        },
    )
}

/** Returns the string resource explaining why the language can't be changed, or null if it can. */
@StringRes
private fun languageDisabledMessage(device: DeviceWithState): Int? {
    val info = device.stateInfo?.info
    return when {
        !device.isOnline || info == null -> R.string.language_unavailable_offline

        else -> when (ReleaseService.getIneligibilityReason(info)) {
            UpdateIneligibilityReason.NOT_ILLUMIDEL -> R.string.language_unavailable_not_illumidel
            UpdateIneligibilityReason.OTA_DISABLED -> R.string.language_unavailable_ota_disabled
            null -> null
        }
    }
}

/** French / English segmented button row for firmware language selection. */
@Composable
private fun BranchSelector(
    options: List<Pair<Branch, String>>,
    selectedBranch: Branch,
    enabled: Boolean = true,
    disabledReason: String? = null,
    onBranchChange: (Branch) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.device_language),
                maxLines = 2,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            SingleChoiceSegmentedButtonRow {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size,
                        ),
                        enabled = enabled,
                        onClick = { onBranchChange(option.first) },
                        selected = option.first == selectedBranch,
                    ) {
                        Text(text = option.second)
                    }
                }
            }
        }
        if (!enabled && disabledReason != null) {
            Text(
                text = disabledReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Card showing the current version or available stable update. */
@Composable
private fun UpdateStatusCard(
    device: DeviceWithState,
    uiState: DeviceEditUiState,
    onCheckForUpdates: () -> Unit,
    onSeeUpdateDetails: (String) -> Unit,
) {
    val isOfflineNoState = !device.isOnline && device.stateInfo == null
    val updateDisabledMessage = device.stateInfo?.info
        ?.let(ReleaseService::getIneligibilityReason)
        ?.let { reason ->
            when (reason) {
                UpdateIneligibilityReason.NOT_ILLUMIDEL -> R.string.update_unavailable_not_illumidel
                UpdateIneligibilityReason.OTA_DISABLED -> R.string.update_unavailable_ota_disabled
            }
        }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
        ) {
            if (isOfflineNoState) {
                DeviceOfflineStatus()
            } else if (updateDisabledMessage != null) {
                Text(
                    text = stringResource(updateDisabledMessage),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                val currentUpdateTag = uiState.updateTag
                if (currentUpdateTag != null) {
                    UpdateAvailable(
                        device = device,
                        updateTag = currentUpdateTag,
                        seeUpdateDetails = { onSeeUpdateDetails(currentUpdateTag) },
                    )
                } else {
                    NoUpdateAvailable(
                        device = device,
                        isCheckingUpdates = uiState.isCheckingUpdates,
                        checkForUpdate = onCheckForUpdates,
                    )
                }
            }
        }
    }
}

/** Opt-in Beta 2.0 migration card with external Cloud App info link. */
@Composable
fun BetaUpdateAvailableCard(betaVersion: VersionWithAssets, enabled: Boolean = true, onTryBeta: () -> Unit) {
    val displayTag = "v${betaVersion.version.tagName.stripVersionPrefix()}"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.baseline_experiment_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    stringResource(R.string.beta_version_available, displayTag),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            BetaLearnMoreLink()
            Text(
                stringResource(R.string.beta_migration_card_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            OutlinedButton(
                modifier = Modifier.padding(top = 8.dp),
                enabled = enabled,
                onClick = onTryBeta,
            ) {
                Text(stringResource(R.string.try_new_beta_version))
            }
        }
    }
}

@Composable
private fun BetaLearnMoreLink() {
    val uriHandler = LocalUriHandler.current
    val betaCloudAppUrl = stringResource(R.string.beta_cloudapp_url)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 6.dp)
            .clickable {
                uriHandler.openUriSafely(betaCloudAppUrl)
            },
    ) {
        Text(
            stringResource(R.string.learn_more_beta_app),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            painter = painterResource(R.drawable.ic_baseline_launch_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 4.dp)
                .width(16.dp)
                .height(16.dp),
        )
    }
}

/** Shows an informational message when the device is offline and has no known state. */
@Composable
private fun DeviceOfflineStatus() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            modifier = Modifier
                .padding(8.dp)
                .padding(end = 10.dp),
            painter = painterResource(R.drawable.outline_wifi_off_24),
            contentDescription = stringResource(R.string.is_offline),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column {
            Text(
                stringResource(R.string.device_offline_update_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.device_offline_update_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Editable custom name field with debounced saves.
 *
 * Only persists changes after typing stops for 800 ms.
 */
@Composable
private fun CustomNameTextField(device: Device, onValueChange: (String) -> Unit) {
    val deviceName = device.customName
    var inputText by remember { mutableStateOf(TextFieldValue(deviceName)) }
    OutlinedTextField(
        value = inputText,
        onValueChange = { inputText = it },
        label = { Text(stringResource(R.string.custom_name)) },
        supportingText = {
            Text(stringResource(R.string.leave_this_empty_to_use_the_device_name))
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    )
    LaunchedEffect(key1 = inputText.text) {
        if (deviceName == inputText.text) return@LaunchedEffect
        delay(800)
        onValueChange(inputText.text)
    }
}

/** Shows the current version and a "check for update" button. */
@Composable
private fun NoUpdateAvailable(device: DeviceWithState, isCheckingUpdates: Boolean, checkForUpdate: () -> Unit) {
    Text(
        stringResource(R.string.your_device_is_up_to_date),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        stringResource(
            R.string.version_v_num,
            device.stateInfo?.info?.version ?: "<?>",
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedButton(onClick = checkForUpdate) {
        val buttonText = if (isCheckingUpdates) {
            stringResource(R.string.checking_progress_update)
        } else {
            stringResource(R.string.check_for_update)
        }
        AnimatedContent(
            targetState = buttonText,
            transitionSpec = { scaleIn() togetherWith fadeOut() },
            label = "check_for_update_label",
        ) {
            Text(it)
        }
        AnimatedVisibility(visible = isCheckingUpdates) {
            val size = MaterialTheme.typography.titleSmall.lineHeight.value - 4
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .padding(bottom = 2.dp)
                    .width(size.dp)
                    .height(size.dp),
            )
        }
    }
}

/** Shows the available version and a button to view release details. */
@Composable
private fun UpdateAvailable(device: DeviceWithState, updateTag: String, seeUpdateDetails: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            modifier = Modifier
                .padding(8.dp)
                .padding(end = 10.dp),
            painter = painterResource(R.drawable.baseline_download_24),
            contentDescription = stringResource(R.string.update_available),
        )
        Column {
            Text(
                stringResource(R.string.update_available),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(
                    R.string.from_version_to_version,
                    device.stateInfo?.info?.version ?: "<?>",
                    updateTag,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                modifier = Modifier.padding(top = 6.dp),
                onClick = seeUpdateDetails,
            ) {
                Text(stringResource(R.string.see_update_details))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

/** Creates a [DeviceWithState] populated with dummy data for previews. */
private fun previewDevice(
    name: String = "Living Room LEDs",
    address: String = "192.168.1.42",
    version: String = "1.5.2",
    branch: Branch = Branch.STABLE,
): DeviceWithState = DeviceWithState(
    device = Device(
        macAddress = AP_MODE_MAC_ADDRESS,
        address = address,
        originalName = name,
        branch = branch,
    ),
    websocketStatus = WebsocketStatus.CONNECTED,
    stateInfo = DeviceStateInfo(
        state = State(isOn = true, brightness = 200, transition = 7),
        info = Info(
            version = version,
            leds = Leds(count = 144),
            wifi = Wifi(
                bssid = "aa:bb:cc:dd:ee:ff",
                rssi = -60,
                signal = 75,
                channel = 6,
            ),
            name = name,
            brand = UpdateSourceRegistry.ILLUMIDEL_BRAND,
        ),
    ),
)

@Preview(name = "Up to date - Dark", showBackground = true)
@Composable
private fun PreviewUpToDateDark() {
    WLEDNativeTheme(darkTheme = true) {
        DeviceEditContent(
            device = previewDevice(),
            uiState = DeviceEditUiState(),
            actions = DeviceEditActions(),
            canNavigateBack = true,
            navigateUp = {},
        )
    }
}

@Preview(name = "Checking for update", showBackground = true)
@Composable
private fun PreviewCheckingUpdate() {
    WLEDNativeTheme(darkTheme = true) {
        DeviceEditContent(
            device = previewDevice(),
            uiState = DeviceEditUiState(
                isCheckingUpdates = true,
            ),
            actions = DeviceEditActions(),
            canNavigateBack = true,
            navigateUp = {},
        )
    }
}

@Preview(name = "Update available", showBackground = true)
@Composable
private fun PreviewUpdateAvailable() {
    WLEDNativeTheme(darkTheme = true) {
        DeviceEditContent(
            device = previewDevice(version = "1.5.2"),
            uiState = DeviceEditUiState(
                updateTag = "1.5.3",
            ),
            actions = DeviceEditActions(),
            canNavigateBack = true,
            navigateUp = {},
        )
    }
}

@Preview(name = "English language - no back nav", showBackground = true)
@Composable
private fun PreviewEnglishNoBack() {
    WLEDNativeTheme(darkTheme = true) {
        DeviceEditContent(
            device = previewDevice(branch = Branch.BETA),
            uiState = DeviceEditUiState(),
            actions = DeviceEditActions(),
            canNavigateBack = false,
            navigateUp = {},
        )
    }
}

@Preview(name = "Offline - no state", showBackground = true)
@Composable
private fun PreviewOfflineNoState() {
    WLEDNativeTheme(darkTheme = true) {
        DeviceEditContent(
            device = DeviceWithState(
                Device(
                    macAddress = AP_MODE_MAC_ADDRESS,
                    address = "192.168.1.42",
                    originalName = "Garage LEDs",
                ),
            ),
            uiState = DeviceEditUiState(),
            actions = DeviceEditActions(),
            canNavigateBack = true,
            navigateUp = {},
        )
    }
}
