package ca.cgagnier.wlednativeandroid.ui.homeScreen.firmwareBrowser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.ui.components.deviceName
import ca.cgagnier.wlednativeandroid.ui.homeScreen.update.UpdateDetailsContent
import ca.cgagnier.wlednativeandroid.ui.homeScreen.update.UpdateDetailsMode
import ca.cgagnier.wlednativeandroid.ui.homeScreen.update.UpdateDisclaimerDialog
import ca.cgagnier.wlednativeandroid.ui.homeScreen.update.UpdateInstallingDialog
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun FirmwareBrowserScreen(
    macAddress: String,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FirmwareBrowserViewModel = koinViewModel(
        key = "FirmwareBrowser:$macAddress",
        parameters = { parametersOf(macAddress) },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
        .copy(horizontalPartitionSpacerSize = 0.dp)
    val navigator = rememberListDetailPaneScaffoldNavigator<Unit>(scaffoldDirective = scaffoldDirective)
    val coroutineScope = rememberCoroutineScope()
    val isTwoPane =
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded &&
            navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded

    LaunchedEffect(isTwoPane, uiState.selectedVersion, uiState.versions) {
        if (uiState.selectedVersion == null) {
            if (navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Detail) {
                navigator.navigateBack(BackNavigationBehavior.PopUntilCurrentDestinationChange)
            }
            if (isTwoPane && uiState.versions.isNotEmpty()) viewModel.selectVersion(uiState.versions.first())
        }
    }

    Scaffold(modifier = modifier) { innerPadding ->
        NavigableListDetailPaneScaffold(
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding).imePadding(),
            navigator = navigator,
            defaultBackBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange,
            listPane = {
                AnimatedPane {
                    FirmwareBrowserListPane(
                        uiState = uiState,
                        highlightSelection = isTwoPane,
                        viewModel = viewModel,
                        navigateUp = navigateUp,
                        onOpenDetail = {
                            if (navigator.currentDestination?.pane != ListDetailPaneScaffoldRole.Detail) {
                                coroutineScope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail) }
                            }
                        },
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    FirmwareBrowserDetailPane(
                        uiState = uiState,
                        canNavigateBack = !isTwoPane && navigator.canNavigateBack(),
                        navigateBack = { coroutineScope.launch { navigator.navigateBack() } },
                        viewModel = viewModel,
                    )
                }
            },
        )
    }

    FirmwareBrowserDialogs(uiState = uiState, viewModel = viewModel, navigateUp = navigateUp)
}

@Composable
private fun FirmwareBrowserListPane(
    uiState: FirmwareBrowserUiState,
    highlightSelection: Boolean,
    viewModel: FirmwareBrowserViewModel,
    navigateUp: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    Scaffold(
        topBar = {
            FirmwareBrowserListTopBar(
                deviceTitle = uiState.device?.let { deviceName(it.device) },
                isLoading = uiState.isLoadingVersions,
                navigateUp = navigateUp,
                onRefresh = viewModel::refreshSelectedRepository,
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val installedRepoId = uiState.device?.device?.repositoryId
            val selectedRepo = uiState.selectedRepository
            RepositoryDropdown(
                uiState = uiState,
                installedRepoId = installedRepoId,
                onSelect = viewModel::selectRepository,
            )
            if (selectedRepo != null && installedRepoId != null && selectedRepo.id != installedRepoId) {
                SourceSwitchWarningBanner(selectedRepo.name)
            }
            BranchTabs(uiState.selectedBranch, viewModel::selectBranch)
            VersionListSection(
                uiState = uiState,
                highlightSelection = highlightSelection,
                onRefresh = viewModel::refreshSelectedRepository,
                onVersionClick = { item ->
                    viewModel.selectVersion(item)
                    onOpenDetail()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FirmwareBrowserDetailPane(
    uiState: FirmwareBrowserUiState,
    canNavigateBack: Boolean,
    navigateBack: () -> Unit,
    viewModel: FirmwareBrowserViewModel,
) {
    val selected = uiState.selectedVersion
    if (selected == null) {
        SelectVersionPlaceholder()
        return
    }

    Scaffold(
        topBar = {
            DetailPaneTopBar(
                version = selected.versionWithAssets,
                repositoryOwnerAndRepo = uiState.selectedRepository?.ownerAndRepo,
                canNavigateBack = canNavigateBack,
                navigateBack = navigateBack,
            )
        },
    ) { innerPadding ->
        Card(
            modifier = Modifier
                .padding(innerPadding)
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)
                .fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            UpdateDetailsContent(
                device = uiState.device,
                version = selected.versionWithAssets,
                mode = UpdateDetailsMode.BinaryPicker(
                    installableAssets = selected.installableAssets,
                    selectedAsset = selected.selectedAsset,
                    onSelectAsset = viewModel::selectBinaryAsset,
                ),
                onInstall = { viewModel.requestInstallSelectedVersion() },
            )
        }
    }
}

@Composable
private fun DetailPaneTopBar(
    version: VersionWithAssets,
    repositoryOwnerAndRepo: String?,
    canNavigateBack: Boolean,
    navigateBack: () -> Unit,
) {
    val subtitle = listOfNotNull(
        version.version.distinctReleaseName,
        repositoryOwnerAndRepo?.takeIf { it.isNotBlank() },
    ).joinToString(" • ")

    TopAppBar(
        title = {
            Column {
                Text(version.version.tagName, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        },
        navigationIcon = {
            AnimatedVisibility(visible = canNavigateBack) {
                IconButton(onClick = navigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.description_back_button))
                }
            }
        },
    )
}

@Composable
private fun SelectVersionPlaceholder() {
    Card(
        modifier = Modifier
            .padding(top = TopAppBarDefaults.MediumAppBarCollapsedHeight)
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)
            .fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.select_a_version_from_the_list),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FirmwareBrowserDialogs(
    uiState: FirmwareBrowserUiState,
    viewModel: FirmwareBrowserViewModel,
    navigateUp: () -> Unit,
) {
    val device = uiState.device
    when (val dialog = uiState.dialog) {
        is FirmwareBrowserDialog.Disclaimer -> UpdateDisclaimerDialog(
            onDismiss = viewModel::dismissDisclaimer,
            onAccept = viewModel::confirmDisclaimer,
            switchingToSourceName = dialog.switchingToSourceName,
        )

        is FirmwareBrowserDialog.Installing -> if (device != null) {
            UpdateInstallingDialog(
                device = device,
                version = dialog.versionWithAssets,
                selectedAsset = dialog.selectedAsset,
                onDismiss = { wasSuccessful ->
                    viewModel.onInstallFinished()
                    if (wasSuccessful) navigateUp()
                },
            )
        }

        null -> Unit
    }
}

@Composable
private fun FirmwareBrowserListTopBar(
    deviceTitle: String?,
    isLoading: Boolean,
    navigateUp: () -> Unit,
    onRefresh: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.firmware_versions), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.description_back_button))
            }
        },
        actions = {
            IconButton(onClick = onRefresh, enabled = !isLoading) {
                Icon(painterResource(R.drawable.outline_refresh_24), stringResource(R.string.refresh_page))
            }
        },
    )
}

