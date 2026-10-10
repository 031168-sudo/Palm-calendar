package ru.palmdate.app.model

import ru.palmdate.app.str
import ru.palmdate.app.R
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.LocalDate
import java.time.LocalDateTime
import ru.palmdate.app.Lang
import ru.palmdate.app.str

/**
 * Тип события — то, что делало Agendus на Palm удобным:
 * не "текст во времени", а "звонок Сергею" / "встреча с Олегом".
 */
enum class EventType(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    private val baseColor: Color,
    val defaultMinutes: Int,
    val needsContact: Boolean,
    val defaultReminders: List<Int>,
) {
    CALL(R.string.type_call, Icons.Outlined.Phone, Color(0xFFC0392B), 15, true, listOf(5)),
    MEETING(R.string.type_meeting, Icons.Outlined.Groups, Color(0xFF1E3A6E), 60, true, listOf(15)),
    TASK(R.string.type_task, Icons.Outlined.TaskAlt, Color(0xFF2E7D32), 30, false, listOf(15)),
    TRIP(R.string.type_trip, Icons.Outlined.Place, Color(0xFF6A4C93), 120, false, listOf(60)),
    MAIL(R.string.type_mail, Icons.Outlined.Email, Color(0xFF00838F), 15, false, listOf(15)),
    OTHER(R.string.type_other, Icons.Outlined.Event, Color(0xFF546E7A), 60, false, listOf(15)),
    // Только для дней рождения из карточек контактов — выбрать его нельзя.
    // Старые события «Праздник» (метка t=BIRTHDAY) становятся обычными событиями.
    BIRTHDAY(R.string.type_birthday, Icons.Outlined.Cake, Color(0xFFD35400), 0, false, listOf(0));

    /** Название типа на выбранном языке. */
    val label: String get() = str(labelRes)

    /** Названия типа на всех языках — чтобы узнавать заголовки, записанные раньше на другом языке. */
    val allLabels: List<String> get() = Lang.TAGS.map { Lang.strIn(it, labelRes) }

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
fun reminderOptions(): List<Pair<Int, String>> = listOf(
    0 to str(R.string.rem_at_start),
    5 to str(R.string.rem_min, 5),
    15 to str(R.string.rem_min, 15),
    30 to str(R.string.rem_min, 30),
    60 to str(R.string.rem_hour, 1),
    1440 to str(R.string.rem_one_day),
)

/** Время дня из минут: 540 → "9:00". */
fun minutesToTime(m: Int): String = "%d:%02d".format(m / 60, m % 60)

/**
 * Напоминания для событий на весь день. Android/Google считают их от полуночи начала дня:
 * отрицательные минуты — "в этот день в ...", положительные — "накануне в ...".
 */
val ALLDAY_REMINDER_OPTIONS: List<Int> = listOf(-480, -540, -720, 360)

fun allDayReminderLabel(m: Int): String = when {
    m <= 0 -> str(R.string.rem_allday_same, minutesToTime(-m))
    m <= 1440 -> str(R.string.rem_allday_before, minutesToTime(1440 - m))
    else -> str(R.string.rem_days_before, (m + 1439) / 1440)
}

fun reminderLabel(m: Int): String = reminderOptions().firstOrNull { it.first == m }?.second
    ?: when {
        m % 1440 == 0 -> str(R.string.rem_days, m / 1440)
        m % 60 == 0 -> str(R.string.rem_hour, m / 60)
        else -> str(R.string.rem_min, m)
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
            !writable -> str(R.string.cal_problem_readonly)
            local -> str(R.string.cal_problem_local)
            !synced -> str(R.string.cal_problem_nosync)
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
    val place: String? = null,         // адрес выезда (поле «Место» календаря); только у выездов
    val hasFiles: Boolean = false,     // есть прикреплённые документы выезда — значок во второй строке
) {
    /** Название без приставки типа: "Задача: тест" → "тест". Пусто, если названия нет. */
    val shortTitle: String
        get() {
            val t = type ?: return title
            // Заголовок мог быть записан на другом языке — узнаём приставку на любом
            val prefixes = if (t == EventType.MAIL) (mail?.kind ?: MailKind.REPLY).allVerbs + t.allLabels else t.allLabels
            if (title in prefixes) return ""
            prefixes.firstOrNull { title.startsWith("$it: ") }?.let { return title.removePrefix("$it: ") }
            return title
        }

    /**
     * Заголовок для списков: приставка типа («Звонок: …») — на выбранном языке,
     * даже если событие записано в календарь на другом.
     */
    val displayTitle: String
        get() {
            val t = type ?: return title
            if (fromContacts) return title
            val prefixes = if (t == EventType.MAIL) (mail?.kind ?: MailKind.REPLY).allVerbs + t.allLabels else t.allLabels
            val known = title in prefixes || prefixes.any { title.startsWith("$it: ") }
            if (!known) return title
            val prefix = if (t == EventType.MAIL) (mail?.kind ?: MailKind.REPLY).verb else t.label
            return shortTitle.let { if (it.isEmpty()) prefix else "$prefix: $it" }
        }

    /** Подпись типа: у дней рождения из контактов своя, остальные — по типу. */
    val typeLabel: String?
        get() = if (fromContacts) str(R.string.type_birthday) else type?.label

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
    fun label(type: EventType?): String = str(labelRes(type))

    /** Подпись на конкретном языке — чтобы узнавать итоги, записанные в описании раньше. */
    fun labelIn(tag: String, type: EventType?): String = Lang.strIn(tag, labelRes(type))

    @StringRes
    private fun labelRes(type: EventType?): Int = when (this) {
        DONE -> when (type) {
            EventType.MAIL -> R.string.outcome_done_mail
            EventType.CALL -> R.string.outcome_done_call
            EventType.MEETING -> R.string.outcome_done_meeting
            EventType.TASK -> R.string.outcome_done_task
            else -> R.string.outcome_done_other
        }
        NO_ANSWER -> R.string.outcome_no_answer
        NO_SHOW -> R.string.outcome_no_show
        NOT_DONE -> if (type == EventType.MAIL) R.string.outcome_not_sent else R.string.outcome_not_done
        RESCHEDULED -> when (type) {
            EventType.CALL -> R.string.outcome_moved_call
            EventType.MEETING -> R.string.outcome_moved_meeting
            else -> R.string.outcome_moved_other
        }
        CANCELLED -> when (type) {
            EventType.CALL -> R.string.outcome_cancelled_call
            EventType.MEETING, EventType.TASK -> R.string.outcome_cancelled_f
            else -> R.string.outcome_cancelled_other
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
                Lang.TAGS.any { tag ->
                    (EventType.entries.map { o.labelIn(tag, it) } + o.labelIn(tag, null)).any { it.lowercase() == l }
                }
            }
        }
    }
}

