package ca.cgagnier.wlednativeandroid.ui.homeScreen.firmwareBrowser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.ui.components.deviceName
import ca.cgagnier.wlednativeandroid.ui.homeScreen.update.UpdateDisclaimerDialog
import ca.cgagnier.wlednativeandroid.ui.homeScreen.update.UpdateInstallingDialog
import ca.cgagnier.wlednativeandroid.ui.preview.getPreviewDevice
import ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private const val ISO_DATE_PREFIX_LENGTH = 10

@Composable
fun FirmwareBrowserScreen(
    macAddress: String,
    navigateUp: () -> Unit,
    viewModel: FirmwareBrowserViewModel = koinViewModel(
        key = "FirmwareBrowser:$macAddress",
        parameters = { parametersOf(macAddress) },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        FirmwareBrowserActions(
            onSelectRepository = viewModel::selectRepository,
            onSelectBranch = viewModel::selectBranch,
            onRefresh = viewModel::refreshSelectedRepository,
            onVersionClick = viewModel::showVersionDetails,
            onDismissVersionDetails = viewModel::hideVersionDetails,
            onSelectBinaryAsset = viewModel::selectBinaryAsset,
            onRequestInstall = viewModel::requestInstallSelectedVersion,
            onDismissDisclaimer = viewModel::dismissDisclaimer,
            onConfirmDisclaimer = viewModel::confirmDisclaimer,
            onInstallFinished = viewModel::onInstallFinished,
        )
    }

    FirmwareBrowserContent(
        uiState = uiState,
        actions = actions,
        navigateUp = navigateUp,
    )
}

@Composable
fun FirmwareBrowserContent(
    uiState: FirmwareBrowserUiState,
    actions: FirmwareBrowserActions,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            FirmwareBrowserTopBar(
                deviceTitle = uiState.device?.let { deviceName(it.device) },
                isLoading = uiState.isLoadingVersions,
                navigateUp = navigateUp,
                onRefresh = actions.onRefresh,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxSize(),
            ) {
                FirmwareBrowserHeader(uiState = uiState, actions = actions)
                VersionListContainer(
                    uiState = uiState,
                    onRefresh = actions.onRefresh,
                    onVersionClick = actions.onVersionClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    FirmwareBrowserDialogs(
        uiState = uiState,
        actions = actions,
        navigateUp = navigateUp,
    )
}

@Composable
private fun FirmwareBrowserHeader(uiState: FirmwareBrowserUiState, actions: FirmwareBrowserActions) {
    val installedRepositoryId = uiState.device?.device?.repositoryId
    val selectedRepository = uiState.selectedRepository
    RepositoryDropdown(
        repositories = uiState.repositories,
        selectedRepository = selectedRepository,
        installedRepositoryId = installedRepositoryId,
        onSelectRepository = actions.onSelectRepository,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
    if (selectedRepository != null &&
        installedRepositoryId != null &&
        selectedRepository.id != installedRepositoryId
    ) {
        SourceSwitchWarningBanner(
            sourceName = selectedRepository.name,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        )
    }
    BranchTabs(
        selectedBranch = uiState.selectedBranch,
        onSelectBranch = actions.onSelectBranch,
    )
}

@Composable
private fun FirmwareBrowserDialogs(
    uiState: FirmwareBrowserUiState,
    actions: FirmwareBrowserActions,
    navigateUp: () -> Unit,
) {
    val device = uiState.device

    uiState.versionDetails?.let { details ->
        FirmwareVersionDetailsDialog(
            details = details,
            device = device,
            repositoryOwnerAndRepo = uiState.selectedRepository?.ownerAndRepo,
            actions = actions,
        )
    }

    uiState.disclaimerTarget?.let { target ->
        val targetRepoId = target.versionWithAssets.version.repositoryId
        val switchingToSourceName = if (device != null && targetRepoId != device.device.repositoryId) {
            uiState.repositories.firstOrNull { it.id == targetRepoId }?.name
        } else {
            null
        }
        UpdateDisclaimerDialog(
            onDismiss = actions.onDismissDisclaimer,
            onAccept = actions.onConfirmDisclaimer,
            switchingToSourceName = switchingToSourceName,
        )
    }

    val installingTarget = uiState.installingTarget
    if (installingTarget != null && device != null) {
        UpdateInstallingDialog(
            device = device,
            version = installingTarget.versionWithAssets,
            selectedAsset = installingTarget.selectedAsset,
            onDismiss = { wasSuccessful ->
                actions.onInstallFinished()
                if (wasSuccessful) {
                    navigateUp()
                }
            },
        )
    }
}

@Composable
private fun FirmwareBrowserTopBar(
    deviceTitle: String?,
    isLoading: Boolean,
    navigateUp: () -> Unit,
    onRefresh: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.firmware_versions),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!deviceTitle.isNullOrBlank()) {
                    Text(
                        text = deviceTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = navigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.description_back_button),
                )
            }
        },
        actions = {
            IconButton(
                onClick = onRefresh,
                enabled = !isLoading,
            ) {
                Icon(
                    painter = painterResource(R.drawable.outline_refresh_24),
                    contentDescription = stringResource(R.string.refresh_versions),
                )
            }
        },
    )
}

