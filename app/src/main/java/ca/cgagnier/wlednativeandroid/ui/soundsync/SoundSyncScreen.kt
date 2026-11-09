package ca.cgagnier.wlednativeandroid.ui.soundsync

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.soundsync.SoundSyncError
import ca.cgagnier.wlednativeandroid.service.soundsync.SoundSyncIconVisualizer
import ca.cgagnier.wlednativeandroid.service.soundsync.UdpSoundSyncPacket
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.AgcMode
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.GainControl
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.SoundSyncProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

private const val CHART_HEIGHT_DP = 120
private const val CHANNEL_MAX_VALUE = 255f
private const val BAR_GAP_RATIO = 0.15f
private const val MAX_PORT_DIGITS = 5

/**
 * Callbacks invoked from [SoundSyncContent]. Grouped so [SoundSyncContent] stays stateless and
 * easy to render in Compose previews and screenshot tests.
 */
@Immutable
data class SoundSyncActions(
    val navigateUp: () -> Unit = {},
    val onStart: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onGainChanged: (Float) -> Unit = {},
    val onGainChangeFinished: () -> Unit = {},
    val onAgcEnabledChanged: (Boolean) -> Unit = {},
    val onAgcModeChanged: (AgcMode) -> Unit = {},
    val onSquelchChanged: (Int) -> Unit = {},
    val onSquelchChangeFinished: () -> Unit = {},
    val onSquelchStepped: (Int) -> Unit = {},
    val onPortChanged: (Int) -> Unit = {},
)

@Composable
fun SoundSyncScreen(navigateUp: () -> Unit, viewModel: SoundSyncViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        viewModel.refreshPermissionState()
        if (results[Manifest.permission.RECORD_AUDIO] == true) {
            permanentlyDenied = false
            viewModel.start()
        } else {
            val activity = context.findActivity()
            permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissionState()
        onPauseOrDispose { }
    }

    if (uiState.hasRecordAudioPermission && permanentlyDenied) {
        permanentlyDenied = false
    }

    val hasRecordAudioPermission = uiState.hasRecordAudioPermission
    val actions = remember(navigateUp, viewModel, hasRecordAudioPermission, permanentlyDenied, context) {
        SoundSyncActions(
            navigateUp = navigateUp,
            onStart = {
                if (!hasRecordAudioPermission) {
                    if (permanentlyDenied) {
                        openAppSettings(context)
                    } else {
                        permissionLauncher.launch(requiredPermissions())
                    }
                } else {
                    viewModel.start()
                }
            },
            onStop = { viewModel.stop() },
            onGainChanged = { viewModel.setGain(it) },
            onGainChangeFinished = { viewModel.saveGain() },
            onAgcEnabledChanged = { viewModel.setAgcEnabled(it) },
            onAgcModeChanged = { viewModel.setAgcMode(it) },
            onSquelchChanged = { viewModel.setSquelch(it) },
            onSquelchChangeFinished = { viewModel.saveSquelch() },
            onSquelchStepped = { viewModel.stepSquelch(it) },
            onPortChanged = { viewModel.setPort(it) },
        )
    }

    SoundSyncContent(
        uiState = uiState,
        permanentlyDenied = permanentlyDenied,
        latestPacketFlow = viewModel.latestPacket,
        actions = actions,
    )
}

@Composable
private fun SoundSyncTopBar(navigateUp: () -> Unit) {
    CenterAlignedTopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sound_sync))
                Surface(
                    modifier = Modifier.padding(start = 8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.beta_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
    )
}

