package ru.palmdate.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Палитра Palm Tungsten: тёплая "бумага", чернила, тёмно-синяя вкладка заголовка
 * и серые пунктирные строки. Никаких теней и градиентов — только линии.
 */
object Palm {
    val paper = Color(0xFFF8F7F2)
    val ink = Color(0xFF1B1F2A)
    val inkSoft = Color(0xFF5B6272)
    val navy = Color(0xFF1E3A6E)
    val navyLight = Color(0xFFDCE3F0)
    val rule = Color(0xFFB8BECB)     // пунктир строк
    val nowLine = Color(0xFFC0392B)  // линия "сейчас"

    val title = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
    val time = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
    val small = TextStyle(fontSize = 12.sp)
    val button = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun PalmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Palm.navy,
            onPrimary = Color.White,
            background = Palm.paper,
            surface = Palm.paper,
            onSurface = Palm.ink,
            onBackground = Palm.ink,
            surfaceContainerLow = Palm.paper,
        ),
        content = content,
    )
}
