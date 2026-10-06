package ru.palmdate.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.palmdate.app.DayViewModel
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.ui.theme.Palm
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val RU = Locale.forLanguageTag("ru")
private val HM = DateTimeFormatter.ofPattern("H:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(vm: DayViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    var newAt by remember { mutableStateOf<LocalDateTime?>(null) }
    var details by remember { mutableStateOf<PalmEvent?>(null) }
    var pickDate by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Palm.paper)) {
        DateHeader(
            date = state.date,
            onSelect = vm::select,
            onShift = vm::shift,
        )
        DayBody(
            date = state.date,
            events = state.events,
            modifier = Modifier.weight(1f),
            onSlot = { hour -> newAt = state.date.atTime(hour, 0) },
            onIcon = { ctx.runPrimaryAction(it) },
            onEvent = { details = it },
        )
        ButtonBar(
            onNew = {
                val now = LocalTime.now()
                val hour = if (state.date == LocalDate.now()) (now.hour + 1).coerceAtMost(23) else 9
                newAt = state.date.atTime(hour, 0)
            },
            onToday = vm::today,
            onGoTo = { pickDate = true },
        )
    }

    state.error?.let { msg ->
        Box(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp), contentAlignment = Alignment.BottomCenter) {
            Snackbar(
                action = { TextButton(onClick = vm::dismissError) { Text("OK") } },
                containerColor = Palm.ink,
            ) { Text(msg) }
        }
    }

    newAt?.let { start ->
        NewEventSheet(
            initialStart = start,
            searchContacts = vm::searchContacts,
            onDismiss = { newAt = null },
            onCreate = { vm.create(it); newAt = null },
        )
    }

    details?.let { e ->
        EventDetailsSheet(
            event = e,
            onDismiss = { details = null },
            onAction = { ctx.runPrimaryAction(e) },
            onOpen = { ctx.openInCalendar(e) },
            onDelete = { vm.delete(e); details = null },
        )
    }

    if (pickDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        vm.select(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    pickDate = false
                }) { Text("Перейти") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Отмена") } },
        ) { DatePicker(pickerState) }
    }
}

/* ---------- Заголовок: вкладка с датой + полоса дней недели, как в Date Book ---------- */

