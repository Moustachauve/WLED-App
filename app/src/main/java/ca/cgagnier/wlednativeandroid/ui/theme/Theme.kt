package ca.cgagnier.wlednativeandroid.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.domain.HolidayTheme
import ca.cgagnier.wlednativeandroid.model.getColorFromState
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.repository.ThemeSettings
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.rememberDynamicColorScheme
import org.koin.androidx.compose.koinViewModel

private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)

private val mediumContrastLightColorScheme = lightColorScheme(
    primary = primaryLightMediumContrast,
    onPrimary = onPrimaryLightMediumContrast,
    primaryContainer = primaryContainerLightMediumContrast,
    onPrimaryContainer = onPrimaryContainerLightMediumContrast,
    secondary = secondaryLightMediumContrast,
    onSecondary = onSecondaryLightMediumContrast,
    secondaryContainer = secondaryContainerLightMediumContrast,
    onSecondaryContainer = onSecondaryContainerLightMediumContrast,
    tertiary = tertiaryLightMediumContrast,
    onTertiary = onTertiaryLightMediumContrast,
    tertiaryContainer = tertiaryContainerLightMediumContrast,
    onTertiaryContainer = onTertiaryContainerLightMediumContrast,
    error = errorLightMediumContrast,
    onError = onErrorLightMediumContrast,
    errorContainer = errorContainerLightMediumContrast,
    onErrorContainer = onErrorContainerLightMediumContrast,
    background = backgroundLightMediumContrast,
    onBackground = onBackgroundLightMediumContrast,
    surface = surfaceLightMediumContrast,
    onSurface = onSurfaceLightMediumContrast,
    surfaceVariant = surfaceVariantLightMediumContrast,
    onSurfaceVariant = onSurfaceVariantLightMediumContrast,
    outline = outlineLightMediumContrast,
    outlineVariant = outlineVariantLightMediumContrast,
    scrim = scrimLightMediumContrast,
    inverseSurface = inverseSurfaceLightMediumContrast,
    inverseOnSurface = inverseOnSurfaceLightMediumContrast,
    inversePrimary = inversePrimaryLightMediumContrast,
    surfaceDim = surfaceDimLightMediumContrast,
    surfaceBright = surfaceBrightLightMediumContrast,
    surfaceContainerLowest = surfaceContainerLowestLightMediumContrast,
    surfaceContainerLow = surfaceContainerLowLightMediumContrast,
    surfaceContainer = surfaceContainerLightMediumContrast,
    surfaceContainerHigh = surfaceContainerHighLightMediumContrast,
    surfaceContainerHighest = surfaceContainerHighestLightMediumContrast,
)

private val highContrastLightColorScheme = lightColorScheme(
    primary = primaryLightHighContrast,
    onPrimary = onPrimaryLightHighContrast,
    primaryContainer = primaryContainerLightHighContrast,
    onPrimaryContainer = onPrimaryContainerLightHighContrast,
    secondary = secondaryLightHighContrast,
    onSecondary = onSecondaryLightHighContrast,
    secondaryContainer = secondaryContainerLightHighContrast,
    onSecondaryContainer = onSecondaryContainerLightHighContrast,
    tertiary = tertiaryLightHighContrast,
    onTertiary = onTertiaryLightHighContrast,
    tertiaryContainer = tertiaryContainerLightHighContrast,
    onTertiaryContainer = onTertiaryContainerLightHighContrast,
    error = errorLightHighContrast,
    onError = onErrorLightHighContrast,
    errorContainer = errorContainerLightHighContrast,
    onErrorContainer = onErrorContainerLightHighContrast,
    background = backgroundLightHighContrast,
    onBackground = onBackgroundLightHighContrast,
    surface = surfaceLightHighContrast,
    onSurface = onSurfaceLightHighContrast,
    surfaceVariant = surfaceVariantLightHighContrast,
    onSurfaceVariant = onSurfaceVariantLightHighContrast,
    outline = outlineLightHighContrast,
    outlineVariant = outlineVariantLightHighContrast,
    scrim = scrimLightHighContrast,
    inverseSurface = inverseSurfaceLightHighContrast,
    inverseOnSurface = inverseOnSurfaceLightHighContrast,
    inversePrimary = inversePrimaryLightHighContrast,
    surfaceDim = surfaceDimLightHighContrast,
    surfaceBright = surfaceBrightLightHighContrast,
    surfaceContainerLowest = surfaceContainerLowestLightHighContrast,
    surfaceContainerLow = surfaceContainerLowLightHighContrast,
    surfaceContainer = surfaceContainerLightHighContrast,
    surfaceContainerHigh = surfaceContainerHighLightHighContrast,
    surfaceContainerHighest = surfaceContainerHighestLightHighContrast,
)

