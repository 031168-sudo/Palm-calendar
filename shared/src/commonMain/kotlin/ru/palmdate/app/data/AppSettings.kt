package ru.palmdate.app.data

import ru.palmdate.app.model.EventType
import ru.palmdate.app.str

/** С какого вида открывается приложение. */
enum class StartView(val labelKey: String) {
    LAST("start_last"), DAY("start_day"), AGENDA("start_agenda");

    val label: String get() = str(labelKey)
}

/** Через что звонить по кнопке "Позвонить". */
enum class CallVia(val labelKey: String) {
    PHONE("call_phone"), WHATSAPP("call_whatsapp"), TELEGRAM("call_telegram");

    val label: String get() = str(labelKey)
}

/** Все настройки DateBook. */
data class AppSettings(
    // События по умолчанию
    val durations: Map<EventType, Int> = EventType.entries.associateWith { it.defaultMinutes },
    val reminders: Map<EventType, List<Int>> = EventType.entries.associateWith { it.defaultReminders },
    val calendarFixedId: Long? = null,          // null — последний использованный
    val timeStep: Int = 15,
    // Весь день: напоминание в этот день в N минут от полуночи (540 = 9:00); null — без напоминания
    val allDayReminderAt: Int? = 540,
    // Вид
    val startView: StartView = StartView.LAST,
    val weekStartsSunday: Boolean = false,
    val dayFrom: Int = 8,
    val dayTo: Int = 18,
    val durationBand: Boolean = true,   // бледная заливка длительности событий в «Дне»
    // Контакты
    val birthdaysShown: Boolean = true,
    val birthdayNotify: Boolean = false,
    val birthdayNotifyAt: Int = 540,
    val callVia: CallVia = CallVia.PHONE,
) {
    fun duration(t: EventType) = durations[t] ?: t.defaultMinutes
    fun remindersFor(t: EventType) = reminders[t] ?: t.defaultReminders

    /** Напоминание для события на весь день: минус минуты = "в этот день в ..." (так его хранит Android/Google). */
    val allDayReminders: List<Int> get() = allDayReminderAt?.let { listOf(-it) } ?: emptyList()

    companion object
}

/**
 * Текущие настройки для общего кода. На Android источник задаёт SettingsStore (живое состояние:
 * экраны, которые читают настройки, перерисовываются сами).
 */
object CurrentSettings {
    var provider: () -> AppSettings = { AppSettings() }
    val current: AppSettings get() = provider()
}
