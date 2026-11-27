package ca.cgagnier.wlednativeandroid.ui.settingsScreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.domain.HolidayTheme
import ca.cgagnier.wlednativeandroid.repository.ThemeSettings
import ca.cgagnier.wlednativeandroid.ui.theme.holiday.HolidayTopBar
import ca.cgagnier.wlednativeandroid.ui.theme.holiday.holidayCardColors
import ca.cgagnier.wlednativeandroid.ui.theme.holiday.holidayParticles
import org.koin.androidx.compose.koinViewModel

@Composable
fun Settings(navigateUp: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        SettingsActions(
            setShowHiddenDevices = { viewModel.setShowHiddenDevices(it) },
            setAutoDiscover = { viewModel.setAutoDiscover(it) },
            setShowOfflineDevicesLast = { viewModel.setShowOfflineDevicesLast(it) },
            setTheme = { viewModel.setTheme(it) },
            setHolidayThemesEnabled = { viewModel.setHolidayThemesEnabled(it) },
            setHolidayThemeOverride = { viewModel.setHolidayThemeOverride(it) },
            setSoundSyncBetaEnabled = { viewModel.setSoundSyncBetaEnabled(it) },
        )
    }
    SettingsContent(
        settingsState = settingsState,
        actions = actions,
        navigateUp = navigateUp,
    )
}

/**
 * Callbacks invoked when the user changes a setting. Grouped so [SettingsContent] stays stateless
 * and easy to render in previews and screenshot tests.
 */
@Immutable
data class SettingsActions(
    val setShowHiddenDevices: (Boolean) -> Unit = {},
    val setAutoDiscover: (Boolean) -> Unit = {},
    val setShowOfflineDevicesLast: (Boolean) -> Unit = {},
    val setTheme: (ThemeSettings) -> Unit = {},
    val setHolidayThemesEnabled: (Boolean) -> Unit = {},
    val setHolidayThemeOverride: (HolidayTheme?) -> Unit = {},
    val setSoundSyncBetaEnabled: (Boolean) -> Unit = {},
)

@Composable
fun SettingsContent(
    settingsState: SettingsState,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    actions: SettingsActions = SettingsActions(),
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            HolidayTopBar {
                DeviceSettingsAppBar(
                    navigateUp = navigateUp,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .holidayParticles()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OneOrTwoColumnLayout(
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .widthIn(0.dp, 1100.dp),
                firstColumn = {
                    ListingOptions(
                        showHiddenDevices = settingsState.showHiddenDevices,
                        isAutoDiscoveryEnabled = settingsState.isAutoDiscoveryEnabled,
                        showOfflineDevicesLast = settingsState.showOfflineLast,
                        setShowHiddenDevices = actions.setShowHiddenDevices,
                        setAutoDiscover = actions.setAutoDiscover,
                        setShowOfflineDevicesLast = actions.setShowOfflineDevicesLast,
                    )
                    ThemeOptions(
                        currentTheme = settingsState.theme,
                        setTheme = actions.setTheme,
                    )
                },
                secondColumn = {
                    HolidayThemeOptions(
                        isHolidayThemesEnabled = settingsState.isHolidayThemesEnabled,
                        holidayThemeOverride = settingsState.holidayThemeOverride,
                        setHolidayThemesEnabled = actions.setHolidayThemesEnabled,
                        setHolidayThemeOverride = actions.setHolidayThemeOverride,
                    )
                    BetaOptions(
                        isSoundSyncBetaEnabled = settingsState.isSoundSyncBetaEnabled,
                        setSoundSyncBetaEnabled = actions.setSoundSyncBetaEnabled,
                    )
                },
            )
        }
    }
}

