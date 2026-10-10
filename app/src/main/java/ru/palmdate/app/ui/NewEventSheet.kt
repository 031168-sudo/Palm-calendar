package ru.palmdate.app.ui

import ru.palmdate.app.R
import ru.palmdate.app.str
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.palmdate.app.model.CalendarInfo
import ru.palmdate.app.model.ContactRef
import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.NewEvent
import ru.palmdate.app.model.Outcome
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.model.PhoneNumber
import ru.palmdate.app.model.reminderOptions
import ru.palmdate.app.model.minutesToTime
import ru.palmdate.app.model.reminderLabel
import ru.palmdate.app.model.ALLDAY_REMINDER_OPTIONS
import ru.palmdate.app.model.allDayReminderLabel
import ru.palmdate.app.model.repeatOptions
import ru.palmdate.app.model.matchRepeat
import ru.palmdate.app.ui.theme.Palm
import java.time.LocalDateTime

typealias PhonesFor = suspend (String) -> Pair<List<PhoneNumber>, String?>

/**
 * Какой номер взять для контакта: если номер один — его; если для контакта уже выбирали — тот же;
 * иначе null — тогда надо спросить. Возвращает список номеров и выбранный номер.
 */
private suspend fun pickPhone(contact: ContactRef, phonesFor: PhonesFor): Pair<List<PhoneNumber>, String?> {
    val (phones, remembered) = phonesFor(contact.lookupKey)
    val chosen = when {
        phones.size == 1 -> phones.first().number
        remembered != null && phones.any { it.number == remembered } -> remembered
        else -> null
    }
    return phones to chosen
}

private enum class Step { TYPE, WHO, PHONE, MAIL_KIND, MAIL_FIND, MAIL_TO, CALENDAR, WHEN }

