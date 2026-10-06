package ru.palmdate.app.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
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
    val color: Color,
    val defaultMinutes: Int,
    val needsContact: Boolean,
    val defaultReminders: List<Int>,
) {
    CALL("Звонок", Icons.Outlined.Phone, Color(0xFFC0392B), 15, true, listOf(5)),
    MEETING("Встреча", Icons.Outlined.Groups, Color(0xFF1E3A6E), 60, true, listOf(15)),
    TASK("Задача", Icons.Outlined.TaskAlt, Color(0xFF2E7D32), 30, false, listOf(15)),
    TRIP("Поездка", Icons.Outlined.Flight, Color(0xFF6A4C93), 120, false, listOf(60)),
    BIRTHDAY("День рождения", Icons.Outlined.Cake, Color(0xFFD35400), 0, true, listOf(0)),
    OTHER("Событие", Icons.Outlined.Event, Color(0xFF546E7A), 60, false, listOf(15));

    companion object {
        fun parse(s: String?): EventType? = entries.firstOrNull { it.name == s }
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

/** Календарь, в который можно записывать (Google-аккаунт + конкретный календарь в нём). */
data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val isPrimary: Boolean,
)

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
) {
    /** Дни, которые занимает событие (для недели, месяца, года). */
    fun days(): List<LocalDate> {
        val first = start.toLocalDate()
        val endExclusive = end.minusNanos(1)
        val last = if (endExclusive.isBefore(start)) first else endExclusive.toLocalDate()
        return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
    }
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
)
