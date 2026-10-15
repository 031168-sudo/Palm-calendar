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
import platform.Contacts.CNContactFormatter
import platform.Contacts.CNContactFormatterStyleFullName
import platform.Contacts.CNEntityTypeContacts
import platform.Contacts.CNContactPhoneNumbersKey
import platform.Contacts.CNContactPostalAddressesKey
import platform.Contacts.CNContactStore
import platform.Contacts.CNLabeledValue
import platform.Contacts.CNPhoneNumber
import platform.Contacts.CNPostalAddress
import platform.EventKit.EKAlarm
import platform.EventKit.EKCalendar
import platform.EventKit.EKEntityTypeEvent
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKit.EKEventStoreChangedNotification
import platform.EventKit.EKRecurrenceFrequencyDaily
import platform.EventKit.EKRecurrenceFrequencyMonthly
import platform.EventKit.EKRecurrenceFrequencyWeekly
import platform.EventKit.EKRecurrenceFrequencyYearly
import platform.EventKit.EKRecurrenceEnd
import platform.EventKit.EKRecurrenceRule
import platform.EventKit.EKSpanFutureEvents
import platform.EventKit.EKSpanThisEvent
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

    private val authorized: Boolean
        get() = EKEventStore.authorizationStatusForEntityType(EKEntityTypeEvent) == 3L

    private fun requestAccess() {
        val done: (Boolean, platform.Foundation.NSError?) -> Unit = { ok, _ ->
            if (ok) platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) { listeners.toList().forEach { it() } }
        }
        if (platform.Foundation.NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion } >= 17L) {
            store.requestFullAccessToEventsWithCompletion(done)
        } else {
            store.requestAccessToEntityType(EKEntityTypeEvent, done)
        }
        contacts.requestAccessForEntityType(CNEntityTypeContacts) { _, _ -> }
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
        val hit = fetch(day, day.plusDays(1)).firstOrNull { (it.eventIdentifier ?: it.calendarItemIdentifier) == ident && it.startDate.toLdt() == e.start }
        return hit ?: store.eventWithIdentifier(ident)
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

    private class Parsed(val type: EventType, val key: String?, val phone: String?)

    private fun parseMarker(notes: String?): Parsed? {
        val m = notes?.let { markerRe.find(it) } ?: return null
        val t = EventType.parse(m.groupValues[1]) ?: return null
        fun g(i: Int) = m.groupValues[i].takeIf { it.isNotEmpty() }?.let { dec(it) }
        return Parsed(t, g(2), g(3))
    }

    private fun makeMarker(type: EventType, key: String?, phone: String?) =
        "[palm:t=${type.name}" + (key?.let { ";c=" + enc(it) } ?: "") + (phone?.let { ";p=" + enc(it) } ?: "") + "]"

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
            out += store.eventsMatchingPredicate(p).filterIsInstance<EKEvent>()
            s = e
        }
        return out
    }

    private fun contactRef(key: String, fallbackName: String, phone: String?): ContactRef {
        val c = contactCache.getOrPut(key) { loadContact(key) }
        return if (c != null) c.copy(phone = phone ?: c.phone) else ContactRef(key, fallbackName, phone)
    }

    private fun toPalm(ev: EKEvent): PalmEvent {
        val id = idOf(ev)
        val notes = ev.notes
        val mk = parseMarker(notes)
        val birthday = ev.calendar?.type == platform.EventKit.EKCalendarTypeBirthday
        val title = ev.title ?: ""
        val start = ev.startDate.toLdt()
        val allDay = ev.allDay
        val e0 = ev.endDate.toLdt()
        val end = when {
            allDay && !(e0.time == LocalTime(0, 0) && e0 > start) -> e0.date.plusDays(1).atStartOfDay()
            e0 < start -> start
            else -> e0
        }
        val cal = ev.calendar
        cal?.let { calMap[hid(it.calendarIdentifier)] = it }
        val oc = outcomeRe.find(notes ?: "")
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
            instanceStart = ev.startDate.timeIntervalSince1970.toLong() * 1000,
            outcome = outcome,
            outcomeNote = oc?.groupValues?.get(2)?.takeIf { it.isNotBlank() && outcome != null },
            fromContacts = birthday,
            rrule = ruleOf(ev),
            place = ev.location?.takeIf { it.isNotBlank() },
            hasAlarm = if (birthday) null else !ev.alarms.isNullOrEmpty(),
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
        return fetch(today.minusYears(5), today.plusYears(1)).map { toPalm(it) }
            .filter { it.contact?.lookupKey == lookupKey }.sortedByDescending { it.start }
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
        store.saveEvent(ev, if (series && ev.hasRecurrenceRules) EKSpanFutureEvents else EKSpanThisEvent, true, null)
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

    private fun titleFor(e: NewEvent): String = e.title?.takeIf { it.isNotBlank() }
        ?.let { "${e.type.label}: $it" } ?: e.contact?.let { "${e.type.label}: ${it.name}" } ?: e.type.label

    override suspend fun create(e: NewEvent): Long {
        val ev = EKEvent.eventWithEventStore(store)
        ev.calendar = calendarFor(e.calendarId) ?: store.defaultCalendarForNewEvents
        ev.title = titleFor(e)
        applyTime(ev, e.start, e.minutes)
        ev.notes = compose(null, e.note, makeMarker(e.type, e.contact?.lookupKey, e.contact?.phone))
        ev.location = e.place?.takeIf { it.isNotBlank() }
        e.reminders.forEach { ev.addAlarm(EKAlarm.alarmWithRelativeOffset(-it * 60.0)) }
        e.rrule?.let { r -> parseRule(r)?.let { ev.addRecurrenceRule(it) } }
        store.saveEvent(ev, EKSpanThisEvent, true, null)
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
        val marker = type?.let { makeMarker(it, contact?.lookupKey, contact?.phone) }
        ev.notes = compose(outcomeOf(n), body(n), marker)
    }

    override suspend fun setRepeat(e: PalmEvent, rrule: String?) {
        val ev = occurrence(e) ?: return
        ev.recurrenceRules?.filterIsInstance<EKRecurrenceRule>()?.forEach { ev.removeRecurrenceRule(it) }
        rrule?.let { r -> parseRule(r)?.let { ev.addRecurrenceRule(it) } }
        store.saveEvent(ev, EKSpanFutureEvents, true, null)
    }

    override fun setTitle(eventId: Long, title: String) = editMaster(eventId) { it.title = title }

    override fun setNote(eventId: Long, note: String?) = editMaster(eventId) { ev ->
        val n = ev.notes
        ev.notes = compose(outcomeOf(n), note, markerOf(n))
    }

    override suspend fun setTime(e: PalmEvent, start: LocalDateTime, minutes: Int) {
        val ev = occurrence(e) ?: return
        applyTime(ev, start, minutes)
        store.saveEvent(ev, EKSpanFutureEvents, true, null)
    }

    override fun reminders(eventId: Long): List<Int> =
        master(eventId)?.alarms?.filterIsInstance<EKAlarm>()?.map { (-it.relativeOffset / 60).toInt() }.orEmpty()

    override fun setReminders(eventId: Long, minutes: List<Int>) = editMaster(eventId) { ev ->
        ev.alarms?.filterIsInstance<EKAlarm>()?.forEach { ev.removeAlarm(it) }
        minutes.forEach { ev.addAlarm(EKAlarm.alarmWithRelativeOffset(-it * 60.0)) }
    }

    override suspend fun deleteOne(e: PalmEvent) {
        val ev = occurrence(e) ?: return
        store.removeEvent(ev, EKSpanThisEvent, true, null)
    }

    override suspend fun deleteFollowing(e: PalmEvent) {
        val ev = occurrence(e) ?: return
        store.removeEvent(ev, EKSpanFutureEvents, true, null)
    }

    override suspend fun delete(eventId: Long) {
        val ev = master(eventId) ?: return
        store.removeEvent(ev, EKSpanFutureEvents, true, null)
    }

    override fun allCalendars(): List<CalendarInfo> {
        if (!authorized) return emptyList()
        val def = store.defaultCalendarForNewEvents?.calendarIdentifier
        return store.calendarsForEntityType(EKEntityTypeEvent).filterIsInstance<EKCalendar>()
            .filter { it.type != platform.EventKit.EKCalendarTypeBirthday }
            .map { c ->
                val id = hid(c.calendarIdentifier)
                calMap[id] = c
                val local = c.source?.sourceType == platform.EventKit.EKSourceTypeLocal
                CalendarInfo(
                    id = id, name = c.title ?: "", accountName = c.source?.title ?: "", accountType = "",
                    color = colorOf(c), isPrimary = c.calendarIdentifier == def,
                    writable = c.allowsContentModifications, synced = true, local = local,
                )
            }
            .sortedWith(compareBy<CalendarInfo>({ it.local }, { it.accountName.lowercase() }, { !it.isPrimary }, { it.name.lowercase() }))
    }

    override suspend fun move(eventId: Long, targetCalendarId: Long): Long {
        val ev = master(eventId) ?: return eventId
        calendarFor(targetCalendarId)?.let { ev.calendar = it; saveEvent(ev) }
        return eventId
    }

    override suspend fun setOutcome(e: PalmEvent, outcome: Outcome?, note: String?) = editMaster(e.eventId) { ev ->
        val n = ev.notes
        ev.notes = compose(outcome?.let { outcomeLine(it, e.type, note) }, body(n), markerOf(n))
    }

    /* ---------- повторы: RRULE ⇄ EKRecurrenceRule (простые правила) ---------- */

    private fun parseRule(rrule: String): EKRecurrenceRule? {
        val m = rrule.split(";").mapNotNull { p -> p.indexOf('=').takeIf { it > 0 }?.let { p.substring(0, it).uppercase() to p.substring(it + 1) } }.toMap()
        val f = when (m["FREQ"]) {
            "DAILY" -> EKRecurrenceFrequencyDaily
            "WEEKLY" -> EKRecurrenceFrequencyWeekly
            "MONTHLY" -> EKRecurrenceFrequencyMonthly
            "YEARLY" -> EKRecurrenceFrequencyYearly
            else -> return null
        }
        val interval = m["INTERVAL"]?.toLongOrNull() ?: 1L
        val end = m["COUNT"]?.toLongOrNull()?.let { EKRecurrenceEnd.recurrenceEndWithOccurrenceCount(it) }
        return EKRecurrenceRule(f, interval, end)
    }

    private fun ruleOf(ev: EKEvent): String? {
        val r = ev.recurrenceRules?.filterIsInstance<EKRecurrenceRule>()?.firstOrNull() ?: return null
        val f = when (r.frequency) {
            EKRecurrenceFrequencyDaily -> "DAILY"
            EKRecurrenceFrequencyWeekly -> "WEEKLY"
            EKRecurrenceFrequencyMonthly -> "MONTHLY"
            EKRecurrenceFrequencyYearly -> "YEARLY"
            else -> return null
        }
        return buildString {
            append("FREQ=$f")
            if (r.interval > 1) append(";INTERVAL=${r.interval}")
            r.recurrenceEnd?.occurrenceCount?.takeIf { it > 0 }?.let { append(";COUNT=$it") }
        }
    }

    /* ---------- контакты ---------- */

    private val keys: List<Any?> by lazy {
        listOf(
            CNContactFormatter.descriptorForRequiredKeysForStyle(CNContactFormatterStyleFullName),
            CNContactPhoneNumbersKey, CNContactPostalAddressesKey,
        )
    }

    private fun toRef(c: CNContact): ContactRef {
        val name = CNContactFormatter.stringFromContact(c, CNContactFormatterStyleFullName) ?: ""
        val phone = c.phoneNumbers.filterIsInstance<CNLabeledValue>().firstOrNull()?.let { (it.value as? CNPhoneNumber)?.stringValue }
        val addr = c.postalAddresses.filterIsInstance<CNLabeledValue>().firstOrNull()?.let { addrText(it.value as? CNPostalAddress) }
        return ContactRef(c.identifier, name, phone, addr)
    }

    private fun addrText(a: CNPostalAddress?): String? =
        a?.let { listOf(it.city, it.street).filter { s -> s.isNotBlank() }.joinToString(", ") }?.takeIf { it.isNotBlank() }

    private fun loadContact(key: String): ContactRef? =
        runCatching { contacts.unifiedContactWithIdentifier(key, keys, null)?.let { toRef(it) } }.getOrNull()

    override fun invalidateContacts() { contactCache.clear() }
    override fun searchEmails(query: String): List<EmailContact> = emptyList()
    override fun contactByEmail(address: String): ContactRef? = null

    override fun searchContacts(query: String): List<ContactRef> = runCatching {
        val q = query.trim()
        val list = if (q.isEmpty()) {
            contacts.unifiedContactsMatchingPredicate(CNContact.predicateForContactsInContainerWithIdentifier(contacts.defaultContainerIdentifier()), keys, null)
        } else {
            contacts.unifiedContactsMatchingPredicate(CNContact.predicateForContactsMatchingName(q), keys, null)
        }
        list.orEmpty().filterIsInstance<CNContact>().map { toRef(it) }.filter { it.name.isNotBlank() }.sortedBy { it.name.lowercase() }.take(200)
    }.getOrDefault(emptyList())

    override fun phones(lookupKey: String): List<PhoneNumber> = runCatching {
        val c = contacts.unifiedContactWithIdentifier(lookupKey, keys, null) ?: return emptyList()
        c.phoneNumbers.filterIsInstance<CNLabeledValue>().mapNotNull { lv ->
            val n = (lv.value as? CNPhoneNumber)?.stringValue ?: return@mapNotNull null
            PhoneNumber(n, lv.label?.let { CNLabeledValue.localizedStringForLabel(it) } ?: "")
        }
    }.getOrDefault(emptyList())

    override suspend fun rememberedPhone(lookupKey: String): String? = IosStore.getString("phone.$lookupKey")

    override fun addresses(lookupKey: String): List<PhoneNumber> = runCatching {
        val c = contacts.unifiedContactWithIdentifier(lookupKey, keys, null) ?: return emptyList()
        c.postalAddresses.filterIsInstance<CNLabeledValue>().mapNotNull { lv ->
            val t = addrText(lv.value as? CNPostalAddress) ?: return@mapNotNull null
            PhoneNumber(t, lv.label?.let { CNLabeledValue.localizedStringForLabel(it) } ?: "")
        }
    }.getOrDefault(emptyList())

    override fun rememberAddress(lookupKey: String, address: String) = IosStore.putString("addr.$lookupKey", address)

    /* ---------- документы (пока не поддержаны на iPhone) ---------- */

    override suspend fun tripFiles(eventId: Long): List<TripDoc> = emptyList()
    override suspend fun addTripFile(eventId: Long, uri: String, label: String) {}
    override suspend fun deleteTripFile(doc: TripDoc) {}

    /* ---------- хранилище и изменения снаружи ---------- */

    override fun getString(key: String): String? = IosStore.getString(key)
    override fun putString(key: String, value: String) = IosStore.putString(key, value)
    override fun getLong(key: String): Long? = IosStore.getLong(key)
    override fun putLong(key: String, value: Long) = IosStore.putLong(key, value)
    override fun getStringSet(key: String): Set<String> = IosStore.getStringSet(key)
    override fun putStringSet(key: String, value: Set<String>) = IosStore.putStringSet(key, value)

    override fun observeExternalChanges(onChange: () -> Unit) { listeners += onChange }
}
