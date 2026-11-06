package ru.palmdate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import ru.palmdate.app.data.ColorTheme
import ru.palmdate.app.data.CurrentSettings
import ru.palmdate.app.data.ThemeMode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Набор цветов одной темы. */
data class PalmColors(
    val paper: Color,
    val ink: Color,
    val inkSoft: Color,
    val navy: Color,
    val navyLight: Color,
    val rule: Color,
    val nowLine: Color,
)

/**
 * Светлая — тёплая "бумага", чернила, тёмно-синяя вкладка и серые пунктирные строки.
 * Никаких теней и градиентов — только линии.
 */
private val Light = PalmColors(
    paper = Color(0xFFF8F7F2),
    ink = Color(0xFF1B1F2A),
    inkSoft = Color(0xFF5B6272),
    navy = Color(0xFF1E3A6E),
    navyLight = Color(0xFFDCE3F0),
    rule = Color(0xFFB8BECB),
    nowLine = Color(0xFFC0392B),
)

/** Тёмная — "Palm ночью": тёплый графит, светлые чернила, синий чуть светлее, чтобы не тонул. */
private val Dark = PalmColors(
    paper = Color(0xFF17191E),
    ink = Color(0xFFE9E6DD),
    inkSoft = Color(0xFF9AA0AD),
    navy = Color(0xFF4A6FB5),
    navyLight = Color(0xFF243048),
    rule = Color(0xFF3C424E),
    nowLine = Color(0xFFE5675A),
)

private fun pal(paper: Long, ink: Long, inkSoft: Long, navy: Long, navyLight: Long, rule: Long, nowLine: Long) =
    PalmColors(Color(paper), Color(ink), Color(inkSoft), Color(navy), Color(navyLight), Color(rule), Color(nowLine))

/**
 * Цветовые темы (Настройки → Вид → Тема): у каждой своя светлая и тёмная версия.
 * «Классический КПК» — исходные цвета. navy — основной цвет темы (вкладка, кнопки, заголовки):
 * в тёмной версии он такой, чтобы белый текст на нём читался.
 */
private fun palette(t: ColorTheme, dark: Boolean): PalmColors = when (t) {
    ColorTheme.CLASSIC -> if (dark) Dark else Light
    ColorTheme.GREEN -> if (dark) pal(0xFF151A16, 0xFFE6EBE3, 0xFF98A69B, 0xFF3F7D52, 0xFF22352A, 0xFF37443A, 0xFFE5675A)
        else pal(0xFFF5F7F0, 0xFF1A241D, 0xFF56645A, 0xFF2E5E3E, 0xFFD9E8DC, 0xFFB5C2B7, 0xFFC0392B)
    ColorTheme.BURGUNDY -> if (dark) pal(0xFF1C1617, 0xFFEDE4E5, 0xFFA8999C, 0xFFA33A52, 0xFF3A2228, 0xFF46383B, 0xFFF0884A)
        else pal(0xFFF9F5F3, 0xFF241A1C, 0xFF66585B, 0xFF7A1F33, 0xFFF0DCE0, 0xFFC7B7BA, 0xFFD35400)
    ColorTheme.GRAPHITE -> if (dark) pal(0xFF141516, 0xFFE8E8E8, 0xFF9C9EA3, 0xFF5F6670, 0xFF2A2D31, 0xFF3B3E43, 0xFFE5675A)
        else pal(0xFFF6F6F6, 0xFF1C1C1E, 0xFF5E6066, 0xFF3A3F47, 0xFFDDE0E4, 0xFFBDC0C6, 0xFFC0392B)
    ColorTheme.SAND -> if (dark) pal(0xFF1D1913, 0xFFEEE5D6, 0xFFA89B87, 0xFFA06A3A, 0xFF3A2E20, 0xFF4A4032, 0xFFE5675A)
        else pal(0xFFF6EFE2, 0xFF2A2118, 0xFF6B5D4C, 0xFF7A4E2A, 0xFFEADCC6, 0xFFCDBFA8, 0xFFB03A2E)
    ColorTheme.PINK -> if (dark) pal(0xFF1E1619, 0xFFF2E6EB, 0xFFAC97A0, 0xFFC2457B, 0xFF40232F, 0xFF4C3740, 0xFFF0884A)
        else pal(0xFFFCF5F7, 0xFF2A1B22, 0xFF6E5A63, 0xFFB03A6E, 0xFFF6DCE7, 0xFFD6BCC7, 0xFFD84315)
}

/**
 * Цвета и шрифты приложения. Цвета переключаются вместе с темой телефона:
 * PalmTheme выставляет нужный набор, а всё, что их читает, перерисовывается.
 */
object Palm {
    private var colors by mutableStateOf(Light)

    var dark by mutableStateOf(false)
        private set

    internal fun apply(isDark: Boolean, theme: ColorTheme = ColorTheme.CLASSIC) {
        dark = isDark
        colors = palette(theme, isDark)
    }

    /** Образец основного цвета темы — для кружков в настройках (в текущем режиме светлая/тёмная). */
    fun swatch(t: ColorTheme): Color = palette(t, dark).navy

    val paper get() = colors.paper
    val ink get() = colors.ink
    val inkSoft get() = colors.inkSoft
    val navy get() = colors.navy
    val navyLight get() = colors.navyLight
    val rule get() = colors.rule
    val nowLine get() = colors.nowLine

    /** Пунктир нерабочих часов — бледно-зелёный. */
    val offRule get() = if (dark) Color(0xFF3F6B4F) else Color(0xFFA6D4B5)

    /** Насыщенность подсветки строки цветом календаря: в темноте бледную не видно. */
    val tintAlpha get() = if (dark) 0.22f else 0.11f

    /** Цвета типов событий в тёмной теме осветляем, чтобы иконки не терялись. */
    fun typeColor(base: Color): Color = if (dark) lerp(base, Color.White, 0.3f) else base

    val title = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
    val time = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
    val small = TextStyle(fontSize = 12.sp)
    val button = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
}

/**
 * Тема — из настроек (цвет и «светлая / тёмная / как в телефоне»). [onMode] — платформе подстроить системные
 * панели (часы, батарея) под выбранный режим: (режим, тёмная ли сейчас).
 */
@Composable
fun PalmTheme(onMode: (ThemeMode, Boolean) -> Unit = { _, _ -> }, content: @Composable () -> Unit) {
    val settings = CurrentSettings.current
    val system = isSystemInDarkTheme()
    val isDark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> system
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    Palm.apply(isDark, settings.colorTheme)
    LaunchedEffect(settings.themeMode, isDark) { onMode(settings.themeMode, isDark) }
    val scheme = if (isDark) {
        darkColorScheme(
            primary = Palm.navy,
            onPrimary = Color.White,
            primaryContainer = Palm.navyLight,
            onPrimaryContainer = Palm.ink,
            background = Palm.paper,
            surface = Palm.paper,
            onSurface = Palm.ink,
            onSurfaceVariant = Palm.inkSoft,
            onBackground = Palm.ink,
            outline = Palm.rule,
            surfaceContainerLow = Palm.paper,
            surfaceContainer = Color(0xFF1E2128),
            surfaceContainerHigh = Color(0xFF23262E),
            surfaceContainerHighest = Color(0xFF2A2E37),
        )
    } else {
        lightColorScheme(
            primary = Palm.navy,
            onPrimary = Color.White,
            background = Palm.paper,
            surface = Palm.paper,
            onSurface = Palm.ink,
            onBackground = Palm.ink,
            surfaceContainerLow = Palm.paper,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
