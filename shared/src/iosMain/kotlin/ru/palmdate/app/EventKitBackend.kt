@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package ru.palmdate.app

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import platform.Contacts.CNContact
import platform.Contacts.CNEntityType
import platform.Contacts.CNContactPhoneNumbersKey
import platform.Contacts.CNContactGivenNameKey
import platform.Contacts.CNContactFamilyNameKey
import platform.Contacts.CNContactOrganizationNameKey
import platform.Contacts.CNContactPostalAddressesKey
import platform.Contacts.CNContactStore
import platform.Contacts.CNLabeledValue
import platform.Contacts.CNPhoneNumber
import platform.Contacts.CNPostalAddress
import platform.Contacts.predicateForContactsInContainerWithIdentifier
import platform.Contacts.predicateForContactsMatchingName
import platform.EventKit.EKAlarm
import platform.EventKit.EKCalendar
import platform.EventKit.EKEntityType
import platform.EventKit.EKSpan
import platform.EventKit.EKRecurrenceFrequency
import platform.EventKit.EKCalendarType
import platform.EventKit.EKSourceType
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKit.EKEventStoreChangedNotification
import platform.EventKit.EKRecurrenceDayOfWeek
import platform.EventKit.EKRecurrenceEnd
import platform.EventKit.EKRecurrenceRule
import platform.Foundation.NSDate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIColor
import ru.palmdate.app.model.CalendarInfo
import ru.palmdate.app.model.ContactRef
import ru.palmdate.app.model.ContactStat
import ru.palmdate.app.model.EmailContact
import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.L10n
import ru.palmdate.app.model.MailInfo
import ru.palmdate.app.model.MailKind
import ru.palmdate.app.model.NewEvent
import ru.palmdate.app.model.Outcome
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.model.PhoneNumber
import ru.palmdate.app.data.CurrentSettings
import ru.palmdate.app.time.*
import ru.palmdate.app.ui.TripDoc

/**
 * Настоящий календарь и контакты iPhone: события — через EventKit, люди — через Contacts.
 * Тип события, человек и итог пишутся в заметку события («[palm:t=CALL;c=…]», «Итог: …») —
 * ровно так же, как делает Android-версия, поэтому всё восстанавливается.
 */
class EventKitBackend : AppBackend {
    private val store = EKEventStore()
    private val contacts = CNContactStore()
    private val tz get() = TimeZone.currentSystemDefault()
    private val idMap = HashMap<Long, String>()
    private val calMap = HashMap<Long, EKCalendar>()
    private val contactCache = HashMap<String, ContactRef?>()
    private val colorCache = HashMap<String, Int>()
    private val listeners = mutableListOf<() -> Unit>()

    init {
        requestAccess()
        NSNotificationCenter.defaultCenter.addObserverForName(EKEventStoreChangedNotification, null, NSOperationQueue.mainQueue) { _ ->
            listeners.toList().forEach { it() }
        }
    }

    private var authorized = false

