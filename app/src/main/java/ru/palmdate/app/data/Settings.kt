package ru.palmdate.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import ru.palmdate.app.model.EventType

// Сами настройки (AppSettings) — в модуле shared; здесь — JSON и хранилище на Android.

fun AppSettings.toJson(): JSONObject = JSONObject().apply {
    put("durations", JSONObject().apply { durations.forEach { (k, v) -> put(k.name, v) } })
    put("reminders", JSONObject().apply { reminders.forEach { (k, v) -> put(k.name, v.joinToString(",")) } })
    put("calendarFixedId", calendarFixedId ?: -1)
    put("timeStep", timeStep)
    put("allDayReminderAt", allDayReminderAt ?: -1)
    put("startView", startView.name)
    put("weekStartsSunday", weekStartsSunday)
    put("dayFrom", dayFrom)
    put("dayTo", dayTo)
    put("durationBand", durationBand)
    put("birthdaysShown", birthdaysShown)
    put("birthdayNotify", birthdayNotify)
    put("birthdayNotifyAt", birthdayNotifyAt)
    put("callVia", callVia.name)
}

fun AppSettings.Companion.fromJson(j: JSONObject): AppSettings {
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
        durationBand = j.optBoolean("durationBand", true),
        birthdaysShown = j.optBoolean("birthdaysShown", true),
        birthdayNotify = j.optBoolean("birthdayNotify", false),
        birthdayNotifyAt = j.optInt("birthdayNotifyAt", 540),
        callVia = runCatching { CallVia.valueOf(j.optString("callVia")) }.getOrDefault(d.callVia),
    )
}

/**
 * Хранилище настроек. Текущие настройки — живое состояние: экраны, которые их читают,
 * перерисовываются сами, когда что-то поменяли.
 */
object SettingsStore {
    init { CurrentSettings.provider = { current } }

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
