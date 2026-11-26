package ca.cgagnier.wlednativeandroid.ui.theme.holiday

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.domain.HolidayTheme
import ca.cgagnier.wlednativeandroid.ui.theme.LocalDarkTheme
import ca.cgagnier.wlednativeandroid.ui.theme.LocalHolidayTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val HALF = 0.5f
private const val TWO_PI = (2.0 * PI).toFloat()

private const val SNOW_COUNT = 38
private const val SNOW_SEED = 20261225
private const val CRYSTAL_DIVISOR = 2
private const val CRYSTAL_ARMS = 3
private const val CRYSTAL_ANGLE_STEP_RAD = (PI / 3.0).toFloat()
private const val CRYSTAL_STROKE_DP = 1.15f
private const val SNOW_MIN_SPEED = 0.045f
private const val SNOW_SPEED_RANGE = 0.055f
private const val SNOW_DOT_MIN_RADIUS_DP = 1.4f
private const val SNOW_DOT_RADIUS_RANGE_DP = 1.8f
private const val SNOW_CRYSTAL_MIN_RADIUS_DP = 3.0f
private const val SNOW_CRYSTAL_RADIUS_RANGE_DP = 3.0f
private const val SNOW_MIN_SWAY_DP = 6.0f
private const val SNOW_SWAY_RANGE_DP = 10.0f
private const val SNOW_MIN_SWAY_FREQ = 0.8f
private const val SNOW_SWAY_FREQ_RANGE = 0.9f
private const val SNOW_MIN_ALPHA_DARK = 0.28f
private const val SNOW_ALPHA_RANGE_DARK = 0.34f
private const val SNOW_MIN_ALPHA_LIGHT = 0.32f
private const val SNOW_ALPHA_RANGE_LIGHT = 0.32f
private const val SNOW_CORE_RADIUS_FACTOR = 0.45f
private const val SNOW_CORE_ALPHA_FACTOR = 0.85f

private const val BURST_COUNT = 5
private const val SPARKS_PER_BURST = 16
private const val TOTAL_SPARKS = BURST_COUNT * SPARKS_PER_BURST
private const val FIREWORK_SEED = 20260101
private const val CYCLE_DURATION_SEC = 3.6f
private const val LAUNCH_END_PROGRESS = 0.16f
private const val BURST_END_PROGRESS = 0.80f
private const val MAX_RADIUS_DP = 76f
private const val GRAVITY_DROOP_DP = 26f
private const val SPARK_HEAD_RADIUS_DP = 2.5f
private const val SPARK_TRAIL_WIDTH_DP = 1.6f
private const val SPARK_TRAIL_SCALE = 0.52f
private const val MIN_SPARK_SPEED = 0.55f
private const val SPARK_SPEED_RANGE = 0.55f
private const val ORIGIN_X_MIN = 0.15f
private const val ORIGIN_X_RANGE = 0.70f
private const val ORIGIN_Y_MIN = 0.10f
private const val ORIGIN_Y_RANGE = 0.75f
private const val HASH_PRIME_X = 37
private const val HASH_PRIME_Y = 53
private const val HASH_PRIME_OFFSET = 19
private const val HASH_MODULO = 97
private const val ROCKET_START_OFFSET_DP = 44f
private const val ROCKET_RADIUS_DP = 1.8f
private const val FIREWORK_MAX_ALPHA_DARK = 0.85f
private const val FIREWORK_MAX_ALPHA_LIGHT = 0.65f

private const val SNOW_FLAKE_COLOR_DARK_HEX = 0xFFEBF5FFL
private const val SNOW_FLAKE_COLOR_LIGHT_HEX = 0xFF4F8CC9L
private const val FW_DARK_GOLD_HEX = 0xFFFFD54FL
private const val FW_DARK_CYAN_HEX = 0xFF4DD0E1L
private const val FW_DARK_ROSE_HEX = 0xFFFF6E91L
private const val FW_DARK_MINT_HEX = 0xFF69F0AEL
private const val FW_DARK_AMBER_HEX = 0xFFFFB74DL
private const val FW_LIGHT_GOLD_HEX = 0xFFD98200L
private const val FW_LIGHT_CYAN_HEX = 0xFF00798CL
private const val FW_LIGHT_ROSE_HEX = 0xFFC2185BL
private const val FW_LIGHT_GREEN_HEX = 0xFF1B7A4AL
private const val FW_LIGHT_VIOLET_HEX = 0xFF5E35B1L