/**
 * str(R.string.new_event): тип → кто → (номер) → (календарь) → когда.
 * Писать текст не обязательно — заголовок соберётся сам: "Звонок: Сергей".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEventSheet(
    initialStart: LocalDateTime,
    searchContacts: suspend (String) -> List<ContactRef>,
    phonesFor: PhonesFor,
    loadCalendars: suspend () -> List<CalendarInfo>,
    lastCalendarId: Long?,
    onDismiss: () -> Unit,
    onCreate: (NewEvent) -> Unit,
    presetType: EventType? = null,       // из статистики: тип и человек уже выбраны
    presetContact: ContactRef? = null,
    searchEmails: suspend (String) -> List<ru.palmdate.app.data.ContactsRepository.EmailContact> = { emptyList() },
    contactByEmail: suspend (String) -> ContactRef? = { null },
) {
    val mailer = LocalMailer.current
    var mail by remember { mutableStateOf<ru.palmdate.app.model.MailInfo?>(null) }
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.TYPE) }
    var presetPending by remember { mutableStateOf(presetType != null) }
    var type by remember { mutableStateOf<EventType?>(null) }
    var contact by remember { mutableStateOf<ContactRef?>(null) }
    var phones by remember { mutableStateOf<List<PhoneNumber>>(emptyList()) }
    var title by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(initialStart) }
    var minutes by remember { mutableStateOf(60) }
    var note by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var reminders by remember { mutableStateOf<List<Int>>(emptyList()) }
    var rrule by remember { mutableStateOf<String?>(null) }

    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var calendarId by remember { mutableStateOf<Long?>(null) }
    var calLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        calendars = loadCalendars()
        calendarId = when {
            calendars.any { it.id == lastCalendarId && it.writable } -> lastCalendarId // последний использованный
            calendars.count { it.usable } == 1 -> calendars.first { it.usable }.id      // выбирать не из чего
            else -> null                                               // первый раз — спросим
        }
        calLoaded = true
    }

    // После "кто"/"номер": если календарь ещё не выбран — сначала спрашиваем его
    val toCalendarOrWhen = { step = if (calendarId == null) Step.CALENDAR else Step.WHEN }

    // Контакт выбран: для звонка разбираемся с номером
    val onContact: (ContactRef) -> Unit = { c ->
        contact = c
        if (type == EventType.CALL) {
            scope.launch {
                val (list, chosen) = pickPhone(c, phonesFor)
                phones = list
                if (chosen != null || list.isEmpty()) {
                    contact = c.copy(phone = chosen)
                    toCalendarOrWhen()
                } else {
                    step = Step.PHONE
                }
            }
        } else {
            toCalendarOrWhen()
        }
    }

    // Тип и человек заданы заранее — сразу к номеру / календарю / времени
    LaunchedEffect(calLoaded) {
        if (!presetPending || !calLoaded) return@LaunchedEffect
        val t = presetType ?: return@LaunchedEffect
        type = t
        val st = ru.palmdate.app.data.SettingsStore.current
        minutes = st.duration(t)
        reminders = if (minutes == 0) st.allDayReminders else st.remindersFor(t)
        if (presetContact != null) onContact(presetContact) else step = Step.WHO
    }
    // Ушли с первого шага — заготовка отработала
    LaunchedEffect(step) { if (step != Step.TYPE) presetPending = false }

    PalmSheet(onDismissRequest = onDismiss, fixedHeight = false) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            val done: () -> Unit = {
                val cal = calendarId
                if (cal == null) step = Step.CALENDAR
                else onCreate(NewEvent(
                    type!!, contact, title, start, minutes, note, cal, reminders, rrule, mail,
                    place = place.takeIf { type == EventType.TRIP && it.isNotBlank() },
                ))
            }
            SheetTitle(
                action = if (step == Step.WHEN && !presetPending) ({ PalmButton(str(R.string.done), filled = true, onClick = done) }) else null,
                type = type ?: presetType,
                who = 
                mail?.let { m -> m.kind.verb + ": " + ((if (m.kind == ru.palmdate.app.model.MailKind.REPLY) m.subject else null) ?: m.peer ?: "") }
                    ?: contact?.name ?: presetContact?.name ?: title.takeIf { it.isNotBlank() },
            )
            Spacer(Modifier.height(12.dp))

            if (presetPending) {
                Text("…", style = Palm.body, color = Palm.inkSoft)
            } else when (step) {
                Step.TYPE -> TypeGrid { t ->
                    type = t
                    val st = ru.palmdate.app.data.SettingsStore.current
                    minutes = st.duration(t)
                    reminders = if (minutes == 0) st.allDayReminders else st.remindersFor(t)
                    step = if (t == EventType.MAIL) Step.MAIL_KIND else Step.WHO
                }

                // Письмо: ответить на пришедшее или написать новое
                Step.MAIL_KIND -> MailKindPicker(
                    onReply = {
                        // Через почтовую программу писем не видно — запоминаем, от кого и что искать
                        if (ru.palmdate.app.data.SettingsStore.current.mailMode != ru.palmdate.app.data.MailMode.BUILTIN) {
                            step = Step.MAIL_FIND
                        } else mailer.pick("") { h ->
                            mail = ru.palmdate.app.model.MailInfo(
                                ru.palmdate.app.model.MailKind.REPLY, h.messageId, h.folder, h.subject,
                                h.from?.name, h.from?.email, h.date,
                            )
                            scope.launch {
                                contact = h.from?.email?.let { contactByEmail(it) }
                                toCalendarOrWhen()
                            }
                        }
                    },
                    onNew = { step = Step.MAIL_TO },
                )

                Step.MAIL_FIND -> ReplyFinder(searchEmails) { name, addr, key, words ->
                    mail = ru.palmdate.app.model.MailInfo(
                        ru.palmdate.app.model.MailKind.REPLY, subject = words, peerName = name, peerAddr = addr,
                    )
                    scope.launch {
                        contact = key?.let { ContactRef(it, name ?: addr ?: "") } ?: addr?.let { contactByEmail(it) }
                        toCalendarOrWhen()
                    }
                }

                Step.MAIL_TO -> RecipientPicker(searchEmails) { name, addr, key ->
                    mail = ru.palmdate.app.model.MailInfo(ru.palmdate.app.model.MailKind.NEW, peerName = name, peerAddr = addr)
                    scope.launch {
                        contact = key?.let { ContactRef(it, name ?: addr) } ?: contactByEmail(addr)
                        toCalendarOrWhen()
                    }
                }

                Step.WHO -> {
                    val t = type!!
                    if (t.needsContact) {
                        ContactPicker(search = searchContacts, onPick = onContact, onSkip = toCalendarOrWhen)
                    } else {
                        OutlinedTextField(
                            value = title, onValueChange = { title = it },
                            label = { Text(str(R.string.new_title_optional)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        if (t == EventType.TRIP) {
                            OutlinedTextField(
                                value = place, onValueChange = { place = it },
                                label = { Text(str(R.string.new_address_optional)) },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        Row { Spacer(Modifier.weight(1f)); PalmButton(str(R.string.next), filled = true, onClick = toCalendarOrWhen) }
                    }
                }

                Step.PHONE -> PhonePicker(phones, selected = contact?.phone) { number ->
                    contact = contact?.copy(phone = number)
                    toCalendarOrWhen()
                }

                Step.CALENDAR -> CalendarPicker(calendars, selected = calendarId) {
                    calendarId = it
                    step = Step.WHEN
                }

                // Прокрутка "по необходимости": без клавиатуры всё помещается и окно выглядит как раньше,
                // с клавиатурой поле заметки уезжает над ней
                Step.WHEN -> Column(Modifier.verticalScroll(rememberScrollState())) { WhenPicker(
                    type = type!!,
                    phone = contact?.phone?.takeIf { type == EventType.CALL },
                    onChangePhone = if (type == EventType.CALL && contact != null) {
                        {
                            scope.launch {
                                phones = phonesFor(contact!!.lookupKey).first
                                step = Step.PHONE
                            }
                        }
                    } else null,
                    calendar = calendars.firstOrNull { it.id == calendarId },
                    showAccount = calendars.filter { it.usable }.map { it.accountName }.distinct().size > 1,
                    onChangeCalendar = { step = Step.CALENDAR },
                    start = start, onStart = { start = it },
                    minutes = minutes, onMinutes = { minutes = it },
                    reminders = reminders, onReminders = { reminders = it },
                    rrule = rrule, onRrule = { rrule = it },
                    note = note, onNote = { note = it },
                    onDone = done,
                ) }
            }
        }
    }
}

@Composable
private fun SheetTitle(
    type: EventType?, who: String?, label: String? = null, onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.then(if (onClick != null) Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            type?.icon ?: Icons.Outlined.Event, null,
            tint = type?.color ?: Palm.navy, modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            listOfNotNull(label ?: type?.label ?: str(R.string.new_event), who).joinToString(" · "),
            style = Palm.title, color = Palm.ink,
            modifier = Modifier.weight(1f, fill = action != null), maxLines = 2,
        )
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Outlined.Edit, str(R.string.edit), tint = Palm.inkSoft, modifier = Modifier.size(16.dp))
        }
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            action()
        }
    }
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(2.dp).background(Palm.navy))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeGrid(onNone: (() -> Unit)? = null, exclude: Set<EventType> = emptySet(), onPick: (EventType) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 3,
    ) {
        EventType.pickable.filter { it !in exclude }.forEach { t ->
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
    if (onNone != null) {
        Spacer(Modifier.height(10.dp))
        Row { Spacer(Modifier.weight(1f)); PalmButton(str(R.string.no_type), onClick = onNone) }
    }
}

/** «Письмо»: два пути — ответить на пришедшее или написать новое. */
@Composable
private fun MailKindPicker(onReply: () -> Unit, onNew: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MailKindCard(str(R.string.mail_verb_reply), str(R.string.mailkind_reply_sub), Modifier.weight(1f), onReply)
        MailKindCard(str(R.string.mail_verb_new), str(R.string.mailkind_new_sub), Modifier.weight(1f), onNew)
    }
}

@Composable
private fun MailKindCard(title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Palm.rule, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(EventType.MAIL.icon, null, tint = EventType.MAIL.color, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(6.dp))
        Text(title, style = Palm.button, color = Palm.ink)
        Text(sub, style = Palm.small, color = Palm.inkSoft, textAlign = TextAlign.Center)
    }
}

/**
 * «Ответить» через почтовую программу: от кого письмо (человек из контактов или адрес — можно пропустить)
 * и слова для поиска. По ним письмо потом ищется в почте.
 */
