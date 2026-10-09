package ru.palmdate.app.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Тип события — то, что делало Agendus на Palm удобным:
 * не "текст во времени", а "звонок Сергею" / "встреча с Олегом".
 */
enum class EventType(
    val label: String,
    val icon: ImageVector,
    private val baseColor: Color,
    val defaultMinutes: Int,
    val needsContact: Boolean,
    val defaultReminders: List<Int>,
) {
    CALL("Звонок", Icons.Outlined.Phone, Color(0xFFC0392B), 15, true, listOf(5)),
    MEETING("Встреча", Icons.Outlined.Groups, Color(0xFF1E3A6E), 60, true, listOf(15)),
    TASK("Задача", Icons.Outlined.TaskAlt, Color(0xFF2E7D32), 30, false, listOf(15)),
    TRIP("Поездка", Icons.Outlined.Flight, Color(0xFF6A4C93), 120, false, listOf(60)),
    MAIL("Письмо", Icons.Outlined.Email, Color(0xFF00838F), 15, false, listOf(15)),
    OTHER("Событие", Icons.Outlined.Event, Color(0xFF546E7A), 60, false, listOf(15)),
    // Только для дней рождения из карточек контактов — выбрать его нельзя.
    // Старые события «Праздник» (метка t=BIRTHDAY) становятся обычными событиями.
    BIRTHDAY("День рождения", Icons.Outlined.Cake, Color(0xFFD35400), 0, false, listOf(0));

    /** Цвет типа с поправкой на тему (в тёмной — светлее). */
    val color: Color get() = ru.palmdate.app.ui.theme.Palm.typeColor(baseColor)

    companion object {
        /** Типы, которые можно выбрать в «Новое» и в настройках. */
        val pickable: List<EventType> get() = entries.filter { it != BIRTHDAY }

        /** Тип из метки или базы. BIRTHDAY («Праздник») больше не тип — такие события обычные. */
        fun parse(s: String?): EventType? = entries.firstOrNull { it.name == s && it != BIRTHDAY }
    }
}

/** Варианты напоминаний (минуты до начала) и их подписи. */
val REMINDER_OPTIONS: List<Pair<Int, String>> = listOf(
    0 to "В момент",
    5 to "5 мин",
    15 to "15 мин",
    30 to "30 мин",
    60 to "1 ч",
    1440 to "1 день",
)

/** Время дня из минут: 540 → "9:00". */
fun minutesToTime(m: Int): String = "%d:%02d".format(m / 60, m % 60)

/**
 * Напоминания для событий на весь день. Android/Google считают их от полуночи начала дня:
 * отрицательные минуты — "в этот день в ...", положительные — "накануне в ...".
 */
val ALLDAY_REMINDER_OPTIONS: List<Int> = listOf(-480, -540, -720, 360)

fun allDayReminderLabel(m: Int): String = when {
    m <= 0 -> "В этот день в " + minutesToTime(-m)
    m <= 1440 -> "Накануне в " + minutesToTime(1440 - m)
    else -> "За ${(m + 1439) / 1440} дн"
}

fun reminderLabel(m: Int): String = REMINDER_OPTIONS.firstOrNull { it.first == m }?.second
    ?: when {
        m % 1440 == 0 -> "${m / 1440} дн"
        m % 60 == 0 -> "${m / 60} ч"
        else -> "$m мин"
    }

/**
 * Ссылка на контакт из телефонной книги. Храним LOOKUP_KEY — он переживает слияние контактов.
 * phone — номер, выбранный для этого события/контакта.
 */
data class ContactRef(
    val lookupKey: String,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
)

data class PhoneNumber(val number: String, val label: String)

/** Календарь телефона (Google-аккаунт + конкретный календарь в нём) и его ограничения. */
data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val isPrimary: Boolean,
    val writable: Boolean = true,   // можно ли туда записывать
    val synced: Boolean = true,     // синхронизируется ли с Google
    val local: Boolean = false,     // только на этом телефоне
) {
    /** Можно ли спокойно записывать: доступ есть, уходит в Google. */
    val usable: Boolean get() = writable && synced && !local

    /** Почему календарь не годится (или null, если годится). */
    val problem: String?
        get() = when {
            !writable -> "только чтение"
            local -> "только на этом телефоне"
            !synced -> "синхронизация выключена"
            else -> null
        }
}

