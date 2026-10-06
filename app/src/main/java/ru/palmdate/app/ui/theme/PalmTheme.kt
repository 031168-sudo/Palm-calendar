package ru.palmdate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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

/**
 * Цвета и шрифты приложения. Цвета переключаются вместе с темой телефона:
 * PalmTheme выставляет нужный набор, а всё, что их читает, перерисовывается.
 */
object Palm {
    private var colors by mutableStateOf(Light)

    var dark by mutableStateOf(false)
        private set

    internal fun apply(isDark: Boolean) {
        dark = isDark
        colors = if (isDark) Dark else Light
    }

    val paper get() = colors.paper
    val ink get() = colors.ink
    val inkSoft get() = colors.inkSoft
    val navy get() = colors.navy
    val navyLight get() = colors.navyLight
    val rule get() = colors.rule
    val nowLine get() = colors.nowLine

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

/** Тема следует за системной: тёмная тема телефона — тёмное приложение. */
@Composable
fun PalmTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    Palm.apply(isDark)
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
