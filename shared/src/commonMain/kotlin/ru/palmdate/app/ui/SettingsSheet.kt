package ru.palmdate.app.ui

import ru.palmdate.app.str
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.palmdate.app.data.AppSettings
import ru.palmdate.app.data.CurrentSettings
import ru.palmdate.app.data.StartView
import ru.palmdate.app.model.CalendarInfo
import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.icon
import ru.palmdate.app.model.color
import ru.palmdate.app.model.minutesToTime
import ru.palmdate.app.model.reminderLabel
import ru.palmdate.app.ui.theme.Palm

/** Настройки DateBook — по разделам, одной прокручиваемой панелью. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    platform: SettingsPlatform,
    loadCalendars: suspend () -> List<CalendarInfo>,
    onCalendars: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = CurrentSettings.current

    // Любая правка: сохранить, перечитать календарь, переставить напоминания о ДР
    fun set(block: (AppSettings) -> AppSettings) {
        CurrentSettings.update(block)
        platform.onSettingsChanged()
    }

    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    LaunchedEffect(Unit) { calendars = loadCalendars().filter { it.usable } }

    PalmSheet(onDismissRequest = onDismiss, fixedHeight = true) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Settings, null, tint = Palm.navy, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(str("settings_title"), style = Palm.title, color = Palm.ink)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(Palm.navy))
        }

        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {

            /* ---------- События по умолчанию ---------- */
            SettingsSection(str("settings_defaults"))
            var openType by remember { mutableStateOf<EventType?>(null) }
            EventType.pickable.forEach { t ->
                val dur = s.duration(t)
                val rem = if (dur == 0) s.allDayReminders else s.remindersFor(t)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .clickable { openType = if (openType == t) null else t }.dottedRule(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(t.icon, null, tint = t.color, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.label, style = Palm.body, color = Palm.ink)
                        Text(
                            durationLabel(dur) + " · " + (if (dur == 0) str("settings_allday_reminder_same")
                            else rem.joinToString(", ") { reminderLabel(it) }.ifEmpty { str("settings_no_reminder") }),
                            style = Palm.small, color = Palm.inkSoft,
                        )
                    }
                    Icon(
                        if (openType == t) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null, tint = Palm.inkSoft,
                    )
                }
                if (openType == t) {
                    Spacer(Modifier.height(8.dp))
                    Text(str("settings_duration"), style = Palm.small, color = Palm.inkSoft)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60, 120, 0).forEach { m ->
                            Chip(durationLabel(m), selected = dur == m) { set { it.copy(durations = it.durations + (t to m)) } }
                        }
                    }
                    if (dur != 0) {
                        Spacer(Modifier.height(10.dp))
                        ReminderChips(rem) { new -> set { it.copy(reminders = it.reminders + (t to new)) } }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            SettingsLabel(str("settings_new_calendar"))
            var pickCal by remember { mutableStateOf(false) }
            val fixed = calendars.firstOrNull { it.id == s.calendarFixedId }
            SettingRow(
                str("settings_write_to"),
                fixed?.displayName() ?: str("settings_last_used_lc"),
                sub = fixed?.accountName?.takeIf { it != fixed.displayName() },
                dot = fixed?.let { Color(it.color) },
                onClick = { pickCal = !pickCal },
            )
            if (pickCal) {
                PickRow(str("settings_last_used"), s.calendarFixedId == null) {
                    set { it.copy(calendarFixedId = null) }; pickCal = false
                }
                calendars.forEach { c ->
                    PickRow(c.displayName() + " · " + c.accountName, s.calendarFixedId == c.id, dot = Color(c.color)) {
                        set { it.copy(calendarFixedId = c.id) }; pickCal = false
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            SettingsLabel(str("settings_time_step"))
            Chips(listOf(15 to str("settings_minutes", 15), 30 to str("settings_minutes", 30)), s.timeStep) { v -> set { it.copy(timeStep = v) } }

            /* ---------- События на весь день ---------- */
            SettingsSection(str("settings_allday"))
            SettingsLabel(str("settings_allday_reminder"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(str("settings_without"), selected = s.allDayReminderAt == null) { set { it.copy(allDayReminderAt = null) } }
                Spacer(Modifier.width(8.dp))
                Chip(str("settings_remind"), selected = s.allDayReminderAt != null) {
                    if (s.allDayReminderAt == null) set { it.copy(allDayReminderAt = 540) }
                }
            }
            s.allDayReminderAt?.let { at ->
                Spacer(Modifier.height(8.dp))
                TimeStepper(at, step = 30) { v -> set { it.copy(allDayReminderAt = v) } }
                SettingsHint(str("settings_allday_hint"))
            }

            /* ---------- Вид ---------- */
            SettingsSection(str("settings_view"))
            SettingsLabel(str("settings_language"))
            Chips(
                listOf(
                    "AUTO" to str("settings_lang_auto"),
                    "RU" to "Русский",
                    "EN" to "English",
                ),
                platform.langMode,
            ) { m -> if (m != platform.langMode) platform.setLangMode(m) }
            Spacer(Modifier.height(10.dp))
            SettingsLabel(str("settings_open"))
            Chips(StartView.entries.map { it to it.label }, s.startView) { v -> set { it.copy(startView = v) } }
            Spacer(Modifier.height(10.dp))
            SettingsLabel(str("settings_week_starts"))
            Chips(listOf(false to str("settings_monday"), true to str("settings_sunday")), s.weekStartsSunday) { v -> set { it.copy(weekStartsSunday = v) } }
            Spacer(Modifier.height(10.dp))
            SettingsLabel(str("settings_work_hours"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str("settings_from"), style = Palm.body, color = Palm.inkSoft, modifier = Modifier.width(24.dp))
                HourStepper(s.dayFrom, 0..(s.dayTo - 1)) { v -> set { it.copy(dayFrom = v) } }
                Spacer(Modifier.width(16.dp))
                Text(str("settings_to"), style = Palm.body, color = Palm.inkSoft, modifier = Modifier.width(30.dp))
                HourStepper(s.dayTo, (s.dayFrom + 1)..23) { v -> set { it.copy(dayTo = v) } }
            }

            Spacer(Modifier.height(10.dp))
            SettingsToggle(str("settings_duration_band"), s.durationBand) { v -> set { it.copy(durationBand = v) } }

            /* ---------- Календари и контакты ---------- */
            SettingsSection(str("settings_cal_contacts"))
            SettingRow(str("calendars_title"), str("settings_which_show"), onClick = onCalendars)
            SettingsToggle(str("settings_birthdays"), s.birthdaysShown) { v -> set { it.copy(birthdaysShown = v) } }
            if (s.birthdaysShown) {
                platform.BirthdayNotifySwitch(s.birthdayNotify) { v -> set { it.copy(birthdayNotify = v) } }
                if (s.birthdayNotify) {
                    TimeStepper(s.birthdayNotifyAt, step = 30) { v -> set { it.copy(birthdayNotifyAt = v) } }
                    SettingsHint(str("settings_birthday_hint"))
                }
            }

            with(platform) { ExtraSections() }
        }
    }
}

private fun durationLabel(m: Int) = when (m) {
    0 -> str("settings_all_day")
    60 -> str("rem_hour", 1)
    120 -> str("rem_hour", 2)
    else -> if (m % 60 == 0) str("rem_hour", m / 60) else str("rem_min", m)
}

@Composable
fun SettingsSection(title: String) {
    Spacer(Modifier.height(18.dp))
    Text(title, style = Palm.button, color = Palm.navy)
    Spacer(Modifier.height(4.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Palm.navy.copy(alpha = 0.35f)))
    Spacer(Modifier.height(8.dp))
}

@Composable
fun SettingsLabel(text: String) {
    Text(text, style = Palm.small, color = Palm.inkSoft)
    Spacer(Modifier.height(6.dp))
}

@Composable
fun SettingsHint(text: String) {
    Text(text, style = Palm.small, color = Palm.inkSoft, modifier = Modifier.padding(top = 6.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) -> Chip(label, selected = v == selected) { onPick(v) } }
    }
}

@Composable
fun SettingsToggle(text: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onChange(!on) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Palm.body, color = Palm.ink, modifier = Modifier.weight(1f))
        Switch(
            checked = on, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Palm.navy),
        )
    }
}

@Composable
private fun PickRow(text: String, selected: Boolean, dot: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onClick).padding(start = 12.dp).dottedRule(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Palm.body, color = Palm.ink, modifier = Modifier.weight(1f), maxLines = 1)
        if (selected) Text("✓", style = Palm.title, color = Palm.navy)
    }
}

/** Время дня с шагом: [−] 9:00 [+]. */
@Composable
private fun TimeStepper(minutes: Int, step: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepIcon(Icons.Filled.Remove) { onChange((minutes - step).coerceAtLeast(0)) }
        Text(minutesToTime(minutes), style = Palm.title, color = Palm.navy, textAlign = TextAlign.Center, modifier = Modifier.width(90.dp))
        StepIcon(Icons.Filled.Add) { onChange((minutes + step).coerceAtMost(23 * 60 + 30)) }
    }
}

@Composable
private fun HourStepper(hour: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepIcon(Icons.Filled.Remove) { onChange((hour - 1).coerceIn(range)) }
        Text("$hour:00", style = Palm.body, color = Palm.navy, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
        StepIcon(Icons.Filled.Add) { onChange((hour + 1).coerceIn(range)) }
    }
}