@Composable
private fun ReplyFinder(
    search: suspend (String) -> List<ru.palmdate.app.data.ContactsRepository.EmailContact>,
    onDone: (name: String?, addr: String?, lookupKey: String?, words: String?) -> Unit,
) {
    var who by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<ru.palmdate.app.data.ContactsRepository.EmailContact?>(null) }
    var words by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ru.palmdate.app.data.ContactsRepository.EmailContact>>(emptyList()) }
    LaunchedEffect(who) { delay(150); results = if (who.isBlank()) emptyList() else search(who) }
    val typed = who.trim()
    val typedAddr = typed.takeIf { Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(it) }

    val c = chosen
    if (c != null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(str(R.string.mail_from_label, c.name), style = Palm.body, color = Palm.ink)
                Text(c.email, style = Palm.small, color = Palm.inkSoft)
            }
            TextButton(onClick = { chosen = null }) { Text(str(R.string.other_lc)) }
        }
    } else {
        OutlinedTextField(
            value = who, onValueChange = { who = it },
            label = { Text(str(R.string.mail_reply_from_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        if (results.isNotEmpty()) EmailList(results, Modifier.heightIn(max = 200.dp)) { chosen = it; who = "" }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = words, onValueChange = { words = it },
        label = { Text(str(R.string.mail_reply_search_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    Text(
        str(R.string.mail_reply_search_note),
        style = Palm.small, color = Palm.inkSoft, modifier = Modifier.padding(top = 4.dp),
    )
    Spacer(Modifier.height(10.dp))
    val ready = c != null || typedAddr != null || words.isNotBlank()
    Row {
        Spacer(Modifier.weight(1f))
        PalmButton(str(R.string.next), filled = ready) {
            if (!ready) return@PalmButton
            onDone(c?.name ?: typed.takeIf { typedAddr == null && it.isNotEmpty() }, c?.email ?: typedAddr, c?.lookupKey,
                words.trim().takeIf { it.isNotEmpty() })
        }
    }
}

/** Кому написать: человек с почтой из контактов или просто адрес. */
@Composable
private fun RecipientPicker(
    search: suspend (String) -> List<ru.palmdate.app.data.ContactsRepository.EmailContact>,
    onPick: (name: String?, addr: String, lookupKey: String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ru.palmdate.app.data.ContactsRepository.EmailContact>>(emptyList()) }
    LaunchedEffect(query) { delay(150); results = search(query) }
    val typed = query.trim()
    val isAddress = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(typed)
    OutlinedTextField(
        value = query, onValueChange = { query = it },
        label = { Text(str(R.string.mail_to_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    if (isAddress) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(typed, style = Palm.body, color = Palm.ink, modifier = Modifier.weight(1f), maxLines = 1)
            PalmButton(str(R.string.next), filled = true) { onPick(null, typed, null) }
        }
    }
    Spacer(Modifier.height(6.dp))
    EmailList(results, Modifier.heightIn(max = 340.dp)) { p -> onPick(p.name, p.email, p.lookupKey) }
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
        label = { Text(str(R.string.who)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
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
    Row { Spacer(Modifier.weight(1f)); PalmButton(str(R.string.no_contact), onClick = onSkip) }
}

/** Выбор номера из всех номеров контакта. Выбор запоминается для этого контакта. */
@Composable
private fun PhonePicker(phones: List<PhoneNumber>, selected: String?, onPick: (String) -> Unit) {
    Text(str(R.string.which_number), style = Palm.body, color = Palm.ink)
    Spacer(Modifier.height(8.dp))
    if (phones.isEmpty()) {
        Text(str(R.string.no_numbers), style = Palm.small, color = Palm.inkSoft)
        return
    }
    phones.forEach { p ->
        Row(
            Modifier.fillMaxWidth().height(50.dp).clickable { onPick(p.number) }.dottedRule(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(p.number, style = Palm.body, color = Palm.ink)
                Text(p.label, style = Palm.small, color = Palm.inkSoft)
            }
            if (p.number == selected) Text("✓", style = Palm.title, color = Palm.navy)
        }
    }
}

/** Как показывать название календаря: основной календарь аккаунта называется его адресом. */
internal fun CalendarInfo.displayName(): String = when {
    name == accountName && isPrimary -> str(R.string.my_calendar)
    name == accountName && local -> str(R.string.main_calendar)
    else -> name
}

/**
 * Выбор календаря. Если аккаунтов несколько — календари сгруппированы по аккаунтам.
 * Показываются все календари телефона; непригодные — серым внизу группы с пометкой почему.
 * В календари "только чтение" записать нельзя — они не нажимаются.
 */
@Composable
private fun CalendarPicker(calendars: List<CalendarInfo>, selected: Long?, onPick: (Long) -> Unit) {
    Text(str(R.string.where_to_save), style = Palm.body, color = Palm.ink)
    Spacer(Modifier.height(8.dp))
    if (calendars.none { it.writable }) {
        Text(
            str(R.string.no_writable_calendars),
            style = Palm.small, color = Palm.inkSoft,
        )
        return
    }
    val byAccount = calendars.groupBy { it.accountName }
    val multi = byAccount.size > 1
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
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
                val problem = c.problem
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 46.dp)
                        .then(if (c.writable) Modifier.clickable { onPick(c.id) } else Modifier)
                        .dottedRule()
                        .padding(start = if (multi) 8.dp else 0.dp, top = 4.dp, bottom = 4.dp)
                        .alpha(if (problem == null) 1f else 0.5f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(c.color)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.displayName(), style = Palm.body, color = Palm.ink)
                        if (problem != null) Text(problem, style = Palm.small, color = Palm.inkSoft)
                    }
                    if (c.id == selected) Text("✓", style = Palm.title, color = Palm.navy)
                }
            }
        }
        if (calendars.any { it.writable && !it.synced }) {
            item(key = "hint") {
                Text(
                    str(R.string.calendar_sync_off_hint),
                    style = Palm.small, color = Palm.inkSoft, modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/** Окно правки текста: название или заметка. */
@Composable
private fun TextEditDialog(title: String, initial: String, singleLine: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palm.paper,
        title = { Text(title, style = Palm.title, color = Palm.ink) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                singleLine = singleLine, maxLines = if (singleLine) 1 else 6,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text(str(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(R.string.cancel)) } },
    )
}

/** Окно правки времени: дата, время с шагом 15 минут, длительность. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeEditDialog(event: PalmEvent, onDismiss: () -> Unit, onSave: (LocalDateTime, Int) -> Unit) {
    var start by remember { mutableStateOf(event.start) }
    val initialMinutes = if (event.allDay) 0
    else java.time.Duration.between(event.start, event.end).toMinutes().toInt().coerceAtLeast(5)
    var minutes by remember { mutableStateOf(initialMinutes) }
    val options = (durationShortOptions() +
        (if (initialMinutes !in setOf(0, 15, 30, 60, 120)) listOf(initialMinutes to reminderLabel(initialMinutes)) else emptyList()) +
        listOf(0 to str(R.string.dur_all_day)))
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palm.paper,
        title = { Text(if (event.recurring) str(R.string.time_from_this) else str(R.string.when_label), style = Palm.title, color = Palm.ink) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepIcon(Icons.AutoMirrored.Filled.KeyboardArrowLeft) { start = start.minusDays(1) }
                    Text(start.toLocalDate().pretty(), style = Palm.body, color = Palm.ink,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    StepIcon(Icons.AutoMirrored.Filled.KeyboardArrowRight) { start = start.plusDays(1) }
                }
                if (minutes != 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepIcon(Icons.Filled.Remove) { start = start.minusMinutes(timeStep()) }
                        Text(start.format(HM), style = Palm.title, color = Palm.navy,
                            modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        StepIcon(Icons.Filled.Add) { start = start.plusMinutes(timeStep()) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(str(R.string.settings_duration), style = Palm.small, color = Palm.inkSoft)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (m, label) ->
                        Chip(label, selected = minutes == m) {
                            // Из "весь день" во время — ставим 9:00
                            if (minutes == 0 && m != 0 && start.hour == 0 && start.minute == 0) start = start.withHour(9)
                            minutes = m
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(start, minutes) }) { Text(str(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(R.string.cancel)) } },
    )
}

/** Строка адреса: тап по адресу — карты; стрелка справа — выбор из адресов контакта. */
@Composable
private fun AddressRow(address: String, onOpen: () -> Unit, onChoose: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(str(R.string.address_label), style = Palm.small, color = Palm.inkSoft, modifier = Modifier.width(80.dp))
        Text(
            address, style = Palm.body, color = Palm.navy,
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpen).padding(vertical = 6.dp),
        )
        if (onChoose != null) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onChoose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, str(R.string.other_address), tint = Palm.inkSoft) }
        }
    }
}

/** Выбор адреса из всех адресов контакта. Запоминается для контакта. */
@Composable
private fun AddressPicker(addresses: List<PhoneNumber>, selected: String?, onPick: (String) -> Unit) {
    Text(str(R.string.which_address), style = Palm.body, color = Palm.ink)
    Spacer(Modifier.height(8.dp))
    addresses.forEach { a ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable { onPick(a.number) }.dottedRule()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(a.number, style = Palm.body, color = Palm.ink)
                Text(a.label, style = Palm.small, color = Palm.inkSoft)
            }
            if (a.number == selected) Text("✓", style = Palm.title, color = Palm.navy)
        }
    }
}

/** Повтор: не повторяется, каждый день, по будням, каждую неделю/месяц/год. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatChips(rrule: String?, title: String = str(R.string.repeat_title), onChange: (String?) -> Unit) {
    val current = matchRepeat(rrule)
    Text(title, style = Palm.small, color = Palm.inkSoft)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeatOptions().forEach { (rule, label) ->
            Chip(label, selected = current == rule) { if (current != rule) onChange(rule) }
        }
        // Правило из Google, которого нет среди вариантов, — показываем, чтобы было видно, что повтор есть
        if (current == "") Chip(str(R.string.repeat_custom), selected = true) {}
    }
}

/** Напоминания: можно выбрать несколько, str(R.string.settings_without) — снять все. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReminderChips(selected: List<Int>, allDay: Boolean = false, onChange: (List<Int>) -> Unit) {
    Text(str(R.string.reminder_title), style = Palm.small, color = Palm.inkSoft)
    Spacer(Modifier.height(6.dp))
    // У событий на весь день — "в этот день в 9:00", "накануне в 18:00"; у остальных — "за N минут"
    val options: List<Pair<Int, String>> = if (allDay) {
        val st = ru.palmdate.app.data.SettingsStore.current
        (ALLDAY_REMINDER_OPTIONS + st.allDayReminders + selected).distinct().sortedBy { -it }
            .map { m ->
                m to when {
                    m <= 0 -> str(R.string.rem_allday_same_short, minutesToTime(-m))
                    m <= 1440 -> str(R.string.rem_allday_before_short, minutesToTime(1440 - m))
                    else -> allDayReminderLabel(m)
                }
            }
    } else {
        (reminderOptions() + selected.filter { m -> reminderOptions().none { it.first == m } }.map { it to reminderLabel(it) })
            .sortedBy { it.first }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(str(R.string.settings_without), selected = selected.isEmpty()) { onChange(emptyList()) }
        options.forEach { (m, label) ->
            val on = m in selected
            Chip(label, selected = on) {
                onChange(if (on) selected - m else (selected + m).sorted().take(5))
            }
        }
    }
}

@Composable
internal fun SettingRow(label: String, value: String, sub: String? = null, dot: Color? = null, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Palm.small, color = Palm.inkSoft, modifier = Modifier.width(80.dp))
        if (dot != null) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(value, style = Palm.body, color = if (onClick != null && value.endsWith("…")) Palm.navy else Palm.ink, maxLines = 1)
            if (sub != null) Text(sub, style = Palm.small, color = Palm.inkSoft, maxLines = 1)
        }
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palm.inkSoft)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhenPicker(
    type: EventType,
    phone: String?,
    onChangePhone: (() -> Unit)?,
    calendar: CalendarInfo?,
    showAccount: Boolean,
    onChangeCalendar: () -> Unit,
    start: LocalDateTime, onStart: (LocalDateTime) -> Unit,
    minutes: Int, onMinutes: (Int) -> Unit,
    reminders: List<Int>, onReminders: (List<Int>) -> Unit,
    rrule: String?, onRrule: (String?) -> Unit,
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
            StepIcon(Icons.Filled.Remove) { onStart(start.minusMinutes(timeStep())) }
            Text(start.format(HM), style = Palm.title.copy(fontSize = Palm.title.fontSize * 1.6f),
                color = Palm.navy, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            StepIcon(Icons.Filled.Add) { onStart(start.plusMinutes(timeStep())) }
        }
        Spacer(Modifier.height(10.dp))
    }

    // Быстрый выбор
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        Chip(str(R.string.chip_in_hour)) { onStart(now.plusHours(1).withMinute((now.minute / 15) * 15)) }
        Chip(str(R.string.chip_tomorrow_10)) { onStart(now.toLocalDate().plusDays(1).atTime(10, 0)) }
        Chip(str(R.string.chip_monday_10)) {
            var d = now.toLocalDate().plusDays(1)
            while (d.dayOfWeek.value != 1) d = d.plusDays(1)
            onStart(d.atTime(10, 0))
        }
    }
    Spacer(Modifier.height(12.dp))

    // Длительность
    Text(str(R.string.settings_duration), style = Palm.small, color = Palm.inkSoft)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (durationShortOptions() + (0 to str(R.string.dur_all_day))).forEach { (m, label) ->
            Chip(label, selected = minutes == m) {
                val st = ru.palmdate.app.data.SettingsStore.current
                // Переключились на "весь день" или обратно — подставляем подходящие напоминания
                if ((m == 0) != (minutes == 0)) onReminders(if (m == 0) st.allDayReminders else st.remindersFor(type))
                onMinutes(m)
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    RepeatChips(rrule, onChange = onRrule)
    Spacer(Modifier.height(12.dp))

    ReminderChips(reminders, allDay = minutes == 0, onChange = onReminders)
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = note, onValueChange = onNote,
        label = { Text(if (type == EventType.CALL) str(R.string.new_about_optional) else str(R.string.new_note_optional)) },
        modifier = Modifier.fillMaxWidth().keepAboveKeyboard(), maxLines = 3,
    )
    Spacer(Modifier.height(6.dp))

    if (onChangePhone != null) {
        SettingRow(str(R.string.number_label), phone ?: str(R.string.choose_ellipsis), onClick = onChangePhone)
    }
    SettingRow(
        str(R.string.cal_label),
        calendar?.displayName() ?: str(R.string.choose_ellipsis),
        sub = listOfNotNull(
            calendar?.accountName?.takeIf { showAccount && it != calendar.displayName() },
            calendar?.problem,
        ).joinToString(" · ").ifEmpty { null },
        dot = calendar?.let { Color(it.color) },
        onClick = onChangeCalendar,
    )

    Spacer(Modifier.height(10.dp))
    // «Готово» — вверху окна, в строке заголовка
}

@Composable
internal fun StepIcon(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).border(1.dp, Palm.navy, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Palm.navy) }
}

@Composable
internal fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
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

private enum class DetailMode { VIEW, TYPE, CONTACT, PHONE, CALENDAR, ADDRESS, PLACE_CONTACT, PLACE_ADDR }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventDetailsSheet(
    event: PalmEvent,
    searchContacts: suspend (String) -> List<ContactRef>,
    phonesFor: PhonesFor,
    loadReminders: suspend (Long) -> List<Int>,
    loadCalendars: suspend () -> List<CalendarInfo>,
    onMove: (Long) -> Unit,
    onSetReminders: (List<Int>) -> Unit,
    onSetLink: (EventType?, ContactRef?) -> Unit,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    onOpen: () -> Unit,
    onDelete: (ru.palmdate.app.model.DeleteScope) -> Unit,
    onHistory: (() -> Unit)? = null,
    onOutcome: (Outcome?, String?) -> Unit = { _, _ -> },
    onFollowUp: (LocalDateTime) -> Unit = {},
    onRepeat: (String?) -> Unit = {},
    addressesFor: suspend (String) -> List<PhoneNumber> = { emptyList() },
    onPickAddress: (String) -> Unit = {},
    onEditTitle: (String) -> Unit = {},
    onEditTime: (LocalDateTime, Int) -> Unit = { _, _ -> },
    onEditNote: (String?) -> Unit = {},
    loadPlace: suspend (Long) -> String? = { null },
    onSetPlace: (String?) -> Unit = {},
    loadFiles: suspend (Long) -> List<ru.palmdate.app.data.TripFile> = { emptyList() },
    onAddFile: suspend (Long, android.net.Uri, String) -> Unit = { _, _, _ -> },
    onDeleteFile: suspend (ru.palmdate.app.data.TripFile) -> Unit = {},
    asPane: Boolean = false, // на широком экране — колонка справа, а не панель снизу
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(DetailMode.VIEW) }
    var pendingType by remember { mutableStateOf<EventType?>(null) }
    var pendingContact by remember { mutableStateOf<ContactRef?>(null) }
    var phones by remember { mutableStateOf<List<PhoneNumber>>(emptyList()) }
    var addresses by remember { mutableStateOf<List<PhoneNumber>>(emptyList()) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var reminders by remember { mutableStateOf<List<Int>?>(null) }
    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var moveTo by remember { mutableStateOf<CalendarInfo?>(null) }
    // Выезд: адрес и документы
    var place by remember(event.eventId) { mutableStateOf<String?>(null) }
    var files by remember(event.eventId) { mutableStateOf<List<ru.palmdate.app.data.TripFile>>(emptyList()) }
    var editPlace by remember { mutableStateOf(false) }
    LaunchedEffect(event.eventId) {
        reminders = loadReminders(event.eventId)
        calendars = loadCalendars()
        if (event.type == EventType.TRIP) {
            place = loadPlace(event.eventId)
            files = loadFiles(event.eventId)
        }
    }
    // Перенести можно, только если календарь события доступен для записи и есть куда переносить
    // Править название, время и заметку можно, если календарь события доступен для записи
    val editable = !event.fromContacts && calendars.any { it.id == event.calendarId && it.writable }
    var askDelete by remember { mutableStateOf(false) }
    if (askDelete) {
        DeleteDialog(event, onDismiss = { askDelete = false }) { scope -> askDelete = false; onDelete(scope) }
    }
    var editTitle by remember { mutableStateOf(false) }
    var editTime by remember { mutableStateOf(false) }
    var editNote by remember { mutableStateOf(false) }
    if (editTitle) {
        TextEditDialog(str(R.string.title_label), event.shortTitle, singleLine = true, onDismiss = { editTitle = false }) {
            onEditTitle(it); editTitle = false
        }
    }
    if (editNote) {
        TextEditDialog(str(R.string.note_label), event.note ?: "", singleLine = false, onDismiss = { editNote = false }) {
            onEditNote(it); editNote = false
        }
    }
    if (editPlace) {
        TextEditDialog(str(R.string.address_label), place ?: "", singleLine = true, onDismiss = { editPlace = false }) { v ->
            val new = v.takeIf { it.isNotBlank() }
            onSetPlace(new); place = new; editPlace = false
        }
    }
    if (editTime) {
        TimeEditDialog(event, onDismiss = { editTime = false }) { start, minutes ->
            onEditTime(start, minutes); editTime = false
        }
    }
    val canMove = !event.fromContacts && calendars.count { it.writable } > 1 &&
        calendars.any { it.id == event.calendarId && it.writable }

    moveTo?.let { target ->
        AlertDialog(
            onDismissRequest = { moveTo = null },
            title = { Text(str(R.string.move_to_title, target.name)) },
            text = {
                Text(
                    if (event.recurring) str(R.string.move_series_note)
                    else str(R.string.move_single_note),
                )
            },
            confirmButton = { TextButton(onClick = { onMove(target.id); moveTo = null }) { Text(str(R.string.move)) } },
            dismissButton = { TextButton(onClick = { moveTo = null }) { Text(str(R.string.cancel)) } },
        )
    }

    /** Контакт выбран (при назначении типа): для звонка — разобраться с номером. */
    fun applyContact(type: EventType, c: ContactRef?) {
        if (c == null || type != EventType.CALL) {
            onSetLink(type, c); mode = DetailMode.VIEW; return
        }
        scope.launch {
            val (list, chosen) = pickPhone(c, phonesFor)
            phones = list
            if (chosen != null || list.isEmpty()) {
                onSetLink(type, c.copy(phone = chosen)); mode = DetailMode.VIEW
            } else {
                pendingType = type; pendingContact = c; mode = DetailMode.PHONE
            }
        }
    }

    val body: @Composable () -> Unit = {
        // Всегда на всю высоту экрана: когда в итоге появляются поля и кнопки, окно не прыгает
        Column(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
            // Тип · название (у событий с контактом — имя). Тап: правка названия или выбор контакта
            val isMail = event.type == EventType.MAIL
            SheetTitle(
                event.type,
                if (isMail) event.shortTitle.takeIf { it.isNotBlank() }
                else event.contact?.name ?: event.shortTitle.takeIf { it.isNotBlank() },
                if (isMail) event.mail?.kind?.verb ?: str(R.string.type_mail) else event.typeLabel ?: str(R.string.type_other),
                action = { PalmButton(str(R.string.close), filled = true, onClick = onDismiss) },
                onClick = if (!editable) null else ({
                    if (!isMail && event.contact != null && event.type != null) {
                        pendingType = event.type; mode = DetailMode.CONTACT
                    } else editTitle = true
                }),
            )
            Spacer(Modifier.height(12.dp))

            when (mode) {
                // Письмом существующее событие не сделать — письмо выбирается при создании
                DetailMode.TYPE -> TypeGrid(
                    onNone = { onSetLink(null, null); mode = DetailMode.VIEW },
                    exclude = setOf(EventType.MAIL),
                ) { t ->
                    pendingType = t
                    if (t.needsContact) mode = DetailMode.CONTACT else applyContact(t, null)
                }

                DetailMode.CONTACT -> ContactPicker(
                    search = searchContacts,
                    onPick = { applyContact(pendingType!!, it) },
                    onSkip = { applyContact(pendingType!!, null) },
                )

                DetailMode.PHONE -> PhonePicker(phones, selected = (pendingContact ?: event.contact)?.phone) { number ->
                    val c = (pendingContact ?: event.contact)!!.copy(phone = number)
                    onSetLink(pendingType ?: event.type, c)
                    pendingContact = null; pendingType = null
                    mode = DetailMode.VIEW
                }

                DetailMode.ADDRESS -> AddressPicker(addresses, selected = event.contact?.address) { addr ->
                    onPickAddress(addr)
                    mode = DetailMode.VIEW
                }

                // Выезд: человек → его адрес → в «Место»
                DetailMode.PLACE_CONTACT -> ContactPicker(
                    search = searchContacts,
                    onPick = { c ->
                        scope.launch {
                            addresses = addressesFor(c.lookupKey)
                            mode = if (addresses.isEmpty()) DetailMode.VIEW else DetailMode.PLACE_ADDR
                        }
                    },
                    onSkip = { mode = DetailMode.VIEW },
                )

                DetailMode.PLACE_ADDR -> AddressPicker(addresses, selected = place) { addr ->
                    onSetPlace(addr); place = addr
                    mode = DetailMode.VIEW
                }

                DetailMode.CALENDAR -> CalendarPicker(calendars, selected = event.calendarId) { id ->
                    mode = DetailMode.VIEW
                    if (id != event.calendarId) moveTo = calendars.firstOrNull { it.id == id }
                }

                DetailMode.VIEW -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    val whenText = if (event.allDay) str(R.string.date_all_day, event.start.toLocalDate().pretty())
                    else event.start.toLocalDate().pretty() + ", " + event.start.format(HM) + "–" + event.end.format(HM)
                    if (editable) SettingRow(str(R.string.when_label), whenText, onClick = { editTime = true })
                    else DetailLine(str(R.string.when_label), whenText)
                    if (event.calendarName.isNotEmpty()) {
                        SettingRow(
                            str(R.string.cal_label),
                            if (event.calendarName == event.accountName) str(R.string.my_calendar) else event.calendarName,
                            sub = event.accountName.takeIf { it.isNotEmpty() && it != event.calendarName },
                            dot = Color(event.color),
                            onClick = if (canMove) ({ mode = DetailMode.CALENDAR }) else null,
                        )
                    }
                    // Письмо: какое (ответить) или кому (написать); тап — открыть
                    event.mail?.takeIf { isMail }?.let { m ->
                        if (m.kind == ru.palmdate.app.model.MailKind.REPLY) {
                            SettingRow(
                                str(R.string.type_mail), m.subject ?: str(R.string.mail_no_subject),
                                sub = listOfNotNull(m.peer?.let { str(R.string.mail_from, it) }, m.date?.let { longDate(it) }).joinToString(" · ").takeIf { it.isNotEmpty() },
                                onClick = onAction,
                            )
                        } else {
                            SettingRow(str(R.string.mail_to_label), m.peer ?: "—", sub = m.peerAddr?.takeIf { it != m.peer }, onClick = onAction)
                        }
                        if (m.hasDraft) DetailLine(str(R.string.draft_cap), str(R.string.draft_saved_note))
                    }
                    event.contact?.takeIf { !isMail }?.let { c ->
                        // Номер можно сменить из списка номеров контакта
                        SettingRow(str(R.string.call_phone), c.phone ?: str(R.string.no_number), onClick = {
                            scope.launch {
                                phones = phonesFor(c.lookupKey).first
                                pendingType = event.type; pendingContact = null
                                mode = DetailMode.PHONE
                            }
                        })
                        // Запомненный способ связи ("всегда так для этого человека") — можно сбросить
                        val callPrefs = remember { ru.palmdate.app.data.CallOptions(ctx) }
                        var callVia by remember(c.lookupKey) { mutableStateOf<String?>(null) }
                        LaunchedEffect(c.lookupKey) {
                            callVia = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                callPrefs.rememberedLabel(c.lookupKey)
                            }
                        }
                        callVia?.let { label ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(str(R.string.contact_via), style = Palm.small, color = Palm.inkSoft, modifier = Modifier.width(80.dp))
                                Text(label, style = Palm.body, color = Palm.ink, modifier = Modifier.weight(1f), maxLines = 2)
                                TextButton(onClick = { callPrefs.remember(c.lookupKey, null); callVia = null }) { Text(str(R.string.settings_reset)) }
                            }
                        }
                        // Адрес: тап — открыть в картах; стрелка справа — выбрать другой адрес контакта
                        var addrCount by remember(c.lookupKey) { mutableStateOf(0) }
                        LaunchedEffect(c.lookupKey) { addrCount = addressesFor(c.lookupKey).size }
                        c.address?.let { addr ->
                            AddressRow(
                                address = addr,
                                onOpen = { ctx.openMap(addr) },
                                onChoose = if (addrCount > 1) ({
                                    scope.launch { addresses = addressesFor(c.lookupKey); mode = DetailMode.ADDRESS }
                                }) else null,
                            )
                        }
                    }
                    if (editable) SettingRow(str(R.string.note_label), event.note ?: str(R.string.add_ellipsis), onClick = { editNote = true })
                    else event.note?.let { DetailLine(str(R.string.note_label), it) }

                    // Выезд: адрес (можно построить маршрут) и документы
                    if (event.type == EventType.TRIP && !event.fromContacts) {
                        val placeText = place
                        if (editable) SettingRow(str(R.string.place_label), placeText ?: str(R.string.add_address), onClick = { editPlace = true })
                        else placeText?.let { DetailLine(str(R.string.place_label), it) }
                        placeText?.let { addr ->
                            PalmButton(str(R.string.action_route), compact = true, onClick = { ctx.openMap(addr) })
                        }
                        if (editable) SettingRow(str(R.string.address_from_contact), str(R.string.choose_person), onClick = { mode = DetailMode.PLACE_CONTACT })
                        TripFilesSection(
                            files = files,
                            onAdd = { label, uris ->
                                scope.launch {
                                    uris.forEach { onAddFile(event.eventId, it, label) }
                                    files = loadFiles(event.eventId)
                                }
                            },
                            onOpen = { f ->
                                runCatching {
                                    ctx.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW)
                                            .setDataAndType(ru.palmdate.app.data.TripFiles.uriFor(ctx, f), f.mime ?: "*/*")
                                            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                    )
                                }
                            },
                            onDelete = { f ->
                                scope.launch {
                                    onDeleteFile(f)
                                    files = loadFiles(event.eventId)
                                }
                            },
                        )
                    }

                    // Главные действия — сразу под данными события, до итога
                    val action = primaryActionLabel(event)
                    if (action != null || onHistory != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            action?.let { PalmButton(it, filled = true, onClick = onAction) }
                            onHistory?.let { PalmButton(str(R.string.history_title), onClick = it) }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    OutcomeSection(event, onOutcome, onFollowUp)

                    // Повтор — только если календарь события доступен для записи
                    var repeat by remember(event.eventId) { mutableStateOf(event.rrule) }
                    if (!event.fromContacts && calendars.any { it.id == event.calendarId && it.writable }) {
                        Spacer(Modifier.height(10.dp))
                        RepeatChips(repeat, title = if (event.recurring) str(R.string.repeat_from_this) else str(R.string.repeat_title)) { rule ->
                            repeat = rule
                            onRepeat(rule)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    if (!event.fromContacts) reminders?.let { r ->
                        ReminderChips(r, allDay = event.allDay) { new ->
                            reminders = new
                            onSetReminders(new)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // День рождения из карточки контакта — не событие календаря: менять и удалять нечего
                        if (!event.fromContacts) {
                            PalmButton(if (event.type == null) str(R.string.assign_type) else str(R.string.type_and_contact)) { mode = DetailMode.TYPE }
                            PalmButton(str(R.string.in_calendar), onClick = onOpen)
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).clickable { askDelete = true },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.Delete, str(R.string.delete), tint = Palm.nowLine) }
                        }
                    }
                }
            }
        }
    }
    if (asPane) {
        androidx.activity.compose.BackHandler(onBack = onDismiss)
        Column(
            Modifier.fillMaxSize().background(Palm.paper)
                .statusBarsPadding().navigationBarsPadding().imePadding().padding(top = 14.dp),
        ) { body() }
    } else {
        PalmSheet(onDismissRequest = onDismiss, fixedHeight = true) { body() }
    }
}