@Composable
fun DeviceSettingsAppBar(modifier: Modifier = Modifier, navigateUp: () -> Unit) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text(stringResource(R.string.settings))
        },
        navigationIcon = {
            IconButton(onClick = navigateUp) {
                Icon(
                    painter = painterResource(R.drawable.baseline_arrow_back_24),
                    contentDescription = stringResource(R.string.description_back_button),
                )
            }
        },
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, description: String? = null) {
    Row(
        modifier = Modifier.clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun RadioRow(label: String, checked: Boolean, onSelected: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelected() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = checked,
            onClick = onSelected,
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ListingOptions(
    showHiddenDevices: Boolean,
    isAutoDiscoveryEnabled: Boolean,
    showOfflineDevicesLast: Boolean,
    setShowHiddenDevices: (Boolean) -> Unit,
    setAutoDiscover: (Boolean) -> Unit,
    setShowOfflineDevicesLast: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        colors = holidayCardColors(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.listing_options),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            SwitchRow(
                label = stringResource(R.string.show_hidden_devices),
                checked = showHiddenDevices,
                onCheckedChange = setShowHiddenDevices,
            )
            SwitchRow(
                label = stringResource(R.string.automatically_discover_new_devices),
                checked = isAutoDiscoveryEnabled,
                onCheckedChange = setAutoDiscover,
                description = stringResource(R.string.automatically_discover_new_devices_description),
            )
            SwitchRow(
                label = stringResource(R.string.show_offline_devices_last),
                checked = showOfflineDevicesLast,
                onCheckedChange = setShowOfflineDevicesLast,
            )
        }
    }
}

@Composable
fun ThemeOptions(currentTheme: ThemeSettings, setTheme: (ThemeSettings) -> Unit = {}) {
    val themes = listOf(
        Pair(ThemeSettings.Dark, stringResource(R.string.dark)),
        Pair(ThemeSettings.Light, stringResource(R.string.light)),
        Pair(ThemeSettings.Auto, stringResource(R.string.system_default)),
    )
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        colors = holidayCardColors(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.theme),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            themes.forEach { theme ->
                RadioRow(
                    label = theme.second,
                    checked = currentTheme == theme.first,
                    onSelected = {
                        setTheme(theme.first)
                    },
                )
            }
        }
    }
}

@Composable
fun HolidayThemeOptions(
    isHolidayThemesEnabled: Boolean,
    holidayThemeOverride: HolidayTheme?,
    setHolidayThemesEnabled: (Boolean) -> Unit = {},
    setHolidayThemeOverride: (HolidayTheme?) -> Unit = {},
) {
    val overrideOptions = listOf(
        Pair<HolidayTheme?, String>(null, stringResource(R.string.holiday_theme_automatic)),
        Pair(HolidayTheme.WinterHolidays, stringResource(R.string.holiday_theme_winter_holidays)),
        Pair(HolidayTheme.NewYear, stringResource(R.string.holiday_theme_new_year)),
    )
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        colors = holidayCardColors(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.holiday_themes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            SwitchRow(
                label = stringResource(R.string.enable_holiday_themes),
                checked = isHolidayThemesEnabled,
                onCheckedChange = setHolidayThemesEnabled,
                description = stringResource(R.string.enable_holiday_themes_description),
            )
            AnimatedVisibility(visible = isHolidayThemesEnabled) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        stringResource(R.string.holiday_theme_override),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    overrideOptions.forEach { (themeOption, label) ->
                        RadioRow(
                            label = label,
                            checked = holidayThemeOverride == themeOption,
                            onSelected = { setHolidayThemeOverride(themeOption) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BetaOptions(isSoundSyncBetaEnabled: Boolean, setSoundSyncBetaEnabled: (Boolean) -> Unit = {}) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        colors = holidayCardColors(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.beta_features),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            SwitchRow(
                label = stringResource(R.string.sound_sync_beta_setting),
                checked = isSoundSyncBetaEnabled,
                onCheckedChange = setSoundSyncBetaEnabled,
                description = stringResource(R.string.sound_sync_beta_setting_description),
            )
        }
    }
}

@Composable
fun OneOrTwoColumnLayout(
    modifier: Modifier = Modifier,
    firstColumn: @Composable () -> Unit,
    secondColumn: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        if (maxWidth < 800.dp) {
            Column(modifier) {
                firstColumn()
                secondColumn()
            }
        } else {
            Row(modifier.height(IntrinsicSize.Min)) {
                Column(modifier = Modifier.weight(1f)) {
                    firstColumn()
                }
                Column(modifier = Modifier.weight(1f)) {
                    secondColumn()
                }
            }
        }
    }
}
