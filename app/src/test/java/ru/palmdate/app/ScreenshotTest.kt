package ru.palmdate.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.palmdate.app.model.CalendarInfo
import ru.palmdate.app.model.ContactRef
import ru.palmdate.app.model.ContactStat
import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.MailInfo
import ru.palmdate.app.model.MailKind
import ru.palmdate.app.model.Outcome
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.ui.CalendarFrame
import ru.palmdate.app.ui.Hinge
import ru.palmdate.app.ui.LocalHinge
import ru.palmdate.app.ui.EventDetailsSheet
import ru.palmdate.app.ui.usesPane
import ru.palmdate.app.ui.LocalSheetTop
import ru.palmdate.app.ui.MainActions
import ru.palmdate.app.ui.MainLayout
import ru.palmdate.app.ui.NewEventSheet
import ru.palmdate.app.ui.StatsSheet
import ru.palmdate.app.ui.theme.PalmTheme
import java.time.LocalDate
import java.time.LocalDateTime

/** Тестовые события на сегодня и ближайшие дни — одинаковые для всех экранов. */
internal object Sample {
    val today: LocalDate = LocalDate.now()
    private const val BLUE = 0xFF4FC3F7.toInt()
    private const val GREEN = 0xFF33B679.toInt()
    private const val RED = 0xFFE67C73.toInt()

    private val ivan = ContactRef("k1", "Иван Петров", "+7 916 123-45-67", "Москва, Тверская ул., 7")
    private val olga = ContactRef("k2", "Ольга Смирнова", "+7 903 555-12-34")
    private val sergey = ContactRef("k3", "Сергей Кузнецов", "+7 926 000-11-22")

    private fun at(day: Long, h: Int, m: Int = 0): LocalDateTime = today.plusDays(day).atTime(h, m)

    private fun ev(
        id: Long, title: String, start: LocalDateTime, minutes: Long, type: EventType?, contact: ContactRef? = null,
        color: Int = BLUE, outcome: Outcome? = null, note: String? = null, allDay: Boolean = false, mail: MailInfo? = null,
        recurring: Boolean = false,
    ) = PalmEvent(
        eventId = id, title = title, start = start, end = if (allDay) start.plusDays(1) else start.plusMinutes(minutes),
        allDay = allDay, type = type, contact = contact, note = note, color = color, calendarName = "Работа",
        accountName = "me@gmail.com", calendarId = 1, outcome = outcome, mail = mail, recurring = recurring,
        instanceStart = id * 1000,
    )

    val events: List<PalmEvent> = listOf(
        ev(1, "День рождения", today.atStartOfDay(), 0, EventType.BIRTHDAY, olga, color = 0xFFD35400.toInt(), allDay = true)
            .copy(fromContacts = true, note = "исполняется 40"),
        ev(2, "Звонок: Иван Петров", at(0, 9, 30), 15, EventType.CALL, ivan, outcome = Outcome.DONE),
        ev(3, "Задача: отчёт за квартал", at(0, 11), 30, EventType.TASK, note = "свести цифры", recurring = true),
        ev(4, "Встреча: Ольга Смирнова", at(0, 13), 60, EventType.MEETING, olga, color = GREEN),
        ev(5, "Ответить: Счёт за сентябрь", at(0, 15), 15, EventType.MAIL,
            mail = MailInfo(MailKind.REPLY, subject = "Счёт за сентябрь", peerName = "Иван Петров", peerAddr = "ivan@x.ru")),
        ev(6, "Звонок: Сергей Кузнецов", at(0, 16, 30), 15, EventType.CALL, sergey, outcome = Outcome.NO_ANSWER),
        ev(7, "Планёрка", at(0, 18), 60, null, color = RED),
        ev(8, "Поездка: Санкт-Петербург", at(1, 8), 180, EventType.TRIP, color = GREEN),
        ev(9, "Встреча: Иван Петров", at(1, 14), 60, EventType.MEETING, ivan),
        ev(10, "Написать: Сергей Кузнецов", at(2, 10), 15, EventType.MAIL, sergey,
            mail = MailInfo(MailKind.NEW, peerName = "Сергей Кузнецов", peerAddr = "sergey@x.ru")),
        ev(11, "Задача: продлить домен", at(3, 12), 30, EventType.TASK, outcome = Outcome.DONE),
    )

    val calendars = listOf(
        CalendarInfo(1, "Работа", "me@gmail.com", "com.google", BLUE, isPrimary = false),
        CalendarInfo(2, "me@gmail.com", "me@gmail.com", "com.google", GREEN, isPrimary = true),
    )

    val stats = listOf(
        ContactStat(ivan, calls = 14, meetings = 5, mails = 3),
        ContactStat(olga, calls = 3, meetings = 9, mails = 0),
        ContactStat(sergey, calls = 8, meetings = 0, mails = 6),
        ContactStat(ContactRef("k4", "Анна Волкова"), calls = 1, meetings = 2, mails = 1),
    )

    fun state(mode: ViewMode, date: LocalDate = today): CalState {
        val r = CalState(mode = mode, date = date).range
        return CalState(
        mode = mode, date = date,
        events = events.filter { e -> e.days().any { it >= r.first && it < r.second } },
        yearDays = events.map { it.start.toLocalDate() }.toSet() + (1..40).map { today.withDayOfYear(1).plusDays(it * 7L) },
        )
    }
}