@Composable
fun SoundSyncContent(
    uiState: SoundSyncUiState,
    modifier: Modifier = Modifier,
    permanentlyDenied: Boolean = false,
    latestPacketFlow: StateFlow<UdpSoundSyncPacket> = remember { MutableStateFlow(UdpSoundSyncPacket()) },
    actions: SoundSyncActions = SoundSyncActions(),
) {
    Scaffold(
        modifier = modifier,
        topBar = { SoundSyncTopBar(navigateUp = actions.navigateUp) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .widthIn(0.dp, 1100.dp),
            ) {
                DescriptionCard {
                    StartStopControls(
                        isRunning = uiState.isRunning,
                        hasRecordAudioPermission = uiState.hasRecordAudioPermission,
                        permanentlyDenied = permanentlyDenied,
                        onStart = actions.onStart,
                        onStop = actions.onStop,
                    )
                }
                uiState.error?.let { ErrorCard(it) }
                if (uiState.isRunning) {
                    VisualizationCard(latestPacketFlow = latestPacketFlow)
                }
                GainCard(
                    uiState = uiState,
                    actions = actions,
                )
                PortCard(
                    port = uiState.port,
                    isRunning = uiState.isRunning,
                    onPortChanged = actions.onPortChanged,
                )
            }
        }
    }
}