    private fun requestAccess() {
        val done: (Boolean, platform.Foundation.NSError?) -> Unit = { ok, _ ->
            authorized = ok
            if (ok) platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) { listeners.toList().forEach { it() } }
        }
        if (platform.Foundation.NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion } >= 17L) {
            store.requestFullAccessToEventsWithCompletion(done)
        } else {
            store.requestAccessToEntityType(EKEntityType.EKEntityTypeEvent, done)
        }
        contacts.requestAccessForEntityType(CNEntityType.CNEntityTypeContacts) { _, _ -> }
    }

    /* ---------- даты и id ---------- */

    private fun LocalDateTime.toNs(): NSDate = NSDate.dateWithTimeIntervalSince1970(toInstant(tz).epochSeconds.toDouble())
    private fun NSDate.toLdt(): LocalDateTime = Instant.fromEpochSeconds(timeIntervalSince1970.toLong()).toLocalDateTime(tz)

    private fun hid(s: String): Long {
        var h = 1125899906842597L
        for (c in s) h = 31 * h + c.code
        return h and Long.MAX_VALUE
    }

    private fun idOf(ev: EKEvent): Long {
        val s = ev.eventIdentifier ?: ev.calendarItemIdentifier
        return hid(s).also { idMap[it] = s }
    }

    private fun master(id: Long): EKEvent? = idMap[id]?.let { store.eventWithIdentifier(it) }

    private fun occurrence(e: PalmEvent): EKEvent? {
        val ident = idMap[e.eventId] ?: return null
        val day = e.start.date
        val hit = fetch(day, day.plusDays(1)).firstOrNull { (it.eventIdentifier ?: it.calendarItemIdentifier) == ident && it.startDate!!.toLdt() == e.start }
        return hit ?: store.eventWithIdentifier(ident)
    }

    /** Именно этот раз серии (без подмены первым разом, если не нашли). */
    private fun exactOccurrence(e: PalmEvent): EKEvent? {
        val ident = idMap[e.eventId] ?: return null
        val day = e.start.date
        return fetch(day, day.plusDays(1)).firstOrNull {
            (it.eventIdentifier ?: it.calendarItemIdentifier) == ident &&
                it.startDate!!.timeIntervalSince1970.toLong() * 1000 == e.instanceStart
        }
    }

    private fun colorOf(cal: EKCalendar?): Int {
        val cg = cal?.CGColor ?: return 0xFF4285F4.toInt()
        val key = cal.calendarIdentifier
        colorCache[key]?.let { return it }
        val c = memScoped {
            val r = alloc<DoubleVar>(); val g = alloc<DoubleVar>(); val b = alloc<DoubleVar>(); val a = alloc<DoubleVar>()
            UIColor.colorWithCGColor(cg).getRed(r.ptr, g.ptr, b.ptr, a.ptr)
            fun ch(v: Double) = (v.coerceIn(0.0, 1.0) * 255 + 0.5).toInt()
            (0xFF shl 24) or (ch(r.value) shl 16) or (ch(g.value) shl 8) or ch(b.value)
        }
        colorCache[key] = c
        return c
    }

    /* ---------- заметка: метка типа/контакта и строка итога ---------- */

    private val markerRe = Regex("""\[palm:t=(\w+)(?:;c=([^;\]]*))?(?:;p=([^;\]]*))?(?:;m=([^;\]]*))?]""")
    private val outcomeRe = Regex("""(?m)^(?:Итог|Result): ([^\n—]+?)(?: — ([^\n]*))?\s*$""")

    private fun enc(s: String): String = buildString {
        for (b in s.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch.isLetterOrDigit() && c < 128 || ch in "-_.~") append(ch)
            else append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
        }
    }

    private fun dec(s: String): String {
        val out = ArrayList<Byte>()
        var i = 0
        val src = s.encodeToByteArray()
        while (i < src.size) {
            val c = src[i].toInt().toChar()
            if (c == '%' && i + 2 < src.size) {
                val v = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (v != null) { out += v.toByte(); i += 3; continue }
            }
            out += src[i]; i++
        }
        return out.toByteArray().decodeToString()
    }

    private class Parsed(val type: EventType, val key: String?, val phone: String?, val mail: String? = null)

    private fun parseMarker(notes: String?): Parsed? {
        val m = notes?.let { markerRe.find(it) } ?: return null
        val t = EventType.parse(m.groupValues[1]) ?: return null
        fun g(i: Int) = m.groupValues[i].takeIf { it.isNotEmpty() }?.let { dec(it) }
        return Parsed(t, g(2), g(3), g(4))
    }

    private fun makeMarker(type: EventType, key: String?, phone: String?, mail: String? = null) =
        "[palm:t=${type.name}" + (key?.let { ";c=" + enc(it) } ?: "") + (phone?.let { ";p=" + enc(it) } ?: "") +
            (mail?.let { ";m=" + enc(it) } ?: "") + "]"

    /** Письмо в метке: "N|адрес" — написать; "R|адрес|имя" — ответить (что искать в почте). */
    private fun mailMarker(m: MailInfo?): String? {
        fun clean(x: String?) = x?.replace("|", " ")?.trim().orEmpty()
        return when (m?.kind) {
            null -> null
            MailKind.NEW -> m.peerAddr?.let { "N|" + clean(it) }
            MailKind.REPLY -> "R|" + clean(m.peerAddr) + "|" + clean(m.peerName)
        }
    }

    private fun mailFromMarker(raw: String?, title: String): MailInfo? {
        if (raw == null || raw.length < 2 || raw[1] != '|') return null
        val parts = raw.substring(2).split("|")
        return when (raw[0]) {
            'N' -> MailInfo(MailKind.NEW, peerAddr = parts.getOrNull(0)?.takeIf { it.isNotBlank() })
            'R' -> MailInfo(
                MailKind.REPLY,
                subject = title.substringAfter(": ", "").takeIf { it.isNotBlank() },
                peerAddr = parts.getOrNull(0)?.takeIf { it.isNotBlank() },
                peerName = parts.getOrNull(1)?.takeIf { it.isNotBlank() },
            )
            else -> null
        }
    }

    private fun outcomeLine(o: Outcome, type: EventType?, note: String?) =
        L10n.str("outcome_line_prefix") + o.label(type) + (note?.trim()?.takeIf { it.isNotEmpty() }?.let { " — $it" } ?: "")

    private fun body(notes: String?): String? =
        notes?.replace(markerRe, "")?.replace(outcomeRe, "")?.trim()?.takeIf { it.isNotEmpty() }

    private fun compose(outcome: String?, body: String?, marker: String?): String =
        listOfNotNull(outcome, body?.takeIf { it.isNotBlank() }, marker).joinToString("\n")

    private fun markerOf(notes: String?) = notes?.let { markerRe.find(it)?.value }
    private fun outcomeOf(notes: String?) = notes?.let { outcomeRe.find(it)?.value?.trim() }

    /* ---------- события ---------- */

    private fun fetch(from: LocalDate, to: LocalDate): List<EKEvent> {
        if (!authorized) return emptyList()
        val out = ArrayList<EKEvent>()
        var s = from
        while (s < to) {
            val e = minOf(s.plusDays(1400), to)
            val p = store.predicateForEventsWithStartDate(s.atStartOfDay().toNs(), e.atStartOfDay().toNs(), null)
            out += store.eventsMatchingPredicate(p).orEmpty().filterIsInstance<EKEvent>()
            s = e
        }
        return out
    }

    private fun contactRef(key: String, fallbackName: String, phone: String?): ContactRef {
        val c = contactCache.getOrPut(key) { loadContact(key) }
        if (c != null) return c.copy(phone = phone ?: c.phone)
        // Ключ контакта мог измениться (новый телефон): ищем по номеру из метки события
        phone?.let { contactByPhone(it) }?.let { return it.copy(phone = phone) }
        return ContactRef(key, fallbackName, phone)
    }

    /** Цифры номера, последние 10 — чтобы «+7 999…» и «8 999…» совпадали. */
    private fun phoneKey(s: String?): String = s.orEmpty().filter { it.isDigit() }.takeLast(10)

    private var phoneIndex: Map<String, ContactRef>? = null

    /** Контакт по номеру телефона (запасной поиск, когда ключ контакта изменился). */
    private fun contactByPhone(phone: String): ContactRef? {
        val key = phoneKey(phone)
        if (key.length < 7) return null
        val index = phoneIndex ?: HashMap<String, ContactRef>().also { m ->
            allContacts().forEach { c ->
                val ref = toRef(c)
                c.phoneNumbers.filterIsInstance<CNLabeledValue>().forEach { lv ->
                    val k = phoneKey((lv.value as? CNPhoneNumber)?.stringValue)
                    if (k.isNotEmpty() && !m.containsKey(k)) m[k] = ref
                }
            }
            phoneIndex = m
        }
        return index[key]
    }

    private fun toPalm(ev: EKEvent): PalmEvent {
        val id = idOf(ev)
        val notes = ev.notes
        val mk = parseMarker(notes)
        val birthday = ev.calendar?.type == EKCalendarType.EKCalendarTypeBirthday
        val title = ev.title ?: ""
        val start = ev.startDate!!.toLdt()
        val allDay = ev.allDay
        val e0 = ev.endDate!!.toLdt()
        val end = when {
            allDay && !(e0.time == LocalTime(0, 0) && e0 > start) -> e0.date.plusDays(1).atStartOfDay()
            e0 < start -> start
            else -> e0
        }
        val cal = ev.calendar
        cal?.let { calMap[hid(it.calendarIdentifier)] = it }
        // У повторяющегося итог есть только у отдельно сохранённого раза; строка итога в самой серии
        // (так было до исправления — один итог на все разы) не показывается.
        val oc = if (ev.hasRecurrenceRules && !ev.isDetached) null else outcomeRe.find(notes ?: "")
        val outcome = oc?.let { Outcome.fromLabel(it.groupValues[1]) }
        val fallbackName = title.substringAfter(": ", title)
        return PalmEvent(
            eventId = id,
            title = title,
            start = start,
            end = end,
            allDay = allDay,
            type = if (birthday) EventType.BIRTHDAY else mk?.type,
            contact = mk?.key?.let { contactRef(it, fallbackName, mk.phone) },
            note = body(notes),
            color = colorOf(cal),
            calendarName = cal?.title ?: "",
            accountName = cal?.source?.title ?: "",
            calendarId = cal?.let { hid(it.calendarIdentifier) } ?: -1,
            recurring = ev.hasRecurrenceRules,
            instanceStart = ev.startDate!!.timeIntervalSince1970.toLong() * 1000,
            outcome = outcome,
            outcomeNote = oc?.groupValues?.get(2)?.takeIf { it.isNotBlank() && outcome != null },
            fromContacts = birthday,
            rrule = ruleOf(ev),
            place = ev.location?.takeIf { it.isNotBlank() },
            hasAlarm = if (birthday) null else !ev.alarms.isNullOrEmpty(),
            hasFiles = !birthday && ru.palmdate.app.ui.IosFiles.has(id),
            mail = if (mk?.type == EventType.MAIL) mailFromMarker(mk.mail, title) else null,
        )
    }

    private fun visible(p: PalmEvent): Boolean =
        p.calendarId !in hiddenCalendars && (!p.fromContacts || CurrentSettings.current.birthdaysShown)

    override var hiddenCalendars: Set<Long> = emptySet()

    override suspend fun eventsBetween(from: LocalDate, toExclusive: LocalDate): List<PalmEvent> =
        fetch(from, toExclusive).map { toPalm(it) }.filter { visible(it) }.sortedBy { it.start }

    override fun daysWithEvents(from: LocalDate, toExclusive: LocalDate): Set<LocalDate> =
        fetch(from, toExclusive).map { toPalm(it) }.filter { visible(it) }.flatMap { it.days() }
            .filter { it >= from && it < toExclusive }.toSet()

    override suspend fun historyFor(lookupKey: String): List<PalmEvent> {
        val today = LocalDate.now()
        // Ключ контакта мог измениться (новый телефон): тогда совпадаем по номеру из метки
        val numbers = phones(lookupKey).map { phoneKey(it.number) }.filter { it.isNotEmpty() }.toSet()
        return fetch(today.minusYears(5), today.plusYears(1)).map { toPalm(it) }
            .filter { it.contact?.lookupKey == lookupKey || (numbers.isNotEmpty() && phoneKey(it.contact?.phone) in numbers) }
            .sortedByDescending { it.start }
    }

    override suspend fun contactStats(from: LocalDate): List<ContactStat> {
        val now = LocalDateTime.now()
        val list = fetch(from, LocalDate.now().plusDays(1)).map { toPalm(it) }
            .filter { it.contact != null && !it.start.isAfter(now) && it.outcome == Outcome.DONE }
        return list.groupBy { it.contact!!.lookupKey }.map { (_, l) ->
            ContactStat(
                contact = l.last().contact!!,
                calls = l.count { it.type == EventType.CALL },
                meetings = l.count { it.type == EventType.MEETING },
                mails = l.count { it.type == EventType.MAIL },
            )
        }.filter { it.total > 0 }
    }

    override suspend fun location(eventId: Long): String? = master(eventId)?.location?.takeIf { it.isNotBlank() }

    private fun saveEvent(ev: EKEvent, series: Boolean = true) {
        store.saveEvent(ev, if (series && ev.hasRecurrenceRules) EKSpan.EKSpanFutureEvents else EKSpan.EKSpanThisEvent, true, null)
    }

    private fun editMaster(id: Long, f: (EKEvent) -> Unit) {
        val ev = master(id) ?: return
        f(ev)
        saveEvent(ev)
    }

    override suspend fun setLocation(eventId: Long, place: String?) = editMaster(eventId) { it.location = place?.trim()?.takeIf { p -> p.isNotEmpty() } }

    private fun calendarFor(id: Long): EKCalendar? {
        calMap[id]?.let { return it }
        allCalendars()
        return calMap[id]
    }

    private fun titleFor(e: NewEvent): String {
        val m = e.mail
        if (e.type == EventType.MAIL && m != null) {
            // Письмо: "Ответить: тема" / "Написать: Иван Петров"
            val what = e.title?.takeIf { it.isNotBlank() }
                ?: if (m.kind == MailKind.REPLY) m.subject?.takeIf { it.isNotBlank() } ?: m.peer else e.contact?.name ?: m.peer
            return if (what != null) "${m.kind.verb}: $what" else m.kind.verb
        }
        return e.title?.takeIf { it.isNotBlank() }
            ?.let { "${e.type.label}: $it" } ?: e.contact?.let { "${e.type.label}: ${it.name}" } ?: e.type.label
    }

    override suspend fun create(e: NewEvent): Long {
        val ev = EKEvent.eventWithEventStore(store)
        ev.calendar = calendarFor(e.calendarId) ?: store.defaultCalendarForNewEvents
        ev.title = titleFor(e)
        applyTime(ev, e.start, e.minutes)
        ev.notes = compose(null, e.note, makeMarker(e.type, e.contact?.lookupKey, e.contact?.phone, mailMarker(e.mail)))
        ev.location = e.place?.takeIf { it.isNotBlank() }
        e.reminders.forEach { ev.addAlarm(EKAlarm.alarmWithRelativeOffset(-it * 60.0)) }
        e.rrule?.let { r -> parseRule(r)?.let { ev.addRecurrenceRule(it) } }
        store.saveEvent(ev, EKSpan.EKSpanThisEvent, true, null)
        return idOf(ev)
    }

    private fun applyTime(ev: EKEvent, start: LocalDateTime, minutes: Int) {
        if (minutes == 0) {
            ev.allDay = true
            ev.startDate = start.date.atStartOfDay().toNs()
            ev.endDate = start.date.atStartOfDay().plusMinutes(24 * 60 - 1).toNs()
        } else {
            ev.allDay = false
            ev.startDate = start.toNs()
            ev.endDate = start.plusMinutes(minutes).toNs()
        }
    }

    override suspend fun setLink(eventId: Long, type: EventType?, contact: ContactRef?) = editMaster(eventId) { ev ->
        val n = ev.notes
        val keepMail = parseMarker(n)?.mail?.takeIf { type == EventType.MAIL }
        val marker = type?.let { makeMarker(it, contact?.lookupKey, contact?.phone, keepMail) }
        ev.notes = compose(outcomeOf(n), body(n), marker)
    }

    override suspend fun setRepeat(e: PalmEvent, rrule: String?) {
        val ev = occurrence(e) ?: return
        ev.recurrenceRules?.filterIsInstance<EKRecurrenceRule>()?.forEach { ev.removeRecurrenceRule(it) }
        rrule?.let { r -> parseRule(r)?.let { ev.addRecurrenceRule(it) } }
        store.saveEvent(ev, EKSpan.EKSpanFutureEvents, true, null)
    }

    override fun setTitle(eventId: Long, title: String) = editMaster(eventId) { it.title = title }

    override fun setNote(eventId: Long, note: String?) = editMaster(eventId) { ev ->
        val n = ev.notes
        ev.notes = compose(outcomeOf(n), note, markerOf(n))
    }

    override suspend fun setTime(e: PalmEvent, start: LocalDateTime, minutes: Int, scope: ru.palmdate.app.model.DeleteScope) {
        val ev = occurrence(e) ?: return
        val whole = e.recurring && scope == ru.palmdate.app.model.DeleteScope.ALL
        if (!whole) {
            applyTime(ev, start, minutes)
            // Только этот раз — исключение в серии; этот и следующие (или одиночное) — с этого раза
            val span = if (e.recurring && scope == ru.palmdate.app.model.DeleteScope.ONE) EKSpan.EKSpanThisEvent else EKSpan.EKSpanFutureEvents
            store.saveEvent(ev, span, true, null)
            return
        }
        // Вся серия: первый раз сдвигаем на столько же, на сколько сдвинули этот
        val m = master(e.eventId) ?: ev
        val masterStart = m.startDate!!.timeIntervalSince1970
        val oldOcc = ev.startDate!!.timeIntervalSince1970
        applyTime(ev, start, minutes) // выставляет «весь день», начало и конец этого раза
        val delta = ev.startDate!!.timeIntervalSince1970 - oldOcc
        val len = ev.endDate!!.timeIntervalSince1970 - ev.startDate!!.timeIntervalSince1970
        m.allDay = ev.allDay
        m.startDate = NSDate.dateWithTimeIntervalSince1970(masterStart + delta)
        m.endDate = NSDate.dateWithTimeIntervalSince1970(masterStart + delta + len)
        store.saveEvent(m, EKSpan.EKSpanFutureEvents, true, null)
    }

    override fun reminders(eventId: Long): List<Int> =
        master(eventId)?.alarms?.filterIsInstance<EKAlarm>()?.map { (-it.relativeOffset / 60).toInt() }.orEmpty()

    override fun setReminders(eventId: Long, minutes: List<Int>) = editMaster(eventId) { ev ->
        ev.alarms?.filterIsInstance<EKAlarm>()?.forEach { ev.removeAlarm(it) }
        minutes.forEach { ev.addAlarm(EKAlarm.alarmWithRelativeOffset(-it * 60.0)) }
    }

    override suspend fun deleteOne(e: PalmEvent) {
        val ev = occurrence(e) ?: return
        store.removeEvent(ev, EKSpan.EKSpanThisEvent, true, null)
    }

    override suspend fun deleteFollowing(e: PalmEvent) {
        val ev = occurrence(e) ?: return
        store.removeEvent(ev, EKSpan.EKSpanFutureEvents, true, null)
    }

    override suspend fun delete(eventId: Long) {
        val ev = master(eventId) ?: return
        store.removeEvent(ev, EKSpan.EKSpanFutureEvents, true, null)
        ru.palmdate.app.ui.IosFiles.deleteAll(eventId)
    }

    override fun allCalendars(): List<CalendarInfo> {
        if (!authorized) return emptyList()
        return runCatching {
            val def = runCatching { store.defaultCalendarForNewEvents?.calendarIdentifier }.getOrNull()
            store.calendarsForEntityType(EKEntityType.EKEntityTypeEvent).orEmpty().filterIsInstance<EKCalendar>()
                .mapNotNull { c ->
                    runCatching {
                        if (c.type == EKCalendarType.EKCalendarTypeBirthday) return@runCatching null
                        val id = hid(c.calendarIdentifier)
                        calMap[id] = c
                        val src = runCatching { c.source }.getOrNull()
                        val local = src?.sourceType == EKSourceType.EKSourceTypeLocal
                        CalendarInfo(
                            id = id, name = runCatching { c.title }.getOrNull() ?: "",
                            accountName = runCatching { src?.title }.getOrNull() ?: "", accountType = "",
                            color = runCatching { colorOf(c) }.getOrDefault(0xFF4285F4.toInt()),
                            isPrimary = c.calendarIdentifier == def,
                            writable = c.allowsContentModifications, synced = true, local = local,
                        )
                    }.getOrNull()
                }
                .sortedWith(compareBy<CalendarInfo>({ it.local }, { it.accountName.lowercase() }, { !it.isPrimary }, { it.name.lowercase() }))
        }.getOrDefault(emptyList())
    }

    override suspend fun move(eventId: Long, targetCalendarId: Long): Long {
        val ev = master(eventId) ?: return eventId
        calendarFor(targetCalendarId)?.let { ev.calendar = it; saveEvent(ev) }
        return eventId
    }

    override suspend fun setOutcome(e: PalmEvent, outcome: Outcome?, note: String?) {
        val line = outcome?.let { outcomeLine(it, e.type, note) }
        if (!e.recurring) {
            editMaster(e.eventId) { ev ->
                val n = ev.notes; ev.notes = compose(line, body(n), markerOf(n))
                silenceAlarms(ev, e, outcome)
            }
            return
        }
        // Повторяющееся: итог (и снятие напоминаний) только у этого раза. Раз сохраняется отдельно внутри серии
        // (как исключение) — его хранит и синхронизирует сам календарь, остальные разы не меняются.
        val ev = exactOccurrence(e) ?: return
        val n = ev.notes
        ev.notes = compose(line, body(n), markerOf(n))
        silenceAlarms(ev, e, outcome)
        store.saveEvent(ev, EKSpan.EKSpanThisEvent, true, null)
    }

    /**
     * Отметили «сделано/отменено/перенесено» — снимаем напоминания у этого события (раза), запомнив их минуты;
     * вернули «не сделано» — восстанавливаем те же. Сохранение — у вызывающего.
     */
    private fun silenceAlarms(ev: EKEvent, e: PalmEvent, outcome: Outcome?) {
        val was = e.outcome?.silencesAlarms == true
        val now = outcome?.silencesAlarms == true
        val key = "alarms_off_${e.eventId}_${e.instanceStart}"
        val current = ev.alarms?.filterIsInstance<EKAlarm>().orEmpty()
        if (now && !was && current.isNotEmpty()) {
            IosStore.putString(key, current.map { (-it.relativeOffset / 60).toInt() }.joinToString(","))
            current.forEach { ev.removeAlarm(it) }
        } else if (!now && was) {
            val saved = IosStore.getString(key) ?: return
            IosStore.remove(key)
            // Если напоминания успели поставить вручную — не трогаем
            if (current.isEmpty()) saved.split(",").mapNotNull { it.trim().toIntOrNull() }
                .forEach { ev.addAlarm(EKAlarm.alarmWithRelativeOffset(-it * 60.0)) }
        }
    }

    /* ---------- повторы: RRULE ⇄ EKRecurrenceRule (простые правила) ---------- */

    private val dayCodes = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")  // номер по ISO: понедельник = 1

    /** «MO» / «2WE» / «-1WE» → день недели EventKit (воскресенье = 1) с номером недели месяца. */
    private fun parseDayOfWeek(part: String): EKRecurrenceDayOfWeek? {
        val code = part.takeLast(2)
        val iso = dayCodes.indexOf(code) + 1
        if (iso == 0) return null
        val num = part.dropLast(2).toLongOrNull() ?: 0L
        return EKRecurrenceDayOfWeek.dayOfWeek((iso % 7 + 1).toLong(), num)
    }

    /** UNTIL из RRULE («20261231» или «20261231T205959Z») → конец повтора. */
    private fun parseUntil(u: String): EKRecurrenceEnd? {
        if (u.length < 8) return null
        val y = u.substring(0, 4).toIntOrNull() ?: return null
        val mo = u.substring(4, 6).toIntOrNull() ?: return null
        val d = u.substring(6, 8).toIntOrNull() ?: return null
        val instant = if (u.contains("T") && u.length >= 15) {
            val h = u.substring(9, 11).toIntOrNull() ?: 0
            val mi = u.substring(11, 13).toIntOrNull() ?: 0
            val se = u.substring(13, 15).toIntOrNull() ?: 0
            LocalDateTime(y, mo, d, h, mi, se).toInstant(kotlinx.datetime.TimeZone.UTC)
        } else LocalDateTime(y, mo, d, 23, 59, 59).toInstant(tz)
        return EKRecurrenceEnd.recurrenceEndWithEndDate(NSDate.dateWithTimeIntervalSince1970(instant.epochSeconds.toDouble()))
    }

    private fun parseRule(rrule: String): EKRecurrenceRule? {
        val m = rrule.removePrefix("RRULE:").split(";").mapNotNull { p -> p.indexOf('=').takeIf { it > 0 }?.let { p.substring(0, it).uppercase() to p.substring(it + 1) } }.toMap()
        val f = when (m["FREQ"]) {
            "DAILY" -> EKRecurrenceFrequency.EKRecurrenceFrequencyDaily
            "WEEKLY" -> EKRecurrenceFrequency.EKRecurrenceFrequencyWeekly
            "MONTHLY" -> EKRecurrenceFrequency.EKRecurrenceFrequencyMonthly
            "YEARLY" -> EKRecurrenceFrequency.EKRecurrenceFrequencyYearly
            else -> return null
        }
        val interval = m["INTERVAL"]?.toLongOrNull() ?: 1L
        val end = m["COUNT"]?.toLongOrNull()?.let { EKRecurrenceEnd.recurrenceEndWithOccurrenceCount(it.toULong()) }
            ?: m["UNTIL"]?.let { parseUntil(it) }
        val days = m["BYDAY"]?.split(",")?.mapNotNull { parseDayOfWeek(it.trim()) }?.takeIf { it.isNotEmpty() }
        val monthDays = m["BYMONTHDAY"]?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.isNotEmpty() }
        if (days == null && monthDays == null) return EKRecurrenceRule(f, interval, end)
        return EKRecurrenceRule(f, interval, days, monthDays, null, null, null, null, end)
    }

    private fun ruleOf(ev: EKEvent): String? {
        val r = ev.recurrenceRules?.filterIsInstance<EKRecurrenceRule>()?.firstOrNull() ?: return null
        val f = when (r.frequency) {
            EKRecurrenceFrequency.EKRecurrenceFrequencyDaily -> "DAILY"
            EKRecurrenceFrequency.EKRecurrenceFrequencyWeekly -> "WEEKLY"
            EKRecurrenceFrequency.EKRecurrenceFrequencyMonthly -> "MONTHLY"
            EKRecurrenceFrequency.EKRecurrenceFrequencyYearly -> "YEARLY"
            else -> return null
        }
        return buildString {
            append("FREQ=$f")
            if (r.interval > 1) append(";INTERVAL=${r.interval}")
            val days = r.daysOfTheWeek?.filterIsInstance<EKRecurrenceDayOfWeek>().orEmpty()
            if (days.isNotEmpty()) {
                // EventKit: воскресенье = 1; в RRULE — по ISO, понедельник первым
                val parts = days.map { d ->
                    val iso = ((d.dayOfTheWeek.toInt() + 5) % 7) + 1
                    iso to ((if (d.weekNumber != 0L) d.weekNumber.toString() else "") + dayCodes[iso - 1])
                }.sortedBy { it.first }
                append(";BYDAY=" + parts.joinToString(",") { it.second })
            }
            val md = r.daysOfTheMonth?.mapNotNull { (it as? Number)?.toInt() }.orEmpty()
            if (md.isNotEmpty()) append(";BYMONTHDAY=" + md.joinToString(","))
            r.recurrenceEnd?.let { e ->
                if (e.occurrenceCount > 0uL) append(";COUNT=${e.occurrenceCount}")
                else e.endDate?.let { dt ->
                    val u = Instant.fromEpochSeconds(dt.timeIntervalSince1970.toLong()).toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
                    fun p2(n: Int) = n.toString().padStart(2, '0')
                    append(";UNTIL=" + u.year.toString().padStart(4, '0') + p2(u.monthNumber) + p2(u.dayOfMonth) + "T" + p2(u.hour) + p2(u.minute) + p2(u.second) + "Z")
                }
            }
        }
    }

    /* ---------- контакты ---------- */

    private val keys: List<Any?> by lazy {
        listOf(
            CNContactGivenNameKey, CNContactFamilyNameKey, CNContactOrganizationNameKey,
            CNContactPhoneNumbersKey, CNContactPostalAddressesKey, platform.Contacts.CNContactEmailAddressesKey,
        )
    }

    private fun toRef(c: CNContact): ContactRef {
        val name = listOf(c.givenName, c.familyName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { c.organizationName }
        val phone = c.phoneNumbers.filterIsInstance<CNLabeledValue>().firstOrNull()?.let { (it.value as? CNPhoneNumber)?.stringValue }
        val addr = c.postalAddresses.filterIsInstance<CNLabeledValue>().firstOrNull()?.let { addrText(it.value as? CNPostalAddress) }
        return ContactRef(c.identifier, name, phone, addr)
    }

    private fun labelText(l: String?): String = l?.removePrefix("_\$!<")?.removeSuffix(">!\$_") ?: ""

    private fun addrText(a: CNPostalAddress?): String? =
        a?.let { listOf(it.city, it.street).filter { s -> s.isNotBlank() }.joinToString(", ") }?.takeIf { it.isNotBlank() }

    private fun loadContact(key: String): ContactRef? =
        runCatching { contacts.unifiedContactWithIdentifier(key, keys, null)?.let { toRef(it) } }.getOrNull()

    override fun invalidateContacts() { contactCache.clear(); phoneIndex = null }

    /** Все контакты (объединённые) через обычный перебор — так надёжнее, чем предикат контейнера. */
    private fun allContacts(max: Int = 2000): List<CNContact> {
        val out = ArrayList<CNContact>()
        val req = platform.Contacts.CNContactFetchRequest(keysToFetch = keys)
        contacts.enumerateContactsWithFetchRequest(req, null) { c, _ -> if (c != null && out.size < max) out.add(c) }
        return out
    }

    private fun byName(q: String): List<CNContact> =
        contacts.unifiedContactsMatchingPredicate(CNContact.predicateForContactsMatchingName(q), keys, null).orEmpty().filterIsInstance<CNContact>()

    override fun searchContacts(query: String): List<ContactRef> = runCatching {
        val q = query.trim()
        val list = if (q.isEmpty()) allContacts() else byName(q)
        list.map { toRef(it) }.filter { it.name.isNotBlank() }.sortedBy { it.name.lowercase() }.take(200)
    }.getOrDefault(emptyList())

    private fun emailsOf(c: CNContact): List<String> =
        c.emailAddresses.filterIsInstance<CNLabeledValue>().mapNotNull { (it.value as? String)?.trim()?.takeIf { s -> s.contains("@") } }

    override fun searchEmails(query: String): List<EmailContact> = runCatching {
        val q = query.trim()
        val list = if (q.isEmpty()) allContacts() else {
            val low = q.lowercase()
            (byName(q) + allContacts().filter { c -> emailsOf(c).any { it.lowercase().contains(low) } }).distinctBy { it.identifier }
        }
        val seen = HashSet<String>()
        val out = ArrayList<EmailContact>()
        for (c in list.sortedBy { toRef(it).name.lowercase() }) {
            val name = toRef(c).name
            for (e in emailsOf(c)) if (seen.add(e.lowercase())) out += EmailContact(c.identifier, name.ifBlank { e }, e)
            if (out.size >= 50) break
        }
        out
    }.getOrDefault(emptyList())

    override fun contactByEmail(address: String): ContactRef? = runCatching {
        val low = address.trim().lowercase()
        allContacts().firstOrNull { c -> emailsOf(c).any { it.lowercase() == low } }?.let { toRef(it) }
    }.getOrNull()

    override fun phones(lookupKey: String): List<PhoneNumber> = runCatching {
        val c = contacts.unifiedContactWithIdentifier(lookupKey, keys, null) ?: return emptyList()
        c.phoneNumbers.filterIsInstance<CNLabeledValue>().mapNotNull { lv ->
            val n = (lv.value as? CNPhoneNumber)?.stringValue ?: return@mapNotNull null
            PhoneNumber(n, labelText(lv.label))
        }
    }.getOrDefault(emptyList())

    override suspend fun rememberedPhone(lookupKey: String): String? = IosStore.getString("phone.$lookupKey")

    override fun addresses(lookupKey: String): List<PhoneNumber> = runCatching {
        val c = contacts.unifiedContactWithIdentifier(lookupKey, keys, null) ?: return emptyList()
        c.postalAddresses.filterIsInstance<CNLabeledValue>().mapNotNull { lv ->
            val t = addrText(lv.value as? CNPostalAddress) ?: return@mapNotNull null
            PhoneNumber(t, labelText(lv.label))
        }
    }.getOrDefault(emptyList())

    override fun rememberAddress(lookupKey: String, address: String) = IosStore.putString("addr.$lookupKey", address)

    /* ---------- документы (пока не поддержаны на iPhone) ---------- */

    override suspend fun tripFiles(eventId: Long): List<TripDoc> = ru.palmdate.app.ui.IosFiles.list(eventId)
    override suspend fun addTripFile(eventId: Long, uri: String, label: String) = ru.palmdate.app.ui.IosFiles.add(eventId, uri, label)
    override suspend fun deleteTripFile(doc: TripDoc) = ru.palmdate.app.ui.IosFiles.delete(doc)

    /* ---------- хранилище и изменения снаружи ---------- */

    override fun getString(key: String): String? = IosStore.getString(key)
    override fun putString(key: String, value: String) = IosStore.putString(key, value)
    override fun getLong(key: String): Long? = IosStore.getLong(key)
    override fun putLong(key: String, value: Long) = IosStore.putLong(key, value)
    override fun getStringSet(key: String): Set<String> = IosStore.getStringSet(key)
    override fun putStringSet(key: String, value: Set<String>) = IosStore.putStringSet(key, value)

    override fun observeExternalChanges(onChange: () -> Unit) { listeners += onChange }
}