private val mediumContrastDarkColorScheme = darkColorScheme(
    primary = primaryDarkMediumContrast,
    onPrimary = onPrimaryDarkMediumContrast,
    primaryContainer = primaryContainerDarkMediumContrast,
    onPrimaryContainer = onPrimaryContainerDarkMediumContrast,
    secondary = secondaryDarkMediumContrast,
    onSecondary = onSecondaryDarkMediumContrast,
    secondaryContainer = secondaryContainerDarkMediumContrast,
    onSecondaryContainer = onSecondaryContainerDarkMediumContrast,
    tertiary = tertiaryDarkMediumContrast,
    onTertiary = onTertiaryDarkMediumContrast,
    tertiaryContainer = tertiaryContainerDarkMediumContrast,
    onTertiaryContainer = onTertiaryContainerDarkMediumContrast,
    error = errorDarkMediumContrast,
    onError = onErrorDarkMediumContrast,
    errorContainer = errorContainerDarkMediumContrast,
    onErrorContainer = onErrorContainerDarkMediumContrast,
    background = backgroundDarkMediumContrast,
    onBackground = onBackgroundDarkMediumContrast,
    surface = surfaceDarkMediumContrast,
    onSurface = onSurfaceDarkMediumContrast,
    surfaceVariant = surfaceVariantDarkMediumContrast,
    onSurfaceVariant = onSurfaceVariantDarkMediumContrast,
    outline = outlineDarkMediumContrast,
    outlineVariant = outlineVariantDarkMediumContrast,
    scrim = scrimDarkMediumContrast,
    inverseSurface = inverseSurfaceDarkMediumContrast,
    inverseOnSurface = inverseOnSurfaceDarkMediumContrast,
    inversePrimary = inversePrimaryDarkMediumContrast,
    surfaceDim = surfaceDimDarkMediumContrast,
    surfaceBright = surfaceBrightDarkMediumContrast,
    surfaceContainerLowest = surfaceContainerLowestDarkMediumContrast,
    surfaceContainerLow = surfaceContainerLowDarkMediumContrast,
    surfaceContainer = surfaceContainerDarkMediumContrast,
    surfaceContainerHigh = surfaceContainerHighDarkMediumContrast,
    surfaceContainerHighest = surfaceContainerHighestDarkMediumContrast,
)

private val highContrastDarkColorScheme = darkColorScheme(
    primary = primaryDarkHighContrast,
    onPrimary = onPrimaryDarkHighContrast,
    primaryContainer = primaryContainerDarkHighContrast,
    onPrimaryContainer = onPrimaryContainerDarkHighContrast,
    secondary = secondaryDarkHighContrast,
    onSecondary = onSecondaryDarkHighContrast,
    secondaryContainer = secondaryContainerDarkHighContrast,
    onSecondaryContainer = onSecondaryContainerDarkHighContrast,
    tertiary = tertiaryDarkHighContrast,
    onTertiary = onTertiaryDarkHighContrast,
    tertiaryContainer = tertiaryContainerDarkHighContrast,
    onTertiaryContainer = onTertiaryContainerDarkHighContrast,
    error = errorDarkHighContrast,
    onError = onErrorDarkHighContrast,
    errorContainer = errorContainerDarkHighContrast,
    onErrorContainer = onErrorContainerDarkHighContrast,
    background = backgroundDarkHighContrast,
    onBackground = onBackgroundDarkHighContrast,
    surface = surfaceDarkHighContrast,
    onSurface = onSurfaceDarkHighContrast,
    surfaceVariant = surfaceVariantDarkHighContrast,
    onSurfaceVariant = onSurfaceVariantDarkHighContrast,
    outline = outlineDarkHighContrast,
    outlineVariant = outlineVariantDarkHighContrast,
    scrim = scrimDarkHighContrast,
    inverseSurface = inverseSurfaceDarkHighContrast,
    inverseOnSurface = inverseOnSurfaceDarkHighContrast,
    inversePrimary = inversePrimaryDarkHighContrast,
    surfaceDim = surfaceDimDarkHighContrast,
    surfaceBright = surfaceBrightDarkHighContrast,
    surfaceContainerLowest = surfaceContainerLowestDarkHighContrast,
    surfaceContainerLow = surfaceContainerLowDarkHighContrast,
    surfaceContainer = surfaceContainerDarkHighContrast,
    surfaceContainerHigh = surfaceContainerHighDarkHighContrast,
    surfaceContainerHighest = surfaceContainerHighestDarkHighContrast,
)

