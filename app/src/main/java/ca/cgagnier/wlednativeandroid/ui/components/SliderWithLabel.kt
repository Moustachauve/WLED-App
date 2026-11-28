package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private data class SliderInteractionState(val isPressed: Boolean = false, val isDragging: Boolean = false) {
    val isInteracting: Boolean get() = isPressed || isDragging
}

@Composable
fun SliderWithLabel(
    value: Float,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
    val interactionState = rememberSliderInteractionState(interactionSource)
    val sliderPosition = rememberAnimatedSliderValue(
        targetValue = value,
        isDragging = interactionState.isDragging,
    )
    val sliderState = remember(valueRange) {
        SliderState(value = sliderPosition, trackRange = valueRange)
    }
    sliderState.value = sliderPosition
    Slider(
        state = sliderState,
        enabled = enabled,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        thumb = {
            if (interactionState.isInteracting) {
                val labelValue = value.roundToInt().coerceIn(
                    valueRange.start.roundToInt(),
                    valueRange.endInclusive.roundToInt(),
                )
                Label(
                    label = {
                        PlainTooltip(shape = MaterialTheme.shapes.large) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .padding(8.dp),
                            ) {
                                Text(
                                    labelValue.toString(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    },
                    interactionSource = interactionSource,
                    isPersistent = true,
                ) {
                    SliderDefaults.Thumb(interactionSource = interactionSource)
                }
            } else {
                SliderDefaults.Thumb(interactionSource = interactionSource)
            }
        },
    )
}

@Composable
private fun rememberSliderInteractionState(interactionSource: MutableInteractionSource): SliderInteractionState {
    var interactionState by remember(interactionSource) { mutableStateOf(SliderInteractionState()) }
    LaunchedEffect(interactionSource) {
        val activePresses = mutableListOf<PressInteraction.Press>()
        val activeDrags = mutableListOf<DragInteraction.Start>()
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> activePresses.add(interaction)
                is PressInteraction.Release -> activePresses.remove(interaction.press)
                is PressInteraction.Cancel -> activePresses.remove(interaction.press)
                is DragInteraction.Start -> activeDrags.add(interaction)
                is DragInteraction.Stop -> activeDrags.remove(interaction.start)
                is DragInteraction.Cancel -> activeDrags.remove(interaction.start)
            }
            interactionState = SliderInteractionState(
                isPressed = activePresses.isNotEmpty(),
                isDragging = activeDrags.isNotEmpty(),
            )
        }
    }
    return interactionState
}

@Composable
private fun rememberAnimatedSliderValue(targetValue: Float, isDragging: Boolean): Float {
    var settledValue by remember { mutableFloatStateOf(targetValue) }
    if (isDragging) {
        SideEffect { settledValue = targetValue }
        return targetValue
    }
    if (settledValue == targetValue) {
        return targetValue
    }
    val animatable = remember { Animatable(settledValue) }
    LaunchedEffect(targetValue) {
        animatable.animateTo(
            targetValue = targetValue,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        )
        settledValue = targetValue
    }
    return animatable.value
}