/** Событие, как его показывают экраны. */
data class PalmEvent(
    val eventId: Long,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val type: EventType?,      // null — обычное событие, созданное не в приложении
    val contact: ContactRef?,
    val note: String?,
    val color: Int,            // цвет, который показывает Google (цвет события или календаря)
    val calendarName: String = "",
    val accountName: String = "",
    val calendarId: Long = -1,
    val recurring: Boolean = false,
    val instanceStart: Long = 0,      // начало именно этого раза (для повторяющихся — у каждого своё)
    val outcome: Outcome? = null,      // итог: состоялось, не дозвонился…
    val outcomeNote: String? = null,
    val fromContacts: Boolean = false, // день рождения из карточки контакта, а не событие календаря
    val rrule: String? = null,         // правило повтора серии
    val mail: MailInfo? = null,        // письмо, к которому относится событие «Письмо»
) {
    /** Название без приставки типа: "Задача: тест" → "тест". Пусто, если названия нет. */
    val shortTitle: String
        get() {
            val t = type ?: return title
            if (t == EventType.MAIL) {
                val prefix = (mail?.kind ?: MailKind.REPLY).verb
                return when {
                    title == prefix || title == t.label -> ""
                    title.startsWith("$prefix: ") -> title.removePrefix("$prefix: ")
                    else -> title
                }
            }
            return when {
                title == t.label -> ""
                title.startsWith(t.label + ": ") -> title.removePrefix(t.label + ": ")
                else -> title
            }
        }

    /** Подпись типа: у дней рождения из контактов своя, остальные — по типу. */
    val typeLabel: String?
        get() = if (fromContacts) "День рождения" else type?.label

    /** Дни, которые занимает событие (для недели, месяца, года). */
    fun days(): List<LocalDate> {
        val first = start.toLocalDate()
        val endExclusive = end.minusNanos(1)
        val last = if (endExclusive.isBefore(start)) first else endExclusive.toLocalDate()
        return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
    }
}

/** Как отображать итог: зелёная галочка, красный крестик или стрелка переноса. */
enum class OutcomeKind { GOOD, BAD, MOVED }

/** Итог события — что произошло на самом деле. */
enum class Outcome(val kind: OutcomeKind) {
    DONE(OutcomeKind.GOOD),
    NO_ANSWER(OutcomeKind.BAD),
    NO_SHOW(OutcomeKind.BAD),
    NOT_DONE(OutcomeKind.BAD),
    RESCHEDULED(OutcomeKind.MOVED),
    CANCELLED(OutcomeKind.BAD);

    /** Подпись с учётом типа события: звонок "состоялся", встреча "состоялась", задача "выполнена". */
    fun label(type: EventType?): String = when (this) {
        DONE -> when (type) {
            EventType.MAIL -> "Отправлено"
            EventType.CALL -> "Состоялся"
            EventType.MEETING -> "Состоялась"
            EventType.TASK -> "Выполнена"
            else -> "Состоялось"
        }
        NO_ANSWER -> "Не дозвонился"
        NO_SHOW -> "Не пришли"
        NOT_DONE -> if (type == EventType.MAIL) "Не отправлено" else "Не выполнена"
        RESCHEDULED -> when (type) {
            EventType.CALL -> "Перенесён"
            EventType.MEETING -> "Перенесена"
            else -> "Перенесено"
        }
        CANCELLED -> when (type) {
            EventType.CALL -> "Отменён"
            EventType.MEETING, EventType.TASK -> "Отменена"
            else -> "Отменено"
        }
    }