private val SnowFlakeColorDark = Color(SNOW_FLAKE_COLOR_DARK_HEX)
private val SnowFlakeColorLight = Color(SNOW_FLAKE_COLOR_LIGHT_HEX)

private val FireworkPaletteDark = listOf(
    Color(FW_DARK_GOLD_HEX),
    Color(FW_DARK_CYAN_HEX),
    Color(FW_DARK_ROSE_HEX),
    Color(FW_DARK_MINT_HEX),
    Color(FW_DARK_AMBER_HEX),
)

private val FireworkPaletteLight = listOf(
    Color(FW_LIGHT_GOLD_HEX),
    Color(FW_LIGHT_CYAN_HEX),
    Color(FW_LIGHT_ROSE_HEX),
    Color(FW_LIGHT_GREEN_HEX),
    Color(FW_LIGHT_VIOLET_HEX),
)

private object SnowField {
    private val rng = Random(SNOW_SEED)
    val isCrystal = BooleanArray(SNOW_COUNT) { it % CRYSTAL_DIVISOR == 0 }
    val xFractions = FloatArray(SNOW_COUNT) { rng.nextFloat() }
    val yPhases = FloatArray(SNOW_COUNT) { rng.nextFloat() }
    val speeds = FloatArray(SNOW_COUNT) { SNOW_MIN_SPEED + rng.nextFloat() * SNOW_SPEED_RANGE }
    val radiiDp = FloatArray(SNOW_COUNT) { index ->
        if (isCrystal[index]) {
            SNOW_CRYSTAL_MIN_RADIUS_DP + rng.nextFloat() * SNOW_CRYSTAL_RADIUS_RANGE_DP
        } else {
            SNOW_DOT_MIN_RADIUS_DP + rng.nextFloat() * SNOW_DOT_RADIUS_RANGE_DP
        }
    }
    val swayAmpsDp = FloatArray(SNOW_COUNT) { SNOW_MIN_SWAY_DP + rng.nextFloat() * SNOW_SWAY_RANGE_DP }
    val swayFreqs = FloatArray(SNOW_COUNT) { SNOW_MIN_SWAY_FREQ + rng.nextFloat() * SNOW_SWAY_FREQ_RANGE }
    val alphaFractions = FloatArray(SNOW_COUNT) { rng.nextFloat() }
}

private object FireworkField {
    private val rng = Random(FIREWORK_SEED)
    val burstTimeOffsets = FloatArray(BURST_COUNT) { index ->
        index.toFloat() / BURST_COUNT * CYCLE_DURATION_SEC
    }
    val sparkAngles = FloatArray(TOTAL_SPARKS) { index ->
        val slot = index % SPARKS_PER_BURST
        slot.toFloat() / SPARKS_PER_BURST * TWO_PI + (rng.nextFloat() - HALF) * (TWO_PI / SPARKS_PER_BURST)
    }
    val sparkSpeeds = FloatArray(TOTAL_SPARKS) { MIN_SPARK_SPEED + rng.nextFloat() * SPARK_SPEED_RANGE }
}

private class SnowDrawCache(
    val radiiPx: FloatArray,
    val swayPx: FloatArray,
    val crystalStrokePx: Float,
    val flakeColor: Color,
    val isDarkTheme: Boolean,
)

private class FireworkDrawCache(
    val maxRadiusPx: Float,
    val gravityPx: Float,
    val headRadiusPx: Float,
    val trailWidthPx: Float,
    val palette: List<Color>,
    val maxAlpha: Float,
)

/**
 * Draws non-interactive seasonal particles (falling snowflakes for [HolidayTheme.WinterHolidays]
 * or celebratory fireworks for [HolidayTheme.NewYear]) behind the modified composable's content.
 */
