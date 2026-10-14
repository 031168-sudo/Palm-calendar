package ru.palmdate.app

import kotlinx.datetime.LocalDate
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.time.*

/** Виды, как на Palm: переключаются иконками в нижней панели. */
enum class ViewMode(val labelKey: String) {
    AGENDA("start_agenda"), DAY("start_day"), WEEK("view_week"), MONTH("view_month"), YEAR("stats_year");

    val label: String get() = str(labelKey)
}

data class CalState(
    val mode: ViewMode = ViewMode.DAY,
    val date: LocalDate = LocalDate.now(),
    val events: List<PalmEvent> = emptyList(),
    val yearDays: Set<LocalDate> = emptySet(),
    val error: String? = null,
) {
    /** Начало и конец (не включительно) загружаемого диапазона. */
    val range: Pair<LocalDate, LocalDate>
        get() = when (mode) {
            ViewMode.DAY -> date to date.plusDays(1)
            ViewMode.AGENDA -> date to date.plusDays(7)
            ViewMode.WEEK -> weekStart(date).let { it to it.plusDays(7) }
            ViewMode.MONTH -> monthGridStart(date).let { it to it.plusDays(42) }
            ViewMode.YEAR -> date.withDayOfYear(1).let { it to it.plusYears(1) }
        }

    /** Ключ "страницы": меняется — экран уезжает свайпом. */
    val pageKey: String get() = "$mode:${range.first}"
}

