package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

val DefaultDialogBlurBehindRadius = 10.dp

fun blurredDialogProperties(
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    usePlatformDefaultWidth: Boolean = true,
): DialogProperties = DialogProperties(
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = dismissOnClickOutside,
    usePlatformDefaultWidth = usePlatformDefaultWidth,
    blurBehindRadius = DefaultDialogBlurBehindRadius,
)
