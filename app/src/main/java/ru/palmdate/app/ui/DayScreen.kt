package ru.palmdate.app.ui

import ru.palmdate.app.R
import ru.palmdate.app.str
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
import androidx.compose.runtime.key
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.DisabledByDefault
import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.icon
import ru.palmdate.app.model.color
import ru.palmdate.app.model.Outcome
import ru.palmdate.app.model.OutcomeKind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import ru.palmdate.app.time.*
import java.time.format.TextStyle
import java.util.Locale

/** Язык интерфейса — для названий месяцев, дней недели и дат. */
internal val UiLocale: Locale get() = ru.palmdate.app.Lang.locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(vm: DayViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    var newAt by remember { mutableStateOf<LocalDateTime?>(null) }
    var details by remember { mutableStateOf<PalmEvent?>(null) }
    val call = LocalCaller.current
    val mailer = LocalMailer.current
    var history by remember { mutableStateOf<ru.palmdate.app.model.ContactRef?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var showCalendars by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    // Новое событие из статистики: тип и человек выбраны заранее
    var newPreset by remember { mutableStateOf<Pair<EventType, ru.palmdate.app.model.ContactRef>?>(null) }

    // Широкий экран (планшет, разложенная раскладушка): подробности события — колонкой справа
    val wide = usesPane()

    // Нижние панели поднимаются только до синей шапки с датой
    var headerBottom by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val sheetTop = with(density) { headerBottom.toDp() }

    androidx.compose.runtime.CompositionLocalProvider(LocalSheetTop provides sheetTop) {
    Box(Modifier.fillMaxSize()) {
    // Подробности события: на широком экране — колонка справа, на телефоне — панель снизу
    @Composable
    fun EventDetails(e: PalmEvent, pane: Boolean) = key(e.eventId, e.instanceStart) {
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
            onAction = { if (e.type == EventType.MAIL) details = null; ctx.runPrimaryAction(e, call, mailer.open) },
            onOpen = { ctx.openInCalendar(e) },
            onDelete = { scope -> vm.delete(e, scope); details = null },
            onHistory = e.contact?.let { c -> { details = null; history = c } },
            onOutcome = { o, note ->
                vm.setOutcome(e, o, note)
                details = e.copy(outcome = o, outcomeNote = note?.takeIf { it.isNotBlank() })
            },
            onFollowUp = { start -> vm.followUp(e, start) },
            // У повторяющегося с этого раза начинается новая часть серии — карточку закрываем
            onRepeat = { rule -> vm.setRepeat(e, rule); if (e.recurring) details = null },
            onEditTitle = { t ->
                val newTitle = e.type?.let { type ->
                    val prefix = if (type == EventType.MAIL) e.mail?.kind?.verb ?: type.label else type.label
                    if (t.isBlank()) prefix else "$prefix: $t"
                } ?: t
                vm.setTitle(e, newTitle)
                details = e.copy(title = newTitle)
            },
            onEditTime = { start, minutes ->
                vm.setTime(e, start, minutes)
                if (e.recurring) details = null else details = e.copy(
                    start = if (minutes == 0) start.toLocalDate().atStartOfDay() else start,
                    end = if (minutes == 0) start.toLocalDate().plusDays(1).atStartOfDay() else start.plusMinutes(minutes.toLong()),
                    allDay = minutes == 0,
                )
            },
            onEditNote = { n ->
                vm.setNote(e, n)
                details = e.copy(note = n?.takeIf { it.isNotBlank() })
            },
            loadPlace = vm::place,
            onSetPlace = { vm.setPlace(e.eventId, it) },
            loadFiles = vm::tripFiles,
            onAddFile = { id, uri, label -> vm.addTripFile(id, android.net.Uri.parse(uri), label) },
            onDeleteFile = { vm.deleteTripFile(it) },
            addressesFor = vm::addressesFor,
            onPickAddress = { addr ->
                e.contact?.let { c ->
                    vm.setAddress(c.lookupKey, addr)
                    details = e.copy(contact = c.copy(address = addr))
                }
            },
            asPane = pane,
        )
    }

    val shown = details
    val paneContent: (@Composable () -> Unit)? = if (wide && shown != null) ({ EventDetails(shown, pane = true) }) else null
    CalendarFrame(
        pane = paneContent,
        state = state,
        headerModifier = Modifier.onGloballyPositioned { headerBottom = it.boundsInWindow().bottom.toInt() },
        actions = MainActions(
            onSelect = vm::select,
            onShiftWeek = { vm.shift(7L * it) },
            onShiftPeriod = vm::shiftPeriod,
            onTitle = { pickDate = true },
            onSlot = { date, hour -> newAt = date.atTime(hour, 0) },
            // Задача — отметить выполненной; остальное — позвонить / маршрут / письмо
            onIcon = { if (it.type == EventType.TASK) vm.toggleTask(it) else ctx.runPrimaryAction(it, call, mailer.open) },
            onEvent = { details = it },
            onDay = { vm.setMode(ViewMode.DAY, it) },
            onMonth = { vm.setMode(ViewMode.MONTH, it) },
            onNew = {
                val now = LocalTime.now()
                val hour = if (state.date == LocalDate.now()) (now.hour + 1).coerceAtMost(23) else 9
                newAt = state.date.atTime(hour, 0)
            },
            onToday = vm::today,
            onGoTo = { pickDate = true },
            onStats = { showStats = true },
            onSettings = { showSettings = true },
            onMode = { vm.setMode(it) },
        ),
    )

    if (showSettings) {
        SettingsSheet(
            platform = remember(ctx, vm) { AndroidSettingsPlatform(ctx, vm) },
            loadCalendars = vm::allCalendars,
            onCalendars = { showSettings = false; showCalendars = true },
            onDismiss = { showSettings = false },
        )
    }

    if (showStats) {
        StatsSheet(
            load = vm::contactStats,
            onAction = { a ->
                when (a) {
                    is StatAction.CallNow -> call(a.contact)
                    is StatAction.Create -> {
                        showStats = false
                        newPreset = a.type to a.contact
                        val now = LocalTime.now()
                        val today = LocalDate.now()
                        newAt = if (now.hour < 23) today.atTime(now.hour + 1, 0) else today.plusDays(1).atTime(9, 0)
                    }
                    is StatAction.History -> { showStats = false; history = a.contact }
                }
            },
            onDismiss = { showStats = false },
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
            onDismiss = { newAt = null; newPreset = null },
            onCreate = { vm.create(it); newAt = null; newPreset = null },
            searchEmails = vm::searchEmails,
            contactByEmail = vm::contactByEmail,
            presetType = newPreset?.first,
            presetContact = newPreset?.second,
        )
    }

    if (!wide) details?.let { e -> EventDetails(e, pane = false) }

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
            initialSelectedDateMillis = state.date.startOfDayUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        vm.select(utcMillisToDate(it))
                    }
                    pickDate = false
                }) { Text(str(R.string.go_to)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.today(); pickDate = false }) { Text(str(R.string.today_cap)) }
                    TextButton(onClick = { pickDate = false }) { Text(str(R.string.cancel)) }
                }
            },
        ) { DatePicker(pickerState) }
    }
    }
    }
}
