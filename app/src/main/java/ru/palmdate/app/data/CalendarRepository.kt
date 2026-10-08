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
import ru.palmdate.app.model.Outcome
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

    /** Календари, которые пользователь скрыл галочками. "Невидимость" по мнению Android не учитываем. */
    @Volatile var hiddenCalendars: Set<Long> = emptySet()

    private fun shownFilter(): String =
        hiddenCalendars.takeIf { it.isNotEmpty() }
            ?.let { "${Instances.CALENDAR_ID} NOT IN (${it.joinToString(",")})" } ?: "1"

    private fun instancesUri(from: Long, to: Long): Uri =
        Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()

    suspend fun eventsFor(day: LocalDate) = eventsBetween(day, day.plusDays(1))

    /**
     * История контакта: все разы всех событий, привязанных к нему, за 3 года назад и год вперёд.
     * Повторяющиеся события разворачиваются в отдельные разы.
     */
    suspend fun historyFor(lookupKey: String): List<PalmEvent> {
        val ids = links.byContact(lookupKey).map { it.eventId }
        val today = LocalDate.now()
        val from = today.minusYears(3)
        val to = today.plusYears(1)
        val events = ids.chunked(500).flatMap { chunk -> eventsBetween(from, to, onlyIds = chunk) }
        val bdays = birthdayEvents(from, to, onlyKey = lookupKey)
        return (events + bdays).sortedBy { it.start }
    }

    /** Все события в диапазоне дней [from, toExclusive), с типом, контактом и цветом. */
    suspend fun eventsBetween(
        from: LocalDate,
        toExclusive: LocalDate,
        onlyIds: List<Long>? = null,
    ): List<PalmEvent> {
        data class Raw(
            val id: Long, val title: String, val begin: Long, val end: Long,
            val allDay: Boolean, val desc: String?, val color: Int,
            val calName: String, val calId: Long, val rrule: String?,
        ) { val recurring get() = !rrule.isNullOrEmpty() }

        val raws = ArrayList<Raw>()
        resolver.query(
            instancesUri(from.millis(), toExclusive.millis()),
            arrayOf(
                Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END,
                Instances.ALL_DAY, Instances.DESCRIPTION, Instances.DISPLAY_COLOR,
                Instances.CALENDAR_DISPLAY_NAME, Instances.CALENDAR_ID, Instances.RRULE,
            ),
            shownFilter() +
                (onlyIds?.let { " AND ${Instances.EVENT_ID} IN (${it.joinToString(",")})" } ?: ""),
            null,
            "${Instances.BEGIN} ASC, ${Instances.ALL_DAY} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                raws += Raw(
                    c.getLong(0), c.getString(1) ?: "", c.getLong(2), c.getLong(3),
                    c.getInt(4) == 1, c.getString(5), c.getInt(6),
                    c.getString(7) ?: "", c.getLong(8), c.getString(9),
                )
            }
        }

        val ids = raws.map { it.id }.distinct()
        val linkById = links.byIds(ids).associateBy { it.eventId }.toMutableMap()
        val outcomeByKey = links.outcomes(ids).associateBy { it.eventId to it.instanceStart }
        val accountByCal = calendarAccounts()

        val calendarEvents = raws.map { r ->
            // Итог: из базы, а для обычных (не повторяющихся) событий — восстанавливаем из строки в описании
            var outcome = outcomeByKey[r.id to r.begin]
            if (outcome == null && !r.recurring) {
                OutcomeLine.parse(r.desc)?.let { (o, n) ->
                    outcome = OutcomeRow(r.id, r.begin, o.name, n).also { row -> links.setOutcome(row) }
                }
            }
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
                note = OutcomeLine.strip(Marker.strip(r.desc))?.takeIf { it.isNotBlank() },
                color = r.color,
                calendarName = r.calName,
                accountName = accountByCal[r.calId] ?: "",
                calendarId = r.calId,
                recurring = r.recurring,
                rrule = r.rrule,
                instanceStart = r.begin,
                outcome = Outcome.parse(outcome?.status),
                outcomeNote = outcome?.note,
            )
        }
        if (onlyIds != null) return calendarEvents

        // Дни рождения из контактов — без дублей с календарём "Дни рождения"
        val bdays = birthdayEvents(from, toExclusive).filterNot { b ->
            val name = b.contact?.name?.lowercase() ?: return@filterNot false
            calendarEvents.any { it.allDay && it.start.toLocalDate() == b.start.toLocalDate() && it.title.lowercase().contains(name) }
        }
        return (calendarEvents + bdays).sortedWith(compareBy({ it.start }, { !it.allDay }))
    }

    /**
     * Дни рождения из карточек контактов как события на весь день.
     * У каждого свой постоянный отрицательный id, чтобы не путать с событиями календаря.
     */
    private suspend fun birthdayEvents(from: LocalDate, toExclusive: LocalDate, onlyKey: String? = null): List<PalmEvent> {
        if (!SettingsStore.current.birthdaysShown) return emptyList()
        val list = contacts.birthdays().filter { onlyKey == null || it.lookupKey == onlyKey }
        if (list.isEmpty()) return emptyList()
        val ids = list.map { birthdayId(it.lookupKey) }
        val outcomeByKey = links.outcomes(ids).associateBy { it.eventId to it.instanceStart }
        val result = ArrayList<PalmEvent>()
        for (b in list) {
            for (year in from.year..toExclusive.year) {
                val date = runCatching {
                    // 29 февраля в невисокосный год — 28-го
                    java.time.LocalDate.of(year, b.month, minOf(b.day, java.time.YearMonth.of(year, b.month).lengthOfMonth()))
                }.getOrNull() ?: continue
                if (date < from || date >= toExclusive) continue
                val id = birthdayId(b.lookupKey)
                val instance = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                val age = b.year?.let { year - it }?.takeIf { it in 1..129 }
                val o = outcomeByKey[id to instance]
                result += PalmEvent(
                    eventId = id,
                    title = "День рождения",
                    start = date.atStartOfDay(),
                    end = date.plusDays(1).atStartOfDay(),
                    allDay = true,
                    type = EventType.BIRTHDAY,
                    contact = contacts.byLookupKey(b.lookupKey, links.rememberedPhone(b.lookupKey)),
                    note = age?.let { "исполняется $it" },
                    color = 0xFFD35400.toInt(),
                    calendarName = "Контакты",
                    recurring = true,          // итог хранится только в приложении
                    instanceStart = instance,
                    outcome = Outcome.parse(o?.status),
                    outcomeNote = o?.note,
                    fromContacts = true,
                )
            }
        }
        return result
    }

    private fun birthdayId(lookupKey: String): Long = -(1L + (lookupKey.hashCode().toLong() and 0x7fffffffL))

    /**
     * Поставить или снять итог (outcome == null) у конкретного раза события.
     * У обычного события итог дописывается строкой "Итог: …" в описание — его видно в Google Календаре.
     * У повторяющегося описание общее на всю серию, поэтому итог хранится только в приложении.
     */
    suspend fun setOutcome(e: PalmEvent, outcome: Outcome?, note: String?) {
        if (outcome == null) links.clearOutcome(e.eventId, e.instanceStart)
        else links.setOutcome(OutcomeRow(e.eventId, e.instanceStart, outcome.name, note?.trim()?.takeIf { it.isNotEmpty() }))

        if (e.recurring) return
        runCatching {
            val uri = ContentUris.withAppendedId(Events.CONTENT_URI, e.eventId)
            val desc = resolver.query(uri, arrayOf(Events.DESCRIPTION), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
            val newDesc = OutcomeLine.replace(desc, outcome?.let { OutcomeLine.make(it, e.type, note) })
            resolver.update(uri, ContentValues().apply { put(Events.DESCRIPTION, newDesc) }, null, null)
        }
    }

    /** Строка итога в описании события: "Итог: Не дозвонился — перезвонить после обеда". */
    private object OutcomeLine {
        private val re = Regex("""(?m)^Итог: ([^\n—]+?)(?: — ([^\n]*))?\s*$""")

        fun make(o: Outcome, type: EventType?, note: String?) =
            "Итог: " + o.label(type) + (note?.trim()?.takeIf { it.isNotEmpty() }?.let { " — $it" } ?: "")

        fun parse(desc: String?): Pair<Outcome, String?>? {
            val m = desc?.let { re.find(it) } ?: return null
            val o = Outcome.fromLabel(m.groupValues[1]) ?: return null
            return o to m.groupValues[2].takeIf { it.isNotBlank() }
        }

        fun strip(desc: String?) = desc?.replace(re, "")?.trim()

        fun find(desc: String): String? = re.find(desc)?.value?.trim()

        /** Заменить строку итога (или убрать, если line == null), сохранив остальное описание и метку. */
        fun replace(desc: String?, line: String?): String {
            val rest = desc?.replace(re, "")?.trim()?.takeIf { it.isNotEmpty() }
            return listOfNotNull(line, rest).joinToString("\n")
        }
    }

    /** id календаря → аккаунт (для подписи в подробностях). */
    private fun calendarAccounts(): Map<Long, String> {
        val map = HashMap<Long, String>()
        resolver.query(Calendars.CONTENT_URI, arrayOf(Calendars._ID, Calendars.ACCOUNT_NAME), null, null, null)
            ?.use { c -> while (c.moveToNext()) map[c.getLong(0)] = c.getString(1) ?: "" }
        return map
    }

    /**
     * Перенести событие в другой календарь. Android не умеет "перемещать", поэтому:
     * копия в новом календаре (время, текст, повтор, место, напоминания, тип и контакт) + удаление оригинала.
     * Повторяющееся событие переносится всей серией. Нельзя: одно повторение из серии и встречи с гостями.
     * Возвращает id нового события.
     */
    suspend fun move(eventId: Long, targetCalendarId: Long): Long {
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
        val cols = arrayOf(
            Events.TITLE, Events.DESCRIPTION, Events.DTSTART, Events.DTEND, Events.DURATION,
            Events.ALL_DAY, Events.EVENT_TIMEZONE, Events.EVENT_END_TIMEZONE, Events.RRULE, Events.RDATE,
            Events.EXRULE, Events.EXDATE, Events.EVENT_LOCATION, Events.AVAILABILITY, Events.ACCESS_LEVEL,
            Events.ORIGINAL_ID, Events.CALENDAR_ID,
        )
        val values = ContentValues()
        var sourceCal = -1L
        resolver.query(uri, cols, null, null, null)?.use { c ->
            if (!c.moveToFirst()) error("Событие не найдено")
            if (!c.isNull(15)) error("Одно повторение из серии перенести нельзя — откройте всю серию")
            sourceCal = c.getLong(16)
            for (i in 0 until 15) {
                if (c.isNull(i)) continue
                when (c.getType(i)) {
                    android.database.Cursor.FIELD_TYPE_INTEGER -> values.put(cols[i], c.getLong(i))
                    else -> values.put(cols[i], c.getString(i))
                }
            }
        } ?: error("Событие не найдено")
        if (sourceCal == targetCalendarId) return eventId

        // Встречи с гостями: при удалении оригинала гости получат отмену — так не переносим
        val guests = resolver.query(
            CalendarContract.Attendees.CONTENT_URI, arrayOf(CalendarContract.Attendees.ATTENDEE_EMAIL),
            "${CalendarContract.Attendees.EVENT_ID} = ?", arrayOf(eventId.toString()), null,
        )?.use { it.count } ?: 0
        if (guests > 1) error("Встречи с гостями переносить нельзя — гости получат отмену")

        // У повторяющихся событий вместо DTEND должна быть DURATION
        if (values.containsKey(Events.RRULE)) {
            if (!values.containsKey(Events.DURATION)) {
                val start = values.getAsLong(Events.DTSTART)
                val end = values.getAsLong(Events.DTEND)
                if (start != null && end != null) values.put(Events.DURATION, "P${(end - start) / 1000}S")
            }
            values.remove(Events.DTEND)
        } else {
            values.remove(Events.DURATION)
        }
        values.put(Events.CALENDAR_ID, targetCalendarId)

        val reminders = reminders(eventId)
        val newId = resolver.insert(Events.CONTENT_URI, values)?.let { ContentUris.parseId(it) }
            ?: error("Не удалось создать событие в новом календаре")
        insertReminders(newId, reminders)

        // Тип, контакт и номер переезжают вместе с событием
        links.byIds(listOf(eventId)).firstOrNull()?.let { links.upsert(it.copy(eventId = newId)) }
        links.outcomes(listOf(eventId)).forEach { links.setOutcome(it.copy(eventId = newId)) }
        links.clearOutcomes(eventId)

        resolver.delete(uri, null, null)
        links.delete(eventId)
        return newId
    }

    /** Дни года, в которые есть хоть одно событие — для вида "Год". Лёгкий запрос без контактов. */
    fun daysWithEvents(from: LocalDate, toExclusive: LocalDate): Set<LocalDate> {
        val days = HashSet<LocalDate>()
        resolver.query(
            instancesUri(from.millis(), toExclusive.millis()),
            arrayOf(Instances.BEGIN, Instances.END, Instances.ALL_DAY),
            shownFilter(), null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val tz = if (c.getInt(2) == 1) ZoneOffset.UTC else zone
                var d = Instant.ofEpochMilli(c.getLong(0)).atZone(tz).toLocalDate()
                val last = Instant.ofEpochMilli(maxOf(c.getLong(0), c.getLong(1) - 1)).atZone(tz).toLocalDate()
                while (!d.isAfter(last) && d.isBefore(toExclusive)) { days += d; d = d.plusDays(1) }
            }
        }
        if (SettingsStore.current.birthdaysShown) contacts.birthdays().forEach { b ->
            for (year in from.year..toExclusive.year) {
                runCatching {
                    java.time.LocalDate.of(year, b.month, minOf(b.day, java.time.YearMonth.of(year, b.month).lengthOfMonth()))
                }.getOrNull()?.takeIf { it >= from && it < toExclusive }?.let { days += it }
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
            } else {
                val start = e.start.atZone(zone).toInstant().toEpochMilli()
                put(Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(Events.DTSTART, start)
                put(Events.DTEND, start + e.minutes * 60_000L)
            }
            // Повтор: у повторяющегося события вместо конца — длительность
            e.rrule?.let { rule ->
                remove(Events.DTEND)
                put(Events.RRULE, rule)
                put(Events.DURATION, if (e.minutes == 0) "P1D" else "P${e.minutes * 60}S")
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

    /**
     * Сменить повтор серии (rrule == null — сделать событие одиночным).
     * Android требует: у повторяющегося события DURATION вместо DTEND, у одиночного — наоборот.
     */
    fun setRepeat(eventId: Long, rrule: String?) {
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
        var start = 0L
        var end = 0L
        var duration: String? = null
        var allDay = false
        resolver.query(uri, arrayOf(Events.DTSTART, Events.DTEND, Events.DURATION, Events.ALL_DAY), null, null, null)
            ?.use { c ->
                if (!c.moveToFirst()) error("Событие не найдено")
                start = c.getLong(0); end = c.getLong(1); duration = c.getString(2); allDay = c.getInt(3) == 1
            } ?: error("Событие не найдено")
        val lengthMs = when {
            end > start -> end - start
            duration != null -> parseDuration(duration!!) ?: 3_600_000L
            allDay -> 86_400_000L
            else -> 3_600_000L
        }
        val values = ContentValues()
        if (rrule == null) {
            values.putNull(Events.RRULE)
            values.putNull(Events.DURATION)
            values.put(Events.DTEND, start + lengthMs)
        } else {
            values.put(Events.RRULE, rrule)
            values.putNull(Events.DTEND)
            values.put(Events.DURATION, if (allDay) "P${maxOf(1L, lengthMs / 86_400_000L)}D" else "P${lengthMs / 1000}S")
        }
        if (resolver.update(uri, values, null, null) <= 0) error("Не удалось изменить повтор")
    }

    /** P3600S, PT1H, P1D, P1W → миллисекунды. */
    private fun parseDuration(d: String): Long? {
        val m = Regex("P(?:(\\d+)W)?(?:(\\d+)D)?(?:T?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?").matchEntire(d.trim()) ?: return null
        fun g(i: Int) = m.groupValues[i].toLongOrNull() ?: 0L
        return ((g(1) * 7 + g(2)) * 86_400L + g(3) * 3600L + g(4) * 60L + g(5)) * 1000L
    }

    /* ---------- Правка события из подробностей ---------- */

    fun setTitle(eventId: Long, title: String) {
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
        resolver.update(uri, ContentValues().apply { put(Events.TITLE, title) }, null, null)
    }

    /**
     * Новая заметка. Метка DateBook и строка итога в описании сохраняются.
     */
    fun setNote(eventId: Long, note: String?) {
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
        val desc = resolver.query(uri, arrayOf(Events.DESCRIPTION), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
        val outcomeLine = desc?.let { OutcomeLine.find(it) }
        val marker = desc?.let { Marker.find(it) }
        val newDesc = listOfNotNull(outcomeLine, note?.trim()?.takeIf { it.isNotEmpty() }, marker).joinToString("\n")
        resolver.update(uri, ContentValues().apply { put(Events.DESCRIPTION, newDesc) }, null, null)
    }

    /**
     * Новое время. minutes == 0 — на весь день.
     * У повторяющегося события сдвигается вся серия на ту же разницу, что и этот раз.
     */
    fun setTime(e: PalmEvent, start: LocalDateTime, minutes: Int) {
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, e.eventId)
        val allDay = minutes == 0
        val values = ContentValues()
        values.put(Events.ALL_DAY, if (allDay) 1 else 0)
        values.put(Events.EVENT_TIMEZONE, if (allDay) "UTC" else TimeZone.getDefault().id)
        if (!e.recurring) {
            if (allDay) {
                val d = start.toLocalDate()
                values.put(Events.DTSTART, d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                values.put(Events.DTEND, d.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            } else {
                val ms = start.atZone(zone).toInstant().toEpochMilli()
                values.put(Events.DTSTART, ms)
                values.put(Events.DTEND, ms + minutes * 60_000L)
            }
        } else {
            val seriesStart = resolver.query(uri, arrayOf(Events.DTSTART), null, null, null)
                ?.use { if (it.moveToFirst()) it.getLong(0) else null } ?: error("Событие не найдено")
            if (allDay) {
                val days = java.time.temporal.ChronoUnit.DAYS.between(e.start.toLocalDate(), start.toLocalDate())
                val seriesDate = Instant.ofEpochMilli(seriesStart).atZone(if (e.allDay) ZoneOffset.UTC else zone).toLocalDate()
                values.put(Events.DTSTART, seriesDate.plusDays(days).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                values.put(Events.DURATION, "P1D")
            } else {
                val oldInstance = e.start.atZone(if (e.allDay) ZoneOffset.UTC else zone).toInstant().toEpochMilli()
                val delta = start.atZone(zone).toInstant().toEpochMilli() - oldInstance
                values.put(Events.DTSTART, seriesStart + delta)
                values.put(Events.DURATION, "P${minutes * 60}S")
            }
            values.putNull(Events.DTEND)
        }
        if (resolver.update(uri, values, null, null) <= 0) error("Не удалось изменить время")
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
        links.clearOutcomes(eventId)
    }

    /**
     * Все календари телефона с пометками: можно ли записывать, синхронизируется ли, локальный ли.
     * По аккаунтам; внутри аккаунта — пригодные первыми, основной впереди.
     */
    fun allCalendars(): List<CalendarInfo> {
        val list = ArrayList<CalendarInfo>()
        resolver.query(
            Calendars.CONTENT_URI,
            arrayOf(
                Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
                Calendars.ACCOUNT_TYPE, Calendars.CALENDAR_COLOR, Calendars.IS_PRIMARY,
                Calendars.CALENDAR_ACCESS_LEVEL, Calendars.SYNC_EVENTS,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val accountType = c.getString(3) ?: ""
                val accountName = c.getString(2) ?: ""
                val local = accountType == CalendarContract.ACCOUNT_TYPE_LOCAL ||
                    accountType.contains("local", ignoreCase = true) ||
                    accountName.contains("local", ignoreCase = true)
                list += CalendarInfo(
                    id = c.getLong(0),
                    name = c.getString(1) ?: accountName.ifEmpty { "Календарь" },
                    accountName = accountName,
                    accountType = accountType,
                    color = c.getInt(4),
                    isPrimary = c.getInt(5) == 1,
                    writable = c.getInt(6) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
                    synced = local || c.getInt(7) == 1,
                    local = local,
                )
            }
        }
        return list.sortedWith(
            compareBy<CalendarInfo>({ it.local }, { it.accountName.lowercase() }, { !it.usable }, { !it.isPrimary }, { it.name.lowercase() }),
        )
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

        fun find(desc: String): String? = re.find(desc)?.value
    }

    companion object {
        /** Открыть событие в системном календаре. */
        fun eventUri(eventId: Long): Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
    }
}
