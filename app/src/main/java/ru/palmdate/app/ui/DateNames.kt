package ru.palmdate.app.ui

import java.time.format.TextStyle
import java.util.Locale

// Названия дней недели и месяцев на языке интерфейса — как раньше, через java.time (Android).
// Для iOS здесь будет своя реализация.

internal fun kotlinx.datetime.DayOfWeek.getDisplayName(style: TextStyle, locale: Locale): String =
    java.time.DayOfWeek.of(ordinal + 1).getDisplayName(style, locale)

internal fun kotlinx.datetime.Month.getDisplayName(style: TextStyle, locale: Locale): String =
    java.time.Month.of(ordinal + 1).getDisplayName(style, locale)