/** Варианты повтора: правило RRULE (null — не повторяется) и подпись. */
fun repeatOptions(): List<Pair<String?, String>> = listOf(
    null to str(R.string.repeat_none),
    "FREQ=DAILY" to str(R.string.repeat_daily),
    "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR" to str(R.string.repeat_weekdays),
    "FREQ=WEEKLY" to str(R.string.repeat_weekly),
    "FREQ=MONTHLY" to str(R.string.repeat_monthly),
    "FREQ=YEARLY" to str(R.string.repeat_yearly),
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
    "" -> str(R.string.repeat_custom)
    else -> repeatOptions().first { it.first == m }.second
}

/** Письмо: ответить на пришедшее или написать новое. */
enum class MailKind(@StringRes val verbRes: Int) {
    REPLY(R.string.mail_verb_reply), NEW(R.string.mail_verb_new);

    /** Глагол на выбранном языке: «Ответить» / «Reply». */
    val verb: String get() = str(verbRes)

    /** Глагол на всех языках — чтобы узнавать заголовки, записанные раньше на другом языке. */
    val allVerbs: List<String> get() = Lang.TAGS.map { Lang.strIn(it, verbRes) }
}

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
    val place: String? = null,   // адрес выезда: пишется в поле «Место» календаря
)

/** Строка статистики: контакт и сколько с ним состоялось звонков, встреч и отправлено писем. */
data class ContactStat(val contact: ContactRef, val calls: Int, val meetings: Int, val mails: Int = 0) {
    val total get() = calls + meetings + mails
}

/** Что удалить у повторяющегося события. */
enum class DeleteScope { ONE, FOLLOWING, ALL }
