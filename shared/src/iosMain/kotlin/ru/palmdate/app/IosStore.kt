package ru.palmdate.app

import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults
import ru.palmdate.app.data.AppSettings
import ru.palmdate.app.data.CallVia
import ru.palmdate.app.data.StartView
import ru.palmdate.app.model.EventType

/** Маленькое хранилище iPhone (NSUserDefaults) и запись настроек в нём. */
object IosStore {
    private val d get() = NSUserDefaults.standardUserDefaults
    private fun k(key: String) = "pd_$key"

    fun getString(key: String): String? = d.stringForKey(k(key))
    fun putString(key: String, value: String) = d.setObject(value, k(key))
    fun remove(key: String) = d.removeObjectForKey(k(key))
    fun getLong(key: String): Long? = (d.objectForKey(k(key)) as? NSNumber)?.longLongValue
    fun putLong(key: String, value: Long) = d.setObject(NSNumber(long = value), k(key))
    fun getStringSet(key: String): Set<String> = (d.stringArrayForKey(k(key)) ?: emptyList<Any?>()).filterIsInstance<String>().toSet()
    fun putStringSet(key: String, value: Set<String>) = d.setObject(value.toList(), k(key))

    fun loadSettings(): AppSettings {
        val text = getString("settings") ?: return AppSettings()
        val m = text.lines().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
        val d0 = AppSettings()
        fun int(key: String, def: Int) = m[key]?.toIntOrNull() ?: def
        fun bool(key: String, def: Boolean) = m[key]?.let { it == "1" } ?: def
        return AppSettings(
            durations = EventType.entries.associateWith { t -> m["dur.${t.name}"]?.toIntOrNull() ?: d0.duration(t) },
            reminders = EventType.entries.associateWith { t ->
                m["rem.${t.name}"]?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: d0.remindersFor(t)
            },
            calendarFixedId = m["calendarFixedId"]?.toLongOrNull()?.takeIf { it >= 0 },
            timeStep = int("timeStep", 15),
            allDayReminderAt = m["allDayReminderAt"]?.toIntOrNull()?.takeIf { it >= 0 } ?: if (m.containsKey("allDayReminderAt")) null else d0.allDayReminderAt,
            startView = runCatching { StartView.valueOf(m["startView"] ?: "") }.getOrDefault(d0.startView),
            weekStartsSunday = bool("weekStartsSunday", false),
            dayFrom = int("dayFrom", 8),
            dayTo = int("dayTo", 18),
            durationBand = bool("durationBand", true),
            birthdaysShown = bool("birthdaysShown", true),
            birthdayNotify = bool("birthdayNotify", false),
            birthdayNotifyAt = int("birthdayNotifyAt", 540),
            callVia = runCatching { CallVia.valueOf(m["callVia"] ?: "") }.getOrDefault(d0.callVia),
        )
    }

    fun saveSettings(s: AppSettings) {
        val sb = StringBuilder()
        s.durations.forEach { (t, v) -> sb.append("dur.${t.name}=$v\n") }
        s.reminders.forEach { (t, v) -> sb.append("rem.${t.name}=${v.joinToString(",")}\n") }
        sb.append("calendarFixedId=${s.calendarFixedId ?: -1}\n")
        sb.append("timeStep=${s.timeStep}\n")
        sb.append("allDayReminderAt=${s.allDayReminderAt ?: -1}\n")
        sb.append("startView=${s.startView.name}\n")
        sb.append("weekStartsSunday=${if (s.weekStartsSunday) 1 else 0}\n")
        sb.append("dayFrom=${s.dayFrom}\n")
        sb.append("dayTo=${s.dayTo}\n")
        sb.append("durationBand=${if (s.durationBand) 1 else 0}\n")
        sb.append("birthdaysShown=${if (s.birthdaysShown) 1 else 0}\n")
        sb.append("birthdayNotify=${if (s.birthdayNotify) 1 else 0}\n")
        sb.append("birthdayNotifyAt=${s.birthdayNotifyAt}\n")
        sb.append("callVia=${s.callVia.name}\n")
        putString("settings", sb.toString())
    }
}
