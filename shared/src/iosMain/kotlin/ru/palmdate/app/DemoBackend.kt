package ru.palmdate.app

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import ru.palmdate.app.model.CalendarInfo
import ru.palmdate.app.model.ContactRef
import ru.palmdate.app.model.ContactStat
import ru.palmdate.app.model.EmailContact
import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.NewEvent
import ru.palmdate.app.model.Outcome
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.model.PhoneNumber
import ru.palmdate.app.time.*
import ru.palmdate.app.ui.TripDoc

/**
 * Временный источник данных для iPhone: события хранятся в памяти и пропадают при закрытии.
 * Нужен, чтобы увидеть и проверить экраны; потом заменится на календарь и контакты iPhone (EventKit, Contacts).
 */
class DemoBackend : AppBackend {
    private val calendar = CalendarInfo(1, "DateBook", "demo", "local", 0xFF1E3A6E.toInt(), isPrimary = true)
    private val ivan = ContactRef("ivan", "Иван Петров", "+7 916 123-45-67", "Москва, Тверская 1")
    private val olga = ContactRef("olga", "Ольга Смирнова", "+7 903 555-01-02")
    private val people = listOf(ivan, olga, ContactRef("sergey", "Сергей Кузнецов", "+7 925 000-11-22"))

    private var nextId = 100L
    private val events = mutableListOf<PalmEvent>()
    private val prefs = HashMap<String, Any>()

    init {
        val today = LocalDate.now()
        fun at(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m)
        fun add(id: Long, title: String, start: LocalDateTime, minutes: Int, type: EventType?, c: ContactRef? = null, place: String? = null, color: Int = 0xFF4285F4.toInt()) {
            events += PalmEvent(
                eventId = id, title = title, start = start, end = start.plusMinutes(minutes.toLong()), allDay = false,
                type = type, contact = c, note = null, color = color, calendarName = calendar.name, calendarId = calendar.id,
                place = place, hasAlarm = true,
            )
        }
        add(1, "Звонок: Иван Петров", at(today, 10), 15, EventType.CALL, ivan)
        add(2, "Встреча: Ольга Смирнова", at(today, 14), 60, EventType.MEETING, olga, "Москва, Арбат 10", 0xFF0B8043.toInt())
        add(3, "Задача: отправить отчёт", at(today, 16, 30), 30, EventType.TASK, color = 0xFFF4511E.toInt())
        add(4, "Выезд: театр", at(today.plusDays(1), 19), 150, EventType.TRIP, place = "Театральная площадь 1", color = 0xFF8E24AA.toInt())
        add(5, "Звонок: Сергей Кузнецов", at(today.plusDays(2), 11), 15, EventType.CALL, people[2])
    }

    override var hiddenCalendars: Set<Long> = emptySet()

    override suspend fun eventsBetween(from: LocalDate, toExclusive: LocalDate): List<PalmEvent> =
        events.filter { it.start.toLocalDate() < toExclusive && it.end.toLocalDate() >= from }.sortedBy { it.start }

    override fun daysWithEvents(from: LocalDate, toExclusive: LocalDate): Set<LocalDate> =
        events.flatMap { it.days() }.filter { it >= from && it < toExclusive }.toSet()

    override suspend fun historyFor(lookupKey: String): List<PalmEvent> =
        events.filter { it.contact?.lookupKey == lookupKey }.sortedByDescending { it.start }

    override suspend fun contactStats(from: LocalDate): List<ContactStat> = emptyList()
    override suspend fun location(eventId: Long): String? = events.firstOrNull { it.eventId == eventId }?.place
    override suspend fun setLocation(eventId: Long, place: String?) = edit(eventId) { it.copy(place = place) }