@Composable
fun Modifier.holidayParticles(
    holidayTheme: HolidayTheme? = LocalHolidayTheme.current,
    isDarkTheme: Boolean = LocalDarkTheme.current ?: isSystemInDarkTheme(),
): Modifier {
    if (holidayTheme == null) return this
    val timeProvider = rememberHolidayTimeSeconds()
    return drawWithCache {
        when (holidayTheme) {
            HolidayTheme.WinterHolidays -> buildWinterSnowDraw(timeProvider, isDarkTheme)
            HolidayTheme.NewYear -> buildNewYearFireworkDraw(timeProvider, isDarkTheme)
        }
    }
}

private fun CacheDrawScope.buildWinterSnowDraw(timeProvider: () -> Float, isDarkTheme: Boolean): DrawResult {
    val cache = SnowDrawCache(
        radiiPx = FloatArray(SNOW_COUNT) { SnowField.radiiDp[it].dp.toPx() },
        swayPx = FloatArray(SNOW_COUNT) { SnowField.swayAmpsDp[it].dp.toPx() },
        crystalStrokePx = CRYSTAL_STROKE_DP.dp.toPx(),
        flakeColor = if (isDarkTheme) SnowFlakeColorDark else SnowFlakeColorLight,
        isDarkTheme = isDarkTheme,
    )

    return onDrawBehind {
        if (size.width > 0f && size.height > 0f) {
            clipRect {
                drawWinterSnow(timeSeconds = timeProvider(), cache = cache)
            }
        }
    }
}

private fun DrawScope.drawWinterSnow(timeSeconds: Float, cache: SnowDrawCache) {
    val baseAlpha = if (cache.isDarkTheme) SNOW_MIN_ALPHA_DARK else SNOW_MIN_ALPHA_LIGHT
    val alphaRange = if (cache.isDarkTheme) SNOW_ALPHA_RANGE_DARK else SNOW_ALPHA_RANGE_LIGHT

    for (i in 0 until SNOW_COUNT) {
        val yFraction = (SnowField.yPhases[i] + SnowField.speeds[i] * timeSeconds) % 1f
        val sway = sin(timeSeconds * SnowField.swayFreqs[i] + SnowField.yPhases[i] * TWO_PI) * cache.swayPx[i]
        val x = (SnowField.xFractions[i] * size.width + sway + size.width) % size.width
        val y = yFraction * size.height
        val alpha = baseAlpha + SnowField.alphaFractions[i] * alphaRange
        val radius = cache.radiiPx[i]
        val color = cache.flakeColor.copy(alpha = alpha)

        if (SnowField.isCrystal[i]) {
            drawCrystalFlake(center = Offset(x, y), radius = radius, strokeWidth = cache.crystalStrokePx, color = color)
        } else {
            drawCircle(color = color, radius = radius, center = Offset(x, y))
            if (!cache.isDarkTheme) {
                drawCircle(
                    color = Color.White.copy(alpha = alpha * SNOW_CORE_ALPHA_FACTOR),
                    radius = radius * SNOW_CORE_RADIUS_FACTOR,
                    center = Offset(x, y),
                )
            }
        }
    }
}

