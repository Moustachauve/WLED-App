package ca.cgagnier.wlednativeandroid.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.di.IO_DISPATCHER
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.ui.components.deviceName
import ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

private const val SELECTED_ROW_BACKGROUND_ALPHA = 0.35f

class WledWidgetConfigureActivity : ComponentActivity() {

    private val deviceRepository: DeviceRepository by inject()
    private val widgetManager: WledWidgetManager by inject()
    private val ioDispatcher: CoroutineDispatcher by inject(IO_DISPATCHER)

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var isConfirming = false

    companion object {
        private const val TAG = "WledWidgetConfigure"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            val devices by deviceRepository.allDevices.collectAsStateWithLifecycle(initialValue = null)
            var selectedMacAddress by remember { mutableStateOf<String?>(null) }
            var isAssignedDeviceLoaded by remember { mutableStateOf(false) }

            LaunchedEffect(appWidgetId) {
                selectedMacAddress = withContext(ioDispatcher) {
                    resolveGlanceId()?.let { glanceId ->
                        widgetManager.getAssignedDeviceMac(this@WledWidgetConfigureActivity, glanceId)
                    }
                }
                isAssignedDeviceLoaded = true
            }

            WLEDNativeTheme {
                ConfigurationScreen(
                    devices = if (isAssignedDeviceLoaded) devices else null,
                    selectedMacAddress = selectedMacAddress,
                    onNavigateBack = { finish() },
                    onDeviceSelected = { device ->
                        confirmConfiguration(device)
                    },
                )
            }
        }
    }

    private fun confirmConfiguration(device: Device) {
        if (isConfirming) return
        isConfirming = true

        lifecycleScope.launch {
            val configured = withContext(ioDispatcher) {
                val glanceId = resolveGlanceId() ?: return@withContext false

                widgetManager.configureWidget(this@WledWidgetConfigureActivity, glanceId, device)
                WidgetActionWorker.enqueue(
                    this@WledWidgetConfigureActivity,
                    WidgetActionRequest(appWidgetId, targetIsOn = null),
                )
                true
            }

            if (configured) {
                val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                setResult(RESULT_OK, resultValue)
            }
            finish()
        }
    }

    private fun resolveGlanceId(): GlanceId? = try {
        GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Unable to resolve GlanceId for widget $appWidgetId", e)
        null
    }
}

@Composable
fun ConfigurationScreen(
    devices: List<Device>?,
    selectedMacAddress: String? = null,
    onNavigateBack: () -> Unit = {},
    onDeviceSelected: (Device) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.select_a_device))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.description_back_button),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            devices == null -> Unit

            devices.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.you_dont_have_any_visible_devices),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .consumeWindowInsets(padding)
                        .selectableGroup(),
                    contentPadding = padding,
                ) {
                    items(devices, key = { it.macAddress }) { device ->
                        DeviceItem(
                            device = device,
                            isSelected = device.macAddress == selectedMacAddress,
                            onClick = { onDeviceSelected(device) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceItem(device: Device, isSelected: Boolean = false, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = SELECTED_ROW_BACKGROUND_ALPHA)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = deviceName(device),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isSelected) {
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
