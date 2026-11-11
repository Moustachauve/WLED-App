package ca.cgagnier.wlednativeandroid.widget.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.WifiSignalLevel
import ca.cgagnier.wlednativeandroid.ui.components.toIconRes
import ca.cgagnier.wlednativeandroid.widget.RefreshAction
import ca.cgagnier.wlednativeandroid.widget.TogglePowerAction

private enum class PowerButtonStyle(
    val buttonSize: Dp,
    val cornerRadius: Dp,
    val iconSize: Dp,
    @param:DrawableRes val fillRes: Int,
    @param:DrawableRes val outlineRes: Int,
) {
    WIDE(
        buttonSize = 52.dp,
        cornerRadius = 16.dp,
        iconSize = 24.dp,
        fillRes = R.drawable.widget_squircle_fill,
        outlineRes = R.drawable.widget_squircle_outline,
    ),
    COMPACT(
        buttonSize = 40.dp,
        cornerRadius = 13.dp,
        iconSize = 20.dp,
        fillRes = R.drawable.widget_squircle_compact_fill,
        outlineRes = R.drawable.widget_squircle_compact_outline,
    ),
}

@Composable
fun PowerButton(isOn: Boolean, isCompact: Boolean = false, showOfflineIcon: Boolean = false) {
    val style = if (isCompact) PowerButtonStyle.COMPACT else PowerButtonStyle.WIDE
    val isActive = isOn && !showOfflineIcon
    val context = LocalContext.current
    val action = if (showOfflineIcon) {
        actionRunCallback<RefreshAction>()
    } else {
        actionRunCallback<TogglePowerAction>(actionParametersOf(TogglePowerAction.keyIsOn to isOn))
    }
    val fillTint = if (isActive) GlanceTheme.colors.primary else GlanceTheme.colors.surfaceVariant
    val iconRes = if (showOfflineIcon) WifiSignalLevel.OFFLINE.toIconRes() else R.drawable.outline_power_settings_new_24
    val iconTint = when {
        isActive -> GlanceTheme.colors.onPrimary
        showOfflineIcon -> GlanceTheme.colors.error
        else -> GlanceTheme.colors.onSurfaceVariant
    }
    val iconDescription = context.getString(
        if (showOfflineIcon) R.string.is_offline else R.string.widget_toggle_power,
    )

    Box(
        modifier = GlanceModifier
            .size(style.buttonSize)
            .cornerRadius(style.cornerRadius)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(style.fillRes),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(fillTint),
        )
        if (!isActive) {
            Image(
                provider = ImageProvider(style.outlineRes),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.outline),
            )
        }
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = iconDescription,
            modifier = GlanceModifier.size(style.iconSize),
            colorFilter = ColorFilter.tint(iconTint),
        )
    }
}

@Suppress("MagicNumber")
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 140, heightDp = 70)
@Composable
private fun WidgetPowerButtonPreview() {
    GlanceTheme {
        Row {
            PowerButton(isOn = true)
            Spacer(modifier = GlanceModifier.width(8.dp))
            PowerButton(isOn = false)
        }
    }
}
