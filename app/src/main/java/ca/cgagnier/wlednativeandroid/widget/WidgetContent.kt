package ca.cgagnier.wlednativeandroid.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.WifiSignalLevel
import ca.cgagnier.wlednativeandroid.ui.components.toIconRes
import ca.cgagnier.wlednativeandroid.widget.components.ElapsedTimeChronometer
import ca.cgagnier.wlednativeandroid.widget.components.PowerButton
import ca.cgagnier.wlednativeandroid.widget.components.QuickActionButtons
import ca.cgagnier.wlednativeandroid.widget.components.QuickActionItem
import ca.cgagnier.wlednativeandroid.widget.components.WLEDWidgetTheme
import kotlinx.serialization.json.Json

val WIDGET_DATA_KEY = stringPreferencesKey("widget_data")

private const val TALL_MAX_TITLE_LINES = 3

private val TINY_WIDGET_WIDTH_THRESHOLD = 110.dp
private val NARROW_WIDGET_WIDTH_THRESHOLD = 160.dp
private val TINY_MULTILINE_HEIGHT_THRESHOLD = 80.dp
private val TINY_TALL_HEIGHT_THRESHOLD = 145.dp
private val TINY_TALL_WIDE_BUTTON_WIDTH_THRESHOLD = 68.dp
private val TINY_TALL_WIDE_BUTTON_HEIGHT_THRESHOLD = 175.dp
private val TINY_TALL_THREE_LINE_HEIGHT_THRESHOLD = 200.dp
private val COMPACT_WIDGET_HEIGHT_THRESHOLD = 90.dp
private val MULTILINE_TITLE_HEIGHT_THRESHOLD = 104.dp

private val WIDE_HORIZONTAL_PADDING = 16.dp
private val NARROW_HORIZONTAL_PADDING = 14.dp
private val TINY_HORIZONTAL_PADDING = 8.dp
private val STANDARD_VERTICAL_PADDING = 14.dp
private val COMPACT_VERTICAL_PADDING = 10.dp
private val TINY_VERTICAL_PADDING = 6.dp
private val STANDARD_ERROR_PADDING = 12.dp
private val TINY_ERROR_PADDING = 6.dp

private val WIDE_GLOW_WIDTH = 156.dp
private val NARROW_GLOW_SIZE = 120.dp
private val TINY_GLOW_HEIGHT = 120.dp

private val STATUS_PILL_CORNER_RADIUS = 999.dp
private val STATUS_PILL_HORIZONTAL_PADDING = 9.dp
private val COMPACT_STATUS_PILL_HORIZONTAL_PADDING = 7.dp
private val STATUS_PILL_VERTICAL_PADDING = 4.dp
private val STATUS_ICON_SIZE = 12.dp

private enum class GlowPosition(
    val alignment: Alignment,
    @param:DrawableRes val drawableRes: Int,
    val imageModifier: GlanceModifier,
) {
    TOP_CENTER(
        alignment = Alignment.TopCenter,
        drawableRes = R.drawable.widget_ambient_glow_top_center,
        imageModifier = GlanceModifier.fillMaxWidth().height(TINY_GLOW_HEIGHT),
    ),
    TOP_START(
        alignment = Alignment.TopStart,
        drawableRes = R.drawable.widget_ambient_glow_top_start,
        imageModifier = GlanceModifier.size(NARROW_GLOW_SIZE),
    ),
    CENTER_END(
        alignment = Alignment.CenterEnd,
        drawableRes = R.drawable.widget_ambient_glow_end,
        imageModifier = GlanceModifier.width(WIDE_GLOW_WIDTH).fillMaxHeight(),
    ),
}

@Composable
fun WidgetContent(context: Context, appWidgetId: Int) {
    val prefs = currentState<Preferences>()
    val jsonString = prefs[WIDGET_DATA_KEY]

    val data = try {
        if (jsonString != null) Json.decodeFromString<WidgetStateData>(jsonString) else null
    } catch (_: Exception) {
        null
    }

    when (data) {
        is WidgetStateData -> {
            WLEDWidgetTheme(data) {
                DeviceWidgetContent(data)
            }
        }

        else -> ErrorState(context, appWidgetId)
    }
}

