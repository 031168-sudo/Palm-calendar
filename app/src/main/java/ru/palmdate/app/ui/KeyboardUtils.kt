package ru.palmdate.app.ui

import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent

// Зависит от клавиатуры Android — остаётся в приложении (на iOS будет своя версия).

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
