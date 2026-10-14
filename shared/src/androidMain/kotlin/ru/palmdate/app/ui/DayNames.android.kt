package ru.palmdate.app.ui

import java.time.format.TextStyle
import java.util.Locale
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month
import ru.palmdate.app.model.L10n

private fun locale() = Locale.forLanguageTag(L10n.language)

actual fun dayOfWeekName(d: DayOfWeek, style: NameStyle): String =
    java.time.DayOfWeek.of(d.ordinal + 1).getDisplayName(
        when (style) {
            NameStyle.NARROW -> TextStyle.NARROW
            NameStyle.SHORT -> TextStyle.SHORT
            NameStyle.FULL -> TextStyle.FULL
        }, locale(),
    )

actual fun monthName(m: Month, style: NameStyle): String =
    java.time.Month.of(m.ordinal + 1).getDisplayName(
        when (style) {
            NameStyle.NARROW -> TextStyle.NARROW_STANDALONE
            NameStyle.SHORT -> TextStyle.SHORT
            NameStyle.FULL -> TextStyle.FULL_STANDALONE
        }, locale(),
    )
