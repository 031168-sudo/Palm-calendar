package ru.palmdate.app.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import ru.palmdate.app.time.*

// События, как их показывают экраны, и то, что собирает окно «Новое»: общий код для Android и iOS.

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
    val hasAlarm: Boolean? = null,     // есть ли напоминание (любое); null — у дней рождения из контактов не показываем
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
        get() = if (fromContacts) L10n.str("type_birthday") else type?.label

    /** Дни, которые занимает событие (для недели, месяца, года). */
    fun days(): List<LocalDate> {
        val first = start.toLocalDate()
        val endExclusive = end.minusNanos(1)
        val last = if (endExclusive.isBefore(start)) first else endExclusive.toLocalDate()
        return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
    }
}

/** То, что собирает окно "Новое". */
/** Файл, прикреплённый при создании события: ссылка из выбора файла и подпись (Билет, Бронь…). */
data class NewFile(val uri: String, val label: String)

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
    val files: List<NewFile> = emptyList(), // документы, прикреплённые сразу при создании
)
