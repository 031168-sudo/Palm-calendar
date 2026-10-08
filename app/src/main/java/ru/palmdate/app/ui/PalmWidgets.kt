package ru.palmdate.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import ru.palmdate.app.ui.theme.Palm

/** Пунктирная строка снизу — главный графический мотив Palm Date Book. */
fun Modifier.dottedRule(color: Color = Palm.rule) = drawBehind {
    val y = size.height - 0.5.dp.toPx()
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 2.5.dp.toPx())),
    )
}

/** Кнопка в стиле Palm OS 5: скруглённая рамка, жирная надпись, без заливки. */
@Composable
fun PalmButton(text: String, modifier: Modifier = Modifier, filled: Boolean = false, compact: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .clip(shape)
            .then(if (filled) Modifier.drawBehind { drawRect(Palm.navy) } else Modifier)
            .border(1.dp, Palm.navy, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 11.dp else 14.dp, vertical = 7.dp),
    ) {
        Text(text, style = Palm.button, color = if (filled) Color.White else Palm.navy)
    }
}

/**
 * Поле ввода уезжает над клавиатурой: когда поле в фокусе и клавиатура открылась,
 * прокручиваемый родитель сдвигает его в видимую область.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Modifier.keepAboveKeyboard(): Modifier {
    val requester = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    LaunchedEffect(focused, imeVisible) {
        if (focused && imeVisible) {
            kotlinx.coroutines.delay(250) // дождаться, пока окно ужмётся под клавиатуру
            requester.bringIntoView()
        }
    }
    return this
        .then(Modifier.bringIntoViewRequester(requester))
        .onFocusEvent { focused = it.isFocused }
}
