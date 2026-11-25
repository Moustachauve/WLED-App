package ca.cgagnier.wlednativeandroid.ui.homeScreen.list

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.ui.components.DeviceInfoTwoRows
import ca.cgagnier.wlednativeandroid.ui.components.OfflineSinceText
import ca.cgagnier.wlednativeandroid.ui.components.WebsocketStatusIndicator
import ca.cgagnier.wlednativeandroid.ui.components.deviceName
import ca.cgagnier.wlednativeandroid.ui.components.deviceNetworkStrengthImage
import ca.cgagnier.wlednativeandroid.ui.theme.DeviceTheme
import ca.cgagnier.wlednativeandroid.ui.theme.getColorFromDeviceState
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun DeviceListItem(
    modifier: Modifier = Modifier,
    device: DeviceWithState,
    isSelected: Boolean = false,
    // Used to update the lastSeen message frequently, leave 0 for no updates
    currentTime: Long = 0,
    onClick: () -> Unit = {},
    swipeToDismissBoxState: SwipeToDismissBoxState,
    onDismiss: (SwipeToDismissBoxValue) -> Unit,
    onPowerSwitchToggle: (isOn: Boolean) -> Unit = {},
    onBrightnessChanged: (brightness: Int) -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current

    DeviceTheme(device) {
        SwipeBox(
            modifier = modifier,
            device = device,
            swipeToDismissBoxState = swipeToDismissBoxState,
            onDismiss = onDismiss,
        ) {
            IlDeviceCard(
                device = device,
                isSelected = isSelected,
                currentTime = currentTime,
                callbacks = IlDeviceCardCallbacks(
                    onClick = onClick,
                    onCheckedChange = { isOn ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPowerSwitchToggle(isOn)
                    },
                    onBrightnessChanged = onBrightnessChanged,
                ),
            )
        }
    }
}

private data class IlDeviceCardCallbacks(
    val onClick: () -> Unit,
    val onCheckedChange: (Boolean) -> Unit,
    val onBrightnessChanged: (Int) -> Unit,
)

private const val IL_CARD_HEX = 0xFF141C2C
private const val IL_ACCENT_HEX = 0xFF2EC5FF
private const val IL_ON_ACCENT_HEX = 0xFF072133
private const val IL_TEXT2_HEX = 0xFFB9C6D8
private const val IL_TEXT3_HEX = 0xFF7F92AD
private const val IL_OFF_HEX = 0xFF55627A
private const val IL_VALUE_HEX = 0xFFCFD9E8

private const val HSV_COMPONENT_COUNT = 3
private const val MIN_TINT_VALUE = 0.35f
private const val DIMMED_CARD_ALPHA = 0.75f
private const val ORB_HIGHLIGHT_X = 0.35f
private const val ORB_HIGHLIGHT_Y = 0.3f
private const val ORB_GRADIENT_RADIUS_FACTOR = 0.62f
private const val MAX_BRIGHTNESS = 255f
private const val PERCENT_SCALE = 100

// IllumiDEL palette (same values as the iOS card and the V1 web page)
private object Il {
    val Card = Color(IL_CARD_HEX)
    val Accent = Color(IL_ACCENT_HEX)
    val OnAccent = Color(IL_ON_ACCENT_HEX)
    val Text2 = Color(IL_TEXT2_HEX)
    val Text3 = Color(IL_TEXT3_HEX)
    val Off = Color(IL_OFF_HEX)
    val Value = Color(IL_VALUE_HEX)
}

// Device colour, kept bright enough to read on the dark background
private fun deviceTint(argb: Int): Color {
    val hsv = FloatArray(HSV_COMPONENT_COUNT)
    android.graphics.Color.colorToHSV(argb, hsv)
    hsv[2] = max(hsv[2], MIN_TINT_VALUE)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
private fun IlDeviceCard(
    device: DeviceWithState,
    isSelected: Boolean,
    currentTime: Long,
    callbacks: IlDeviceCardCallbacks,
) {
    val argb = getColorFromDeviceState(device.stateInfo?.state)
    val tint = remember(argb) { deviceTint(argb) }
    val isOnline = device.isOnline
    val checked = device.stateInfo?.state?.isOn ?: false
    val isLit = checked && isOnline
    val shape = RoundedCornerShape(22.dp)
    val borderColor = when {
        isSelected -> Il.Accent
        isLit -> tint.copy(alpha = 0.25f)
        else -> Color.White.copy(alpha = 0.08f)
    }

    Column(
        modifier = Modifier
            .padding(bottom = 12.dp)
            .alpha(if (isLit || isSelected) 1f else DIMMED_CARD_ALPHA)
            .shadow(14.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Il.Card)
            .background(
                Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = if (isLit) 0.18f else 0f), Color.Transparent),
                    center = Offset.Zero,
                    radius = 900f,
                ),
            )
            .border(1.dp, borderColor, shape)
            .clickable(onClick = callbacks.onClick)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    ) {
        IlDeviceCardHeader(
            device = device,
            tint = tint,
            isLit = isLit,
            currentTime = currentTime,
            onCheckedChange = callbacks.onCheckedChange,
        )
        BrightnessRow(
            brightness = device.stateInfo?.state?.brightness ?: 0,
            isOnline = isOnline,
            isLit = isLit,
            tint = tint,
            onBrightnessChanged = callbacks.onBrightnessChanged,
        )
    }
}

