package ru.palmdate.app.data

import ru.palmdate.app.R
import ru.palmdate.app.str
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import org.json.JSONObject
import ru.palmdate.app.str
import ru.palmdate.app.model.EventType

/** С какого вида открывается приложение. */
enum class StartView(@StringRes val labelRes: Int) {
    LAST(R.string.start_last), DAY(R.string.start_day), AGENDA(R.string.start_agenda);

    val label: String get() = str(labelRes)
}

/** Через что звонить по кнопке "Позвонить". */
enum class CallVia(@StringRes val labelRes: Int) {
    PHONE(R.string.call_phone), WHATSAPP(R.string.call_whatsapp), TELEGRAM(R.string.call_telegram);

    val label: String get() = str(labelRes)
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

    fun toJson(): JSONObject = JSONObject().apply {
        put("durations", JSONObject().apply { durations.forEach { (k, v) -> put(k.name, v) } })
        put("reminders", JSONObject().apply { reminders.forEach { (k, v) -> put(k.name, v.joinToString(",")) } })
        put("calendarFixedId", calendarFixedId ?: -1)
        put("timeStep", timeStep)
        put("allDayReminderAt", allDayReminderAt ?: -1)
        put("startView", startView.name)
        put("weekStartsSunday", weekStartsSunday)
        put("dayFrom", dayFrom)
        put("dayTo", dayTo)
        put("birthdaysShown", birthdaysShown)
        put("birthdayNotify", birthdayNotify)
        put("birthdayNotifyAt", birthdayNotifyAt)
        put("callVia", callVia.name)
    }

    companion object {
        fun fromJson(j: JSONObject): AppSettings {
            val d = AppSettings()
            fun durs() = j.optJSONObject("durations")?.let { o ->
                EventType.entries.associateWith { o.optInt(it.name, d.duration(it)) }
            } ?: d.durations
            fun rems() = j.optJSONObject("reminders")?.let { o ->
                EventType.entries.associateWith { t ->
                    if (!o.has(t.name)) d.remindersFor(t)
                    else o.getString(t.name).split(",").mapNotNull { it.trim().toIntOrNull() }
                }
            } ?: d.reminders
            return AppSettings(
                durations = durs(),
                reminders = rems(),
                calendarFixedId = j.optLong("calendarFixedId", -1).takeIf { it >= 0 },
                timeStep = j.optInt("timeStep", 15),
                allDayReminderAt = j.optInt("allDayReminderAt", 540).takeIf { it >= 0 },
                startView = runCatching { StartView.valueOf(j.optString("startView")) }.getOrDefault(d.startView),
                weekStartsSunday = j.optBoolean("weekStartsSunday", false),
                dayFrom = j.optInt("dayFrom", 8),
                dayTo = j.optInt("dayTo", 18),
                birthdaysShown = j.optBoolean("birthdaysShown", true),
                birthdayNotify = j.optBoolean("birthdayNotify", false),
                birthdayNotifyAt = j.optInt("birthdayNotifyAt", 540),
                callVia = runCatching { CallVia.valueOf(j.optString("callVia")) }.getOrDefault(d.callVia),
            )
        }
    }
}

/**
 * Хранилище настроек. Текущие настройки — живое состояние: экраны, которые их читают,
 * перерисовываются сами, когда что-то поменяли.
 */
object SettingsStore {
    private lateinit var prefs: SharedPreferences

    var current by mutableStateOf(AppSettings())
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        current = prefs.getString("json", null)
            ?.let { runCatching { AppSettings.fromJson(JSONObject(it)) }.getOrNull() } ?: AppSettings()
    }

    fun update(block: (AppSettings) -> AppSettings) {
        current = block(current)
        prefs.edit().putString("json", current.toJson().toString()).apply()
    }

    fun replace(s: AppSettings) = update { s }
}
