package ru.palmdate.app.data

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import ru.palmdate.app.AppBackend
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

/** Android: системный календарь, контакты, Room и SharedPreferences за интерфейсом [AppBackend]. */
class AndroidBackend(
    private val app: Application,
    val contacts: ContactsRepository,
    val db: AppDb,
    val repo: CalendarRepository,
    private val trips: TripFiles,
) : AppBackend {
    private val prefs = app.getSharedPreferences("palmdate", Context.MODE_PRIVATE)
    private var observer: ContentObserver? = null

    override var hiddenCalendars: Set<Long>
        get() = repo.hiddenCalendars
        set(v) { repo.hiddenCalendars = v }

    override suspend fun eventsBetween(from: LocalDate, toExclusive: LocalDate): List<PalmEvent> = repo.eventsBetween(from, toExclusive)
    override fun daysWithEvents(from: LocalDate, toExclusive: LocalDate): Set<LocalDate> = repo.daysWithEvents(from, toExclusive)
    override suspend fun historyFor(lookupKey: String): List<PalmEvent> = repo.historyFor(lookupKey)
    override suspend fun contactStats(from: LocalDate): List<ContactStat> = repo.contactStats(from)
    override suspend fun location(eventId: Long): String? = repo.location(eventId)
    override suspend fun setLocation(eventId: Long, place: String?) = repo.setLocation(eventId, place)
    override suspend fun create(e: NewEvent): Long = repo.create(e)
    override suspend fun setLink(eventId: Long, type: EventType?, contact: ContactRef?) = repo.setLink(eventId, type, contact)
    override suspend fun setRepeat(e: PalmEvent, rrule: String?) = repo.setRepeat(e, rrule)
    override fun setTitle(eventId: Long, title: String) = repo.setTitle(eventId, title)
    override fun setNote(eventId: Long, note: String?) = repo.setNote(eventId, note)
    override suspend fun setTime(e: PalmEvent, start: LocalDateTime, minutes: Int) = repo.setTime(e, start, minutes)
    override fun reminders(eventId: Long): List<Int> = repo.reminders(eventId)
    override fun setReminders(eventId: Long, minutes: List<Int>) = repo.setReminders(eventId, minutes)
    override suspend fun deleteOne(e: PalmEvent) = repo.deleteOne(e)
    override suspend fun deleteFollowing(e: PalmEvent) = repo.deleteFollowing(e)
    override suspend fun delete(eventId: Long) = repo.delete(eventId)
    override fun allCalendars(): List<CalendarInfo> = repo.allCalendars()
    override suspend fun move(eventId: Long, targetCalendarId: Long): Long = repo.move(eventId, targetCalendarId)
    override suspend fun setOutcome(e: PalmEvent, outcome: Outcome?, note: String?) = repo.setOutcome(e, outcome, note)

    override fun invalidateContacts() = contacts.invalidate()
    override fun searchEmails(query: String): List<EmailContact> = contacts.searchEmails(query)
    override fun contactByEmail(address: String): ContactRef? = contacts.byEmail(address)
    override fun searchContacts(query: String): List<ContactRef> = contacts.search(query)
    override fun phones(lookupKey: String): List<PhoneNumber> = contacts.phones(lookupKey)
    override suspend fun rememberedPhone(lookupKey: String): String? = db.links().rememberedPhone(lookupKey)
    override fun addresses(lookupKey: String): List<PhoneNumber> = contacts.addresses(lookupKey)
    override fun rememberAddress(lookupKey: String, address: String) = contacts.rememberAddress(lookupKey, address)

    override suspend fun tripFiles(eventId: Long): List<TripDoc> =
        trips.list(eventId).map { TripDoc(it.id, it.eventId, it.label, it.name, it.path, it.mime) }

    override suspend fun addTripFile(eventId: Long, uri: String, label: String) {
        trips.add(eventId, Uri.parse(uri), label)
    }

    override suspend fun deleteTripFile(doc: TripDoc) {
        trips.delete(TripFile(doc.id, doc.eventId, doc.label, doc.name, doc.path, doc.mime))
    }

    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun getLong(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, -1L) else null
    override fun putLong(key: String, value: Long) { prefs.edit().putLong(key, value).apply() }
    override fun getStringSet(key: String): Set<String> = prefs.getStringSet(key, emptySet()).orEmpty()
    override fun putStringSet(key: String, value: Set<String>) { prefs.edit().putStringSet(key, value).apply() }

    override fun observeExternalChanges(onChange: () -> Unit) {
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        observer = o
        app.contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, o)
    }

    override fun close() {
        observer?.let { app.contentResolver.unregisterContentObserver(it) }
    }
}