@Suppress("MagicNumber")
@Composable
fun WidgetPreviewContent() {
    val previewData = WidgetStateData(
        macAddress = "preview",
        address = "192.168.1.x",
        name = "WLED Device",
        isOn = true,
        isOnline = true,
        color = 0xFF00BFFF.toInt(), // Deep sky blue
    )
    WLEDWidgetTheme(previewData) {
        DeviceWidgetContentWide(previewData)
    }
}

@Composable
private fun ErrorState(context: Context, appWidgetId: Int) {
    val isTiny = LocalSize.current.width < TINY_WIDGET_WIDTH_THRESHOLD
    val configureIntent = Intent(context, WledWidgetConfigureActivity::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(if (isTiny) TINY_ERROR_PADDING else STANDARD_ERROR_PADDING)
            .clickable(actionStartActivity(configureIntent)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(R.drawable.baseline_error_outline_24),
            contentDescription = null,
            modifier = GlanceModifier.size(if (isTiny) 20.dp else 24.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.error),
        )
        Spacer(modifier = GlanceModifier.height(if (isTiny) 4.dp else 6.dp))
        Text(
            text = context.getString(R.string.widget_please_configure),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = if (isTiny) 11.sp else 13.sp,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
        )
    }
}

@Composable
private fun DeviceWidgetContent(data: WidgetStateData) {
    val size = LocalSize.current
    when {
        size.width < TINY_WIDGET_WIDTH_THRESHOLD && size.height >= TINY_TALL_HEIGHT_THRESHOLD -> {
            DeviceWidgetContentTinyTall(data)
        }

        size.width < TINY_WIDGET_WIDTH_THRESHOLD -> DeviceWidgetContentTiny(data)

        size.width < NARROW_WIDGET_WIDTH_THRESHOLD -> DeviceWidgetContentNarrow(data)

        else -> DeviceWidgetContentWide(data)
    }
}

@Composable
private fun DeviceWidgetContentTiny(data: WidgetStateData) {
    val maxTitleLines = if (LocalSize.current.height >= TINY_MULTILINE_HEIGHT_THRESHOLD) 2 else 1
    val titleColor = if (data.isOnline) GlanceTheme.colors.onSurface else GlanceTheme.colors.onSurfaceVariant

    DeviceWidgetContainer(
        data = data,
        glowPosition = GlowPosition.TOP_CENTER,
        horizontalPadding = 0.dp,
        verticalPadding = TINY_VERTICAL_PADDING,
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PowerButton(
                isOn = data.isOnline && data.isOn,
                isCompact = true,
                showOfflineIcon = !data.isOnline,
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = data.name,
                modifier = GlanceModifier.fillMaxWidth().padding(horizontal = TINY_HORIZONTAL_PADDING),
                style = TextStyle(
                    color = titleColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = maxTitleLines,
            )
        }
    }
}

@Composable
private fun DeviceWidgetContentTinyTall(data: WidgetStateData) {
    val size = LocalSize.current
    val isCompactButton =
        size.width < TINY_TALL_WIDE_BUTTON_WIDTH_THRESHOLD || size.height < TINY_TALL_WIDE_BUTTON_HEIGHT_THRESHOLD
    val verticalPadding = if (size.height < TINY_TALL_WIDE_BUTTON_HEIGHT_THRESHOLD) {
        COMPACT_VERTICAL_PADDING
    } else {
        STANDARD_VERTICAL_PADDING
    }
    val maxTitleLines = if (size.height >= TINY_TALL_THREE_LINE_HEIGHT_THRESHOLD) TALL_MAX_TITLE_LINES else 2

    DeviceWidgetContainer(
        data = data,
        glowPosition = GlowPosition.TOP_CENTER,
        horizontalPadding = 0.dp,
        verticalPadding = verticalPadding,
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PowerButton(isOn = data.isOnline && data.isOn, isCompact = isCompactButton)
            Spacer(modifier = GlanceModifier.height(4.dp))
            RefreshTimerButton(lastUpdated = data.lastUpdated)

            Spacer(modifier = GlanceModifier.defaultWeight())

            Box(modifier = GlanceModifier.padding(horizontal = 4.dp)) {
                StatusPill(data, horizontalPadding = COMPACT_STATUS_PILL_HORIZONTAL_PADDING)
            }
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = data.name,
                modifier = GlanceModifier.fillMaxWidth().padding(horizontal = TINY_HORIZONTAL_PADDING),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = maxTitleLines,
            )
        }
    }
}

