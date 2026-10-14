package ru.palmdate.app

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import ru.palmdate.app.data.CurrentSettings
import ru.palmdate.app.time.*

fun firstDayOfWeek(): DayOfWeek =
    if (CurrentSettings.current.weekStartsSunday) DayOfWeek.SUNDAY else DayOfWeek.MONDAY

fun weekStart(d: LocalDate): LocalDate = d.previousOrSame(firstDayOfWeek())
fun monthGridStart(d: LocalDate): LocalDate = weekStart(d.withDayOfMonth(1))