/**
 * Подтверждение удаления. Обычное событие — «Удалить?»; повторяющееся — как в Google:
 * только этот раз, этот и все следующие, вся серия.
 */
@Composable
private fun DeleteDialog(event: PalmEvent, onDismiss: () -> Unit, onDelete: (ru.palmdate.app.model.DeleteScope) -> Unit) {
    val name = event.title.ifBlank { str(R.string.type_other) }
    if (!event.recurring) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Palm.paper,
            title = { Text(str(R.string.delete_event_q), style = Palm.title, color = Palm.ink) },
            text = { Text(str(R.string.delete_event_msg, name), style = Palm.body, color = Palm.ink) },
            confirmButton = {
                TextButton(onClick = { onDelete(ru.palmdate.app.model.DeleteScope.ALL) }) { Text(str(R.string.delete), color = Palm.nowLine) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(str(R.string.cancel)) } },
        )
        return
    }
    var choice by remember { mutableStateOf(ru.palmdate.app.model.DeleteScope.ONE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palm.paper,
        title = { Text(str(R.string.delete_recurring), style = Palm.title, color = Palm.ink) },
        text = {
            Column {
                Text("«$name»", style = Palm.body, color = Palm.ink)
                Spacer(Modifier.height(8.dp))
                listOf(
                    ru.palmdate.app.model.DeleteScope.ONE to str(R.string.delete_only_this),
                    ru.palmdate.app.model.DeleteScope.FOLLOWING to str(R.string.delete_this_following),
                    ru.palmdate.app.model.DeleteScope.ALL to str(R.string.delete_whole_series),
                ).forEach { (v, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { choice = v }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = choice == v, onClick = { choice = v })
                        Text(label, style = Palm.body, color = Palm.ink)
                    }
                }
                if (choice == ru.palmdate.app.model.DeleteScope.ALL) {
                    Text(
                        str(R.string.delete_series_note),
                        style = Palm.small, color = Palm.nowLine, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDelete(choice) }) { Text(str(R.string.delete), color = Palm.nowLine) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(R.string.cancel)) } },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).dottedRule().padding(bottom = 8.dp)) {
        Text(label, style = Palm.small, color = Palm.inkSoft, modifier = Modifier.width(80.dp))
        Text(value, style = Palm.body, color = Palm.ink, modifier = Modifier.weight(1f))
    }
}

