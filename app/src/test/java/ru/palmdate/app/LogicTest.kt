package ru.palmdate.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.palmdate.app.data.Addr
import ru.palmdate.app.data.AppSettings
import ru.palmdate.app.data.CalendarRepository
import ru.palmdate.app.data.ForwardFrom
import ru.palmdate.app.data.MailAttachment
import ru.palmdate.app.data.MailClient
import ru.palmdate.app.data.MailException
import ru.palmdate.app.data.MailHeader
import ru.palmdate.app.data.MailMessage
import ru.palmdate.app.data.MailMode
import ru.palmdate.app.data.Outgoing
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

    @Before fun russian() = Lang.setMode(LangMode.RU, persist = false)

    @Test fun repeatRules() {
        assertNull(matchRepeat(null))
        assertEquals("FREQ=DAILY", matchRepeat("FREQ=DAILY"))
        assertEquals("FREQ=WEEKLY", matchRepeat("FREQ=WEEKLY;BYDAY=TU;WKST=MO"))
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", matchRepeat("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"))
        assertEquals("", matchRepeat("FREQ=DAILY;COUNT=5"))
        assertEquals("Своё правило", repeatLabel("FREQ=DAILY;INTERVAL=2"))
        assertEquals("Каждый месяц", repeatLabel("FREQ=MONTHLY"))
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
        assertEquals("DIAG ${Lang.mode} ${Lang.locale} ${Lang.context.resources.configuration.locales} ${Lang.context.javaClass} ${Lang.strIn("ru", R.string.outcome_done_mail)}", "1 звонок", plu(R.plurals.count_calls, 1))
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

    @Test fun outgoingJson() {
        val o = Outgoing(
            mode = Outgoing.MODE_FORWARD, to = "Иван <ivan@mail.ru>", cc = "", subject = "Fwd: Счёт", body = "Текст\nвторая строка",
            files = listOf("content://x/1"),
            forward = ForwardFrom("<id@x>", "INBOX", listOf(MailAttachment("счёт.pdf", "application/pdf", 1234, listOf(1, 0)))),
            inReplyTo = null, references = "<a@x> <b@x>",
        )
        assertEquals(o, Outgoing.fromJson(o.toJson()))
    }

    private fun message() = MailMessage(
        header = MailHeader("INBOX", 5, "<m1@x.ru>", "Счёт за сентябрь", Addr("Иван Петров", "ivan@x.ru"), 0L, true, false),
        to = listOf(Addr(null, "me@x.ru"), Addr("Олег", "oleg@x.ru")),
        cc = listOf(Addr(null, "anna@x.ru")),
        replyTo = emptyList(),
        html = null,
        text = "Добрый день!\nСчёт во вложении.",
        attachments = listOf(MailAttachment("счёт.pdf", "application/pdf", 100, listOf(1))),
        references = "<m0@x.ru>",
    )

    @Test fun replyAndForward() {
        val r = MailClient.reply(message(), all = false)
        assertEquals("Re: Счёт за сентябрь", r.subject)
        assertEquals("Иван Петров <ivan@x.ru>", r.to)
        assertEquals("", r.cc)
        assertEquals("<m1@x.ru>", r.inReplyTo)
        assertEquals("<m0@x.ru> <m1@x.ru>", r.references)
        assertTrue(r.body.contains("> Добрый день!"))
        assertTrue(r.body.contains("> Счёт во вложении."))

        val all = MailClient.reply(message(), all = true)
        assertTrue(all.cc.contains("oleg@x.ru"))
        assertTrue(all.cc.contains("anna@x.ru"))
        assertTrue(!all.cc.contains("ivan@x.ru"))

        // Повторный ответ не плодит "Re: Re:"
        val again = MailClient.reply(message().copy(header = message().header.copy(subject = "RE: Счёт")), all = false)
        assertEquals("RE: Счёт", again.subject)

        val f = MailClient.forward(message())
        assertEquals("Fwd: Счёт за сентябрь", f.subject)
        assertEquals(1, f.forward!!.parts.size)
        assertTrue(f.body.contains("Пересланное сообщение"))
    }

    @Test fun addresses() {
        val list = MailClient.parseAddresses("Иван Петров <ivan@mail.ru>; petr@x.ru")
        assertEquals(2, list.size)
        assertEquals("Иван Петров", list[0].personal)
        assertEquals("petr@x.ru", list[1].address)
        var failed = false
        try { MailClient.parseAddresses("не адрес") } catch (_: MailException) { failed = true }
        assertTrue(failed)
    }

    @Test fun settingsJson() {
        val s = AppSettings(mailMode = MailMode.BUILTIN, dayFrom = 7, weekStartsSunday = true)
        val back = AppSettings.fromJson(s.toJson())
        assertEquals(MailMode.BUILTIN, back.mailMode)
        assertEquals(7, back.dayFrom)
        assertTrue(back.weekStartsSunday)
        // Старые настройки без режима почты — «через почтовую программу»
        val old = s.toJson().apply { remove("mailMode") }
        assertEquals(MailMode.APP, AppSettings.fromJson(old).mailMode)
    }
}
