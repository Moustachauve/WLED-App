package ca.cgagnier.wlednativeandroid.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.di.IO_DISPATCHER
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.ui.components.DevicePickerConfig
import ca.cgagnier.wlednativeandroid.ui.components.DevicePickerList
import ca.cgagnier.wlednativeandroid.ui.components.DeviceSelectionMode
import ca.cgagnier.wlednativeandroid.ui.components.DeviceStatusVisibility
import ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme
import ca.cgagnier.wlednativeandroid.ui.theme.holiday.HolidayTopBar
import ca.cgagnier.wlednativeandroid.ui.theme.holiday.holidayParticles
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

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
            HolidayTopBar {
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.select_a_device))
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_arrow_back_24),
                                contentDescription = stringResource(R.string.description_back_button),
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        if (devices == null) return@Scaffold

        val layoutDirection = LocalLayoutDirection.current
        val pickerDevices = remember(devices) { devices.map { DeviceWithState(device = it) } }

        DevicePickerList(
            devices = pickerDevices,
            selectionMode = DeviceSelectionMode.Single(
                selectedMacAddress = selectedMacAddress,
                onDeviceSelected = { onDeviceSelected(it.device) },
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .holidayParticles()
                .consumeWindowInsets(padding),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + 12.dp,
                top = 8.dp,
                end = padding.calculateEndPadding(layoutDirection) + 12.dp,
                bottom = padding.calculateBottomPadding() + 8.dp,
            ),
            config = DevicePickerConfig(
                showOfflineDevicesLast = false,
                showHiddenDevices = true,
                statusVisibility = DeviceStatusVisibility.None,
                nameMaxLines = 2,
            ),
        )
    }
}