@Composable
internal fun DeviceWidgetContentWide(data: WidgetStateData) {
    val verticalPadding = if (LocalSize.current.height < COMPACT_WIDGET_HEIGHT_THRESHOLD) {
        COMPACT_VERTICAL_PADDING
    } else {
        STANDARD_VERTICAL_PADDING
    }
    DeviceWidgetContainer(
        data = data,
        glowPosition = GlowPosition.CENTER_END,
        horizontalPadding = WIDE_HORIZONTAL_PADDING,
        verticalPadding = verticalPadding,
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(data)
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        RefreshTimerButton(lastUpdated = data.lastUpdated)
                    }

                    Spacer(modifier = GlanceModifier.defaultWeight())

                    DeviceDetailsColumn(
                        data = data,
                        isNarrow = false,
                    )
                }

                Spacer(modifier = GlanceModifier.width(12.dp))

                PowerButton(isOn = data.isOnline && data.isOn, isCompact = false)
            }
            // TODO: Add a way for users to configure quick actions
            if (data.quickActionsEnabled) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                QuickActionButtons(
                    items = listOf(
                        QuickActionItem(label = "a1", onClick = actionRunCallback<RefreshAction>()),
                        QuickActionItem(label = "b2", onClick = actionRunCallback<RefreshAction>()),
                        QuickActionItem(label = "c3", onClick = actionRunCallback<RefreshAction>()),
                        QuickActionItem(label = "d4", onClick = actionRunCallback<RefreshAction>()),
                    ),
                )
            }
        }
    }
}

@Composable
private fun DeviceWidgetContentNarrow(data: WidgetStateData) {
    val verticalPadding = if (LocalSize.current.height < COMPACT_WIDGET_HEIGHT_THRESHOLD) {
        COMPACT_VERTICAL_PADDING
    } else {
        STANDARD_VERTICAL_PADDING
    }
    DeviceWidgetContainer(
        data = data,
        glowPosition = GlowPosition.TOP_START,
        horizontalPadding = NARROW_HORIZONTAL_PADDING,
        verticalPadding = verticalPadding,
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                PowerButton(isOn = data.isOnline && data.isOn, isCompact = true)
                Box(
                    modifier = GlanceModifier.defaultWeight(),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    RefreshTimerButton(lastUpdated = data.lastUpdated)
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            DeviceDetailsColumn(
                data = data,
                isNarrow = true,
            )
        }
    }
}

@Composable
private fun DeviceWidgetContainer(
    data: WidgetStateData,
    glowPosition: GlowPosition,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    content: @Composable () -> Unit,
) {
    val intent = data.toOpenWidgetInAppIntent(LocalContext.current)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(actionStartActivity(intent)),
    ) {
        if (data.isOnline && data.isOn) {
            AmbientButtonGlow(glowPosition = glowPosition)
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        ) {
            content()
        }
    }
}

@Composable
private fun AmbientButtonGlow(glowPosition: GlowPosition) {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = glowPosition.alignment,
    ) {
        Image(
            provider = ImageProvider(glowPosition.drawableRes),
            contentDescription = null,
            modifier = glowPosition.imageModifier,
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
        )
    }
}

