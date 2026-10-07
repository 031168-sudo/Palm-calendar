package ru.palmdate.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material.icons.outlined.ViewWeek
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
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.DisabledByDefault
import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.Outcome
import ru.palmdate.app.model.OutcomeKind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.palmdate.app.CalState
import ru.palmdate.app.DayViewModel
import ru.palmdate.app.ViewMode
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.ui.theme.Palm
import ru.palmdate.app.weekStart
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal val RU: Locale = Locale.forLanguageTag("ru")
internal val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

internal fun LocalDate.shortMonth() = month.getDisplayName(TextStyle.SHORT, RU).trimEnd('.')
internal fun LocalDate.pretty(): String {
    val dow = dayOfWeek.getDisplayName(TextStyle.SHORT, RU).replaceFirstChar { it.uppercase() }
    return "$dow, $dayOfMonth ${shortMonth()}"
}
internal fun LocalDate.monthTitle() =
    month.getDisplayName(TextStyle.FULL_STANDALONE, RU).replaceFirstChar { it.uppercase() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(vm: DayViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    var newAt by remember { mutableStateOf<LocalDateTime?>(null) }
    var details by remember { mutableStateOf<PalmEvent?>(null) }
    var history by remember { mutableStateOf<ru.palmdate.app.model.ContactRef?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var showCalendars by remember { mutableStateOf(false) }

    val openDay: (LocalDate) -> Unit = { vm.setMode(ViewMode.DAY, it) }

    Column(Modifier.fillMaxSize().background(Palm.paper)) {
        Header(
            state = state,
            onSelect = vm::select,
            onShiftWeek = { vm.shift(7L * it) },
            onShiftPeriod = vm::shiftPeriod,
            onTitle = { pickDate = true },
        )

        // Свайп влево — следующий период, вправо — предыдущий. Страница уезжает в сторону свайпа.
        val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
        AnimatedContent(
            targetState = state,
            contentKey = { it.pageKey },
            transitionSpec = {
                if (targetState.mode != initialState.mode) {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                } else {
                    val forward = targetState.range.first > initialState.range.first
                    (slideInHorizontally(tween(220)) { w -> if (forward) w else -w } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(220)) { w -> if (forward) -w / 3 else w / 3 } + fadeOut(tween(160)))
                }
            },
            label = "page",
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onDragEnd = {
                            when {
                                dx < -swipeThreshold -> vm.shiftPeriod(1)
                                dx > swipeThreshold -> vm.shiftPeriod(-1)
                            }
                        },
                        onHorizontalDrag = { change, amount ->
                            dx += amount
                            change.consume()
                        },
                    )
                },
        ) { page ->
            // Задача — отметить выполненной; остальное — позвонить / маршрут
            val onIcon: (PalmEvent) -> Unit = { if (it.type == EventType.TASK) vm.toggleTask(it) else ctx.runPrimaryAction(it) }
            val onEvent: (PalmEvent) -> Unit = { details = it }
            when (page.mode) {
                ViewMode.DAY -> DayBody(
                    date = page.date,
                    events = page.events,
                    modifier = Modifier.fillMaxSize(),
                    onSlot = { hour -> newAt = page.date.atTime(hour, 0) },
                    onIcon = onIcon,
                    onEvent = onEvent,
                )
                ViewMode.AGENDA -> AgendaView(page.date, page.events, onDay = openDay, onIcon = onIcon, onEvent = onEvent)
                ViewMode.WEEK -> WeekView(page.date, page.events, onDay = openDay, onEvent = onEvent)
                ViewMode.MONTH -> MonthView(page.date, page.events, onDay = openDay)
                ViewMode.YEAR -> YearView(
                    page.date, page.yearDays,
                    onDay = openDay,
                    onMonth = { vm.setMode(ViewMode.MONTH, it) },
                )
            }
        }

        ButtonBar(
            mode = state.mode,
            onNew = {
                val now = LocalTime.now()
                val hour = if (state.date == LocalDate.now()) (now.hour + 1).coerceAtMost(23) else 9
                newAt = state.date.atTime(hour, 0)
            },
            onToday = vm::today,
            onGoTo = { pickDate = true },
            onCalendars = { showCalendars = true },
            onMode = { vm.setMode(it) },
        )
    }

    if (showCalendars) {
        val hidden by vm.hiddenCalendars.collectAsStateWithLifecycle()
        CalendarsSheet(
            load = vm::allCalendars,
            hidden = hidden,
            onToggle = vm::setCalendarShown,
            onDismiss = { showCalendars = false },
        )
    }

    state.error?.let { msg ->
        Box(Modifier.fillMaxSize().navigationBarsPadding().padding(12.dp), contentAlignment = Alignment.BottomCenter) {
            Snackbar(
                action = { TextButton(onClick = vm::dismissError) { Text("OK") } },
                containerColor = Palm.ink,
                contentColor = Palm.paper,
                actionContentColor = Palm.navyLight,
            ) { Text(msg) }
        }
    }

    newAt?.let { start ->
        NewEventSheet(
            initialStart = start,
            searchContacts = vm::searchContacts,
            phonesFor = vm::phonesFor,
            loadCalendars = vm::allCalendars,
            lastCalendarId = vm.lastCalendarId(),
            onDismiss = { newAt = null },
            onCreate = { vm.create(it); newAt = null },
        )
    }

    details?.let { e ->
        EventDetailsSheet(
            event = e,
            searchContacts = vm::searchContacts,
            phonesFor = vm::phonesFor,
            loadReminders = vm::reminders,
            loadCalendars = vm::allCalendars,
            onMove = { calId -> vm.move(e, calId); details = null },
            onSetReminders = { vm.setReminders(e.eventId, it) },
            onSetLink = { type, contact ->
                vm.setLink(e, type, contact)
                details = e.copy(type = type, contact = contact)
            },
            onDismiss = { details = null },
            onAction = { ctx.runPrimaryAction(e) },
            onOpen = { ctx.openInCalendar(e) },
            onDelete = { vm.delete(e); details = null },
            onHistory = e.contact?.let { c -> { details = null; history = c } },
            onOutcome = { o, note ->
                vm.setOutcome(e, o, note)
                details = e.copy(outcome = o, outcomeNote = note?.takeIf { it.isNotBlank() })
            },
            onFollowUp = { start -> vm.followUp(e, start) },
        )
    }

    history?.let { c ->
        ContactHistorySheet(
            contact = c,
            load = vm::history,
            onEvent = { history = null; details = it },
            onDismiss = { history = null },
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
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.today(); pickDate = false }) { Text("Сегодня") }
                    TextButton(onClick = { pickDate = false }) { Text("Отмена") }
                }
            },
        ) { DatePicker(pickerState) }
    }
}

