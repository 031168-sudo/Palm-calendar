package ru.palmdate.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.palmdate.app.model.CalendarInfo
import ru.palmdate.app.model.ContactRef
import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.NewEvent
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.ui.theme.Palm
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val RU = Locale.forLanguageTag("ru")
private val HM = DateTimeFormatter.ofPattern("H:mm")

private fun LocalDate.pretty(): String {
    val dow = dayOfWeek.getDisplayName(TextStyle.SHORT, RU).replaceFirstChar { it.uppercase() }
    return "$dow, $dayOfMonth ${month.getDisplayName(TextStyle.SHORT, RU).trimEnd('.')}"
}

/**
 * "Новое" в три тапа: тип → кто → когда.
 * Писать текст не обязательно — заголовок соберётся сам: "Звонок: Сергей".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEventSheet(
    initialStart: LocalDateTime,
    searchContacts: suspend (String) -> List<ContactRef>,
    loadCalendars: suspend () -> List<CalendarInfo>,
    lastCalendarId: Long?,
    onDismiss: () -> Unit,
    onCreate: (NewEvent) -> Unit,
) {
    // Шаги: 0 — тип, 1 — кто, 2 — календарь, 3 — когда
    var step by remember { mutableIntStateOf(0) }
    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var calendarId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        calendars = loadCalendars()
        calendarId = when {
            calendars.any { it.id == lastCalendarId } -> lastCalendarId // последний использованный
            calendars.size == 1 -> calendars.first().id                // выбирать не из чего
            else -> null                                               // первый раз — спросим
        }
    }
    // После "кто": если календарь ещё не выбран — сначала спрашиваем его
    val afterWho = { step = if (calendarId == null) 2 else 3 }
    var type by remember { mutableStateOf<EventType?>(null) }
    var contact by remember { mutableStateOf<ContactRef?>(null) }
    var title by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(initialStart) }
    var minutes by remember { mutableIntStateOf(60) }
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palm.paper,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).imePadding()) {
            SheetTitle(type, contact?.name ?: title.takeIf { it.isNotBlank() })
            Spacer(Modifier.height(12.dp))

            when (step) {
                0 -> TypeGrid { t ->
                    type = t
                    minutes = t.defaultMinutes
                    step = 1
                }

                1 -> {
                    val t = type!!
                    if (t.needsContact) {
                        ContactPicker(
                            search = searchContacts,
                            onPick = { contact = it; afterWho() },
                            onSkip = afterWho,
                        )
                    } else {
                        OutlinedTextField(
                            value = title, onValueChange = { title = it },
                            label = { Text("Что (можно пусто)") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row { Spacer(Modifier.weight(1f)); PalmButton("Далее", filled = true, onClick = afterWho) }
                    }
                }

                2 -> CalendarPicker(calendars, selected = calendarId) {
                    calendarId = it
                    step = 3
                }

                else -> WhenPicker(
                    type = type!!,
                    calendar = calendars.firstOrNull { it.id == calendarId },
                    showAccount = calendars.map { it.accountName }.distinct().size > 1,
                    onChangeCalendar = { step = 2 },
                    start = start, onStart = { start = it },
                    minutes = minutes, onMinutes = { minutes = it },
                    note = note, onNote = { note = it },
                    onDone = {
                        val cal = calendarId
                        if (cal == null) step = 2
                        else onCreate(NewEvent(type!!, contact, title, start, minutes, note, cal))
                    },
                )
            }
        }
    }
}

@Composable
private fun SheetTitle(type: EventType?, who: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            type?.icon ?: Icons.Outlined.Event, null,
            tint = type?.color ?: Palm.navy, modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            listOfNotNull(type?.label ?: "Новое", who).joinToString(" · "),
            style = Palm.title, color = Palm.ink,
        )
    }
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(2.dp).background(Palm.navy))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeGrid(onPick: (EventType) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 3,
    ) {
        EventType.entries.forEach { t ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Palm.rule, RoundedCornerShape(10.dp))
                    .clickable { onPick(t) }
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(t.icon, null, tint = t.color, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(6.dp))
                Text(t.label, style = Palm.small, color = Palm.ink, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ContactPicker(
    search: suspend (String) -> List<ContactRef>,
    onPick: (ContactRef) -> Unit,
    onSkip: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ContactRef>>(emptyList()) }
    LaunchedEffect(query) {
        delay(150)
        results = search(query)
    }
    OutlinedTextField(
        value = query, onValueChange = { query = it },
        label = { Text("Кто") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
        items(results, key = { it.lookupKey }) { c ->
            Row(
                Modifier.fillMaxWidth().height(46.dp).clickable { onPick(c) }.dottedRule(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Palm.navyLight),
                    contentAlignment = Alignment.Center,
                ) { Text(c.name.take(1).uppercase(), style = Palm.button, color = Palm.navy) }
                Spacer(Modifier.width(10.dp))
                Text(c.name, style = Palm.body, color = Palm.ink)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row { Spacer(Modifier.weight(1f)); PalmButton("Без контакта", onClick = onSkip) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhenPicker(
    type: EventType,
    calendar: CalendarInfo?,
    showAccount: Boolean,
    onChangeCalendar: () -> Unit,
    start: LocalDateTime, onStart: (LocalDateTime) -> Unit,
    minutes: Int, onMinutes: (Int) -> Unit,
    note: String, onNote: (String) -> Unit,
    onDone: () -> Unit,
) {
    val allDay = minutes == 0

    // Дата
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepIcon(Icons.AutoMirrored.Filled.KeyboardArrowLeft) { onStart(start.minusDays(1)) }
        Text(start.toLocalDate().pretty(), style = Palm.title, color = Palm.ink,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        StepIcon(Icons.AutoMirrored.Filled.KeyboardArrowRight) { onStart(start.plusDays(1)) }
    }
    Spacer(Modifier.height(6.dp))

    // Время с шагом 15 минут
    if (!allDay) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepIcon(Icons.Filled.Remove) { onStart(start.minusMinutes(15)) }
            Text(start.format(HM), style = Palm.title.copy(fontSize = Palm.title.fontSize * 1.6f),
                color = Palm.navy, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            StepIcon(Icons.Filled.Add) { onStart(start.plusMinutes(15)) }
        }
        Spacer(Modifier.height(10.dp))
    }

    // Быстрый выбор
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        Chip("Через час") { onStart(now.plusHours(1).withMinute((now.minute / 15) * 15)) }
        Chip("Завтра 10:00") { onStart(now.toLocalDate().plusDays(1).atTime(10, 0)) }
        Chip("Пн 10:00") {
            var d = now.toLocalDate().plusDays(1)
            while (d.dayOfWeek.value != 1) d = d.plusDays(1)
            onStart(d.atTime(10, 0))
        }
    }
    Spacer(Modifier.height(12.dp))

    // Длительность
    Text("Длительность", style = Palm.small, color = Palm.inkSoft)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(15 to "15 мин", 30 to "30 мин", 60 to "1 ч", 120 to "2 ч", 0 to "Весь день").forEach { (m, label) ->
            Chip(label, selected = minutes == m) { onMinutes(m) }
        }
    }
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = note, onValueChange = onNote,
        label = { Text(if (type == EventType.CALL) "О чём (необязательно)" else "Заметка (необязательно)") },
        modifier = Modifier.fillMaxWidth(), maxLines = 3,
    )
    Spacer(Modifier.height(10.dp))

    // Куда запишется событие — тап меняет календарь
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onChangeCalendar)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Календарь", style = Palm.small, color = Palm.inkSoft, modifier = Modifier.width(80.dp))
        if (calendar != null) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(calendar.color)))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(calendar.name, style = Palm.body, color = Palm.ink, maxLines = 1)
                if (showAccount && calendar.name != calendar.accountName) {
                    Text(calendar.accountName, style = Palm.small, color = Palm.inkSoft, maxLines = 1)
                }
            }
        } else {
            Text("выбрать…", style = Palm.body, color = Palm.navy, modifier = Modifier.weight(1f))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palm.inkSoft)
    }

    Spacer(Modifier.height(10.dp))
    Row { Spacer(Modifier.weight(1f)); PalmButton("Готово", filled = true, onClick = onDone) }
}

/**
 * Выбор календаря. Если Google-аккаунтов несколько — календари сгруппированы по аккаунтам,
 * если аккаунт один — просто список календарей без упоминания аккаунта.
 */
