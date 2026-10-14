package ru.palmdate.app

import ru.palmdate.app.data.toKx
import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/** Свои правила повтора принимает настоящий календарь Android и считает по ним нужное число разов. Запускается после SeedCalendar. */
@RunWith(AndroidJUnit4::class)
class RepeatRuleTest {
    @get:Rule val perms: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val cr = ctx.contentResolver
    private val zone = ZoneId.systemDefault()

    private fun calendarId(): Long = cr.query(
        Calendars.CONTENT_URI, arrayOf(Calendars._ID), "${Calendars.ACCOUNT_NAME} = ?", arrayOf("datebook-test"), null,
    )!!.use { check(it.moveToFirst()) { "нет тестового календаря" }; it.getLong(0) }

    /** Одиночное событие сегодня в 10:00 → задаём правило через приложение → считаем разы в ближайшие 200 дней. */
    private fun count(title: String, rule: (LocalDate) -> String): Int = runBlocking {
        val today = LocalDate.now()
        val start = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val id = ContentUris.parseId(cr.insert(Events.CONTENT_URI, ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId()); put(Events.TITLE, title)
            put(Events.DTSTART, start); put(Events.DTEND, start + 3_600_000L); put(Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        })!!)
        try {
            val repo = CalendarRepository(ctx, AppDb.get(ctx).links(), ContactsRepository(ctx))
            val e = repo.eventsBetween(today.toKx(), today.plusDays(1).toKx()).first { it.eventId == id }
            repo.setRepeat(e, rule(today))
            val uri = Instances.CONTENT_URI.buildUpon().let {
                ContentUris.appendId(it, start - 1000); ContentUris.appendId(it, start + 200L * 86_400_000L); it.build()
            }
            cr.query(uri, arrayOf(Instances.BEGIN), "${Instances.TITLE} = ?", arrayOf(title), null)!!.use { it.count }
        } finally {
            cr.delete(Events.CONTENT_URI, "${Events.TITLE} = ?", arrayOf(title))
        }
    }

    @Test fun everyTwoWeeksFiveTimes() = assertEquals(5, count("RepA") { "FREQ=WEEKLY;INTERVAL=2;BYDAY=${it.dayOfWeek.name.take(2)};COUNT=5" })
    @Test fun dailyUntilDate() = assertEquals(4, count("RepB") { d ->
        "FREQ=DAILY;UNTIL=" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .format(d.plusDays(3).atTime(23, 59, 59).atZone(zone).withZoneSameInstant(java.time.ZoneOffset.UTC))
    })
    @Test fun nthWeekdayMonthly() = assertEquals(3, count("RepC") { d ->
        val nth = (d.dayOfMonth - 1) / 7 + 1
        "FREQ=MONTHLY;BYDAY=${nth.coerceAtMost(4)}${d.dayOfWeek.name.take(2)};COUNT=3"
    })
}
