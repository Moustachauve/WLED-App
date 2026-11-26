package ca.cgagnier.wlednativeandroid.ui.theme.holiday

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.domain.HolidayTheme
import ca.cgagnier.wlednativeandroid.ui.theme.LocalDarkTheme
import ca.cgagnier.wlednativeandroid.ui.theme.LocalHolidayTheme
import kotlin.math.sin

private const val HALF = 0.5f
private const val STRING_LIGHTS_HEIGHT_DP = 18
private const val BULB_SPACING_DP = 32f
private const val MIN_BULBS = 6
private const val MAX_BULBS = 30
private const val WIRE_Y_DP = 1.5f
private const val WIRE_STROKE_DP = 1.25f
private const val WIRE_ALPHA_DARK = 0.55f
private const val WIRE_ALPHA_LIGHT = 0.45f
private const val SOCKET_WIDTH_DP = 3.6f
private const val SOCKET_HEIGHT_DP = 2.4f
private const val SOCKET_TOP_DP = 0.9f
private const val SOCKET_CORNER_DP = 0.9f
private const val BULB_WIDTH_DP = 5.4f
private const val BULB_HEIGHT_DP = 7.4f
private const val BULB_TOP_DP = 2.8f
private const val GLOW_RADIUS_DP = 10.5f
private const val GLOW_CENTER_ALPHA = 0.70f
private const val GLOW_MID_ALPHA = 0.24f
private const val GLOW_MID_STOP = 0.48f
private const val SPECULAR_WIDTH_RATIO = 0.28f
private const val SPECULAR_HEIGHT_RATIO = 0.34f
private const val SPECULAR_OFFSET_X_RATIO = 0.20f
private const val SPECULAR_OFFSET_Y_RATIO = 0.16f
private const val TWINKLE_SPEED_RAD = 2.4f
private const val BULB_PHASE_STEP = 1.15f
private const val MIN_TWINKLE_FACTOR = 0.48f
private const val TWINKLE_AMPLITUDE = 0.52f
private const val GLOW_MAX_ALPHA_DARK = 0.60f
private const val GLOW_MAX_ALPHA_LIGHT = 0.42f
private const val BULB_BASE_ALPHA = 0.72f
private const val BULB_TWINKLE_ALPHA = 0.28f
private const val SPECULAR_ALPHA = 0.65f

private const val WIRE_COLOR_DARK_HEX = 0xFF6B7280L
private const val WIRE_COLOR_LIGHT_HEX = 0xFF6B7280L
private const val SOCKET_COLOR_DARK_HEX = 0xFF374151L
private const val SOCKET_COLOR_LIGHT_HEX = 0xFF4B5563L

private const val WINTER_BULB_RED_HEX = 0xFFFF1E27L
private const val WINTER_BULB_GREEN_HEX = 0xFF00C853L
private const val WINTER_BULB_ORANGE_HEX = 0xFFFF6D00L
private const val WINTER_BULB_BLUE_HEX = 0xFF2979FFL
private const val WINTER_BULB_WARM_WHITE_HEX = 0xFFFFF1C1L

private const val NEW_YEAR_GOLD_HEX = 0xFFFFD54FL
private const val NEW_YEAR_CYAN_HEX = 0xFF26C6DAL
private const val NEW_YEAR_ROSE_HEX = 0xFFEC407AL
private const val NEW_YEAR_MINT_HEX = 0xFF2EE6A6L
private const val NEW_YEAR_VIOLET_HEX = 0xFFAB47BCL

private val WireColorDark = Color(WIRE_COLOR_DARK_HEX)
private val WireColorLight = Color(WIRE_COLOR_LIGHT_HEX)
private val SocketColorDark = Color(SOCKET_COLOR_DARK_HEX)
private val SocketColorLight = Color(SOCKET_COLOR_LIGHT_HEX)

private val WinterBulbColors = listOf(
    Color(WINTER_BULB_RED_HEX),
    Color(WINTER_BULB_GREEN_HEX),
    Color(WINTER_BULB_ORANGE_HEX),
    Color(WINTER_BULB_BLUE_HEX),
    Color(WINTER_BULB_WARM_WHITE_HEX),
)

private val NewYearBulbColors = listOf(
    Color(NEW_YEAR_GOLD_HEX),
    Color(NEW_YEAR_CYAN_HEX),
    Color(NEW_YEAR_ROSE_HEX),
    Color(NEW_YEAR_MINT_HEX),
    Color(NEW_YEAR_VIOLET_HEX),
)

private class LightGeometry(
    val wireY: Float,
    val wireStrokePx: Float,
    val socketSize: Size,
    val socketTop: Float,
    val socketCorner: CornerRadius,
    val bulbSize: Size,
)

private class LightMetrics(
    val bulbTop: Float,
    val glowRadiusPx: Float,
    val specularSize: Size,
    val glowMaxAlpha: Float,
    val geometry: LightGeometry,
    val glowBrushes: Array<Brush>,
)

/**
 * Renders a slim decorative strand of twinkling WLED-inspired LED string lights below a top bar
 * when a [holidayTheme] is active, or nothing when `null`.
 */
@Composable
fun HolidayStringLightsBar(
    modifier: Modifier = Modifier,
    holidayTheme: HolidayTheme? = LocalHolidayTheme.current,
    isDarkTheme: Boolean = LocalDarkTheme.current ?: isSystemInDarkTheme(),
) {
    if (holidayTheme == null) return
    val timeProvider = rememberHolidayTimeSeconds()
    val palette = when (holidayTheme) {
        HolidayTheme.WinterHolidays -> WinterBulbColors
        HolidayTheme.NewYear -> NewYearBulbColors
    }

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(STRING_LIGHTS_HEIGHT_DP.dp)
            .drawWithCache {
                buildStringLightsDraw(
                    timeProvider = timeProvider,
                    palette = palette,
                    isDarkTheme = isDarkTheme,
                )
            },
    )
}

