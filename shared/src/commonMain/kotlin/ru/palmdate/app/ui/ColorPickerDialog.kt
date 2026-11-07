package ru.palmdate.app.ui

import ru.palmdate.app.str
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import ru.palmdate.app.ui.theme.Palm
import ru.palmdate.app.ui.theme.customPalette
import ru.palmdate.app.ui.theme.toHsl

/** Готовые оттенки для темы «Моя»: по кругу оттенков, насыщенные и приглушённые, плюс нейтральные. */
private val PRESETS: List<Color> = buildList {
    val hues = listOf(0f, 15f, 30f, 45f, 90f, 140f, 170f, 195f, 215f, 240f, 270f, 300f, 330f)
    hues.forEach { add(Color.hsl(it, 0.62f, 0.38f)) }
    hues.forEach { add(Color.hsl(it, 0.55f, 0.72f)) }
    add(Color(0xFF1E3A6E)); add(Color(0xFF3A3F47)); add(Color(0xFF7A4E2A)); add(Color(0xFFF1A7CC))
}

private fun Color.hex(): String {
    val v = toArgb() and 0xFFFFFF
    return "#" + v.toString(16).uppercase().padStart(6, '0')
}

private fun parseHex(s: String): Color? {
    val t = s.trim().removePrefix("#")
    if (t.length != 6) return null
    val v = t.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or v)
}

/**
 * Окно выбора основного цвета темы «Моя»: образец (вкладка и кнопка в выбранном цвете), палитра готовых
 * оттенков, ползунки «оттенок» и «яркость», код цвета (#RRGGBB). Остальные цвета темы подбираются сами.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(initial: Color, onPick: (Color) -> Unit, onDismiss: () -> Unit) {
    val (h0, s0, l0) = initial.toHsl()
    var hue by remember { mutableStateOf(h0) }
    var sat by remember { mutableStateOf(if (s0 < 0.05f) 0.5f else s0) }
    var light by remember { mutableStateOf(l0) }
    var gray by remember { mutableStateOf(s0 < 0.05f) } // нейтральные (серые) — без оттенка
    val color = Color.hsl(hue, if (gray) 0f else sat, light.coerceIn(0.12f, 0.88f))
    var code by remember { mutableStateOf(color.hex()) }
    fun set(c: Color) {
        val (h, s, l) = c.toHsl()
        hue = h; gray = s < 0.05f; if (!gray) sat = s; light = l
        code = c.hex()
    }
    val pal = customPalette(color, Palm.dark)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("custom_color_title"), style = Palm.title, color = Palm.navy) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Образец: фон темы, вкладка с датой и залитая кнопка
                Text(str("custom_preview"), style = Palm.small, color = Palm.inkSoft)
                Spacer(Modifier.height(4.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(pal.paper)
                        .border(1.dp, pal.rule, RoundedCornerShape(8.dp)).padding(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clip(RoundedCornerShape(topEnd = 10.dp)).background(pal.fill).padding(horizontal = 10.dp, vertical = 5.dp)) {
                            Text(str("today_cap"), style = Palm.button, color = pal.onFill)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(str("view_week"), style = Palm.button, color = pal.navy)
                    }
                    Box(Modifier.fillMaxWidth().height(2.dp).background(pal.navy))
                    Spacer(Modifier.height(6.dp))
                    Text(str("custom_color_title"), style = Palm.body, color = pal.ink)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(pal.fill).padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Text(str("new_event"), style = Palm.button, color = pal.onFill)
                    }
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { c ->
                        val sel = c.hex() == color.hex()
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(c)
                                .border(if (sel) 3.dp else 1.dp, if (sel) Palm.ink else Palm.rule, CircleShape)
                                .clickable { set(c) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(str("custom_hue"), style = Palm.small, color = Palm.inkSoft)
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(
                        Brush.horizontalGradient((0..12).map { Color.hsl(it * 30f % 360f, 0.6f, 0.5f) }),
                    ),
                )
                Slider(
                    value = hue, valueRange = 0f..359f,
                    onValueChange = { hue = it; gray = false; code = Color.hsl(it, sat, light.coerceIn(0.12f, 0.88f)).hex() },
                    colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
                )
                Text(str("custom_lightness"), style = Palm.small, color = Palm.inkSoft)
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(
                        Brush.horizontalGradient(listOf(0.12f, 0.5f, 0.88f).map { Color.hsl(hue, if (gray) 0f else sat, it) }),
                    ),
                )
                Slider(
                    value = light.coerceIn(0.12f, 0.88f), valueRange = 0.12f..0.88f,
                    onValueChange = { light = it; code = Color.hsl(hue, if (gray) 0f else sat, it).hex() },
                    colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.take(7); parseHex(code)?.let { c -> val keep = code; set(c); code = keep } },
                    label = { Text(str("custom_code")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text(str("done"), color = Palm.navy) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str("cancel"), color = Palm.inkSoft) } },
        containerColor = Palm.paper,
    )
}