@Composable
private fun IlDeviceCardHeader(
    device: DeviceWithState,
    tint: Color,
    isLit: Boolean,
    currentTime: Long,
    onCheckedChange: (Boolean) -> Unit,
) {
    val isOnline = device.isOnline
    val checked = device.stateInfo?.state?.isOn ?: false
    Row(verticalAlignment = Alignment.CenterVertically) {
        ColorOrb(tint = tint, isLit = isLit)
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            IlDeviceTitleRow(device = device)
            if (isOnline) {
                Text(
                    stringResource(if (checked) R.string.il_status_on else R.string.il_status_off),
                    color = Il.Text2,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            } else {
                OfflineSinceText(device.device, currentTime = currentTime)
            }
            IlDeviceMetaRow(device = device)
        }
        Switch(
            modifier = Modifier.padding(start = 10.dp),
            checked = checked,
            enabled = isOnline,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Il.Accent,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.White.copy(alpha = 0.16f),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun IlDeviceTitleRow(device: DeviceWithState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            deviceName(device.device),
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (device.updateVersionTag != null) {
            Text(
                stringResource(R.string.il_update_badge),
                color = Il.OnAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .background(Il.Accent, RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun IlDeviceMetaRow(device: DeviceWithState) {
    CompositionLocalProvider(LocalContentColor provides Il.Text3) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WebsocketStatusIndicator(device.websocketStatus)
            Text(
                device.device.address,
                color = Il.Text3,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 5.dp)
                    .weight(1f, fill = false),
            )
            deviceNetworkStrengthImage(device)
            if (device.device.isHidden) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_visibility_off_24),
                    contentDescription = stringResource(R.string.hidden_status),
                    tint = Il.Text3,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(12.dp),
                )
            }
        }
    }
}

// Dot glowing in the light's current colour (grey when off)
@Composable
private fun ColorOrb(tint: Color, isLit: Boolean) {
    val c = if (isLit) tint else Il.Off
    Box(
        modifier = Modifier
            .size(42.dp)
            .drawBehind {
                if (isLit) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(tint.copy(alpha = 0.55f), Color.Transparent),
                            center = center,
                            radius = size.minDimension,
                        ),
                        radius = size.minDimension,
                    )
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, c, c.copy(alpha = 0.6f)),
                        center = Offset(size.width * ORB_HIGHLIGHT_X, size.height * ORB_HIGHLIGHT_Y),
                        radius = size.width * ORB_GRADIENT_RADIUS_FACTOR,
                    ),
                )
            },
    )
}

@Composable
private fun BrightnessRow(
    brightness: Int,
    isOnline: Boolean,
    isLit: Boolean,
    tint: Color,
    onBrightnessChanged: (brightness: Int) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var sliderPosition by remember(brightness) { mutableFloatStateOf(brightness.toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.il_ic_brightness),
            contentDescription = null,
            tint = Il.Text3,
            modifier = Modifier.size(16.dp),
        )
        Slider(
            value = if (isOnline) sliderPosition else 1f,
            enabled = isOnline,
            onValueChange = {
                if (it.roundToInt() != sliderPosition.roundToInt()) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                sliderPosition = it
            },
            valueRange = 1f..MAX_BRIGHTNESS,
            onValueChangeFinished = { onBrightnessChanged(sliderPosition.roundToInt()) },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = if (isLit) tint else Il.Off,
                inactiveTrackColor = Color.White.copy(alpha = 0.10f),
                disabledThumbColor = Il.Off,
                disabledActiveTrackColor = Il.Off,
                disabledInactiveTrackColor = Color.White.copy(alpha = 0.06f),
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        Text(
            "${(sliderPosition / MAX_BRIGHTNESS * PERCENT_SCALE).roundToInt()} %",
            color = Il.Value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.width(44.dp),
        )
    }
}

