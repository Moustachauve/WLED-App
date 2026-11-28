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

/**
 * Displays a device's summary inside a rounded card, optionally themed by the device's live state.
 */
@Composable
fun DeviceInfoCard(
    device: DeviceWithState,
    modifier: Modifier = Modifier,
    currentTime: Long = 0L,
    nameMaxLines: Int = 2,
    style: DeviceInfoTwoRowsStyle = DeviceInfoTwoRowsStyle(),
) {
    OptionalDeviceTheme(
        device = device,
        enabled = style.statusVisibility != DeviceStatusVisibility.None,
    ) {
        DeviceCardSurface(modifier = modifier) {
            DeviceInfoTwoRows(
                modifier = Modifier.weight(1f),
                device = device,
                currentTime = currentTime,
                nameMaxLines = nameMaxLines,
                style = style,
            )
        }
    }
}

@Composable
internal fun OptionalDeviceTheme(device: DeviceWithState, enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) {
        DeviceTheme(device = device, content = content)
    } else {
        content()
    }
}

@Composable
internal fun DeviceCardSurface(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    contentModifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val border = if (isSelected) {
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
                .then(contentModifier)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
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
