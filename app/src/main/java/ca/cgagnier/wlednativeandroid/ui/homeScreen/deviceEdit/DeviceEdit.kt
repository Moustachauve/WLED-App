package ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.ui.components.DeviceVisibleSwitch
import ca.cgagnier.wlednativeandroid.ui.components.deviceName
import ca.cgagnier.wlednativeandroid.ui.homeScreen.update.UpdateDetailsDialog
import ca.cgagnier.wlednativeandroid.ui.homeScreen.update.UpdateDisclaimerDialog
import ca.cgagnier.wlednativeandroid.ui.homeScreen.update.UpdateInstallingDialog
import com.vdurmont.semver4j.Semver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DeviceEdit(
    device: Device,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    reloadWebView: () -> Unit,
    viewModel: DeviceEditViewModel = hiltViewModel()
) {
    val options = listOf(
        Pair(Branch.STABLE, stringResource(R.string.french)),
        Pair(Branch.BETA, stringResource(R.string.english)),
    )
    val context = LocalContext.current
    val updateDetailsVersion by viewModel.updateDetailsVersion.collectAsState()
    val updateDisclaimerVersion by viewModel.updateDisclaimerVersion.collectAsState()
    val updateInstallVersion by viewModel.updateInstallVersion.collectAsState()
    val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsState()
    val latestBetaVersion by viewModel.latestBetaVersion.collectAsState()

    Scaffold(
        topBar = {
            DeviceEditAppBar(
                device = device,
                canNavigateBack = canNavigateBack,
                navigateUp = navigateUp,
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground
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
            )
        ) {
            Column(modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(12.dp)) {
                OutlinedTextField(
                    value = device.address,
                    enabled = false,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.ip_address_or_url)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                )
                CustomNameTextField(device) {
                    viewModel.updateCustomName(device, it)
                }
                DeviceVisibleSwitch(
                    modifier = Modifier.padding(top = 4.dp),
                    isHidden = device.isHidden,
                    onCheckedChange = {
                        viewModel.updateDeviceHidden(device, it)
                    }
                )
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
                                    count = options.size
                                ),
                                onClick = {
                                    viewModel.updateDeviceBranch(device, option.first, context)
                                },
                                selected = option.first == device.branch
                            ) {
                                Text(text = option.second)
                            }
                        }
                    }
                }
                Card(modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)) {
                    Column(modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth()) {
                        if (device.hasUpdateAvailable()) {
                            UpdateAvailable(
                                device,
                                seeUpdateDetails = {
                                    viewModel.showUpdateDetails(device)
                                }
                            )
                        } else {
                            NoUpdateAvailable(
                                device,
                                isCheckingUpdates,
                                checkForUpdate = {
                                    viewModel.checkForUpdates(device, context)
                                }
                            )
                        }
                    }
                }
                latestBetaVersion?.let { betaVersion ->
                    if (isBetaVersionNewer(device.version, betaVersion.version.tagName)) {
                        BetaUpdateAvailableCard(
                            betaVersion = betaVersion,
                            onTryBeta = {
                                viewModel.showUpdateDisclaimer(betaVersion)
                            }
                        )
                    }
                }
            }
        }
    }

    updateDetailsVersion?.let { versionDetails ->
        UpdateDetailsDialog(
            device = device,
            version = versionDetails,
            onDismiss = {
                viewModel.hideUpdateDetails()
            },
            onInstall = { versionInstall ->
                viewModel.hideUpdateDetails()
                viewModel.startUpdateInstall(versionInstall)
            },
            onSkip = {
                viewModel.skipUpdate(device, versionDetails)
            }
        )
    }
    updateDisclaimerVersion?.let { versionDisclaimer ->
        UpdateDisclaimerDialog(
            version = versionDisclaimer,
            onDismiss = {
                viewModel.hideUpdateDisclaimer()
            },
            onAccept = {
                viewModel.hideUpdateDisclaimer()
                viewModel.startUpdateInstall(versionDisclaimer)
            }
        )
    }
    updateInstallVersion?.let { version ->
        val coroutineScope = rememberCoroutineScope()
        UpdateInstallingDialog(
            device = device,
            version = version,
            onInstallSuccessful = {
                coroutineScope.launch(Dispatchers.Main) {
                    delay(1000)
                    reloadWebView()
                    viewModel.refreshDevice(device)
                }
            },
            onDismiss = {
                viewModel.stopUpdateInstall()
            },
        )
    }
}

