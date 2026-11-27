package ca.cgagnier.wlednativeandroid.ui.theme.holiday

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

private const val STATIC_PREVIEW_TIME_SECONDS = 2.4f
private const val NANOS_PER_SECOND = 1_000_000_000f

/**
 * Returns a draw-phase time provider (in seconds) for holiday animations.
 *
 * When running inside previews/screenshot tests or when system animations are disabled via
 * `ANIMATOR_DURATION_SCALE == 0`, returns a fixed timestamp so frames remain deterministic and
 * respect reduced-motion preferences.
 */
@Composable
internal fun rememberHolidayTimeSeconds(): () -> Float {
    if (LocalInspectionMode.current) {
        return remember { { STATIC_PREVIEW_TIME_SECONDS } }
    }

    val context = LocalContext.current
    val animationsEnabled = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    if (!animationsEnabled) {
        return remember { { STATIC_PREVIEW_TIME_SECONDS } }
    }

    val elapsedSeconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val startTimeNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameNanos ->
                elapsedSeconds.floatValue = (frameNanos - startTimeNanos) / NANOS_PER_SECOND
            }
        }
    }
    return remember { { elapsedSeconds.floatValue } }
}