    companion object {
        /** Какие итоги предлагать для типа. */
        fun optionsFor(type: EventType?): List<Outcome> = when (type) {
            EventType.CALL -> listOf(DONE, NO_ANSWER, RESCHEDULED, CANCELLED)
            EventType.MEETING -> listOf(DONE, RESCHEDULED, CANCELLED, NO_SHOW)
            EventType.TASK -> listOf(DONE, NOT_DONE)
            EventType.MAIL -> listOf(DONE, NOT_DONE, RESCHEDULED, CANCELLED)
            else -> listOf(DONE, CANCELLED)
        }

        fun parse(s: String?): Outcome? = entries.firstOrNull { it.name == s }

        /** Восстановить итог по подписи из строки "Итог: …" в описании события. */
        fun fromLabel(label: String): Outcome? {
            val l = label.trim().lowercase()
            return entries.firstOrNull { o ->
                (EventType.entries.map { o.label(it) } + o.label(null)).any { it.lowercase() == l }
            }
        }
    }
}

/** Варианты повтора: правило RRULE (null — не повторяется) и подпись. */
val REPEAT_OPTIONS: List<Pair<String?, String>> = listOf(
    null to "Не повторяется",
    "FREQ=DAILY" to "Каждый день",
    "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR" to "По будням",
    "FREQ=WEEKLY" to "Каждую неделю",
    "FREQ=MONTHLY" to "Каждый месяц",
    "FREQ=YEARLY" to "Каждый год",
)

/**
 * К какому из наших вариантов относится правило повтора события (в т.ч. созданного в Google).
 * Возвращает правило-вариант, null — не повторяется, "" — своё правило (с окончанием, интервалом и т.п.).
 */
fun matchRepeat(rrule: String?): String? {
    if (rrule.isNullOrBlank()) return null
    val parts = rrule.uppercase().split(";").filter { it.isNotBlank() && !it.startsWith("WKST=") }
        .associate { it.substringBefore("=") to it.substringAfter("=") }
    if (parts.keys.any { it !in setOf("FREQ", "BYDAY", "BYMONTHDAY", "BYMONTH") }) return ""
    val byDay = parts["BYDAY"]
    return when (parts["FREQ"]) {
        "DAILY" -> if (byDay == null) "FREQ=DAILY" else ""
        "WEEKLY" -> when {
            byDay == null || !byDay.contains(",") -> "FREQ=WEEKLY"
            byDay.split(",").toSet() == setOf("MO", "TU", "WE", "TH", "FR") -> "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
            else -> ""
        }
        "MONTHLY" -> "FREQ=MONTHLY"
        "YEARLY" -> "FREQ=YEARLY"
        else -> ""
    }
}

fun repeatLabel(rrule: String?): String = when (val m = matchRepeat(rrule)) {
    "" -> "Своё правило"
    else -> REPEAT_OPTIONS.first { it.first == m }.second
}

/** Письмо: ответить на пришедшее или написать новое. */
enum class MailKind(val verb: String) { REPLY("Ответить"), NEW("Написать") }

/**
 * Письмо, привязанное к событию. Для «Ответить» — найденное письмо (по Message-ID),
 * для «Написать» — кому. peerName/peerAddr — от кого (ответ) или кому (новое).
 */
data class MailInfo(
    val kind: MailKind,
    val messageId: String? = null,
    val folder: String? = null,
    val subject: String? = null,
    val peerName: String? = null,
    val peerAddr: String? = null,
    val date: Long? = null,
    val hasDraft: Boolean = false,
) {
    /** Как показать собеседника: имя, иначе адрес. */
    val peer: String? get() = peerName?.takeIf { it.isNotBlank() } ?: peerAddr
}

/** То, что собирает окно "Новое". */
data class NewEvent(
    val type: EventType,
    val contact: ContactRef?,
    val title: String?,
    val start: LocalDateTime,
    val minutes: Int,
    val note: String?,
    val calendarId: Long,
    val reminders: List<Int>,
    val rrule: String? = null,
    val mail: MailInfo? = null,
)

/** Строка статистики: человек и сколько с ним состоялось звонков и встреч. */
data class ContactStat(val contact: ContactRef, val calls: Int, val meetings: Int) {
    val total get() = calls + meetings
}