@Immutable
data class ColorFamily(val color: Color, val onColor: Color, val colorContainer: Color, val onColorContainer: Color)

val unspecified_scheme = ColorFamily(
    Color.Unspecified,
    Color.Unspecified,
    Color.Unspecified,
    Color.Unspecified,
)

private const val WINTER_PRIMARY_SEED = 0xFF1B6B4AL
private const val WINTER_SECONDARY_SEED = 0xFFB3261EL
private const val WINTER_TERTIARY_SEED = 0xFFC8962EL
private const val NEW_YEAR_PRIMARY_SEED = 0xFF3949ABL
private const val NEW_YEAR_SECONDARY_SEED = 0xFFD4AF37L
private const val NEW_YEAR_TERTIARY_SEED = 0xFFC2185BL

internal val LocalDarkTheme = staticCompositionLocalOf<Boolean?> { null }
val LocalHolidayTheme = staticCompositionLocalOf<HolidayTheme?> { null }

@Composable
internal fun currentIsDarkTheme(): Boolean = LocalDarkTheme.current ?: isSystemInDarkTheme()

@Composable
fun WLEDNativeTheme(
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    themeViewModel: ThemeViewModel = koinViewModel(),
    content: @Composable () -> Unit,
) {
    val themeState by themeViewModel.themeState.collectAsStateWithLifecycle()
    val currentState = themeState ?: return
    val darkTheme = when (currentState.theme) {
        ThemeSettings.Dark -> true
        ThemeSettings.Light -> false
        ThemeSettings.Auto -> isSystemInDarkTheme()
    }
    WLEDNativeTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        holidayTheme = currentState.holidayTheme,
        content = content,
    )
}

@Composable
fun WLEDNativeTheme(
    darkTheme: Boolean,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    holidayTheme: HolidayTheme? = null,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val colorScheme = when {
        holidayTheme != null -> holidayColorScheme(holidayTheme = holidayTheme, darkTheme = darkTheme)

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkScheme

        else -> lightScheme
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalHolidayTheme provides holidayTheme,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                !darkTheme // negate darkTheme
        }
    }
}

@Composable
private fun holidayColorScheme(holidayTheme: HolidayTheme, darkTheme: Boolean): ColorScheme = when (holidayTheme) {
    HolidayTheme.WinterHolidays -> rememberDynamicColorScheme(
        seedColor = Color(WINTER_PRIMARY_SEED),
        isDark = darkTheme,
        primary = Color(WINTER_PRIMARY_SEED),
        secondary = Color(WINTER_SECONDARY_SEED),
        tertiary = Color(WINTER_TERTIARY_SEED),
        style = PaletteStyle.TonalSpot,
    )

    HolidayTheme.NewYear -> rememberDynamicColorScheme(
        seedColor = Color(NEW_YEAR_PRIMARY_SEED),
        isDark = darkTheme,
        primary = Color(NEW_YEAR_PRIMARY_SEED),
        secondary = Color(NEW_YEAR_SECONDARY_SEED),
        tertiary = Color(NEW_YEAR_TERTIARY_SEED),
        style = PaletteStyle.TonalSpot,
    )
}