/* ---------- Итог события ---------- */

/**
 * Итог: что произошло на самом деле. Повторный тап по выбранному варианту снимает итог.
 * После "не дозвонился" — быстрые кнопки перезвонить, после "перенесено" — выбор нового времени.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OutcomeSection(
    event: PalmEvent,
    onOutcome: (Outcome?, String?) -> Unit,
    onFollowUp: (LocalDateTime) -> Unit,
) {
    var note by remember(event.eventId, event.instanceStart) { mutableStateOf(event.outcomeNote ?: "") }
    var created by remember(event.eventId, event.instanceStart) { mutableStateOf<LocalDateTime?>(null) }
    val current = event.outcome

    Text(str(R.string.outcome_title), style = Palm.small, color = Palm.inkSoft)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Outcome.optionsFor(event.type).forEach { o ->
            Chip(o.label(event.type), selected = current == o) {
                created = null
                onOutcome(if (current == o) null else o, note)
            }
        }
    }

    if (current != null) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text(str(R.string.outcome_note_hint)) },
                modifier = Modifier.weight(1f).keepAboveKeyboard(), maxLines = 3,
            )
            if (note != (event.outcomeNote ?: "")) {
                Spacer(Modifier.width(8.dp))
                PalmButton("OK", filled = true) { onOutcome(current, note) }
            }
        }
    }

    // Следующий шаг: перезвонить / новое время
    val now = LocalDateTime.now().withSecond(0).withNano(0)
    val nextHour = now.plusHours(1).withMinute((now.minute / 15) * 15)
    val tomorrow10 = now.toLocalDate().plusDays(1).atTime(10, 0)
    when {
        created != null -> {
            Spacer(Modifier.height(8.dp))
            Text(
                str(R.string.created_at, created!!.toLocalDate().pretty() + ", " + created!!.format(HM)),
                style = Palm.small, color = Palm.navy,
            )
        }
        current == Outcome.NO_ANSWER && event.type == EventType.CALL -> {
            Spacer(Modifier.height(8.dp))
            Text(str(R.string.call_back), style = Palm.small, color = Palm.inkSoft)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(str(R.string.chip_in_hour)) { onFollowUp(nextHour); created = nextHour }
                Chip(str(R.string.chip_tomorrow_10)) { onFollowUp(tomorrow10); created = tomorrow10 }
            }
        }
        current == Outcome.RESCHEDULED -> {
            // Новое время: по умолчанию тот же час на следующий день
            var start by remember(event.eventId, event.instanceStart) {
                mutableStateOf(maxOf(event.start.plusDays(1), now.plusHours(1)).withSecond(0).withNano(0))
            }
            Spacer(Modifier.height(8.dp))
            Text(str(R.string.new_time), style = Palm.small, color = Palm.inkSoft)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepIcon(Icons.AutoMirrored.Filled.KeyboardArrowLeft) { start = start.minusDays(1) }
                Text(start.toLocalDate().pretty(), style = Palm.body, color = Palm.ink,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                StepIcon(Icons.AutoMirrored.Filled.KeyboardArrowRight) { start = start.plusDays(1) }
            }
            if (!event.allDay) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepIcon(Icons.Filled.Remove) { start = start.minusMinutes(timeStep()) }
                    Text(start.format(HM), style = Palm.title, color = Palm.navy,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    StepIcon(Icons.Filled.Add) { start = start.plusMinutes(timeStep()) }
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(str(R.string.chip_tomorrow_10)) { start = tomorrow10 }
                Chip(str(R.string.chip_in_week)) { start = event.start.plusWeeks(1) }
                PalmButton(str(R.string.create_for_time), filled = true) { onFollowUp(start); created = start }
            }
        }
    }
}

/** Быстрые варианты длительности: 15 мин, 30 мин, 1 ч, 2 ч. */
private fun durationShortOptions(): List<Pair<Int, String>> = listOf(
    15 to str(R.string.rem_min, 15), 30 to str(R.string.rem_min, 30),
    60 to str(R.string.rem_hour, 1), 120 to str(R.string.rem_hour, 2),
)

