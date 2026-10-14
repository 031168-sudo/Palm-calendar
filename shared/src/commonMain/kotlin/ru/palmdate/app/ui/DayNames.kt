package ru.palmdate.app.ui

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Month

/** Как коротко писать название: «П» / «Пн» / «Понедельник». */
enum class NameStyle { NARROW, SHORT, FULL }

/** Название дня недели на языке интерфейса (на Android — из java.time, на iOS — из системы). */
expect fun dayOfWeekName(d: DayOfWeek, style: NameStyle): String

/** Название месяца на языке интерфейса; FULL — в именительном падеже («Октябрь»). */
expect fun monthName(m: Month, style: NameStyle): String
