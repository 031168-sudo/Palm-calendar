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
) {
    CALL("Звонок", Icons.Outlined.Phone, Color(0xFFC0392B), 15, true),
    MEETING("Встреча", Icons.Outlined.Groups, Color(0xFF1E3A6E), 60, true),
    TASK("Задача", Icons.Outlined.TaskAlt, Color(0xFF2E7D32), 30, false),
    TRIP("Поездка", Icons.Outlined.Flight, Color(0xFF6A4C93), 120, false),
    BIRTHDAY("День рождения", Icons.Outlined.Cake, Color(0xFFD35400), 0, true),
    OTHER("Событие", Icons.Outlined.Event, Color(0xFF546E7A), 60, false);

    companion object {
        fun parse(s: String?): EventType? = entries.firstOrNull { it.name == s }
    }
}

/** Ссылка на контакт из телефонной книги. Храним LOOKUP_KEY — он переживает слияние контактов. */
data class ContactRef(
    val lookupKey: String,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
)

/** Событие, как его показывает экран дня. */
data class PalmEvent(
    val eventId: Long,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val type: EventType?,      // null — обычное событие из чужого календаря
    val contact: ContactRef?,
    val note: String?,
)

/** То, что собирает окно "Новое". */
data class NewEvent(
    val type: EventType,
    val contact: ContactRef?,
    val title: String?,
    val start: LocalDateTime,
    val minutes: Int,
    val note: String?,
)
