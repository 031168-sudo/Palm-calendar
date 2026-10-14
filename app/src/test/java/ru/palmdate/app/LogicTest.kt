package ru.palmdate.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.palmdate.app.data.AppSettings
import ru.palmdate.app.data.CalendarRepository
import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.MailInfo
import ru.palmdate.app.model.MailKind
import ru.palmdate.app.model.Outcome
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.model.matchRepeat
import ru.palmdate.app.model.repeatLabel
import java.time.Instant
import java.time.LocalDateTime

/** Логика без экрана: повторы, итоги, метки, письма. Robolectric — чтобы работали Uri и JSON Android. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogicTest {

    @Before fun russian() {
        Lang.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        Lang.setMode(LangMode.RU, persist = false)
    }

    @Test fun repeatRules() {
        assertNull(matchRepeat(null))
        assertEquals("FREQ=DAILY", matchRepeat("FREQ=DAILY"))
        assertEquals("FREQ=WEEKLY", matchRepeat("FREQ=WEEKLY;BYDAY=TU;WKST=MO"))
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", matchRepeat("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"))
        assertEquals("", matchRepeat("FREQ=DAILY;COUNT=5"))
        assertEquals("Каждые 2 дня", repeatLabel("FREQ=DAILY;INTERVAL=2"))
        assertEquals("Своё правило", repeatLabel("FREQ=DAILY;BYSETPOS=1"))
        assertEquals("", matchRepeat("FREQ=MONTHLY;BYDAY=2WE"))
        assertEquals("Каждый месяц", repeatLabel("FREQ=MONTHLY"))
    }

    @Test fun customRepeatRules() {
        val wed = java.time.LocalDate.of(2026, 10, 14)   // среда, вторая в месяце
        val z = java.time.ZoneId.of("Europe/Moscow")
        fun rr(spec: ru.palmdate.app.model.RepeatSpec, allDay: Boolean = false) = spec.toRrule(wed, allDay, z)
        val base = ru.palmdate.app.model.RepeatSpec.defaultFor(wed)
        // как наши кнопки
        assertEquals("FREQ=WEEKLY", rr(base))
        assertEquals("FREQ=MONTHLY", rr(base.copy(freq = ru.palmdate.app.model.RepFreq.MONTHLY)))
        // каждые 2 недели по пн, ср
        val s2 = base.copy(interval = 2, days = setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY))
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE", rr(s2))
        assertEquals("Каждые 2 недели по пн, ср", s2.describe())
        // вторая среда месяца и последняя
        val nth = base.copy(freq = ru.palmdate.app.model.RepFreq.MONTHLY, monthMode = ru.palmdate.app.model.MonthMode.NTH, nth = 2, weekday = java.time.DayOfWeek.WEDNESDAY)
        assertEquals("FREQ=MONTHLY;BYDAY=2WE", rr(nth))
        assertEquals("Каждую вторую среду", nth.describe())
        assertEquals("FREQ=MONTHLY;BYDAY=-1WE", rr(nth.copy(nth = -1)))
        assertEquals("Каждое последнее воскресенье", nth.copy(nth = -1, weekday = java.time.DayOfWeek.SUNDAY).describe())
        assertEquals("Каждый первый понедельник", nth.copy(nth = 1, weekday = java.time.DayOfWeek.MONDAY).describe())
        // окончание
        val cnt = base.copy(end = ru.palmdate.app.model.RepEnd.COUNT, count = 5)
        assertEquals("FREQ=WEEKLY;BYDAY=WE;COUNT=5", rr(cnt))
        assertEquals("Каждую неделю по ср, 5 раз", cnt.describe())
        val until = base.copy(end = ru.palmdate.app.model.RepEnd.DATE, until = java.time.LocalDate.of(2026, 12, 31))
        assertEquals("FREQ=WEEKLY;BYDAY=WE;UNTIL=20261231", rr(until, allDay = true))
        // обычное событие: UNTIL — конец дня по Москве в UTC
        assertEquals("FREQ=WEEKLY;BYDAY=WE;UNTIL=20261231T205959Z", rr(until))
        // разбор обратно
        listOf(
            "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE", "FREQ=MONTHLY;BYDAY=2WE", "FREQ=MONTHLY;BYDAY=-1WE",
            "FREQ=WEEKLY;BYDAY=WE;COUNT=5", "FREQ=DAILY;INTERVAL=3", "FREQ=YEARLY;INTERVAL=2", "FREQ=MONTHLY;BYMONTHDAY=5",
        ).forEach { r ->
            val sp = ru.palmdate.app.model.RepeatSpec.parse(r, wed, z)!!
            assertEquals(r, sp.toRrule(wed, false, z))
        }
        val parsedUntil = ru.palmdate.app.model.RepeatSpec.parse("FREQ=WEEKLY;BYDAY=WE;UNTIL=20261231T205959Z", wed, z)!!
        assertEquals(java.time.LocalDate.of(2026, 12, 31), parsedUntil.until)
        // правило Google с WKST — тоже понимаем; незнакомые части — нет
        assertEquals("Каждые 2 недели по вт", ru.palmdate.app.model.RepeatSpec.parse("FREQ=WEEKLY;WKST=MO;INTERVAL=2;BYDAY=TU", wed, z)!!.describe())
        assertEquals(null, ru.palmdate.app.model.RepeatSpec.parse("FREQ=MONTHLY;BYSETPOS=2;BYDAY=WE", wed, z))
    }

    @Test fun seriesSplitRules() {
        assertEquals("FREQ=DAILY;UNTIL=20261009", CalendarRepository.ruleWith("FREQ=DAILY;COUNT=5", "20261009"))
        assertEquals("FREQ=WEEKLY;UNTIL=20270101", CalendarRepository.ruleWith("FREQ=WEEKLY;UNTIL=20270101", keepUntil = true))
        assertEquals("FREQ=WEEKLY", CalendarRepository.ruleWith("FREQ=WEEKLY;UNTIL=20270101;COUNT=3"))
        // Весь день: серия заканчивается накануне
        val allDay = Instant.parse("2026-10-10T00:00:00Z").toEpochMilli()
        assertEquals("20261009", CalendarRepository.untilBefore(allDay, allDay = true))
        // Обычное: за секунду до этого раза, в UTC
        val timed = Instant.parse("2026-10-10T09:00:00Z").toEpochMilli()
        assertEquals("20261010T085959Z", CalendarRepository.untilBefore(timed, allDay = false))
    }

    @Test fun markerRoundTrip() {
        val m = CalendarRepository.Marker.make(EventType.MAIL, "0r1-ABC.key", "+7 916 123-45-67", "R|<abc;123]@mail.ru>")
        val p = CalendarRepository.Marker.parse("Заметка\n$m")!!
        assertEquals(EventType.MAIL, p.type)
        assertEquals("0r1-ABC.key", p.lookupKey)
        assertEquals("+7 916 123-45-67", p.phone)
        assertEquals("R|<abc;123]@mail.ru>", p.mail)
        assertEquals("Заметка", CalendarRepository.Marker.strip("Заметка\n$m"))
        // Старая метка без письма читается как раньше
        assertEquals(EventType.CALL, CalendarRepository.Marker.parse("[palm:t=CALL;c=k;p=1]")!!.type)
        // Бывший «Праздник» — теперь обычное событие
        assertNull(CalendarRepository.Marker.parse("[palm:t=BIRTHDAY]"))
    }

    @Test fun types() {
        assertNull(EventType.parse("BIRTHDAY"))
        assertEquals(EventType.MAIL, EventType.parse("MAIL"))
        assertTrue(EventType.BIRTHDAY !in EventType.pickable)
    }

    @Test fun outcomes() {
        assertEquals("Отправлено", Outcome.DONE.label(EventType.MAIL))
        assertEquals("Не отправлено", Outcome.NOT_DONE.label(EventType.MAIL))
        assertEquals("Состоялся", Outcome.DONE.label(EventType.CALL))
        assertEquals(Outcome.DONE, Outcome.fromLabel("Отправлено"))
        assertEquals(Outcome.NO_ANSWER, Outcome.fromLabel(" не дозвонился "))
        assertEquals(listOf(Outcome.DONE, Outcome.NOT_DONE, Outcome.RESCHEDULED, Outcome.CANCELLED), Outcome.optionsFor(EventType.MAIL))
    }

    private fun event(title: String, type: EventType?, mail: MailInfo? = null) = PalmEvent(
        eventId = 1, title = title, start = LocalDateTime.now(), end = LocalDateTime.now().plusHours(1),
        allDay = false, type = type, contact = null, note = null, color = 0, mail = mail,
    )

    @Test fun shortTitles() {
        assertEquals("Иван", event("Звонок: Иван", EventType.CALL).shortTitle)
        assertEquals("", event("Задача", EventType.TASK).shortTitle)
        assertEquals("Счёт за сентябрь", event("Ответить: Счёт за сентябрь", EventType.MAIL, MailInfo(MailKind.REPLY)).shortTitle)
        assertEquals("Иван", event("Написать: Иван", EventType.MAIL, MailInfo(MailKind.NEW)).shortTitle)
        assertEquals("Праздник: тест", event("Праздник: тест", null).shortTitle)
    }

    @Test fun plurals() {
        assertEquals("1 звонок", plu(R.plurals.count_calls, 1))
        assertEquals("3 звонка", plu(R.plurals.count_calls, 3))
        assertEquals("11 звонков", plu(R.plurals.count_calls, 11))
        assertEquals("21 звонок", plu(R.plurals.count_calls, 21))
        Lang.setMode(LangMode.EN, persist = false)
        assertEquals("1 call", plu(R.plurals.count_calls, 1))
        assertEquals("3 calls", plu(R.plurals.count_calls, 3))
    }

    @Test fun titlesRecognizedInBothLanguages() {
        Lang.setMode(LangMode.EN, persist = false)
        assertEquals("Ivan", event("Call: Ivan", EventType.CALL).shortTitle)
        assertEquals("Иван", event("Звонок: Иван", EventType.CALL).shortTitle)
    }

    @Test fun settingsJson() {
        val s = AppSettings(dayFrom = 7, weekStartsSunday = true)
        val back = AppSettings.fromJson(s.toJson())
        assertEquals(7, back.dayFrom)
        assertTrue(back.weekStartsSunday)
        // Старые настройки с полями встроенной почты читаются без ошибок
        val old = s.toJson().apply { put("mailMode", "BUILTIN"); put("mailEmail", "a@b.c") }
        assertEquals(7, AppSettings.fromJson(old).dayFrom)
    }
}