private const val DEVICE_COLOR_SCHEME_CACHE_SIZE = 64
private const val DEVICE_COLOR_SCHEME_CACHE_LOAD_FACTOR = 0.75f

private data class DeviceColorSchemeKey(val seedColorArgb: Int, val isDark: Boolean, val style: PaletteStyle)

private class LastRenderedSchemeHolder(var scheme: ColorScheme)

private val deviceColorSchemeCache = object : LinkedHashMap<DeviceColorSchemeKey, ColorScheme>(
    DEVICE_COLOR_SCHEME_CACHE_SIZE,
    DEVICE_COLOR_SCHEME_CACHE_LOAD_FACTOR,
    true,
) {
    override fun removeEldestEntry(eldest: Map.Entry<DeviceColorSchemeKey, ColorScheme>): Boolean =
        size > DEVICE_COLOR_SCHEME_CACHE_SIZE
}

internal fun clearDeviceColorSchemeCache() {
    synchronized(deviceColorSchemeCache) {
        deviceColorSchemeCache.clear()
    }
}

internal fun getCachedDeviceColorScheme(seedColorArgb: Int, isDark: Boolean, style: PaletteStyle): ColorScheme {
    val key = DeviceColorSchemeKey(seedColorArgb = seedColorArgb, isDark = isDark, style = style)
    synchronized(deviceColorSchemeCache) {
        deviceColorSchemeCache[key]?.let { return it }
    }
    val computed = dynamicColorScheme(
        seedColor = Color(seedColorArgb),
        isDark = isDark,
        style = style,
    )
    synchronized(deviceColorSchemeCache) {
        deviceColorSchemeCache[key]?.let { return it }
        deviceColorSchemeCache[key] = computed
        return computed
    }
}

@Composable
private fun rememberAnimatedDeviceColorScheme(targetScheme: ColorScheme): ColorScheme {
    var settledScheme by remember { mutableStateOf(targetScheme) }
    val renderedHolder = remember { LastRenderedSchemeHolder(targetScheme) }

    if (settledScheme == targetScheme && renderedHolder.scheme == targetScheme) {
        return targetScheme
    }

    val startScheme = remember(targetScheme) { renderedHolder.scheme }
    val progress = remember(targetScheme) { Animatable(0f) }
    LaunchedEffect(targetScheme) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
        )
        settledScheme = targetScheme
    }
    return lerpColorScheme(startScheme, targetScheme, progress.value).also {
        renderedHolder.scheme = it
    }
}