@Composable
private fun RepositoryDropdown(
    repositories: List<Repository>,
    selectedRepository: Repository?,
    installedRepositoryId: Long?,
    onSelectRepository: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedRepository?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.update_source)) },
            supportingText = selectedRepository?.let { repo ->
                {
                    Text(
                        text = repo.ownerAndRepo,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            trailingIcon = {
                RepositoryTrailingContent(
                    selectedRepository = selectedRepository,
                    installedRepositoryId = installedRepositoryId,
                    expanded = expanded,
                )
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            repositories.forEach { repo ->
                DropdownMenuItem(
                    text = {
                        RepositoryMenuItemContent(
                            repository = repo,
                            isInstalled = repo.id == installedRepositoryId,
                        )
                    },
                    onClick = {
                        onSelectRepository(repo.id)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun RepositoryTrailingContent(
    selectedRepository: Repository?,
    installedRepositoryId: Long?,
    expanded: Boolean,
) {
    Row(
        modifier = Modifier.padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selectedRepository?.isOfficial == true) {
            OfficialBadge()
        }
        if (selectedRepository != null && selectedRepository.id == installedRepositoryId) {
            InstalledBadge()
        }
        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepositoryMenuItemContent(repository: Repository, isInstalled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = repository.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isInstalled) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (repository.isOfficial) {
                OfficialBadge()
            }
            if (isInstalled) {
                InstalledBadge()
            }
        }
        Text(
            text = repository.ownerAndRepo,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourceSwitchWarningBanner(sourceName: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.switching_source_warning, sourceName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun BranchTabs(selectedBranch: Branch, onSelectBranch: (Branch) -> Unit) {
    val tabs = listOf(
        Branch.STABLE to stringResource(R.string.stable),
        Branch.BETA to stringResource(R.string.beta),
    )
    val selectedIndex = tabs.indexOfFirst { it.first == selectedBranch }.coerceAtLeast(0)

    PrimaryTabRow(selectedTabIndex = selectedIndex) {
        tabs.forEachIndexed { index, (branch, title) ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelectBranch(branch) },
                text = { Text(title) },
            )
        }
    }
}

@Composable
private fun VersionListContainer(
    uiState: FirmwareBrowserUiState,
    onRefresh: () -> Unit,
    onVersionClick: (VersionWithAssets) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        when {
            uiState.isLoadingVersions && uiState.versions.isEmpty() -> {
                LoadingVersionsPlaceholder(modifier = Modifier.align(Alignment.Center))
            }

            uiState.loadError && uiState.versions.isEmpty() -> {
                LoadErrorPlaceholder(
                    onRefresh = onRefresh,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            uiState.versions.isEmpty() -> {
                Text(
                    text = stringResource(R.string.no_versions_available),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            }

            else -> {
                val installedVersion = uiState.device?.stateInfo?.info?.version
                val deviceRepositoryId = uiState.device?.device?.repositoryId
                val installedTags = remember(uiState.versions, installedVersion, deviceRepositoryId) {
                    if (deviceRepositoryId == null || installedVersion.isNullOrBlank()) {
                        emptySet()
                    } else {
                        uiState.versions
                            .asSequence()
                            .filter { it.matchesInstalledVersion(installedVersion, deviceRepositoryId) }
                            .mapTo(mutableSetOf()) { it.version.tagName }
                    }
                }
                val listState = key(uiState.selectedRepositoryId, uiState.selectedBranch) {
                    rememberLazyListState()
                }
                VersionListContent(
                    versions = uiState.versions,
                    installedTags = installedTags,
                    isLoadingVersions = uiState.isLoadingVersions,
                    listState = listState,
                    onVersionClick = onVersionClick,
                )
            }
        }
    }
}

@Composable
private fun LoadingVersionsPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.loading_versions),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadErrorPlaceholder(onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.failed_to_load_versions),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRefresh) {
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun VersionListContent(
    versions: List<VersionWithAssets>,
    installedTags: Set<String>,
    isLoadingVersions: Boolean,
    listState: LazyListState,
    onVersionClick: (VersionWithAssets) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (isLoadingVersions) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = versions,
                key = { "${it.version.repositoryId}:${it.version.tagName}" },
                contentType = { "version" },
            ) { item ->
                VersionRowCard(
                    item = item,
                    isInstalled = item.version.tagName in installedTags,
                    onClick = { onVersionClick(item) },
                )
            }
        }
    }
}

@Composable
private fun VersionRowCard(item: VersionWithAssets, isInstalled: Boolean, onClick: () -> Unit) {
    val border = if (isInstalled) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        null
    }
    val containerColor = if (isInstalled) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = border,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.version.tagName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isInstalled) FontWeight.SemiBold else FontWeight.Normal,
                )
                val subtitle = buildVersionSubtitle(item.version)
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
            if (isInstalled) {
                InstalledBadge()
            }
        }
    }
}