/** Шаг времени в окнах — из настроек (15 или 30 минут). */
private fun timeStep(): Long = ru.palmdate.app.data.SettingsStore.current.timeStep.toLong()

/** Подписи документов выезда: билет, посадочный, бронь… */
private val TRIP_LABEL_RES = listOf(
    R.string.trip_label_ticket, R.string.trip_label_boarding, R.string.trip_label_booking, R.string.trip_label_document,
)

private fun tripLabels(): List<String> = TRIP_LABEL_RES.map { str(it) }

/** Подпись документа на выбранном языке (она могла быть сохранена на другом). */
private fun tripLabelShown(stored: String): String =
    TRIP_LABEL_RES.firstOrNull { id -> ru.palmdate.app.Lang.TAGS.any { ru.palmdate.app.Lang.strIn(it, id) == stored } }
        ?.let { str(it) } ?: stored

/** Документы выезда: список, выбор подписи и добавление файлов. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TripFilesSection(
    files: List<ru.palmdate.app.data.TripFile>,
    onAdd: (String, List<android.net.Uri>) -> Unit,
    onOpen: (ru.palmdate.app.data.TripFile) -> Unit,
    onDelete: (ru.palmdate.app.data.TripFile) -> Unit,
) {
    var label by remember { mutableStateOf(tripLabels().first()) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) onAdd(label, uris) }

    Text(str(R.string.documents), style = Palm.small, color = Palm.inkSoft, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    if (files.isEmpty()) {
        Text(str(R.string.documents_empty), style = Palm.small, color = Palm.inkSoft)
    }
    files.forEach { f ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                tripLabelShown(f.label), style = Palm.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = Palm.navy, modifier = Modifier.width(96.dp),
            )
            Text(
                f.name, style = Palm.body, color = Palm.ink, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable { onOpen(f) },
            )
            TextButton(onClick = { onDelete(f) }) { Text(str(R.string.delete_lc)) }
        }
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tripLabels().forEach { l ->
            val sel = l == label
            Text(
                l,
                style = Palm.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = if (sel) Color.White else Palm.navy,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (sel) Palm.navy else Color.Transparent)
                    .clickable { label = l }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    PalmButton(str(R.string.add_file), onClick = { picker.launch(arrayOf("*/*")) })
}