internal fun lerpColorScheme(start: ColorScheme, stop: ColorScheme, fraction: Float): ColorScheme {
    if (fraction <= 0f) return start
    if (fraction >= 1f) return stop
    return start.copy(
        primary = lerp(start.primary, stop.primary, fraction),
        onPrimary = lerp(start.onPrimary, stop.onPrimary, fraction),
        primaryContainer = lerp(start.primaryContainer, stop.primaryContainer, fraction),
        onPrimaryContainer = lerp(start.onPrimaryContainer, stop.onPrimaryContainer, fraction),
        inversePrimary = lerp(start.inversePrimary, stop.inversePrimary, fraction),
        secondary = lerp(start.secondary, stop.secondary, fraction),
        onSecondary = lerp(start.onSecondary, stop.onSecondary, fraction),
        secondaryContainer = lerp(start.secondaryContainer, stop.secondaryContainer, fraction),
        onSecondaryContainer = lerp(start.onSecondaryContainer, stop.onSecondaryContainer, fraction),
        tertiary = lerp(start.tertiary, stop.tertiary, fraction),
        onTertiary = lerp(start.onTertiary, stop.onTertiary, fraction),
        tertiaryContainer = lerp(start.tertiaryContainer, stop.tertiaryContainer, fraction),
        onTertiaryContainer = lerp(start.onTertiaryContainer, stop.onTertiaryContainer, fraction),
        background = lerp(start.background, stop.background, fraction),
        onBackground = lerp(start.onBackground, stop.onBackground, fraction),
        surface = lerp(start.surface, stop.surface, fraction),
        onSurface = lerp(start.onSurface, stop.onSurface, fraction),
        surfaceVariant = lerp(start.surfaceVariant, stop.surfaceVariant, fraction),
        onSurfaceVariant = lerp(start.onSurfaceVariant, stop.onSurfaceVariant, fraction),
        surfaceTint = lerp(start.surfaceTint, stop.surfaceTint, fraction),
        inverseSurface = lerp(start.inverseSurface, stop.inverseSurface, fraction),
        inverseOnSurface = lerp(start.inverseOnSurface, stop.inverseOnSurface, fraction),
        error = lerp(start.error, stop.error, fraction),
        onError = lerp(start.onError, stop.onError, fraction),
        errorContainer = lerp(start.errorContainer, stop.errorContainer, fraction),
        onErrorContainer = lerp(start.onErrorContainer, stop.onErrorContainer, fraction),
        outline = lerp(start.outline, stop.outline, fraction),
        outlineVariant = lerp(start.outlineVariant, stop.outlineVariant, fraction),
        scrim = lerp(start.scrim, stop.scrim, fraction),
        surfaceBright = lerp(start.surfaceBright, stop.surfaceBright, fraction),
        surfaceDim = lerp(start.surfaceDim, stop.surfaceDim, fraction),
        surfaceContainer = lerp(start.surfaceContainer, stop.surfaceContainer, fraction),
        surfaceContainerHigh = lerp(start.surfaceContainerHigh, stop.surfaceContainerHigh, fraction),
        surfaceContainerHighest = lerp(start.surfaceContainerHighest, stop.surfaceContainerHighest, fraction),
        surfaceContainerLow = lerp(start.surfaceContainerLow, stop.surfaceContainerLow, fraction),
        surfaceContainerLowest = lerp(start.surfaceContainerLowest, stop.surfaceContainerLowest, fraction),
        primaryFixed = lerp(start.primaryFixed, stop.primaryFixed, fraction),
        primaryFixedDim = lerp(start.primaryFixedDim, stop.primaryFixedDim, fraction),
        onPrimaryFixed = lerp(start.onPrimaryFixed, stop.onPrimaryFixed, fraction),
        onPrimaryFixedVariant = lerp(start.onPrimaryFixedVariant, stop.onPrimaryFixedVariant, fraction),
        secondaryFixed = lerp(start.secondaryFixed, stop.secondaryFixed, fraction),
        secondaryFixedDim = lerp(start.secondaryFixedDim, stop.secondaryFixedDim, fraction),
        onSecondaryFixed = lerp(start.onSecondaryFixed, stop.onSecondaryFixed, fraction),
        onSecondaryFixedVariant = lerp(start.onSecondaryFixedVariant, stop.onSecondaryFixedVariant, fraction),
        tertiaryFixed = lerp(start.tertiaryFixed, stop.tertiaryFixed, fraction),
        tertiaryFixedDim = lerp(start.tertiaryFixedDim, stop.tertiaryFixedDim, fraction),
        onTertiaryFixed = lerp(start.onTertiaryFixed, stop.onTertiaryFixed, fraction),
        onTertiaryFixedVariant = lerp(start.onTertiaryFixedVariant, stop.onTertiaryFixedVariant, fraction),
    )
}

fun getColorFromDeviceState(state: State?): Int = getColorFromState(state) ?: android.graphics.Color.WHITE

@Composable
fun DeviceTheme(device: DeviceWithState, content: @Composable () -> Unit) {
    DeviceTheme(
        device = device,
        darkTheme = currentIsDarkTheme(),
        content = content,
    )
}

@Composable
fun DeviceTheme(device: DeviceWithState, darkTheme: Boolean, content: @Composable () -> Unit) {
    val seedColorArgb = getColorFromDeviceState(device.stateInfo?.state)
    val style = if (device.isOnline) PaletteStyle.Vibrant else PaletteStyle.Neutral
    val targetScheme = remember(seedColorArgb, darkTheme, style) {
        getCachedDeviceColorScheme(
            seedColorArgb = seedColorArgb,
            isDark = darkTheme,
            style = style,
        )
    }
    val animatedScheme = rememberAnimatedDeviceColorScheme(targetScheme)

    MaterialTheme(
        colorScheme = animatedScheme,
        content = content,
    )
}
