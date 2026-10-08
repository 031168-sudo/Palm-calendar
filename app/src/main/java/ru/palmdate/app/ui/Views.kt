package ru.palmdate.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.monthGridStart
import ru.palmdate.app.ui.theme.Palm
import ru.palmdate.app.weekStart
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle

private fun dayLetter(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.NARROW, RU).uppercase()

private fun List<PalmEvent>.on(d: LocalDate) = filter { d in it.days() }

/* ======================= Повестка ======================= */

/** Семь дней списком: заголовок дня, под ним события. Тап по заголовку — открыть день. */
@Composable
fun AgendaView(
    from: LocalDate,
    events: List<PalmEvent>,
    onDay: (LocalDate) -> Unit,
    onIcon: (PalmEvent) -> Unit,
    onEvent: (PalmEvent) -> Unit,
) {
    val today = LocalDate.now()
    LazyColumn(Modifier.fillMaxSize()) {
        for (i in 0L..6L) {
            val d = from.plusDays(i)
            val dayEvents = events.on(d).sortedWith(compareBy({ !it.allDay }, { it.start }))
            item(key = "h$d") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onDay(d) }
                        .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        d.pretty(), style = Palm.button,
                        color = if (d.dayOfWeek.value >= 6) Palm.nowLine else Palm.navy,
                    )
                    if (d == today) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "сегодня", style = Palm.small, color = Color.White,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Palm.navy)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f).height(1.dp).background(Palm.navy.copy(alpha = 0.35f)))
                }
            }
            if (dayEvents.isEmpty()) {
                item(key = "e$d") {
                    Text(
                        "нет событий", style = Palm.small, color = Palm.rule,
                        modifier = Modifier.padding(start = 52.dp, top = 4.dp, bottom = 4.dp),
                    )
                }
            } else {
                dayEvents.forEach { e ->
                    item(key = "$d-${e.eventId}-${e.start}") {
                        val label = when {
                            e.allDay -> "•"
                            e.start.toLocalDate() < d -> "→"
                            else -> e.start.format(HM)
                        }
                        EventLine(e, label, highlight = false, onIcon, onEvent)
                    }
                }
            }
        }
    }
}

/* ======================= Неделя ======================= */

private val HOUR_H = 40.dp

/** Раскладка пересекающихся событий по дорожкам внутри дня. */
private fun lanes(events: List<PalmEvent>): List<Pair<PalmEvent, Pair<Int, Int>>> {
    val sorted = events.sortedBy { it.start }
    val laneEnds = ArrayList<java.time.LocalDateTime>()
    val placed = sorted.map { e ->
        val lane = laneEnds.indexOfFirst { !it.isAfter(e.start) }.let { if (it < 0) laneEnds.size.also { laneEnds += e.end } else it.also { laneEnds[it] = e.end } }
        e to lane
    }
    val total = laneEnds.size.coerceAtLeast(1)
    return placed.map { (e, lane) -> e to (lane to total) }
}

/**
 * Неделя, как на Palm: семь колонок, события — полосками по времени.
 * Тап по полоске показывает событие наверху, второй тап — подробности.
 */