private fun CacheDrawScope.buildStringLightsDraw(
    timeProvider: () -> Float,
    palette: List<Color>,
    isDarkTheme: Boolean,
): DrawResult {
    val bulbCount = (size.width / BULB_SPACING_DP.dp.toPx()).toInt().coerceIn(MIN_BULBS, MAX_BULBS)
    val step = size.width / bulbCount
    val bulbXs = FloatArray(bulbCount) { (it + HALF) * step }
    val glowRadiusPx = GLOW_RADIUS_DP.dp.toPx()
    val bulbSize = Size(BULB_WIDTH_DP.dp.toPx(), BULB_HEIGHT_DP.dp.toPx())
    val metrics = LightMetrics(
        bulbTop = BULB_TOP_DP.dp.toPx(),
        glowRadiusPx = glowRadiusPx,
        specularSize = Size(bulbSize.width * SPECULAR_WIDTH_RATIO, bulbSize.height * SPECULAR_HEIGHT_RATIO),
        glowMaxAlpha = if (isDarkTheme) GLOW_MAX_ALPHA_DARK else GLOW_MAX_ALPHA_LIGHT,
        geometry = LightGeometry(
            wireY = WIRE_Y_DP.dp.toPx(),
            wireStrokePx = WIRE_STROKE_DP.dp.toPx(),
            socketSize = Size(SOCKET_WIDTH_DP.dp.toPx(), SOCKET_HEIGHT_DP.dp.toPx()),
            socketTop = SOCKET_TOP_DP.dp.toPx(),
            socketCorner = CornerRadius(SOCKET_CORNER_DP.dp.toPx()),
            bulbSize = bulbSize,
        ),
        glowBrushes = Array(palette.size) { index ->
            val color = palette[index]
            Brush.radialGradient(
                colorStops = arrayOf(
                    0f to color.copy(alpha = GLOW_CENTER_ALPHA),
                    GLOW_MID_STOP to color.copy(alpha = GLOW_MID_ALPHA),
                    1f to Color.Transparent,
                ),
                center = Offset.Zero,
                radius = glowRadiusPx,
            )
        },
    )
    val wireAlpha = if (isDarkTheme) WIRE_ALPHA_DARK else WIRE_ALPHA_LIGHT
    val wireColor = (if (isDarkTheme) WireColorDark else WireColorLight).copy(alpha = wireAlpha)
    val socketColor = if (isDarkTheme) SocketColorDark else SocketColorLight

    return onDrawBehind {
        if (size.width > 0f) {
            drawLine(
                color = wireColor,
                start = Offset(0f, metrics.geometry.wireY),
                end = Offset(size.width, metrics.geometry.wireY),
                strokeWidth = metrics.geometry.wireStrokePx,
                cap = StrokeCap.Butt,
            )
            drawStringBulbs(
                timeSeconds = timeProvider(),
                palette = palette,
                socketColor = socketColor,
                bulbXs = bulbXs,
                metrics = metrics,
            )
        }
    }
}

private fun DrawScope.drawStringBulbs(
    timeSeconds: Float,
    palette: List<Color>,
    socketColor: Color,
    bulbXs: FloatArray,
    metrics: LightMetrics,
) {
    val geometry = metrics.geometry
    val socketHalfWidth = geometry.socketSize.width * HALF
    val bulbHalfWidth = geometry.bulbSize.width * HALF
    val bulbHalfHeight = geometry.bulbSize.height * HALF
    val specularOffsetX = geometry.bulbSize.width * SPECULAR_OFFSET_X_RATIO
    val specularOffsetY = geometry.bulbSize.height * SPECULAR_OFFSET_Y_RATIO

    for (i in bulbXs.indices) {
        val x = bulbXs[i]
        val twinkle = MIN_TWINKLE_FACTOR +
            TWINKLE_AMPLITUDE * (HALF + HALF * sin(timeSeconds * TWINKLE_SPEED_RAD + i * BULB_PHASE_STEP))
        val colorIndex = i % palette.size
        val color = palette[colorIndex]
        val glowCenterY = metrics.bulbTop + bulbHalfHeight

        translate(left = x, top = glowCenterY) {
            drawCircle(
                brush = metrics.glowBrushes[colorIndex],
                radius = metrics.glowRadiusPx,
                center = Offset.Zero,
                alpha = twinkle * metrics.glowMaxAlpha,
            )
        }
        drawRoundRect(
            color = socketColor,
            topLeft = Offset(x - socketHalfWidth, geometry.socketTop),
            size = geometry.socketSize,
            cornerRadius = geometry.socketCorner,
        )
        val bulbTopLeft = Offset(x - bulbHalfWidth, metrics.bulbTop)
        drawOval(
            color = color.copy(alpha = BULB_BASE_ALPHA + BULB_TWINKLE_ALPHA * twinkle),
            topLeft = bulbTopLeft,
            size = geometry.bulbSize,
        )
        drawOval(
            color = Color.White.copy(alpha = SPECULAR_ALPHA * twinkle),
            topLeft = Offset(bulbTopLeft.x + specularOffsetX, bulbTopLeft.y + specularOffsetY),
            size = metrics.specularSize,
        )
    }
}