@Composable
private fun SwipeBox(
    modifier: Modifier = Modifier,
    device: DeviceWithState,
    swipeToDismissBoxState: SwipeToDismissBoxState,
    onDismiss: (SwipeToDismissBoxValue) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(key1 = swipeToDismissBoxState.targetValue, block = {
        if (swipeToDismissBoxState.progress in 0.01..0.99) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    })

    SwipeToDismissBox(
        modifier = modifier,
        state = swipeToDismissBoxState,
        onDismiss = onDismiss,
        backgroundContent = {
            if (swipeToDismissBoxState.dismissDirection == SwipeToDismissBoxValue.Settled) {
                return@SwipeToDismissBox
            }
            val color by animateColorAsState(
                when (swipeToDismissBoxState.targetValue) {
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.tertiaryContainer
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                },
                label = "ColorAnimation",
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 12.dp)
                    .background(color, shape = RoundedCornerShape(22.dp)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (swipeToDismissBoxState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                        val penIcon =
                            if (swipeToDismissBoxState.targetValue ==
                                SwipeToDismissBoxValue.StartToEnd
                            ) {
                                Icons.Filled.Edit
                            } else {
                                Icons.Outlined.Edit
                            }
                        Crossfade(
                            modifier = Modifier.padding(end = 16.dp),
                            targetState = penIcon,
                            label = "pen icon",
                        ) {
                            Icon(
                                imageVector = it,
                                contentDescription = stringResource(R.string.description_back_button),
                            )
                        }
                    }
                    DeviceInfoTwoRows(
                        modifier = Modifier.weight(1f),
                        device = device,
                    )
                    if (swipeToDismissBoxState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                        val deleteIcon =
                            if (swipeToDismissBoxState.targetValue ==
                                SwipeToDismissBoxValue.EndToStart
                            ) {
                                Icons.Filled.Delete
                            } else {
                                Icons.Outlined.Delete
                            }
                        Crossfade(
                            modifier = Modifier.padding(start = 16.dp),
                            targetState = deleteIcon,
                            label = "delete icon",
                        ) {
                            Icon(
                                imageVector = it,
                                contentDescription = stringResource(R.string.description_back_button),
                            )
                        }
                    }
                }
            }
        },
    ) {
        content()
    }
}

@Composable
fun SelectableCard(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    colors: CardColors = CardDefaults.outlinedCardColors(),
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (isSelected) {
        OutlinedCard(
            modifier = modifier,
            colors = colors,
            onClick = onClick,
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier,
            colors = colors,
            onClick = onClick,
        ) {
            content()
        }
    }
}

@Composable
fun SkeletonDeviceRow() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        shape = CardDefaults.shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Text placeholders (Title and Subtitle)
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .height(16.dp)
                            .fillMaxWidth(0.6f)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .height(12.dp)
                            .fillMaxWidth(0.4f)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect(),
                    )
                }

                Spacer(modifier = Modifier.padding(8.dp))

                // Switch placeholder
                Box(
                    modifier = Modifier
                        .size(width = 50.dp, height = 30.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .shimmerEffect(),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Slider placeholder
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .shimmerEffect(),
            )
        }
    }
}

fun Modifier.shimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")

    val startOffsetX by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1500,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )

    val shimmerColors = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
    )

    background(
        brush = if (size.width == 0) {
            // Fallback before we know the size
            Brush.linearGradient(
                colors = shimmerColors,
                start = Offset.Zero,
                end = Offset.Zero,
            )
        } else {
            // Calculate the diagonal length to ensure smooth coverage
            val diagonal = (size.width + size.height).toFloat()
            // Move the gradient from [-diagonal] to [diagonal]
            // This ensures it enters from the top-left and exits bottom-right completely
            val start = (startOffsetX * 2 * diagonal) - diagonal
            val end = start + diagonal // Keep the gradient band fixed width

            Brush.linearGradient(
                colors = shimmerColors,
                start = Offset(start, start),
                end = Offset(end, end),
            )
        },
    ).onGloballyPositioned {
        size = it.size
    }
}

@Preview(showBackground = true)
@Composable
fun SkeletonDeviceRowPreview() {
    MaterialTheme {
        SkeletonDeviceRow()
    }
}
