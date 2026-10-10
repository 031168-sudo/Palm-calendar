package ru.palmdate.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ru.palmdate.app.ui.theme.Palm

/** Нижний край синей шапки с датой — выше него нижние панели не поднимаются. */
val LocalSheetTop = compositionLocalOf { 0.dp }

/**
 * Нижняя панель DateBook. В отличие от системной, живёт внутри экрана календаря:
 * занимает место от шапки с датой до низа экрана и никогда не заезжает на шапку.
 * Закрывается тапом по затемнению, кнопкой "Назад" или свайпом вниз за полоску сверху.
 *
 * fixedHeight = true — всегда на всю высоту под шапкой (окно подробностей, чтобы не прыгало).
 */
@Composable
fun PalmSheet(
    onDismissRequest: () -> Unit,
    fixedHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val top = LocalSheetTop.current
    BackHandler(onBack = onDismissRequest)
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val dismissDistance = with(LocalDensity.current) { 96.dp.toPx() }

    Box(Modifier.fillMaxSize().padding(top = top)) {
        // Затемнение под панелью — тап закрывает
        AnimatedVisibility(visible, enter = fadeIn(tween(180))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
                    .pointerInput(Unit) { detectTapGestures { onDismissRequest() } },
            )
        }
        AnimatedVisibility(
            visible,
            enter = slideInVertically(tween(220)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    // На планшете и разложенной раскладушке — не во всю ширину, а по центру
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .then(if (fixedHeight) Modifier.fillMaxHeight() else Modifier)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(Palm.paper)
                    .pointerInput(Unit) { detectTapGestures { } } // тапы по панели не закрывают её
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                // Полоска-ручка: потянуть вниз — закрыть
                Box(
                    Modifier.fillMaxWidth().height(28.dp).pointerInput(Unit) {
                        var dy = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dy = 0f },
                            onDragEnd = { if (dy > dismissDistance) onDismissRequest() },
                        ) { change, amount -> dy += amount; change.consume() }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Palm.rule))
                }
                content()
            }
        }
    }
}