/**
 * Скриншоты экранов DateBook — рисуются на сервере без телефона, для разных устройств и тем.
 * Картинки складываются в app/screenshots/<устройство>/… и выкладываются к сборке.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotBase(private val device: String, private val hinge: Hinge? = null) {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            PalmTheme {
                CompositionLocalProvider(LocalSheetTop provides 96.dp, LocalHinge provides hinge) {
                    Box(Modifier.fillMaxSize()) { content() }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$device/$name.png")
    }

    /** Главный экран; на широком — с колонкой справа (pane), как в приложении. */
    @Composable
    private fun Main(mode: ViewMode, pane: (@Composable () -> Unit)? = null) =
        CalendarFrame(Sample.state(mode), MainActions(), pane = pane)

    /** Подробности: на широком экране — колонкой справа, на телефоне — панелью снизу. */
    @Composable
    private fun WithDetails(e: PalmEvent, reminders: List<Int>) {
        @Composable
        fun Details(pane: Boolean) = EventDetailsSheet(
            event = e,
            searchContacts = { emptyList() },
            phonesFor = { emptyList<ru.palmdate.app.model.PhoneNumber>() to null },
            loadReminders = { reminders },
            loadCalendars = { Sample.calendars },
            onMove = {}, onSetReminders = {}, onSetLink = { _, _ -> }, onDismiss = {}, onAction = {}, onOpen = {},
            onDelete = {}, onHistory = if (e.contact != null) ({}) else null,
            asPane = pane,
        )
        if (usesPane()) Main(ViewMode.DAY, pane = { Details(true) })
        else { Main(ViewMode.DAY); Details(false) }
    }

    @Test fun day() = shot("1_day") { Main(ViewMode.DAY) }
    @Test fun agenda() = shot("2_agenda") { Main(ViewMode.AGENDA) }
    @Test fun week() = shot("3_week") { Main(ViewMode.WEEK) }
    @Test fun month() = shot("4_month") { Main(ViewMode.MONTH) }
    @Test fun year() = shot("5_year") { Main(ViewMode.YEAR) }

    @Test fun details() = shot("6_details") { WithDetails(Sample.events[1], listOf(5)) }

    @Test fun detailsMail() = shot("7_details_mail") { WithDetails(Sample.events[4], listOf(15)) }

    @Test fun newEvent() = shot("8_new") {
        Main(ViewMode.DAY)
        NewEventSheet(
            initialStart = Sample.today.atTime(10, 0),
            searchContacts = { emptyList() },
            phonesFor = { emptyList<ru.palmdate.app.model.PhoneNumber>() to null },
            loadCalendars = { Sample.calendars },
            lastCalendarId = 1,
            onDismiss = {}, onCreate = {},
        )
    }

    @Test fun newEventWhen() = shot("9_new_when") {
        Main(ViewMode.DAY)
        NewEventSheet(
            initialStart = Sample.today.atTime(10, 0),
            searchContacts = { emptyList() },
            phonesFor = { listOf(ru.palmdate.app.model.PhoneNumber("+7 916 123-45-67", "Мобильный")) to null },
            loadCalendars = { Sample.calendars },
            lastCalendarId = 1,
            onDismiss = {}, onCreate = {},
            presetType = EventType.CALL,
            presetContact = ContactRef("k1", "Иван Петров", "+7 916 123-45-67"),
        )
    }

    @Test fun stats() = shot("10_stats") {
        Main(ViewMode.DAY)
        StatsSheet(load = { Sample.stats }, onAction = {}, onDismiss = {})
    }
}

// Устройства: размеры экранов в dp, как у настоящих
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xhdpi")
class PhoneScreens : ScreenshotBase("phone")

@Config(sdk = [34], qualifiers = "w393dp-h851dp-night-xhdpi")
class PhoneDarkScreens : ScreenshotBase("phone_dark")

@Config(sdk = [34], qualifiers = "w344dp-h882dp-xhdpi")
class FoldClosedScreens : ScreenshotBase("fold_closed")

@Config(sdk = [34], qualifiers = "w673dp-h841dp-xhdpi")
class FoldOpenScreens : ScreenshotBase("fold_open", Hinge(tabletop = false, vertical = true, bounds = android.graphics.Rect(672, 0, 674, 1682)))

// «Ноутбук»: раскладушка полусложена и стоит на столе, сгиб горизонтально посередине
@Config(sdk = [34], qualifiers = "w841dp-h673dp-land-xhdpi")
class FoldTabletopScreens : ScreenshotBase("fold_tabletop", Hinge(tabletop = true, vertical = false, bounds = android.graphics.Rect(0, 672, 1682, 674)))

@Config(sdk = [34], qualifiers = "w800dp-h1280dp-xhdpi")
class TabletScreens : ScreenshotBase("tablet")

@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-xhdpi")
class TabletLandScreens : ScreenshotBase("tablet_land")

@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-night-xhdpi")
class TabletLandDarkScreens : ScreenshotBase("tablet_land_dark")
