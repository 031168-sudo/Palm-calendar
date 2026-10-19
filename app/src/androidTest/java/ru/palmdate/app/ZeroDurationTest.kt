package ru.palmdate.app

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.palmdate.app.data.AppDb
import ru.palmdate.app.data.CalendarRepository
import ru.palmdate.app.data.ContactsRepository
import kotlinx.datetime.LocalDate
import ru.palmdate.app.data.toJava
import ru.palmdate.app.time.*
import java.time.ZoneId
import java.util.TimeZone

/** Событие 12:00–12:00 (длительность 0), повторяется раз в месяц: время должно меняться. Запускается после SeedCalendar. */
@RunWith(AndroidJUnit4::class)
class ZeroDurationTest {
    @get:Rule val perms: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val cr = ctx.contentResolver
    private val zone = ZoneId.systemDefault()

    private fun calendarId(): Long = cr.query(
        Calendars.CONTENT_URI, arrayOf(Calendars._ID), "${Calendars.ACCOUNT_NAME} = ?", arrayOf("datebook-test"), null,
    )!!.use { check(it.moveToFirst()) { "нет тестового календаря" }; it.getLong(0) }

    private fun insertZero(title: String, firstDay: LocalDate, rrule: String?): Long {
        val start = firstDay.atTime(12, 0).toJava().atZone(zone).toInstant().toEpochMilli()
        val uri = cr.insert(Events.CONTENT_URI, ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId())
            put(Events.TITLE, title)
            put(Events.DTSTART, start)
            put(Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            if (rrule == null) put(Events.DTEND, start)
            else { put(Events.RRULE, rrule); put(Events.DURATION, "P0S") }
        })
        return ContentUris.parseId(uri!!)
    }

    private fun check(title: String, rrule: String?, firstDay: LocalDate) = runBlocking {
        val id = try { insertZero(title, firstDay, rrule) } catch (e: Throwable) { throw AssertionError("вставка нулевого: $e", e) }
        try {
            val repo = CalendarRepository(ctx, AppDb.get(ctx).links(), ContactsRepository(ctx))
            val today = LocalDate.now()
            val e = repo.eventsBetween(today.minusDays(1), today.plusDays(40)).first { it.eventId == id && !it.start.toLocalDate().isBefore(today) }
            assertEquals("до: длина $title", 0L, minutesBetween(e.start, e.end))
            try { repo.setTime(e, e.start, 60, ru.palmdate.app.model.DeleteScope.ALL) } catch (t: Throwable) { throw AssertionError("setTime($title): $t", t) }
            val e2 = repo.eventsBetween(today.minusDays(1), today.plusDays(40)).first { it.eventId != -1L && it.title == e.title && it.start == e.start }
            assertEquals("после: длина $title", 60L, minutesBetween(e2.start, e2.end))
        } finally {
            cr.delete(Events.CONTENT_URI, "${Events.TITLE} = ?", arrayOf(title))
        }
    }

    @Test fun monthlyZeroLength() = check("ZeroMonthly", "FREQ=MONTHLY", LocalDate.now().plusMonths(-2))
    @Test fun singleZeroLength() = check("ZeroSingle", null, LocalDate.now())
}
