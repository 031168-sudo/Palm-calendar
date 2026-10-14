package ru.palmdate.app.ui

import kotlinx.datetime.LocalDate
import ru.palmdate.app.model.L10n
import ru.palmdate.app.time.*

private val ru get() = L10n.language == "ru"

fun LocalDate.shortMonth() = monthName(month, NameStyle.SHORT).trimEnd('.')

fun LocalDate.pretty(): String {
    val dow = dayOfWeekName(dayOfWeek, NameStyle.SHORT).replaceFirstChar { it.uppercase() }
    // По-русски «Сб, 10 окт», по-английски «Sat, Oct 10»
    return if (ru) "$dow, $dayOfMonth ${shortMonth()}" else "$dow, ${shortMonth()} $dayOfMonth"
}

/** День и месяц: «10 окт» / «Oct 10». */
fun LocalDate.dayMonth(): String =
    if (ru) "$dayOfMonth ${shortMonth()}" else "${shortMonth()} $dayOfMonth"

fun LocalDate.monthTitle() =
    monthName(month, NameStyle.FULL).replaceFirstChar { it.uppercase() }
