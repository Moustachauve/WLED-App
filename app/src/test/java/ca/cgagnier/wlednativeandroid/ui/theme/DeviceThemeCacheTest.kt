package ca.cgagnier.wlednativeandroid.ui.theme

import android.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.materialkolor.PaletteStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeviceThemeCacheTest {

    @BeforeEach
    fun setUp() {
        clearDeviceColorSchemeCache()
    }

    @Test
    fun `getCachedDeviceColorScheme returns same instance for identical key`() {
        val first = getCachedDeviceColorScheme(
            seedColorArgb = Color.WHITE,
            isDark = true,
            style = PaletteStyle.Neutral,
        )
        val second = getCachedDeviceColorScheme(
            seedColorArgb = Color.WHITE,
            isDark = true,
            style = PaletteStyle.Neutral,
        )

        assertSame(first, second)
    }

    @Test
    fun `getCachedDeviceColorScheme returns distinct schemes when key differs`() {
        val base = getCachedDeviceColorScheme(Color.WHITE, isDark = true, style = PaletteStyle.Neutral)
        val diffColor = getCachedDeviceColorScheme(Color.RED, isDark = true, style = PaletteStyle.Neutral)
        val diffTheme = getCachedDeviceColorScheme(Color.WHITE, isDark = false, style = PaletteStyle.Neutral)
        val diffStyle = getCachedDeviceColorScheme(Color.WHITE, isDark = true, style = PaletteStyle.Vibrant)

        assertNotSame(base, diffColor)
        assertNotSame(base, diffTheme)
        assertNotSame(base, diffStyle)
    }

    @Test
    fun `lerpColorScheme returns endpoints at bounds and interpolates midpoint`() {
        val start = getCachedDeviceColorScheme(Color.RED, isDark = false, style = PaletteStyle.Vibrant)
        val stop = getCachedDeviceColorScheme(Color.BLUE, isDark = false, style = PaletteStyle.Vibrant)

        assertSame(start, lerpColorScheme(start, stop, 0f))
        assertSame(stop, lerpColorScheme(start, stop, 1f))

        val mid = lerpColorScheme(start, stop, 0.5f)
        assertEquals(lerp(start.primary, stop.primary, 0.5f), mid.primary)
        assertEquals(lerp(start.surfaceContainer, stop.surfaceContainer, 0.5f), mid.surfaceContainer)
    }
}