@Composable
private fun DeviceDetailsColumn(data: WidgetStateData, isNarrow: Boolean, modifier: GlanceModifier = GlanceModifier) {
    val maxTitleLines = if (LocalSize.current.height >= MULTILINE_TITLE_HEIGHT_THRESHOLD) 2 else 1
    val titleFontSize = if (isNarrow) 14.sp else 16.sp

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = data.name,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = titleFontSize,
            ),
            maxLines = maxTitleLines,
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        if (isNarrow) {
            StatusContent(data)
        } else {
            Text(
                text = data.address,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StatusPill(data: WidgetStateData, horizontalPadding: Dp = STATUS_PILL_HORIZONTAL_PADDING) {
    Box(
        modifier = GlanceModifier
            .background(
                imageProvider = ImageProvider(R.drawable.widget_pill_background),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.surfaceVariant),
            )
            .cornerRadius(STATUS_PILL_CORNER_RADIUS)
            .padding(
                horizontal = horizontalPadding,
                vertical = STATUS_PILL_VERTICAL_PADDING,
            ),
        contentAlignment = Alignment.Center,
    ) {
        StatusContent(data)
    }
}

@Composable
private fun StatusContent(data: WidgetStateData) {
    val context = LocalContext.current
    val effectiveSignalLevel = if (data.isOnline) data.signalLevel else WifiSignalLevel.OFFLINE
    val iconColor = when {
        !data.isOnline -> GlanceTheme.colors.error
        data.isOn -> GlanceTheme.colors.primary
        else -> GlanceTheme.colors.outline
    }
    val textColor = if (data.isOnline && data.isOn) {
        GlanceTheme.colors.onSurface
    } else {
        GlanceTheme.colors.onSurfaceVariant
    }
    val statusText = context.getString(
        when {
            !data.isOnline -> R.string.is_offline
            data.isOn -> R.string.control_on
            else -> R.string.control_off
        },
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(effectiveSignalLevel.toIconRes()),
            contentDescription = null,
            modifier = GlanceModifier.size(STATUS_ICON_SIZE),
            colorFilter = ColorFilter.tint(iconColor),
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        Text(
            text = statusText,
            style = TextStyle(
                color = textColor,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun RefreshTimerButton(lastUpdated: Long) {
    Box(
        modifier = GlanceModifier
            .cornerRadius(8.dp)
            .clickable(actionRunCallback<RefreshAction>())
            .padding(horizontal = 4.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElapsedTimeChronometer(lastUpdated = lastUpdated)
    }
}

@Suppress("MagicNumber")
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 76, heightDp = 96)
@Composable
private fun DeviceWidgetContentPreviewTiny() {
    val widgetData = WidgetStateData(
        macAddress = "AA:BB:CC:DD:EE:FF",
        address = "192.168.1.100",
        name = "Dig2Go-Audio",
        isOn = true,
        isOnline = true,
        color = 0xFFFF8C00.toInt(),
    )
    WLEDWidgetTheme(widgetData) {
        DeviceWidgetContent(widgetData)
    }
}

@Suppress("MagicNumber")
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 80, heightDp = 210)
@Composable
private fun DeviceWidgetContentPreviewTinyTall() {
    val widgetData = WidgetStateData(
        macAddress = "AA:BB:CC:DD:EE:FF",
        address = "192.168.1.100",
        name = "WLED bureau",
        isOn = true,
        isOnline = true,
        color = 0xFF5D6BFF.toInt(),
    )
    WLEDWidgetTheme(widgetData) {
        DeviceWidgetContent(widgetData)
    }
}

@Suppress("MagicNumber")
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 220, heightDp = 110)
@Composable
private fun DeviceWidgetContentPreviewOn() {
    WidgetPreviewContent()
}

@Suppress("MagicNumber")
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 140, heightDp = 120)
@Composable
private fun DeviceWidgetContentPreviewNarrow() {
    val widgetData = WidgetStateData(
        macAddress = "AA:BB:CC:DD:EE:FF",
        address = "192.168.1.100",
        name = "Small widget with a long name",
        isOn = false,
        isOnline = true,
        color = 0xFF0000FF.toInt(), // Blue
    )
    WLEDWidgetTheme(widgetData) {
        DeviceWidgetContent(widgetData)
    }
}

@Suppress("MagicNumber")
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 220, heightDp = 110)
@Composable
private fun DeviceWidgetContentPreviewOff() {
    val widgetData = WidgetStateData(
        macAddress = "AA:BB:CC:DD:EE:FF",
        address = "192.168.1.101",
        name = "Offline device",
        isOn = false,
        isOnline = false,
        color = 0xFFFF8000.toInt(), // Orange
    )
    WLEDWidgetTheme(widgetData) {
        DeviceWidgetContent(widgetData)
    }
}

@Suppress("MagicNumber")
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 220, heightDp = 140)
@Composable
private fun DeviceWidgetContentPreviewQuickActions() {
    val widgetData = WidgetStateData(
        macAddress = "AA:BB:CC:DD:EE:FF",
        address = "192.168.1.100",
        name = "WLED Device",
        isOn = true,
        isOnline = true,
        color = 0xFFDFFF00.toInt(), // Chartreuse
        quickActionsEnabled = true,
    )
    WLEDWidgetTheme(widgetData) {
        DeviceWidgetContent(widgetData)
    }
}