@Composable
private fun CalendarPicker(calendars: List<CalendarInfo>, selected: Long?, onPick: (Long) -> Unit) {
    Text("Куда записать?", style = Palm.body, color = Palm.ink)
    Spacer(Modifier.height(8.dp))
    if (calendars.isEmpty()) {
        Text(
            "Нет календарей для записи. Добавьте Google-аккаунт в настройках телефона.",
            style = Palm.small, color = Palm.inkSoft,
        )
        return
    }
    val byAccount = calendars.groupBy { it.accountName }
    val multi = byAccount.size > 1
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
        byAccount.forEach { (account, cals) ->
            if (multi) {
                item(key = "acc:$account") {
                    Text(
                        account, style = Palm.button, color = Palm.navy,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                    )
                }
            }
            items(cals, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().height(46.dp).clickable { onPick(c.id) }.dottedRule()
                        .padding(start = if (multi) 8.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(c.color)))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (c.isPrimary && c.name == c.accountName) "Основной" else c.name,
                        style = Palm.body, color = Palm.ink, modifier = Modifier.weight(1f),
                    )
                    if (c.id == selected) Text("✓", style = Palm.title, color = Palm.navy)
                }
            }
        }
    }
}

@Composable
private fun StepIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).border(1.dp, Palm.navy, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Palm.navy) }
}

@Composable
private fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) Palm.navy else Color.Transparent)
            .border(1.dp, if (selected) Palm.navy else Palm.rule, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(text, style = Palm.button, color = if (selected) Color.White else Palm.ink) }
}

/* ---------- Подробности события ---------- */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventDetailsSheet(
    event: PalmEvent,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Palm.paper,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
            SheetTitle(event.type, event.contact?.name ?: event.title.takeIf { event.type == null })
            Spacer(Modifier.height(12.dp))

            val whenText = if (event.allDay) event.start.toLocalDate().pretty() + ", весь день"
            else event.start.toLocalDate().pretty() + ", " + event.start.format(HM) + "–" + event.end.format(HM)
            DetailLine("Когда", whenText)
            event.contact?.phone?.let { DetailLine("Телефон", it) }
            event.contact?.address?.let { DetailLine("Адрес", it) }
            event.note?.let { DetailLine("Заметка", it) }

            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                primaryActionLabel(event)?.let { PalmButton(it, filled = true, onClick = onAction) }
                PalmButton("В календаре", onClick = onOpen)
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Delete, "Удалить", tint = Palm.nowLine) }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).dottedRule().padding(bottom = 8.dp)) {
        Text(label, style = Palm.small, color = Palm.inkSoft, modifier = Modifier.width(80.dp))
        Text(value, style = Palm.body, color = Palm.ink, modifier = Modifier.weight(1f))
    }
}
