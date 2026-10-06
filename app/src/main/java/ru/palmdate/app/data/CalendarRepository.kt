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
 * Тип и контакт — в своей базе (Room) + дублируются меткой в описании события,
 * чтобы их можно было восстановить, если база потеряется.
 */
class CalendarRepository(
    context: Context,
    private val links: LinkDao,
    private val contacts: ContactsRepository,
) {
    private val resolver = context.contentResolver
    private val zone: ZoneId get() = ZoneId.systemDefault()

    suspend fun eventsFor(day: LocalDate): List<PalmEvent> {
        val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()

        data class Raw(val id: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean, val desc: String?)

        val raws = ArrayList<Raw>()
        resolver.query(
            uri,
            arrayOf(Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.DESCRIPTION),
            "${Instances.VISIBLE} = 1", null,
            "${Instances.ALL_DAY} DESC, ${Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                raws += Raw(c.getLong(0), c.getString(1) ?: "", c.getLong(2), c.getLong(3), c.getInt(4) == 1, c.getString(5))
            }
        }

        val linkById = links.byIds(raws.map { it.id }).associateBy { it.eventId }

        return raws.map { r ->
            val marker = Marker.parse(r.desc)
            val link = linkById[r.id]
                ?: marker?.let { EventLink(r.id, it.type.name, it.lookupKey).also { l -> links.upsert(l) } } // восстановили из метки
            // all-day события хранятся в UTC, обычные — в локальной зоне
            val tz = if (r.allDay) ZoneOffset.UTC else zone
            PalmEvent(
                eventId = r.id,
                title = r.title,
                start = LocalDateTime.ofInstant(Instant.ofEpochMilli(r.begin), tz),
                end = LocalDateTime.ofInstant(Instant.ofEpochMilli(r.end), tz),
                allDay = r.allDay,
                type = EventType.parse(link?.type),
                contact = link?.lookupKey?.let { contacts.byLookupKey(it) },
                note = Marker.strip(r.desc)?.takeIf { it.isNotBlank() },
            )
        }
    }

    suspend fun create(e: NewEvent): Long {
        val calId = e.calendarId

        val name = e.contact?.name ?: e.title?.takeIf { it.isNotBlank() }
        val title = if (name != null) "${e.type.label}: $name" else e.type.label
        val desc = listOfNotNull(e.note?.takeIf { it.isNotBlank() }, Marker.make(e.type, e.contact?.lookupKey))
            .joinToString("\n")

        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, calId)
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
            put(Events.HAS_ALARM, 1)
        }
        val eventId = resolver.insert(Events.CONTENT_URI, values)?.let { ContentUris.parseId(it) }
            ?: error("Календарь отказался сохранить событие")

        resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
            put(Reminders.EVENT_ID, eventId)
            put(Reminders.METHOD, Reminders.METHOD_ALERT)
            put(Reminders.MINUTES, if (e.type == EventType.CALL) 5 else 15)
        })

        links.upsert(EventLink(eventId, e.type.name, e.contact?.lookupKey))
        return eventId
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

    /**
     * Метка в описании события: [palm:t=CALL;c=<lookupKey>]
     * Видна в Google Calendar одной строкой, но позволяет восстановить тип и контакт.
     */
    private object Marker {
        private val re = Regex("""\[palm:t=(\w+)(?:;c=([^\]]*))?]""")

        data class Parsed(val type: EventType, val lookupKey: String?)

        fun make(type: EventType, lookupKey: String?) =
            "[palm:t=${type.name}" + (lookupKey?.let { ";c=" + Uri.encode(it) } ?: "") + "]"

        fun parse(desc: String?): Parsed? {
            val m = desc?.let { re.find(it) } ?: return null
            val type = EventType.parse(m.groupValues[1]) ?: return null
            return Parsed(type, m.groupValues[2].takeIf { it.isNotEmpty() }?.let { Uri.decode(it) })
        }

        fun strip(desc: String?) = desc?.replace(re, "")?.trim()
    }

    companion object {
        /** Открыть событие в системном календаре. */
        fun eventUri(eventId: Long): Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
    }
}
