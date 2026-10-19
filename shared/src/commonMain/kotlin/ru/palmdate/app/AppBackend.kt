package ru.palmdate.app

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import ru.palmdate.app.model.DeleteScope
import ru.palmdate.app.model.CalendarInfo
import ru.palmdate.app.model.ContactRef
import ru.palmdate.app.model.ContactStat
import ru.palmdate.app.model.EmailContact
import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.NewEvent
import ru.palmdate.app.model.Outcome
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.model.PhoneNumber
import ru.palmdate.app.ui.TripDoc

/**
 * Всё, что экранам нужно от «железа»: календарь, контакты, документы, маленькое хранилище.
 * На Android — AndroidBackend (системный календарь, контакты, Room); на iOS будет своя (EventKit, Contacts).
 * Вызывается из фоновых потоков.
 */
interface AppBackend {
    /* ---- Календарь ---- */
    var hiddenCalendars: Set<Long>
    suspend fun eventsBetween(from: LocalDate, toExclusive: LocalDate): List<PalmEvent>
    fun daysWithEvents(from: LocalDate, toExclusive: LocalDate): Set<LocalDate>
    suspend fun historyFor(lookupKey: String): List<PalmEvent>
    suspend fun contactStats(from: LocalDate): List<ContactStat>
    suspend fun location(eventId: Long): String?
    suspend fun setLocation(eventId: Long, place: String?)
    suspend fun create(e: NewEvent): Long
    suspend fun setLink(eventId: Long, type: EventType?, contact: ContactRef?)
    suspend fun setRepeat(e: PalmEvent, rrule: String?)
    fun setTitle(eventId: Long, title: String)
    fun setNote(eventId: Long, note: String?)
    /** У повторяющегося [scope] решает: только этот раз, этот и следующие или вся серия. */
    suspend fun setTime(e: PalmEvent, start: LocalDateTime, minutes: Int, scope: DeleteScope)
    fun reminders(eventId: Long): List<Int>
    fun setReminders(eventId: Long, minutes: List<Int>)
    suspend fun deleteOne(e: PalmEvent)
    suspend fun deleteFollowing(e: PalmEvent)
    suspend fun delete(eventId: Long)
    fun allCalendars(): List<CalendarInfo>
    suspend fun move(eventId: Long, targetCalendarId: Long): Long
    suspend fun setOutcome(e: PalmEvent, outcome: Outcome?, note: String?)

    /* ---- Контакты ---- */
    fun invalidateContacts()
    fun searchEmails(query: String): List<EmailContact>
    fun contactByEmail(address: String): ContactRef?
    fun searchContacts(query: String): List<ContactRef>
    fun phones(lookupKey: String): List<PhoneNumber>
    suspend fun rememberedPhone(lookupKey: String): String?
    fun addresses(lookupKey: String): List<PhoneNumber>
    fun rememberAddress(lookupKey: String, address: String)

    /* ---- Документы событий ---- */
    suspend fun tripFiles(eventId: Long): List<TripDoc>
    suspend fun addTripFile(eventId: Long, uri: String, label: String)
    suspend fun deleteTripFile(doc: TripDoc)

    /* ---- Маленькое хранилище (последний вид, скрытые календари…) ---- */
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getLong(key: String): Long?
    fun putLong(key: String, value: Long)
    fun getStringSet(key: String): Set<String>
    fun putStringSet(key: String, value: Set<String>)

    /* ---- Изменения снаружи ---- */
    /** Календарь поменяли снаружи (Google sync, другое приложение) — вызвать [onChange]. */
    fun observeExternalChanges(onChange: () -> Unit)
    fun close() {}
}