@Composable
private fun OfficialBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            text = stringResource(R.string.official_badge),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun InstalledBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_twotone_check_circle_outline_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.installed_badge),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun buildVersionSubtitle(version: Version): String {
    val datePrefix = version.publishedDate.take(ISO_DATE_PREFIX_LENGTH)
    return buildList {
        version.distinctReleaseName?.let { add(it) }
        if (datePrefix.isNotBlank()) {
            add(datePrefix)
        }
    }.joinToString(" • ")
}

@Preview(showBackground = true)
@Composable
private fun FirmwareBrowserScreenPreview() {
    val repos = Repository.BUILT_IN_REPOSITORIES.mapIndexed { index, repo ->
        repo.copy(id = (index + 1).toLong())
    }
    val previewDevice = getPreviewDevice().copy(
        stateInfo = DeviceStateInfo.createMock(version = "0.14.4"),
    )
    val installedTag = "v${previewDevice.stateInfo?.info?.version ?: "0.14.4"}"
    val sampleVersions = listOf(
        VersionWithAssets(
            version = Version(
                id = 1L,
                repositoryId = 1L,
                tagName = "v0.15.0",
                name = "WLED 0.15.0",
                description = "### Changes\n- Improved effects",
                isPrerelease = false,
                publishedDate = "2025-01-15T12:00:00Z",
                htmlUrl = "https://github.com/wled/WLED/releases/tag/v0.15.0",
            ),
            assets = listOf(
                Asset(
                    id = 10L,
                    versionId = 1L,
                    name = "WLED_0.15.0_ESP32.bin",
                    size = 1_200_000L,
                    downloadUrl = "https://github.com/wled/WLED/releases/download/v0.15.0/WLED_0.15.0_ESP32.bin",
                    assetId = 100,
                ),
            ),
        ),
        VersionWithAssets(
            version = Version(
                id = 2L,
                repositoryId = 1L,
                tagName = installedTag,
                name = "Installed Release",
                description = "Installed version",
                isPrerelease = false,
                publishedDate = "2024-06-01T12:00:00Z",
                htmlUrl = "https://github.com/wled/WLED/releases/tag/$installedTag",
            ),
            assets = emptyList(),
        ),
    )

    WLEDNativeTheme(darkTheme = false) {
        FirmwareBrowserContent(
            uiState = FirmwareBrowserUiState(
                device = previewDevice,
                repositories = repos,
                selectedRepositoryId = 1L,
                selectedBranch = Branch.STABLE,
                versions = sampleVersions,
            ),
            actions = FirmwareBrowserActions(),
            navigateUp = {},
        )
    }
}