@Composable
fun WeekView(
    date: LocalDate,
    events: List<PalmEvent>,
    onDay: (LocalDate) -> Unit,
    onEvent: (PalmEvent) -> Unit,
) {
    val monday = weekStart(date)
    val days = (0L..6L).map { monday.plusDays(it) }
    val today = LocalDate.now()
    var selected by remember(monday) { mutableStateOf<PalmEvent?>(null) }

    val timed = events.filter { !it.allDay }
    val settings = ru.palmdate.app.data.SettingsStore.current
    val firstHour = (timed.minOfOrNull { if (it.start.toLocalDate() < monday) 0 else it.start.hour } ?: settings.dayFrom)
        .coerceAtMost(settings.dayFrom)
    val lastHour = (timed.maxOfOrNull { if (it.end.toLocalDate() > it.start.toLocalDate()) 23 else it.end.hour } ?: (settings.dayTo + 1))
        .coerceAtLeast(settings.dayTo + 1).coerceAtMost(23)
    val hours = firstHour..lastHour

    Column(Modifier.fillMaxSize()) {
        // Строка выбранного события — как подсказка вверху недели на Palm
        Row(
            Modifier.fillMaxWidth().height(40.dp)
                .clickable(enabled = selected != null) { selected?.let(onEvent) }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val s = selected
            if (s == null) {
                Text("Нажмите на событие", style = Palm.small, color = Palm.inkSoft)
            } else {
                s.type?.let { Icon(it.icon, null, tint = it.color, modifier = Modifier.size(18.dp)) }
                    ?: Box(Modifier.size(9.dp).clip(CircleShape).background(Color(s.color)))
                Spacer(Modifier.width(8.dp))
                Text(
                    (s.contact?.name ?: s.title) + "  " +
                        (if (s.allDay) s.start.toLocalDate().pretty() else "${s.start.toLocalDate().pretty()}, ${s.start.format(HM)}–${s.end.format(HM)}"),
                    style = Palm.body, color = Palm.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palm.rule))

        // Шапка с днями
        Row(Modifier.fillMaxWidth().padding(end = 6.dp)) {
            Spacer(Modifier.width(40.dp))
            days.forEach { d ->
                Column(
                    Modifier.weight(1f).clickable { onDay(d) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(dayLetter(d), style = Palm.small, color = if (d.dayOfWeek.value >= 6) Palm.nowLine else Palm.inkSoft)
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(if (d == today) Palm.navy else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${d.dayOfMonth}", style = Palm.time, color = if (d == today) Color.White else Palm.ink)
                    }
                }
            }
        }

        // Ряд событий на весь день
        val allDay = events.filter { it.allDay }
        if (allDay.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 2.dp)) {
                Spacer(Modifier.width(40.dp))
                days.forEach { d ->
                    Column(Modifier.weight(1f).padding(horizontal = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        allDay.on(d).take(2).forEach { e ->
                            Box(
                                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(2.dp))
                                    .background(Color(e.color))
                                    .clickable { if (selected == e) onEvent(e) else selected = e },
                            )
                        }
                    }
                }
            }
        }

        // Сетка часов
        val scroll = rememberScrollState()
        val density = LocalDensity.current
        LaunchedEffect(monday) {
            val target = (if (monday == weekStart(today)) LocalTime.now().hour - 1 else settings.dayFrom) - firstHour
            scroll.scrollTo(with(density) { (HOUR_H * target.coerceAtLeast(0)).roundToPx() })
        }
        Row(Modifier.fillMaxWidth().verticalScroll(scroll).padding(end = 6.dp)) {
            Column(Modifier.width(40.dp)) {
                hours.forEach { h ->
                    Text(
                        "$h", style = Palm.small, color = Palm.inkSoft, textAlign = TextAlign.End,
                        modifier = Modifier.width(40.dp).height(HOUR_H).padding(end = 6.dp),
                    )
                }
            }
            days.forEach { d ->
                BoxWithConstraints(
                    Modifier.weight(1f).height(HOUR_H * hours.count())
                        .background(if (d == today) Palm.navyLight.copy(alpha = 0.35f) else Color.Transparent),
                ) {
                    // Пунктир часов
                    Column {
                        hours.forEach { _ -> Box(Modifier.fillMaxWidth().height(HOUR_H).dottedRule()) }
                    }
                    val colW = maxWidth
                    val dayStart = d.atTime(firstHour, 0)
                    lanes(timed.on(d)).forEach { (e, laneInfo) ->
                        val (lane, total) = laneInfo
                        val startMin = java.time.Duration.between(dayStart, maxOf(e.start, dayStart)).toMinutes()
                            .coerceAtLeast(0)
                        val endMin = java.time.Duration.between(dayStart, minOf(e.end, d.plusDays(1).atStartOfDay())).toMinutes()
                            .coerceAtMost(hours.count() * 60L)
                        val top = HOUR_H * (startMin / 60f)
                        val h = (HOUR_H * ((endMin - startMin).coerceAtLeast(20) / 60f))
                        val w = colW / total
                        val isSel = selected == e
                        Box(
                            Modifier
                                .offset(x = w * lane, y = top)
                                .width(w)
                                .height(h)
                                .padding(horizontal = 1.dp, vertical = 1.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(e.color).copy(alpha = if (isSel) 1f else 0.8f))
                                .then(if (isSel) Modifier.border(BorderStroke(2.dp, Palm.ink), RoundedCornerShape(3.dp)) else Modifier)
                                .clickable { if (isSel) onEvent(e) else selected = e },
                        ) {
                            e.type?.let {
                                Icon(it.icon, null, tint = Color.White, modifier = Modifier.padding(2.dp).size(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ======================= Месяц ======================= */

/** Сетка месяца: в клетке число и до трёх полосок событий в цвете календаря. Тап — открыть день. */
@Composable
fun MonthView(date: LocalDate, events: List<PalmEvent>, onDay: (LocalDate) -> Unit) {
    val start = monthGridStart(date)
    val today = LocalDate.now()
    Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            (0L..6L).forEach { i ->
                val d = start.plusDays(i)
                Text(
                    dayLetter(d), style = Palm.small,
                    color = if (d.dayOfWeek.value >= 6) Palm.nowLine else Palm.inkSoft,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
        for (week in 0 until 6) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (i in 0 until 7) {
                    val d = start.plusDays((week * 7 + i).toLong())
                    val inMonth = d.month == date.month
                    val dayEvents = events.on(d).sortedWith(compareBy({ !it.allDay }, { it.start }))
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .dottedRule()
                            .clickable { onDay(d) }
                            .padding(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape)
                                .background(if (d == today) Palm.navy else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${d.dayOfMonth}",
                                style = Palm.time.copy(fontWeight = if (dayEvents.isNotEmpty()) FontWeight.Bold else FontWeight.Normal),
                                color = when {
                                    d == today -> Color.White
                                    !inMonth -> Palm.rule
                                    d.dayOfWeek.value >= 6 -> Palm.nowLine
                                    else -> Palm.ink
                                },
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        dayEvents.take(3).forEach { e ->
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 1.dp).height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(e.color).copy(alpha = if (inMonth) 1f else 0.35f)),
                            )
                        }
                        if (dayEvents.size > 3) {
                            Text("+${dayEvents.size - 3}", fontSize = 9.sp, color = Palm.inkSoft)
                        }
                    }
                }
            }
        }
    }
}

/* ======================= Год ======================= */

/** Мелкий шрифт дней без внутренних отступов шрифта — иначе цифры съезжают и обрезаются. */
private val MiniDay = androidx.compose.ui.text.TextStyle(
    fontSize = 10.sp,
    lineHeight = 10.sp,
    textAlign = TextAlign.Center,
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
    ),
)

/** Двенадцать маленьких месяцев. Дни с событиями выделены. Тап по дню — день, по названию — месяц. */
@Composable
fun YearView(
    date: LocalDate,
    daysWithEvents: Set<LocalDate>,
    onDay: (LocalDate) -> Unit,
    onMonth: (LocalDate) -> Unit,
) {
    val year = date.year
    val today = LocalDate.now()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (row in 0 until 4) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0 until 3) {
                    val first = LocalDate.of(year, row * 3 + col + 1, 1)
                    MiniMonth(first, today, daysWithEvents, onDay, onMonth, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MiniMonth(
    first: LocalDate,
    today: LocalDate,
    marked: Set<LocalDate>,
    onDay: (LocalDate) -> Unit,
    onMonth: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    val start = weekStart(first)
    Column(modifier) {
        Text(
            first.monthTitle(), style = Palm.button,
            color = if (first.month == today.month && first.year == today.year) Palm.nowLine else Palm.navy,
            modifier = Modifier.clickable { onMonth(first) }.padding(vertical = 2.dp),
        )
        val last = first.plusMonths(1).minusDays(1)
        val weeks = ((java.time.temporal.ChronoUnit.DAYS.between(start, last)) / 7 + 1).toInt()
        for (week in 0 until weeks) {
            val weekStartDay = start.plusDays(week * 7L)
            Row(Modifier.fillMaxWidth().height(18.dp)) {
                for (i in 0 until 7) {
                    val d = weekStartDay.plusDays(i.toLong())
                    val inMonth = d.month == first.month
                    val has = inMonth && d in marked
                    val isToday = inMonth && d == today
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(if (inMonth) Modifier.clickable { onDay(d) } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Кружок только у сегодняшнего дня; дни с событиями — жирные синие
                        if (isToday) Box(Modifier.size(17.dp).clip(CircleShape).background(Palm.navy))
                        if (inMonth) {
                            Text(
                                "${d.dayOfMonth}",
                                style = MiniDay.copy(fontWeight = if (has || isToday) FontWeight.Bold else FontWeight.Normal),
                                color = when {
                                    isToday -> Color.White
                                    has -> Palm.navy
                                    d.dayOfWeek.value >= 6 -> Palm.nowLine.copy(alpha = 0.7f)
                                    else -> Palm.inkSoft.copy(alpha = 0.7f)
                                },
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}
