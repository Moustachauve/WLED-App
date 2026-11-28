package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.ui.preview.DevicePreviewParameterProvider
import ca.cgagnier.wlednativeandroid.ui.theme.DeviceTheme
import ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme
import ca.cgagnier.wlednativeandroid.ui.theme.holiday.holidayCardColors

private val SELECTED_BORDER_WIDTH = 1.5.dp

@Immutable
data class DeviceInfoCardStyle(
    val isSelected: Boolean = false,
    val currentTime: Long = 0L,
    val nameMaxLines: Int = 2,
    val infoStyle: DeviceInfoTwoRowsStyle = DeviceInfoTwoRowsStyle(),
    val contentModifier: Modifier = Modifier,
)

/**
 * Displays a device's summary inside a rounded card, optionally themed by the device's live state
 * and accented when selected.
 */
@Composable
fun DeviceInfoCard(
    device: DeviceWithState,
    modifier: Modifier = Modifier,
    style: DeviceInfoCardStyle = DeviceInfoCardStyle(),
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    if (style.infoStyle.statusVisibility != DeviceStatusVisibility.None) {
        DeviceTheme(device) {
            DeviceInfoCardSurface(
                device = device,
                modifier = modifier,
                style = style,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
            )
        }
    } else {
        DeviceInfoCardSurface(
            device = device,
            modifier = modifier,
            style = style,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
        )
    }
}

@Composable
private fun DeviceInfoCardSurface(
    device: DeviceWithState,
    modifier: Modifier,
    style: DeviceInfoCardStyle,
    leadingContent: (@Composable RowScope.() -> Unit)?,
    trailingContent: (@Composable RowScope.() -> Unit)?,
) {
    val containerColor = if (style.isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val border = if (style.isSelected) {
        BorderStroke(SELECTED_BORDER_WIDTH, MaterialTheme.colorScheme.primary)
    } else {
        null
    }

    Card(
        modifier = modifier,
        colors = holidayCardColors(containerColor = containerColor),
        border = border,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardDefaults.shape)
                .then(style.contentModifier)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leadingContent?.invoke(this)
            DeviceInfoTwoRows(
                modifier = Modifier.weight(1f),
                device = device,
                currentTime = style.currentTime,
                nameMaxLines = style.nameMaxLines,
                style = style.infoStyle,
            )
            trailingContent?.invoke(this)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeviceInfoCardPreview(@PreviewParameter(DevicePreviewParameterProvider::class) device: DeviceWithState) {
    WLEDNativeTheme(darkTheme = false, dynamicColor = false) {
        DeviceInfoCard(
            device = device,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}
