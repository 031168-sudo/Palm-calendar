package ru.palmdate.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.palmdate.app.data.AppSettings
import ru.palmdate.app.data.ColorTheme
import ru.palmdate.app.data.CurrentSettings
import ru.palmdate.app.data.ThemeMode
import ru.palmdate.app.ui.CalendarFrame
import ru.palmdate.app.ui.LocalSheetTop
import ru.palmdate.app.ui.MainActions
import ru.palmdate.app.ui.theme.PalmTheme

/** Цветовые темы (Настройки → Вид → Тема): «День» на телефоне в каждой теме, светлой и тёмной. Папка screenshots/themes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xhdpi")
class ThemeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun setLanguage() {
        Lang.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        Lang.setMode(LangMode.RU, persist = false)
    }

    @After fun reset() { CurrentSettings.provider = { AppSettings() } }

    private fun shot(theme: ColorTheme, mode: ThemeMode) {
        CurrentSettings.provider = { AppSettings(colorTheme = theme, themeMode = mode) }
        compose.setContent {
            PalmTheme {
                CompositionLocalProvider(LocalSheetTop provides 96.dp) {
                    Box(Modifier.fillMaxSize()) { CalendarFrame(Sample.state(ViewMode.DAY), MainActions(), pane = null) }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/themes/${theme.name.lowercase()}_${mode.name.lowercase()}.png")
    }

    @Test fun classicLight() = shot(ColorTheme.CLASSIC, ThemeMode.LIGHT)
    @Test fun classicDark() = shot(ColorTheme.CLASSIC, ThemeMode.DARK)
    @Test fun greenLight() = shot(ColorTheme.GREEN, ThemeMode.LIGHT)
    @Test fun greenDark() = shot(ColorTheme.GREEN, ThemeMode.DARK)
    @Test fun burgundyLight() = shot(ColorTheme.BURGUNDY, ThemeMode.LIGHT)
    @Test fun burgundyDark() = shot(ColorTheme.BURGUNDY, ThemeMode.DARK)
    @Test fun graphiteLight() = shot(ColorTheme.GRAPHITE, ThemeMode.LIGHT)
    @Test fun graphiteDark() = shot(ColorTheme.GRAPHITE, ThemeMode.DARK)
    @Test fun sandLight() = shot(ColorTheme.SAND, ThemeMode.LIGHT)
    @Test fun sandDark() = shot(ColorTheme.SAND, ThemeMode.DARK)
    @Test fun pinkLight() = shot(ColorTheme.PINK, ThemeMode.LIGHT)
    @Test fun pinkDark() = shot(ColorTheme.PINK, ThemeMode.DARK)
}