@Composable
private fun CustomNameTextField(
    device: Device,
    onValueChange: (String) -> Unit
) {
    val deviceName = if (device.isCustomName) device.name else ""
    var inputText by remember { mutableStateOf(TextFieldValue(deviceName)) }
    OutlinedTextField(
        value = inputText,
        onValueChange = {
            inputText = it
        },
        label = { Text(stringResource(R.string.custom_name)) },
        supportingText = { Text(stringResource(R.string.leave_this_empty_to_use_the_device_name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    )
    // Only save after changes and after typing has stopped for at least 2 seconds
    LaunchedEffect(key1 = inputText.text) {
        if (deviceName == inputText.text)
            return@LaunchedEffect
        delay(2000)
        onValueChange(inputText.text)
    }
}

@Composable
fun DeviceEditAppBar(
    modifier: Modifier = Modifier,
    device: Device,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.edit_device_with_name, deviceName(device)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent
        ),
        modifier = modifier,
        navigationIcon = {
            AnimatedVisibility(visible = canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.description_back_button)
                    )
                }
            }
        },
    )
}

@Composable
fun NoUpdateAvailable(device: Device, isCheckingUpdates: Boolean, checkForUpdate: () -> Unit) {
    Text(
        stringResource(R.string.your_device_is_up_to_date),
        style = MaterialTheme.typography.titleMedium
    )
    Text(
        stringResource(R.string.version_v_num, device.version),
        style = MaterialTheme.typography.bodyMedium
    )
    OutlinedButton(onClick = checkForUpdate) {
        val buttonText =
            if (isCheckingUpdates) stringResource(R.string.checking_progress_update)
            else stringResource(R.string.check_for_update)
        AnimatedContent(
            targetState = buttonText,
            transitionSpec = {
                scaleIn() togetherWith fadeOut()
            },
            label = ""
        ) {
            Text(it)
        }

        AnimatedVisibility(
            visible = isCheckingUpdates
        ) {
            val size = (MaterialTheme.typography.titleSmall.lineHeight.value - 4)
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

@Composable
fun UpdateAvailable(device: Device, seeUpdateDetails: () -> Unit) {
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
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.from_version_to_version, device.version, device.newUpdateVersionTagAvailable),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                modifier = Modifier.padding(top = 6.dp),
                onClick = seeUpdateDetails
            ) {
                Text(stringResource(R.string.see_update_details))
            }
        }
    }
}

@Composable
fun BetaUpdateAvailableCard(
    betaVersion: VersionWithAssets,
    onTryBeta: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val betaCloudAppUrl = stringResource(R.string.beta_cloudapp_url)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.baseline_experiment_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    stringResource(R.string.beta_version_available, betaVersion.version.tagName),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable {
                        uriHandler.openUri(betaCloudAppUrl)
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
            Text(
                stringResource(R.string.beta_migration_card_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            OutlinedButton(
                modifier = Modifier.padding(top = 8.dp),
                onClick = onTryBeta,
            ) {
                Text(stringResource(R.string.try_new_beta_version))
            }
        }
    }
}

private fun isBetaVersionNewer(deviceVersion: String, betaTagName: String): Boolean {
    if (deviceVersion.isEmpty() || deviceVersion == Device.UNKNOWN_VALUE) {
        return false
    }
    return try {
        Semver(betaTagName.removePrefix("v"), Semver.SemverType.LOOSE)
            .isGreaterThan(deviceVersion)
    } catch (_: Exception) {
        false
    }
}