/** Вкладка с косым правым краем — фирменный заголовок приложений Palm. */
private val TabShape = GenericShape { size, _ ->
    val slant = size.height * 0.55f
    moveTo(0f, 0f)
    lineTo(size.width - slant, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
private fun DateHeader(date: LocalDate, onSelect: (LocalDate) -> Unit, onShift: (Long) -> Unit) {
    val today = LocalDate.now()
    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val dow = date.dayOfWeek.getDisplayName(TextStyle.SHORT, RU).replaceFirstChar { it.uppercase() }
    val month = date.month.getDisplayName(TextStyle.SHORT, RU).trimEnd('.')

    Column(Modifier.fillMaxWidth().background(Palm.paper).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            Box(
                Modifier
                    .clip(TabShape)
                    .background(Palm.navy)
                    .padding(start = 12.dp, end = 26.dp, top = 6.dp, bottom = 5.dp),
            ) {
                Text("$dow, ${date.dayOfMonth} $month", style = Palm.title, color = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Неделя назад", tint = Palm.navy,
                modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onShift(-7) }.padding(2.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in 0L..6L) {
                    val d = monday.plusDays(i)
                    val selected = d == date
                    val letter = d.dayOfWeek.getDisplayName(TextStyle.NARROW, RU).uppercase()
                    Box(
                        Modifier
                            .size(width = 24.dp, height = 28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (selected) Palm.navy else Color.Transparent)
                            .clickable { onSelect(d) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            letter,
                            style = Palm.time.copy(
                                textDecoration = if (d == today && !selected) TextDecoration.Underline else null,
                            ),
                            color = when {
                                selected -> Color.White
                                d.dayOfWeek.value >= 6 -> Palm.nowLine
                                else -> Palm.ink
                            },
                        )
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, "Неделя вперёд", tint = Palm.navy,
                modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onShift(7) }.padding(2.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        // Синяя линия под вкладкой — как граница заголовка в Palm OS
        Box(Modifier.fillMaxWidth().height(2.dp).background(Palm.navy))
    }
}

/* ---------- Тело дня: часовые строки с пунктиром ---------- */

private sealed interface Row_ {
    data class AllDay(val e: PalmEvent) : Row_
    data class Slot(val hour: Int, val events: List<PalmEvent>) : Row_
}

@Composable
private fun DayBody(
    date: LocalDate,
    events: List<PalmEvent>,
    modifier: Modifier,
    onSlot: (Int) -> Unit,
    onIcon: (PalmEvent) -> Unit,
    onEvent: (PalmEvent) -> Unit,
) {
    val timed = events.filter { !it.allDay }
    val byHour = timed.groupBy { if (it.start.toLocalDate() < date) 0 else it.start.hour }
    val first = (byHour.keys.minOrNull() ?: 8).coerceAtMost(8)
    val last = (byHour.keys.maxOrNull() ?: 18).coerceAtLeast(18)
    val rows: List<Row_> =
        events.filter { it.allDay }.map { Row_.AllDay(it) } +
            (first..last).map { Row_.Slot(it, byHour[it].orEmpty()) }

    val isToday = date == LocalDate.now()
    val nowHour = LocalTime.now().hour
    val listState = rememberLazyListState()
    LaunchedEffect(date) {
        val target = rows.indexOfFirst { it is Row_.Slot && it.hour == (if (isToday) nowHour else 8) }
        if (target > 0) listState.scrollToItem((target - 1).coerceAtLeast(0))
    }

    LazyColumn(modifier.fillMaxWidth(), state = listState) {
        items(rows) { row ->
            when (row) {
                is Row_.AllDay -> EventLine(row.e, timeLabel = "•", highlight = false, onIcon, onEvent)
                is Row_.Slot -> {
                    val label = LocalTime.of(row.hour, 0).format(HM)
                    val hl = isToday && row.hour == nowHour
                    if (row.events.isEmpty()) {
                        EmptyLine(label, hl) { onSlot(row.hour) }
                    } else {
                        // Если первое событие не ровно в начале часа — сам час остаётся отдельной строкой, как на Palm
                        val startsOnHour = row.events.first().start.minute == 0
                        if (!startsOnHour) EmptyLine(label, hl) { onSlot(row.hour) }
                        row.events.forEachIndexed { i, e ->
                            val t = if (e.start.minute == 0 && i == 0) label else e.start.format(HM)
                            EventLine(e, t, hl && i == 0 && startsOnHour, onIcon, onEvent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeLabel(text: String, highlight: Boolean) {
    Text(
        text,
        style = Palm.time,
        color = if (highlight) Palm.nowLine else Palm.inkSoft,
        textAlign = TextAlign.End,
        modifier = Modifier.width(52.dp).padding(end = 10.dp),
    )
}

@Composable
private fun EmptyLine(label: String, highlight: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeLabel(label, highlight)
        Box(Modifier.weight(1f).height(44.dp).padding(end = 12.dp).dottedRule())
    }
}

@Composable
private fun EventLine(
    e: PalmEvent,
    timeLabel: String,
    highlight: Boolean,
    onIcon: (PalmEvent) -> Unit,
    onEvent: (PalmEvent) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeLabel(timeLabel, highlight)
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .padding(end = 12.dp)
                .dottedRule(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Иконка типа — тап по ней сразу выполняет действие (позвонить / маршрут)
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable { onIcon(e) },
                contentAlignment = Alignment.Center,
            ) {
                val type = e.type
                if (type != null) {
                    Icon(type.icon, type.label, tint = type.color, modifier = Modifier.size(20.dp))
                } else {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Palm.inkSoft))
                }
            }
            Column(
                Modifier.weight(1f).clickable { onEvent(e) }.padding(vertical = 6.dp, horizontal = 4.dp),
            ) {
                Text(
                    e.contact?.name ?: e.title,
                    style = Palm.body, color = Palm.ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val sub = buildList {
                    e.type?.let { add(it.label) }
                    if (!e.allDay && e.end.isAfter(e.start)) add("до " + e.end.format(HM))
                    e.note?.let { add(it.lineSequence().first()) }
                }.joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(sub, style = Palm.small, color = Palm.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/* ---------- Нижняя панель кнопок: Новое · Сегодня · Перейти ---------- */

@Composable
private fun ButtonBar(onNew: () -> Unit, onToday: () -> Unit, onGoTo: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Palm.paper).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palm.rule))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PalmButton("Новое", filled = true, onClick = onNew)
            PalmButton("Сегодня", onClick = onToday)
            PalmButton("Перейти", onClick = onGoTo)
        }
    }
}
