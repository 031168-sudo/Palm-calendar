package ru.palmdate.app

import android.Manifest
import android.content.ContentValues
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/**
 * Для прогона на эмуляторе: создаёт тестовый календарь «Работа» и кладёт в него события
 * на сегодня и завтра — звонок, задачу (повторяется), встречу, письмо, обычное событие, поездку.
 */
@RunWith(AndroidJUnit4::class)
class SeedCalendar {
    @get:Rule val perms: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    @Test fun seed() {
        val cr = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val account = "datebook-test"
        fun asSync(uri: android.net.Uri) = uri.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, account)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()

        cr.delete(asSync(Calendars.CONTENT_URI), "${Calendars.ACCOUNT_NAME} = ?", arrayOf(account))
        val calId = cr.insert(asSync(Calendars.CONTENT_URI), ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, account)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, "Работа")
            put(Calendars.CALENDAR_DISPLAY_NAME, "Работа")
            put(Calendars.CALENDAR_COLOR, 0xFF4FC3F7.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, account)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        })!!.lastPathSegment!!.toLong()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        fun ev(title: String, day: Long, h: Int, m: Int, minutes: Int, marker: String?, rrule: String? = null) {
            val start = today.plusDays(day).atTime(h, m).atZone(zone).toInstant().toEpochMilli()
            cr.insert(Events.CONTENT_URI, ContentValues().apply {
                put(Events.CALENDAR_ID, calId)
                put(Events.TITLE, title)
                marker?.let { put(Events.DESCRIPTION, it) }
                put(Events.DTSTART, start)
                put(Events.EVENT_TIMEZONE, zone.id)
                if (rrule == null) put(Events.DTEND, start + minutes * 60_000L)
                else { put(Events.RRULE, rrule); put(Events.DURATION, "P${minutes * 60}S") }
            })
        }
        ev("Звонок: Иван Петров", 0, 9, 30, 15, "[palm:t=CALL]")
        ev("Задача: отчёт за квартал", 0, 11, 0, 30, "свести цифры\n[palm:t=TASK]", rrule = "FREQ=DAILY")
        ev("Встреча: Ольга Смирнова", 0, 13, 0, 60, "[palm:t=MEETING]")
        ev("Ответить: Счёт за сентябрь", 0, 15, 0, 15, "[palm:t=MAIL;m=R%7C%3Cm1%40x.ru%3E]")
        ev("Планёрка", 0, 17, 0, 60, null)
        ev("Выезд: Санкт-Петербург", 1, 8, 0, 180, "[palm:t=TRIP]")
        ev("Встреча: Сергей Кузнецов", 1, 14, 0, 60, "[palm:t=MEETING]")
    }
}
