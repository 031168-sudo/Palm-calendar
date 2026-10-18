package ru.palmdate.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusEvent
import kotlinx.cinterop.useContents
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.UIKit.UIScreen
import ru.palmdate.app.model.L10n

private fun formatter() = NSDateFormatter().apply { locale = NSLocale(localeIdentifier = L10n.language) }

private fun List<*>.at(i: Int): String = this[i] as String

actual fun dayOfWeekName(d: DayOfWeek, style: NameStyle): String {
    val f = formatter()
    val i = (d.ordinal + 1) % 7 // у Apple неделя начинается с воскресенья
    return when (style) {
        NameStyle.NARROW -> f.veryShortWeekdaySymbols.at(i)
        NameStyle.SHORT -> f.shortWeekdaySymbols.at(i)
        NameStyle.FULL -> f.weekdaySymbols.at(i)
    }
}

actual fun monthName(m: Month, style: NameStyle): String {
    val f = formatter()
    return when (style) {
        NameStyle.NARROW -> f.veryShortStandaloneMonthSymbols.at(m.ordinal)
        NameStyle.SHORT -> f.shortMonthSymbols.at(m.ordinal)
        NameStyle.FULL -> f.standaloneMonthSymbols.at(m.ordinal)
    }
}

actual fun longDate(ms: Long): String {
    if (ms <= 0) return ""
    val f = formatter().apply { dateFormat = "d MMMM yyyy, HH:mm" }
    return f.stringFromDate(NSDate.dateWithTimeIntervalSince1970(ms / 1000.0))
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@Composable
actual fun windowWidthDp(): Int = UIScreen.mainScreen.bounds.useContents { size.width }.toInt()

@Composable
actual fun PlatformBackHandler(onBack: () -> Unit) {
    // На iPhone кнопки «Назад» нет: окна закрываются свайпом вниз или тапом по затемнению.
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
actual fun Modifier.keepAboveKeyboard(): Modifier {
    val requester = androidx.compose.runtime.remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(focused) {
        if (focused) {
            kotlinx.coroutines.delay(450) // дождаться, пока окно поднимется над клавиатурой
            requester.bringIntoView()
        }
    }
    return this
        .then(Modifier.bringIntoViewRequester(requester))
        .onFocusEvent { focused = it.isFocused }
}

@Composable
actual fun rememberDocumentPicker(onPicked: (List<String>) -> Unit): () -> Unit = { IosFiles.pick(onPicked) }