/* ---------- Заголовок: вкладка + полоса дней недели или стрелки периода ---------- */

/** Вкладка с косым правым краем — фирменный заголовок приложений Palm. */
private val TabShape = GenericShape { size, _ ->
    val slant = size.height * 0.55f
    moveTo(0f, 0f)
    lineTo(size.width - slant, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

private fun tabTitle(s: CalState): String {
    val d = s.date
    return when (s.mode) {
        ViewMode.DAY -> d.pretty()
        ViewMode.AGENDA -> "с ${d.dayOfMonth} ${d.shortMonth()}"
        ViewMode.WEEK -> {
            val a = weekStart(d)
            val b = a.plusDays(6)
            if (a.month == b.month) "${a.dayOfMonth}–${b.dayOfMonth} ${b.shortMonth()}"
            else "${a.dayOfMonth} ${a.shortMonth()} – ${b.dayOfMonth} ${b.shortMonth()}"
        }
        ViewMode.MONTH -> "${d.monthTitle()} ${d.year}"
        ViewMode.YEAR -> "${d.year}"
    }
}

@Composable
private fun Header(
    state: CalState,
    onSelect: (LocalDate) -> Unit,
    onShiftWeek: (Int) -> Unit,
    onShiftPeriod: (Int) -> Unit,
    onTitle: () -> Unit,
) {
    val date = state.date
    val today = LocalDate.now()
    val showStrip = state.mode == ViewMode.DAY || state.mode == ViewMode.WEEK || state.mode == ViewMode.AGENDA

    Column(Modifier.fillMaxWidth().background(Palm.paper).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            // Тап по вкладке — "Перейти" к дате (там же кнопка "Сегодня")
            Box(
                Modifier
                    .clip(TabShape)
                    .background(Palm.navy)
                    .clickable(onClick = onTitle)
                    .padding(start = 12.dp, end = 26.dp, top = 6.dp, bottom = 5.dp),
            ) {
                Text(tabTitle(state), style = Palm.title, color = Color.White, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            Arrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Назад") {
                if (showStrip) onShiftWeek(-1) else onShiftPeriod(-1)
            }
            if (showStrip) {
                val monday = weekStart(date)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (i in 0L..6L) {
                        val d = monday.plusDays(i)
                        // В виде "Неделя" подсвечена вся неделя, в остальных — выбранный день
                        val selected = if (state.mode == ViewMode.WEEK) false else d == date
                        val letter = d.dayOfWeek.getDisplayName(TextStyle.NARROW, RU).uppercase()
                        Box(
                            Modifier
                                .size(width = 24.dp, height = 28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        selected -> Palm.navy
                                        state.mode == ViewMode.WEEK -> Palm.navyLight
                                        else -> Color.Transparent
                                    },
                                )
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
            }
            Arrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Вперёд") {
                if (showStrip) onShiftWeek(1) else onShiftPeriod(1)
            }
            Spacer(Modifier.width(4.dp))
        }
        // Синяя линия под вкладкой — как граница заголовка в Palm OS
        Box(Modifier.fillMaxWidth().height(2.dp).background(Palm.navy))
    }
}

@Composable
private fun Arrow(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Icon(
        icon, desc, tint = Palm.navy,
        modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClick).padding(4.dp),
    )
}

/* ---------- Вид "День": часовые строки с пунктиром ---------- */

private sealed interface DayRow {
    data class AllDay(val e: PalmEvent) : DayRow
    data class Slot(val hour: Int, val events: List<PalmEvent>) : DayRow
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
    val rows: List<DayRow> =
        events.filter { it.allDay }.map { DayRow.AllDay(it) } +
            (first..last).map { DayRow.Slot(it, byHour[it].orEmpty()) }

    val isToday = date == LocalDate.now()
    val nowHour = LocalTime.now().hour
    val listState = rememberLazyListState()
    LaunchedEffect(date) {
        val target = rows.indexOfFirst { it is DayRow.Slot && it.hour == (if (isToday) nowHour else 8) }
        if (target > 0) listState.scrollToItem((target - 1).coerceAtLeast(0))
    }

    LazyColumn(modifier.fillMaxWidth(), state = listState) {
        items(rows) { row ->
            when (row) {
                is DayRow.AllDay -> EventLine(row.e, timeLabel = "•", highlight = false, onIcon, onEvent)
                is DayRow.Slot -> {
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
internal fun TimeLabel(text: String, highlight: Boolean) {
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

/** Бледная подсветка цветом календаря — как пастельные категории на Palm. */
internal fun calTint(color: Int) = Color(color).copy(alpha = Palm.tintAlpha)

/**
 * Строка события: время · [полоска календаря | иконка типа | имя и подпись].
 * Иконка — в цвете типа, полоска и подсветка — в цвете календаря Google.
 */
@Composable
internal fun EventLine(
    e: PalmEvent,
    timeLabel: String,
    highlight: Boolean,
    onIcon: (PalmEvent) -> Unit,
    onEvent: (PalmEvent) -> Unit,
    primary: String? = null, // свой заголовок строки (в истории контакта имя не нужно)
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
            val faded = e.outcome == Outcome.CANCELLED
            Row(
                Modifier
                    .weight(1f)
                    .padding(vertical = 3.dp)
                    .height(IntrinsicSize.Min)
                    .alpha(if (faded) 0.5f else 1f)
                    .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                    .background(calTint(e.color)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Полоска календаря
                Box(Modifier.width(3.dp).fillMaxHeight().background(Color(e.color)))
                // Иконка типа — тап по ней сразу выполняет действие (позвонить / маршрут / отметить задачу)
                Box(
                    Modifier.size(34.dp).clip(CircleShape).clickable { onIcon(e) },
                    contentAlignment = Alignment.Center,
                ) {
                    val type = e.type
                    when {
                        // Задача — чекбокс, как в Palm To Do
                        type == EventType.TASK -> Icon(
                            when (e.outcome) {
                                Outcome.DONE -> Icons.Outlined.CheckBox
                                Outcome.NOT_DONE -> Icons.Outlined.DisabledByDefault
                                else -> Icons.Outlined.CheckBoxOutlineBlank
                            },
                            type.label, tint = type.color, modifier = Modifier.size(22.dp),
                        )
                        e.fromContacts -> Icon(Icons.Outlined.Cake, "День рождения", tint = EventType.BIRTHDAY.color, modifier = Modifier.size(20.dp))
                        type != null -> Icon(type.icon, type.label, tint = type.color, modifier = Modifier.size(20.dp))
                        else -> Box(Modifier.size(9.dp).clip(CircleShape).background(Color(e.color)))
                    }
                    if (type != EventType.TASK) e.outcome?.let { OutcomeBadge(it, Modifier.align(Alignment.BottomEnd)) }
                }
                Column(
                    Modifier.weight(1f).clickable { onEvent(e) }.padding(vertical = 5.dp).padding(end = 6.dp),
                ) {
                    val struck = e.outcome == Outcome.CANCELLED || (e.type == EventType.TASK && e.outcome == Outcome.DONE)
                    Text(
                        primary ?: e.contact?.name ?: e.title,
                        style = Palm.body.copy(textDecoration = if (struck) TextDecoration.LineThrough else null),
                        color = if (struck) Palm.inkSoft else Palm.ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val sub = buildList {
                        // Итог важнее типа: "Не дозвонился — перезвонить после обеда"
                        e.outcome?.let { o -> add(o.label(e.type) + (e.outcomeNote?.let { " — $it" } ?: "")) }
                        if (primary == null && e.outcome == null) e.type?.let { add(it.label) }
                        if (!e.allDay && e.end.isAfter(e.start)) add("до " + e.end.format(HM))
                        if (e.outcome == null) e.note?.let { add(it.lineSequence().first()) }
                    }.joinToString(" · ")
                    if (sub.isNotEmpty()) {
                        Text(sub, style = Palm.small, color = Palm.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/* ---------- Нижняя панель: Новое · иконки видов ---------- */

private val ViewMode.icon: ImageVector
    get() = when (this) {
        ViewMode.AGENDA -> Icons.Outlined.ViewAgenda
        ViewMode.DAY -> Icons.Outlined.ViewDay
        ViewMode.WEEK -> Icons.Outlined.ViewWeek
        ViewMode.MONTH -> Icons.Outlined.CalendarViewMonth
        ViewMode.YEAR -> Icons.Outlined.GridView
    }

@Composable
private fun ButtonBar(
    mode: ViewMode,
    onNew: () -> Unit,
    onToday: () -> Unit,
    onGoTo: () -> Unit,
    onCalendars: () -> Unit,
    onMode: (ViewMode) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Palm.paper).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palm.rule))
        // Виды: иконка + подпись, на всю ширину
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            ViewMode.entries.forEach { m ->
                val sel = m == mode
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) Palm.navy else Color.Transparent)
                        .clickable { onMode(m) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(m.icon, null, tint = if (sel) Color.White else Palm.navy, modifier = Modifier.size(20.dp))
                    Text(m.label, style = Palm.small.copy(fontSize = Palm.small.fontSize * 0.85f),
                        color = if (sel) Color.White else Palm.navy, maxLines = 1)
                }
            }
        }
        // Кнопки, как на Palm
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp, top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PalmButton("Новое", filled = true, onClick = onNew)
            PalmButton("Сегодня", onClick = onToday)
            PalmButton("Перейти", onClick = onGoTo)
            Spacer(Modifier.weight(1f))
            // Какие календари показывать
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Palm.navy, RoundedCornerShape(10.dp)).clickable(onClick = onCalendars),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Layers, "Календари", tint = Palm.navy, modifier = Modifier.size(20.dp)) }
        }
    }
}

/** Значок итога на иконке события: галочка, крестик или стрелка переноса. */
@Composable
internal fun OutcomeBadge(o: Outcome, modifier: Modifier = Modifier) {
    val (bg, icon) = when (o.kind) {
        OutcomeKind.GOOD -> Color(0xFF2E7D32) to Icons.Filled.Check
        OutcomeKind.BAD -> Color(0xFFC0392B) to Icons.Filled.Close
        OutcomeKind.MOVED -> Palm.navy to Icons.AutoMirrored.Filled.Redo
    }
    Box(
        modifier.padding(1.dp).size(14.dp).clip(CircleShape).background(Palm.paper).padding(1.5.dp)
            .clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(9.dp)) }
}