@Composable
private fun RepositoryDropdown(uiState: FirmwareBrowserUiState, installedRepoId: Long?, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = uiState.selectedRepository?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.update_source)) },
            supportingText = uiState.selectedRepository?.let { repo ->
                {
                    Text(
                        text = repositoryStatusSubtitle(repo, repo.id == installedRepoId),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            uiState.repositories.forEach { repo ->
                val isInstalled = repo.id == installedRepoId
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = repo.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isInstalled) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Text(
                                text = repositoryStatusSubtitle(repo, isInstalled),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelect(repo.id)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun repositoryStatusSubtitle(repository: Repository, isInstalled: Boolean): String = buildList {
    if (repository.isOfficial) add(stringResource(R.string.official_badge))
    if (isInstalled) add(stringResource(R.string.installed_badge))
    add(repository.ownerAndRepo)
}.joinToString(" • ")

@Composable
private fun SourceSwitchWarningBanner(sourceName: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
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
    val tabs = listOf(Branch.STABLE to stringResource(R.string.stable), Branch.BETA to stringResource(R.string.beta))
    val selectedIndex = tabs.indexOfFirst { it.first == selectedBranch }.coerceAtLeast(0)
    PrimaryTabRow(selectedTabIndex = selectedIndex) {
        tabs.forEachIndexed { index, (branch, title) ->
            Tab(selected = selectedIndex == index, onClick = { onSelectBranch(branch) }, text = { Text(title) })
        }
    }
}

@Composable
private fun VersionListSection(
    uiState: FirmwareBrowserUiState,
    highlightSelection: Boolean,
    onRefresh: () -> Unit,
    onVersionClick: (VersionWithAssets) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        when {
            uiState.isLoadingVersions && uiState.versions.isEmpty() -> CenteredStatusColumn {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.loading_versions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            uiState.loadError && uiState.versions.isEmpty() -> CenteredStatusColumn {
                Text(
                    text = stringResource(R.string.failed_to_load_versions),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
            }

            uiState.versions.isEmpty() -> Text(
                text = stringResource(R.string.no_versions_available),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )

            else -> VersionListContent(
                uiState = uiState,
                highlightSelection = highlightSelection,
                onVersionClick = onVersionClick,
            )
        }
    }
}

@Composable
private fun VersionListContent(
    uiState: FirmwareBrowserUiState,
    highlightSelection: Boolean,
    onVersionClick: (VersionWithAssets) -> Unit,
) {
    val listState = key(uiState.selectedRepositoryId, uiState.selectedBranch) { rememberLazyListState() }
    val selectedVersion = uiState.selectedVersion?.versionWithAssets?.version
    Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoadingVersions) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = uiState.versions,
                key = { "${it.version.repositoryId}:${it.version.tagName}" },
                contentType = { "version" },
            ) { item ->
                val isSelected = highlightSelection &&
                    selectedVersion?.repositoryId == item.version.repositoryId &&
                    selectedVersion.tagName == item.version.tagName
                VersionRowCard(
                    item = item,
                    isInstalled = item.version.tagName in uiState.installedTags,
                    isSelected = isSelected,
                    onClick = { onVersionClick(item) },
                )
            }
        }
    }
}

@Composable
private fun CenteredStatusColumn(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun VersionRowCard(item: VersionWithAssets, isInstalled: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = border,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.version.tagName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
                val subtitle = item.version.displaySubtitle
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
private fun InstalledBadge() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
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