@Composable
private fun DescriptionCard(content: @Composable ColumnScope.() -> Unit) {
    SoundSyncCard(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_graphic_eq_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = stringResource(R.string.sound_sync_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.sound_sync_beta_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun ErrorCard(error: SoundSyncError) {
    val messageResId = when (error) {
        SoundSyncError.PERMISSION_REQUIRED -> R.string.sound_sync_error_permission
        SoundSyncError.CAPTURE_FAILED -> R.string.sound_sync_error_capture
    }
    SoundSyncCard {
        Text(
            text = stringResource(R.string.sound_sync_error, stringResource(messageResId)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SoundSyncCard(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun GainCard(uiState: SoundSyncUiState, actions: SoundSyncActions) {
    SoundSyncCard {
        GainSection(
            displayedGain = uiState.displayedGain,
            agcEnabled = uiState.agcEnabled,
            onGainChanged = actions.onGainChanged,
            onGainChangeFinished = actions.onGainChangeFinished,
        )
        SectionDivider()
        AgcModeSection(
            agcEnabled = uiState.agcEnabled,
            agcMode = uiState.agcMode,
            onAgcEnabledChanged = actions.onAgcEnabledChanged,
            onAgcModeChanged = actions.onAgcModeChanged,
        )
        SectionDivider()
        SquelchSection(
            squelch = uiState.squelch,
            onSquelchChanged = actions.onSquelchChanged,
            onSquelchChangeFinished = actions.onSquelchChangeFinished,
            onSquelchStepped = actions.onSquelchStepped,
        )
    }
}

@Composable
private fun GainSection(
    displayedGain: Float,
    agcEnabled: Boolean,
    onGainChanged: (Float) -> Unit,
    onGainChangeFinished: () -> Unit,
) {
    val animatedAgcGain by animateFloatAsState(
        targetValue = displayedGain,
        label = "agcGainSlider",
    )
    val sliderGain = if (agcEnabled) animatedAgcGain else displayedGain
    val gainLabel = stringResource(R.string.sound_sync_gain)
    val gainValueText = stringResource(R.string.sound_sync_gain_value, displayedGain)

    SectionTitle(gainLabel)
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slider(
            value = sliderGain,
            onValueChange = onGainChanged,
            onValueChangeFinished = onGainChangeFinished,
            valueRange = 0f..GainControl.MAX_MANUAL_GAIN,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = gainLabel
                    stateDescription = gainValueText
                },
            enabled = !agcEnabled,
        )
        Text(
            text = gainValueText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun AgcModeSection(
    agcEnabled: Boolean,
    agcMode: AgcMode,
    onAgcEnabledChanged: (Boolean) -> Unit,
    onAgcModeChanged: (AgcMode) -> Unit,
) {
    val modes = AgcMode.entries
    var animateVisibility by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = agcEnabled,
                role = Role.Switch,
                onValueChange = {
                    animateVisibility = true
                    onAgcEnabledChanged(it)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SectionTitle(stringResource(R.string.sound_sync_agc))
            Text(
                text = stringResource(R.string.sound_sync_agc_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = agcEnabled, onCheckedChange = null)
    }
    AnimatedVisibility(
        visible = agcEnabled,
        enter = if (animateVisibility) fadeIn() + expandVertically() else EnterTransition.None,
        exit = if (animateVisibility) fadeOut() + shrinkVertically() else ExitTransition.None,
    ) {
        Column {
            Spacer(modifier = Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = agcMode == mode,
                        onClick = { onAgcModeChanged(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        label = {
                            Text(
                                text = stringResource(mode.labelResId()),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(agcMode.descriptionResId()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SquelchSection(
    squelch: Int,
    onSquelchChanged: (Int) -> Unit,
    onSquelchChangeFinished: () -> Unit,
    onSquelchStepped: (Int) -> Unit,
) {
    val squelchLabel = stringResource(R.string.sound_sync_squelch)
    SectionTitle(squelchLabel)
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onSquelchStepped(-1) },
            enabled = squelch > SoundSyncProcessor.MIN_SQUELCH,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_remove_24),
                contentDescription = stringResource(R.string.sound_sync_squelch_decrease),
            )
        }
        Slider(
            value = squelch.toFloat(),
            onValueChange = { onSquelchChanged(it.roundToInt()) },
            onValueChangeFinished = onSquelchChangeFinished,
            valueRange = SoundSyncProcessor.MIN_SQUELCH.toFloat()..SoundSyncProcessor.MAX_SQUELCH.toFloat(),
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = squelchLabel
                },
        )
        IconButton(
            onClick = { onSquelchStepped(1) },
            enabled = squelch < SoundSyncProcessor.MAX_SQUELCH,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.sound_sync_squelch_increase),
            )
        }
        Text(
            text = squelch.toString(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier
                .widthIn(min = 32.dp)
                .padding(start = 4.dp),
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.sound_sync_squelch_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PortCard(port: Int, isRunning: Boolean, onPortChanged: (Int) -> Unit) {
    val portLabel = stringResource(R.string.sound_sync_port)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var editingPortText by rememberSaveable { mutableStateOf<String?>(null) }
    var hadFocus by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    LaunchedEffect(hadFocus, imeBottom) {
        if (hadFocus && imeBottom > 0) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    val displayedPortText = if (isRunning) port.toString() else editingPortText ?: port.toString()
    val parsedPort = displayedPortText.toIntOrNull()
    val isPortValid = parsedPort != null && UdpSoundSyncPacket.isValidPort(parsedPort)

    SoundSyncCard(modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester)) {
        SectionTitle(portLabel)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = displayedPortText,
            onValueChange = { newValue ->
                val digitsOnly = newValue.filter { it.isDigit() }.take(MAX_PORT_DIGITS)
                editingPortText = digitsOnly
                val candidate = digitsOnly.toIntOrNull()
                if (candidate != null && UdpSoundSyncPacket.isValidPort(candidate)) {
                    onPortChanged(candidate)
                }
            },
            enabled = !isRunning,
            singleLine = true,
            isError = !isPortValid,
            placeholder = { Text(UdpSoundSyncPacket.DEFAULT_PORT.toString()) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = portLabel }
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        hadFocus = true
                    } else if (hadFocus) {
                        hadFocus = false
                        editingPortText = null
                    }
                },
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(
                if (isPortValid) R.string.sound_sync_port_description else R.string.sound_sync_port_error,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (isPortValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
    }
}

private fun AgcMode.labelResId(): Int = when (this) {
    AgcMode.NORMAL -> R.string.sound_sync_agc_mode_normal
    AgcMode.VIVID -> R.string.sound_sync_agc_mode_vivid
    AgcMode.LAZY -> R.string.sound_sync_agc_mode_lazy
}

private fun AgcMode.descriptionResId(): Int = when (this) {
    AgcMode.NORMAL -> R.string.sound_sync_agc_mode_normal_description
    AgcMode.VIVID -> R.string.sound_sync_agc_mode_vivid_description
    AgcMode.LAZY -> R.string.sound_sync_agc_mode_lazy_description
}

@Composable
private fun VisualizationCard(latestPacketFlow: StateFlow<UdpSoundSyncPacket>) {
    val packet by latestPacketFlow.collectAsStateWithLifecycle()
    var frameTimeMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        val startTimeMs = withInfiniteAnimationFrameMillis { it }
        while (true) {
            frameTimeMs = withInfiniteAnimationFrameMillis { it - startTimeMs }
        }
    }

    SoundSyncCard {
        SectionTitle(stringResource(R.string.sound_sync_fft_visualization))
        Spacer(modifier = Modifier.height(8.dp))
        FftBarChart(
            fftChannelsProvider = { packet.fftResult },
            frameTimeMsProvider = { frameTimeMs },
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT_DP.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.sound_sync_sample_level),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        SampleLevelIndicator(
            sampleLevelProvider = { packet.sampleSmth },
            isPeakProvider = { packet.samplePeak },
            frameTimeMsProvider = { frameTimeMs },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
        )
    }
}

@Composable
private fun FftBarChart(
    fftChannelsProvider: () -> ByteArray,
    frameTimeMsProvider: () -> Long,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val lastDrawTimeMs = remember { LongArray(1) }
    val smoothedChannels = remember { FloatArray(UdpSoundSyncPacket.FFT_CHANNEL_COUNT) }

    Canvas(modifier = modifier) {
        val elapsedMs = frameTimeMsProvider()
        val deltaTimeMs = (elapsedMs - lastDrawTimeMs[0]).coerceAtLeast(0L).also {
            lastDrawTimeMs[0] = elapsedMs
        }
        val fftChannels = fftChannelsProvider()
        SoundSyncIconVisualizer.smoothFftChannels(
            fftChannels = fftChannels,
            deltaTimeMs = deltaTimeMs,
            smoothedOutput = smoothedChannels,
        )

        val barWidth = size.width / smoothedChannels.size
        val gap = barWidth * BAR_GAP_RATIO
        val actualBarWidth = barWidth - gap
        for (i in smoothedChannels.indices) {
            val barHeight = smoothedChannels[i] * size.height
            val left = i * barWidth + gap / 2f
            drawRect(
                color = barColor,
                topLeft = Offset(left, size.height - barHeight),
                size = Size(actualBarWidth, barHeight),
            )
        }
    }
}

@Composable
private fun SampleLevelIndicator(
    sampleLevelProvider: () -> Float,
    isPeakProvider: () -> Boolean,
    frameTimeMsProvider: () -> Long,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val lastDrawTimeMs = remember { LongArray(1) }
    val smoothedLevel = remember { FloatArray(1) }

    Canvas(modifier = modifier) {
        val elapsedMs = frameTimeMsProvider()
        val deltaTimeMs = (elapsedMs - lastDrawTimeMs[0]).coerceAtLeast(0L).also {
            lastDrawTimeMs[0] = elapsedMs
        }
        val targetLevel = sampleLevelProvider().coerceIn(0f, CHANNEL_MAX_VALUE)
        smoothedLevel[0] = SoundSyncIconVisualizer.smoothBarLevel(
            previous = smoothedLevel[0],
            target = targetLevel,
            isRunning = true,
            deltaTimeMs = deltaTimeMs,
        )

        drawRect(color = backgroundColor, topLeft = Offset.Zero, size = size)
        val levelColor = if (isPeakProvider()) errorColor else primaryColor
        val levelWidth = smoothedLevel[0] / CHANNEL_MAX_VALUE * size.width
        drawRect(color = levelColor, topLeft = Offset.Zero, size = Size(levelWidth, size.height))
    }
}

@Composable
private fun StartStopControls(
    isRunning: Boolean,
    hasRecordAudioPermission: Boolean,
    permanentlyDenied: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    if (isRunning) {
        Text(
            text = stringResource(R.string.sound_sync_running),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sound_sync_stop))
        }
    } else if (!hasRecordAudioPermission) {
        Text(
            text = stringResource(R.string.sound_sync_permission_required),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            val buttonTextRes = if (permanentlyDenied) {
                R.string.sound_sync_open_settings
            } else {
                R.string.sound_sync_grant_permission
            }
            Text(stringResource(buttonTextRes))
        }
    } else {
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sound_sync_start))
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
} else {
    arrayOf(Manifest.permission.RECORD_AUDIO)
}
