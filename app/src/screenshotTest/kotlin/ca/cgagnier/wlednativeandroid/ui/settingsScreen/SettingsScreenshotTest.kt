package ca.cgagnier.wlednativeandroid.ui.settingsScreen

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ca.cgagnier.wlednativeandroid.domain.HolidayTheme
import ca.cgagnier.wlednativeandroid.repository.ThemeSettings
import ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme
import com.android.tools.screenshot.PreviewTest

class SettingsScreenshotTest {

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun SettingsLightPreview() {
        SettingsPreview(darkTheme = false)
    }

    @PreviewTest
    @Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun SettingsDarkPreview() {
        SettingsPreview(darkTheme = true)
    }

    /** Wide windows (tablets, foldables) lay the settings cards out in two columns. */
    @PreviewTest
    @Preview(showBackground = true, widthDp = 900, heightDp = 640)
    @Composable
    fun SettingsTwoColumnPreview() {
        SettingsPreview(darkTheme = false)
    }

    @PreviewTest
    @Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun SettingsWinterHolidaysDarkPreview() {
        SettingsPreview(
            darkTheme = true,
            holidayTheme = HolidayTheme.WinterHolidays,
            holidayThemeOverride = HolidayTheme.WinterHolidays,
        )
    }

    @Composable
    private fun SettingsPreview(
        darkTheme: Boolean,
        holidayTheme: HolidayTheme? = null,
        holidayThemeOverride: HolidayTheme? = null,
    ) {
        WLEDNativeTheme(darkTheme = darkTheme, dynamicColor = false, holidayTheme = holidayTheme) {
            SettingsContent(
                settingsState = SettingsState(
                    isAutoDiscoveryEnabled = true,
                    showOfflineLast = true,
                    showHiddenDevices = false,
                    theme = ThemeSettings.Auto,
                    isHolidayThemesEnabled = true,
                    holidayThemeOverride = holidayThemeOverride,
                ),
                navigateUp = {},
            )
        }
    }
}
