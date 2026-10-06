package ru.palmdate.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import ru.palmdate.app.model.CalendarInfo
import ru.palmdate.app.model.ContactRef
import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.NewEvent
import ru.palmdate.app.model.PalmEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * События — в системном календаре Android (CalendarContract), поэтому они
 * синхронизируются с Google и видны в любом другом календаре.
 * Тип, контакт и номер — в своей базе (Room) + дублируются меткой в описании события,
 * чтобы их можно было восстановить, если база потеряется.
 */
class CalendarRepository(
    context: Context,
    private val links: LinkDao,
    private val contacts: ContactsRepository,
) {
    private val resolver = context.contentResolver
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun LocalDate.millis() = atStartOfDay(zone).toInstant().toEpochMilli()

    private fun instancesUri(from: Long, to: Long): Uri =
        Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()

    suspend fun eventsFor(day: LocalDate) = eventsBetween(day, day.plusDays(1))

    /** Все события в диапазоне дней [from, toExclusive), с типом, контактом и цветом. */
    suspend fun eventsBetween(from: LocalDate, toExclusive: LocalDate): List<PalmEvent> {
        data class Raw(
            val id: Long, val title: String, val begin: Long, val end: Long,
            val allDay: Boolean, val desc: String?, val color: Int,
            val calName: String, val account: String,
        )

        val raws = ArrayList<Raw>()
        resolver.query(
            instancesUri(from.millis(), toExclusive.millis()),
            arrayOf(
                Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END,
                Instances.ALL_DAY, Instances.DESCRIPTION, Instances.DISPLAY_COLOR,
                Instances.CALENDAR_DISPLAY_NAME, Instances.ACCOUNT_NAME,
            ),
            "${Instances.VISIBLE} = 1", null,
            "${Instances.BEGIN} ASC, ${Instances.ALL_DAY} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                raws += Raw(
                    c.getLong(0), c.getString(1) ?: "", c.getLong(2), c.getLong(3),
                    c.getInt(4) == 1, c.getString(5), c.getInt(6),
                    c.getString(7) ?: "", c.getString(8) ?: "",
                )
            }
        }

        val linkById = links.byIds(raws.map { it.id }.distinct()).associateBy { it.eventId }.toMutableMap()

        return raws.map { r ->
            val link = linkById[r.id] ?: Marker.parse(r.desc)?.let {
                // восстановили связь из метки в описании
                EventLink(r.id, it.type.name, it.lookupKey, it.phone).also { l -> links.upsert(l); linkById[r.id] = l }
            }
            val contact = link?.lookupKey?.let { key ->
                contacts.byLookupKey(key, link.phone ?: links.rememberedPhone(key))
            }
            // all-day события хранятся в UTC, обычные — в локальной зоне
            val tz = if (r.allDay) ZoneOffset.UTC else zone
            PalmEvent(
                eventId = r.id,
                title = r.title,
                start = LocalDateTime.ofInstant(Instant.ofEpochMilli(r.begin), tz),
                end = LocalDateTime.ofInstant(Instant.ofEpochMilli(r.end), tz),
                allDay = r.allDay,
                type = EventType.parse(link?.type),
                contact = contact,
                note = Marker.strip(r.desc)?.takeIf { it.isNotBlank() },
                color = r.color,
                calendarName = r.calName,
                accountName = r.account,
            )
        }
    }

    /** Дни года, в которые есть хоть одно событие — для вида "Год". Лёгкий запрос без контактов. */
    fun daysWithEvents(from: LocalDate, toExclusive: LocalDate): Set<LocalDate> {
        val days = HashSet<LocalDate>()
        resolver.query(
            instancesUri(from.millis(), toExclusive.millis()),
            arrayOf(Instances.BEGIN, Instances.END, Instances.ALL_DAY),
            "${Instances.VISIBLE} = 1", null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val tz = if (c.getInt(2) == 1) ZoneOffset.UTC else zone
                var d = Instant.ofEpochMilli(c.getLong(0)).atZone(tz).toLocalDate()
                val last = Instant.ofEpochMilli(maxOf(c.getLong(0), c.getLong(1) - 1)).atZone(tz).toLocalDate()
                while (!d.isAfter(last) && d.isBefore(toExclusive)) { days += d; d = d.plusDays(1) }
            }
        }
        return days
    }

    suspend fun create(e: NewEvent): Long {
        val name = e.contact?.name ?: e.title?.takeIf { it.isNotBlank() }
        val title = if (name != null) "${e.type.label}: $name" else e.type.label
        val desc = withMarker(e.note, e.type, e.contact?.lookupKey, e.contact?.phone)

        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, e.calendarId)
            put(Events.TITLE, title)
            put(Events.DESCRIPTION, desc)
            if (e.minutes == 0) { // событие на весь день (например, день рождения)
                val d = e.start.toLocalDate()
                put(Events.ALL_DAY, 1)
                put(Events.EVENT_TIMEZONE, "UTC")
                put(Events.DTSTART, d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                put(Events.DTEND, d.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                if (e.type == EventType.BIRTHDAY) put(Events.RRULE, "FREQ=YEARLY")
            } else {
                val start = e.start.atZone(zone).toInstant().toEpochMilli()
                put(Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(Events.DTSTART, start)
                put(Events.DTEND, start + e.minutes * 60_000L)
            }
            put(Events.HAS_ALARM, if (e.reminders.isEmpty()) 0 else 1)
        }
        val eventId = resolver.insert(Events.CONTENT_URI, values)?.let { ContentUris.parseId(it) }
            ?: error("Календарь отказался сохранить событие")

        insertReminders(eventId, e.reminders)
        links.upsert(EventLink(eventId, e.type.name, e.contact?.lookupKey, e.contact?.phone))
        e.contact?.let { c -> c.phone?.let { links.rememberPhone(ContactPhone(c.lookupKey, it)) } }
        return eventId
    }

    /**
     * Назначить (или сменить) событию тип, контакт и номер. Работает и для событий,
     * созданных не в приложении. type == null — снять тип.
     */
    suspend fun setLink(eventId: Long, type: EventType?, contact: ContactRef?) {
        if (type == null) links.delete(eventId)
        else links.upsert(EventLink(eventId, type.name, contact?.lookupKey, contact?.phone))
        if (contact?.phone != null) links.rememberPhone(ContactPhone(contact.lookupKey, contact.phone))

        // Переписываем метку в описании, чтобы связь пережила переустановку.
        // В чужих календарях (только чтение) это не получится — тогда связь живёт только в базе.
        runCatching {
            val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
            val desc = resolver.query(uri, arrayOf(Events.DESCRIPTION), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
            val newDesc = if (type == null) Marker.strip(desc) ?: ""
            else withMarker(Marker.strip(desc), type, contact?.lookupKey, contact?.phone)
            resolver.update(uri, ContentValues().apply { put(Events.DESCRIPTION, newDesc) }, null, null)
        }
    }

    fun reminders(eventId: Long): List<Int> {
        val list = ArrayList<Int>()
        resolver.query(
            Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES),
            "${Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()), "${Reminders.MINUTES} ASC",
        )?.use { c -> while (c.moveToNext()) list += c.getInt(0) }
        return list.distinct()
    }

    fun setReminders(eventId: Long, minutes: List<Int>) {
        resolver.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()))
        insertReminders(eventId, minutes)
        resolver.update(
            ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
            ContentValues().apply { put(Events.HAS_ALARM, if (minutes.isEmpty()) 0 else 1) }, null, null,
        )
    }

    private fun insertReminders(eventId: Long, minutes: List<Int>) {
        // Google принимает до 5 напоминаний на событие
        minutes.distinct().sorted().take(5).forEach { m ->
            resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
                put(Reminders.EVENT_ID, eventId)
                put(Reminders.METHOD, Reminders.METHOD_ALERT)
                put(Reminders.MINUTES, m)
            })
        }
    }

    suspend fun delete(eventId: Long) {
        resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
        links.delete(eventId)
    }

    /** Все календари с правом записи: по аккаунтам, внутри аккаунта основной первым. */
    fun writableCalendars(): List<CalendarInfo> {
        val list = ArrayList<CalendarInfo>()
        resolver.query(
            Calendars.CONTENT_URI,
            arrayOf(
                Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
                Calendars.ACCOUNT_TYPE, Calendars.CALENDAR_COLOR, Calendars.IS_PRIMARY,
            ),
            "${Calendars.VISIBLE} = 1 AND ${Calendars.CALENDAR_ACCESS_LEVEL} >= ${Calendars.CAL_ACCESS_CONTRIBUTOR}",
            null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                list += CalendarInfo(
                    id = c.getLong(0),
                    name = c.getString(1) ?: c.getString(2) ?: "Календарь",
                    accountName = c.getString(2) ?: "",
                    accountType = c.getString(3) ?: "",
                    color = c.getInt(4),
                    isPrimary = c.getInt(5) == 1,
                )
            }
        }
        return list.sortedWith(compareBy({ it.accountName.lowercase() }, { !it.isPrimary }, { it.name.lowercase() }))
    }

    private fun withMarker(note: String?, type: EventType, lookupKey: String?, phone: String?) =
        listOfNotNull(note?.takeIf { it.isNotBlank() }, Marker.make(type, lookupKey, phone)).joinToString("\n")

    /**
     * Метка в описании события: [palm:t=CALL;c=<lookupKey>;p=<номер>]
     * Видна в Google Calendar одной строкой, но позволяет восстановить тип, контакт и номер.
     */
    private object Marker {
        private val re = Regex("""\[palm:t=(\w+)(?:;c=([^;\]]*))?(?:;p=([^;\]]*))?]""")

        data class Parsed(val type: EventType, val lookupKey: String?, val phone: String?)

        fun make(type: EventType, lookupKey: String?, phone: String?) =
            "[palm:t=${type.name}" +
                (lookupKey?.let { ";c=" + Uri.encode(it) } ?: "") +
                (phone?.let { ";p=" + Uri.encode(it) } ?: "") + "]"

        fun parse(desc: String?): Parsed? {
            val m = desc?.let { re.find(it) } ?: return null
            val type = EventType.parse(m.groupValues[1]) ?: return null
            fun g(i: Int) = m.groupValues[i].takeIf { it.isNotEmpty() }?.let { Uri.decode(it) }
            return Parsed(type, g(2), g(3))
        }

        fun strip(desc: String?) = desc?.replace(re, "")?.trim()
    }

    companion object {
        /** Открыть событие в системном календаре. */
        fun eventUri(eventId: Long): Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
    }
}