private fun DrawScope.drawCrystalFlake(center: Offset, radius: Float, strokeWidth: Float, color: Color) {
    for (arm in 0 until CRYSTAL_ARMS) {
        val angle = arm * CRYSTAL_ANGLE_STEP_RAD
        val dx = cos(angle) * radius
        val dy = sin(angle) * radius
        drawLine(
            color = color,
            start = Offset(center.x - dx, center.y - dy),
            end = Offset(center.x + dx, center.y + dy),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

private fun CacheDrawScope.buildNewYearFireworkDraw(timeProvider: () -> Float, isDarkTheme: Boolean): DrawResult {
    val rocketOffsetPx = ROCKET_START_OFFSET_DP.dp.toPx()
    val rocketRadiusPx = ROCKET_RADIUS_DP.dp.toPx()
    val cache = FireworkDrawCache(
        maxRadiusPx = MAX_RADIUS_DP.dp.toPx(),
        gravityPx = GRAVITY_DROOP_DP.dp.toPx(),
        headRadiusPx = SPARK_HEAD_RADIUS_DP.dp.toPx(),
        trailWidthPx = SPARK_TRAIL_WIDTH_DP.dp.toPx(),
        palette = if (isDarkTheme) FireworkPaletteDark else FireworkPaletteLight,
        maxAlpha = if (isDarkTheme) FIREWORK_MAX_ALPHA_DARK else FIREWORK_MAX_ALPHA_LIGHT,
    )

    return onDrawBehind {
        if (size.width > 0f && size.height > 0f) {
            clipRect {
                drawFireworks(
                    timeSeconds = timeProvider(),
                    rocketOffsetPx = rocketOffsetPx,
                    rocketRadiusPx = rocketRadiusPx,
                    cache = cache,
                )
            }
        }
    }
}

private fun DrawScope.drawFireworks(
    timeSeconds: Float,
    rocketOffsetPx: Float,
    rocketRadiusPx: Float,
    cache: FireworkDrawCache,
) {
    for (burst in 0 until BURST_COUNT) {
        val shifted = timeSeconds + FireworkField.burstTimeOffsets[burst]
        val cycle = (shifted / CYCLE_DURATION_SEC).toInt()
        val progress = shifted % CYCLE_DURATION_SEC / CYCLE_DURATION_SEC
        if (progress > BURST_END_PROGRESS) continue

        val xFraction = (burst * HASH_PRIME_X + cycle * HASH_PRIME_OFFSET) % HASH_MODULO / HASH_MODULO.toFloat()
        val yFraction = (burst * HASH_PRIME_Y + cycle * HASH_PRIME_X) % HASH_MODULO / HASH_MODULO.toFloat()
        val originX = (ORIGIN_X_MIN + xFraction * ORIGIN_X_RANGE) * size.width
        val originY = (ORIGIN_Y_MIN + yFraction * ORIGIN_Y_RANGE) * size.height
        val color = cache.palette[(burst + cycle) % cache.palette.size]

        if (progress < LAUNCH_END_PROGRESS) {
            val launchFrac = progress / LAUNCH_END_PROGRESS
            val rocketY = originY + rocketOffsetPx * (1f - launchFrac)
            drawCircle(
                color = color.copy(alpha = cache.maxAlpha * launchFrac),
                radius = rocketRadiusPx,
                center = Offset(originX, rocketY),
            )
        } else {
            val burstAge = (progress - LAUNCH_END_PROGRESS) / (BURST_END_PROGRESS - LAUNCH_END_PROGRESS)
            drawFireworkBurst(
                burstIndex = burst,
                origin = Offset(originX, originY),
                burstAge = burstAge,
                color = color,
                cache = cache,
            )
        }
    }
}

private fun DrawScope.drawFireworkBurst(
    burstIndex: Int,
    origin: Offset,
    burstAge: Float,
    color: Color,
    cache: FireworkDrawCache,
) {
    val invAge = 1f - burstAge
    val eased = 1f - invAge * invAge
    val alpha = (invAge * cache.maxAlpha).coerceIn(0f, 1f)
    val droop = cache.gravityPx * burstAge * burstAge
    val baseSparkIdx = burstIndex * SPARKS_PER_BURST
    val headColor = color.copy(alpha = alpha)
    val trailColor = color.copy(alpha = alpha * SPARK_TRAIL_SCALE)

    for (spark in 0 until SPARKS_PER_BURST) {
        val idx = baseSparkIdx + spark
        val angle = FireworkField.sparkAngles[idx]
        val dist = cache.maxRadiusPx * FireworkField.sparkSpeeds[idx] * eased
        val tailDist = dist * SPARK_TRAIL_SCALE
        val cosA = cos(angle)
        val sinA = sin(angle)
        val start = Offset(origin.x + cosA * tailDist, origin.y + sinA * tailDist + droop * SPARK_TRAIL_SCALE)
        val end = Offset(origin.x + cosA * dist, origin.y + sinA * dist + droop)
        drawLine(color = trailColor, start = start, end = end, strokeWidth = cache.trailWidthPx, cap = StrokeCap.Round)
        drawCircle(color = headColor, radius = cache.headRadiusPx * (HALF + HALF * invAge), center = end)
    }
}
