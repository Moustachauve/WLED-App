package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.soundsync.SoundSyncSendMode
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState

/**
 * Describes which devices are actively receiving Sound Sync audio from this device.
 */
@Immutable
sealed interface ActiveSoundSyncTargets {
    fun isDeviceReceiving(device: DeviceWithState): Boolean

    data object None : ActiveSoundSyncTargets {
        override fun isDeviceReceiving(device: DeviceWithState): Boolean = false
    }

    data object AllOnline : ActiveSoundSyncTargets {
        override fun isDeviceReceiving(device: DeviceWithState): Boolean = device.isOnline && !device.isAPMode
    }

    @Immutable
    data class Selected(val macAddresses: Set<String>, val includeHidden: Boolean = false) : ActiveSoundSyncTargets {
        override fun isDeviceReceiving(device: DeviceWithState): Boolean = device.isOnline &&
            !device.isAPMode &&
            (includeHidden || !device.device.isHidden) &&
            device.device.macAddress in macAddresses
    }
}

fun resolveActiveSoundSyncTargets(
    isRunning: Boolean,
    sendMode: SoundSyncSendMode,
    selectedDeviceMacs: Set<String>,
    showHiddenDevices: Boolean,
): ActiveSoundSyncTargets = when {
    !isRunning -> ActiveSoundSyncTargets.None

    sendMode == SoundSyncSendMode.MULTICAST -> ActiveSoundSyncTargets.AllOnline

    else -> ActiveSoundSyncTargets.Selected(
        macAddresses = selectedDeviceMacs,
        includeHidden = showHiddenDevices,
    )
}

val LocalActiveSoundSyncTargets = compositionLocalOf<ActiveSoundSyncTargets> {
    ActiveSoundSyncTargets.None
}

@Composable
fun deviceSoundSyncImage(device: DeviceWithState, modifier: Modifier = Modifier) {
    val activeTargets = LocalActiveSoundSyncTargets.current
    if (!activeTargets.isDeviceReceiving(device)) return

    TooltipBox(
        positionProvider = rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(stringResource(R.string.sound_sync_notification_title))
            }
        },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_sound_sync_akemi_active),
            contentDescription = stringResource(R.string.sound_sync_notification_title),
            modifier = Modifier
                .padding(start = 4.dp)
                .size(16.dp)
                .offset(y = (-1).dp),
        )
    }
}