    override suspend fun create(e: NewEvent): Long {
        val id = nextId++
        val title = e.title?.takeIf { it.isNotBlank() }
            ?.let { "${e.type.label}: $it" } ?: e.contact?.let { "${e.type.label}: ${it.name}" } ?: e.type.label
        events += PalmEvent(
            eventId = id, title = title, start = e.start,
            end = if (e.minutes == 0) e.start.toLocalDate().plusDays(1).atStartOfDay() else e.start.plusMinutes(e.minutes.toLong()),
            allDay = e.minutes == 0, type = e.type, contact = e.contact, note = e.note, color = calendar.color,
            calendarName = calendar.name, calendarId = calendar.id, place = e.place, hasAlarm = e.reminders.isNotEmpty(),
        )
        return id
    }

    private fun edit(id: Long, f: (PalmEvent) -> PalmEvent) {
        val i = events.indexOfFirst { it.eventId == id }
        if (i >= 0) events[i] = f(events[i])
    }

    override suspend fun setLink(eventId: Long, type: EventType?, contact: ContactRef?) = edit(eventId) { it.copy(type = type, contact = contact) }
    override suspend fun setRepeat(e: PalmEvent, rrule: String?) = edit(e.eventId) { it.copy(rrule = rrule, recurring = rrule != null) }
    override fun setTitle(eventId: Long, title: String) = edit(eventId) { it.copy(title = title) }
    override fun setNote(eventId: Long, note: String?) = edit(eventId) { it.copy(note = note) }
    override suspend fun setTime(e: PalmEvent, start: LocalDateTime, minutes: Int) = edit(e.eventId) {
        it.copy(start = start, end = start.plusMinutes(minutes.toLong()), allDay = minutes == 0)
    }
    override fun reminders(eventId: Long): List<Int> = listOf(10)
    override fun setReminders(eventId: Long, minutes: List<Int>) = edit(eventId) { it.copy(hasAlarm = minutes.isNotEmpty()) }
    override suspend fun deleteOne(e: PalmEvent) { events.removeAll { it.eventId == e.eventId } }
    override suspend fun deleteFollowing(e: PalmEvent) { events.removeAll { it.eventId == e.eventId } }
    override suspend fun delete(eventId: Long) { events.removeAll { it.eventId == eventId } }
    override fun allCalendars(): List<CalendarInfo> = listOf(calendar)
    override suspend fun move(eventId: Long, targetCalendarId: Long): Long = eventId
    override suspend fun setOutcome(e: PalmEvent, outcome: Outcome?, note: String?) =
        edit(e.eventId) { it.copy(outcome = outcome, outcomeNote = note?.takeIf { n -> n.isNotBlank() }) }

    override fun invalidateContacts() {}
    override fun searchEmails(query: String): List<EmailContact> = emptyList()
    override fun contactByEmail(address: String): ContactRef? = null
    override fun searchContacts(query: String): List<ContactRef> =
        people.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    override fun phones(lookupKey: String): List<PhoneNumber> =
        people.firstOrNull { it.lookupKey == lookupKey }?.phone?.let { listOf(PhoneNumber(it, "")) }.orEmpty()
    override suspend fun rememberedPhone(lookupKey: String): String? = null
    override fun addresses(lookupKey: String): List<PhoneNumber> =
        people.firstOrNull { it.lookupKey == lookupKey }?.address?.let { listOf(PhoneNumber(it, "")) }.orEmpty()
    override fun rememberAddress(lookupKey: String, address: String) {}

    override suspend fun tripFiles(eventId: Long): List<TripDoc> = emptyList()
    override suspend fun addTripFile(eventId: Long, uri: String, label: String) {}
    override suspend fun deleteTripFile(doc: TripDoc) {}

    override fun getString(key: String): String? = prefs[key] as? String
    override fun putString(key: String, value: String) { prefs[key] = value }
    override fun getLong(key: String): Long? = prefs[key] as? Long
    override fun putLong(key: String, value: Long) { prefs[key] = value }
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String): Set<String> = (prefs[key] as? Set<String>).orEmpty()
    override fun putStringSet(key: String, value: Set<String>) { prefs[key] = value }

    override fun observeExternalChanges(onChange: () -> Unit) {}
}
