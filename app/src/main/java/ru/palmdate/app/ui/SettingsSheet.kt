package ru.palmdate.app.ui

import ru.palmdate.app.R
import ru.palmdate.app.str
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.palmdate.app.BirthdayReminder
import ru.palmdate.app.BuildConfig
import ru.palmdate.app.DayViewModel
import ru.palmdate.app.Lang
import ru.palmdate.app.LangMode
import ru.palmdate.app.data.AppSettings
import ru.palmdate.app.data.MailPreset
import ru.palmdate.app.data.MailMode
import ru.palmdate.app.data.SettingsStore
import ru.palmdate.app.data.StartView
import ru.palmdate.app.model.CalendarInfo
import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.minutesToTime
import ru.palmdate.app.model.reminderLabel
import ru.palmdate.app.ui.theme.Palm

private const val LATEST_APK = "https://github.com/031168-sudo/Palm-calendar/releases/latest/download/DateBook.apk"

/** Настройки DateBook — по разделам, одной прокручиваемой панелью. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(vm: DayViewModel, onCalendars: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = SettingsStore.current

    // Любая правка: сохранить, перечитать календарь, переставить напоминания о ДР
    fun set(block: (AppSettings) -> AppSettings) {
        SettingsStore.update(block)
        vm.settingsChanged()
        BirthdayReminder.schedule(ctx)
    }

    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    LaunchedEffect(Unit) { calendars = vm.allCalendars().filter { it.usable } }

    PalmSheet(onDismissRequest = onDismiss, fixedHeight = true) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Settings, null, tint = Palm.navy, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(str(R.string.settings_title), style = Palm.title, color = Palm.ink)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(Palm.navy))
        }

        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {

            /* ---------- События по умолчанию ---------- */
            Section(str(R.string.settings_defaults))
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
                            durationLabel(dur) + " · " + (if (dur == 0) str(R.string.settings_allday_reminder_same)
                            else rem.joinToString(", ") { reminderLabel(it) }.ifEmpty { str(R.string.settings_no_reminder) }),
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
                    Text(str(R.string.settings_duration), style = Palm.small, color = Palm.inkSoft)
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
            Label(str(R.string.settings_new_calendar))
            var pickCal by remember { mutableStateOf(false) }
            val fixed = calendars.firstOrNull { it.id == s.calendarFixedId }
            SettingRow(
                str(R.string.settings_write_to),
                fixed?.displayName() ?: str(R.string.settings_last_used_lc),
                sub = fixed?.accountName?.takeIf { it != fixed.displayName() },
                dot = fixed?.let { Color(it.color) },
                onClick = { pickCal = !pickCal },
            )
            if (pickCal) {
                PickRow(str(R.string.settings_last_used), s.calendarFixedId == null) {
                    set { it.copy(calendarFixedId = null) }; pickCal = false
                }
                calendars.forEach { c ->
                    PickRow(c.displayName() + " · " + c.accountName, s.calendarFixedId == c.id, dot = Color(c.color)) {
                        set { it.copy(calendarFixedId = c.id) }; pickCal = false
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Label(str(R.string.settings_time_step))
            Chips(listOf(15 to str(R.string.settings_minutes, 15), 30 to str(R.string.settings_minutes, 30)), s.timeStep) { v -> set { it.copy(timeStep = v) } }

            /* ---------- События на весь день ---------- */
            Section(str(R.string.settings_allday))
            Label(str(R.string.settings_allday_reminder))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(str(R.string.settings_without), selected = s.allDayReminderAt == null) { set { it.copy(allDayReminderAt = null) } }
                Spacer(Modifier.width(8.dp))
                Chip(str(R.string.settings_remind), selected = s.allDayReminderAt != null) {
                    if (s.allDayReminderAt == null) set { it.copy(allDayReminderAt = 540) }
                }
            }
            s.allDayReminderAt?.let { at ->
                Spacer(Modifier.height(8.dp))
                TimeStepper(at, step = 30) { v -> set { it.copy(allDayReminderAt = v) } }
                Hint(str(R.string.settings_allday_hint))
            }

            /* ---------- Вид ---------- */
            Section(str(R.string.settings_view))
            Label(str(R.string.settings_language))
            Chips(
                listOf(
                    LangMode.AUTO to str(R.string.settings_lang_auto),
                    LangMode.RU to "Русский",
                    LangMode.EN to "English",
                ),
                Lang.mode,
            ) { m ->
                if (m != Lang.mode) {
                    Lang.setMode(m)
                    vm.settingsChanged()
                    ctx.findActivity()?.recreate() // пересоздаём экран, чтобы все тексты стали на новом языке
                }
            }
            Spacer(Modifier.height(10.dp))
            Label(str(R.string.settings_open))
            Chips(StartView.entries.map { it to it.label }, s.startView) { v -> set { it.copy(startView = v) } }
            Spacer(Modifier.height(10.dp))
            Label(str(R.string.settings_week_starts))
            Chips(listOf(false to str(R.string.settings_monday), true to str(R.string.settings_sunday)), s.weekStartsSunday) { v -> set { it.copy(weekStartsSunday = v) } }
            Spacer(Modifier.height(10.dp))
            Label(str(R.string.settings_work_hours))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.settings_from), style = Palm.body, color = Palm.inkSoft, modifier = Modifier.width(24.dp))
                HourStepper(s.dayFrom, 0..(s.dayTo - 1)) { v -> set { it.copy(dayFrom = v) } }
                Spacer(Modifier.width(16.dp))
                Text(str(R.string.settings_to), style = Palm.body, color = Palm.inkSoft, modifier = Modifier.width(30.dp))
                HourStepper(s.dayTo, (s.dayFrom + 1)..23) { v -> set { it.copy(dayTo = v) } }
            }

            /* ---------- Календари и контакты ---------- */
            Section(str(R.string.settings_cal_contacts))
            SettingRow(str(R.string.calendars_title), str(R.string.settings_which_show), onClick = onCalendars)
            Toggle(str(R.string.settings_birthdays), s.birthdaysShown) { v -> set { it.copy(birthdaysShown = v) } }
            val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                set { it.copy(birthdayNotify = ok) }
            }
            if (s.birthdaysShown) {
                Toggle(str(R.string.settings_birthday_notify), s.birthdayNotify) { v ->
                    if (v && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else set { it.copy(birthdayNotify = v) }
                }
                if (s.birthdayNotify) {
                    TimeStepper(s.birthdayNotifyAt, step = 30) { v -> set { it.copy(birthdayNotifyAt = v) } }
                    Hint(str(R.string.settings_birthday_hint))
                }
            }

            /* ---------- Почта ---------- */
            Section(str(R.string.settings_mail))
            Chips(MailMode.entries.map { it to it.label }, s.mailMode) { v -> set { it.copy(mailMode = v) } }
            Spacer(Modifier.height(6.dp))
            if (s.mailMode == MailMode.APP) {
                val apps = remember { ru.palmdate.app.data.MailApps(ctx) }
                var appPkg by remember { mutableStateOf(apps.remembered()) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Label(str(R.string.settings_mail_app))
                        Text(appPkg?.let { apps.label(it) } ?: str(R.string.settings_ask_on_open), style = Palm.body, color = Palm.ink)
                    }
                    if (appPkg != null) TextButton(onClick = { apps.remember(null); appPkg = null }) { Text(str(R.string.settings_reset)) }
                }
                Hint(
                    str(R.string.settings_mail_app_hint),
                )
            } else {
            Label(str(R.string.settings_mailbox))
            Chips(MailPreset.entries.map { it to it.label }, s.mailPreset) { v -> set { it.copy(mailPreset = v) } }
            Spacer(Modifier.height(8.dp))
            var email by remember { mutableStateOf(s.mailEmail) }
            OutlinedTextField(
                value = email, onValueChange = { email = it; set { st -> st.copy(mailEmail = it.trim()) } },
                label = { Text(str(R.string.settings_mail_address)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth().keepAboveKeyboard(),
            )
            Spacer(Modifier.height(6.dp))
            var password by remember { mutableStateOf(SettingsStore.mailPassword) }
            OutlinedTextField(
                value = password, onValueChange = { password = it; SettingsStore.mailPassword = it },
                label = { Text(str(R.string.settings_app_password)) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().keepAboveKeyboard(),
            )
            if (s.mailPreset == MailPreset.CUSTOM) {
                Spacer(Modifier.height(6.dp))
                ServerFields("IMAP", s.mailImapHost, s.mailImapPort) { h, p -> set { it.copy(mailImapHost = h, mailImapPort = p) } }
                Spacer(Modifier.height(6.dp))
                ServerFields("SMTP", s.mailSmtpHost, s.mailSmtpPort) { h, p -> set { it.copy(mailSmtpHost = h, mailSmtpPort = p) } }
            }
            Hint(
                when (s.mailPreset) {
                    MailPreset.GMAIL -> str(R.string.settings_hint_gmail)
                    MailPreset.YANDEX -> str(R.string.settings_hint_yandex)
                    MailPreset.MAILRU -> str(R.string.settings_hint_mailru)
                    MailPreset.CUSTOM -> str(R.string.settings_hint_custom)
                } + str(R.string.settings_hint_password_stored),
            )
            var mailResult by remember { mutableStateOf<String?>(null) }
            var checking by remember { mutableStateOf(false) }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PalmButton(if (checking) str(R.string.settings_checking) else str(R.string.settings_check_connection), filled = true) {
                    if (!checking) scope.launch {
                        checking = true
                        mailResult = vm.checkMail(password)?.let { "✗ $it" } ?: str(R.string.settings_mail_ok)
                        checking = false
                    }
                }
            }
            mailResult?.let { Text(it, style = Palm.small, color = if (it.startsWith("✓")) Color(0xFF2E7D32) else Palm.nowLine, modifier = Modifier.padding(top = 6.dp)) }
            }

            /* ---------- Данные ---------- */
            Section(str(R.string.settings_data))
            Hint(str(R.string.settings_data_hint))
            var dataResult by remember { mutableStateOf<String?>(null) }
            val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) scope.launch {
                    dataResult = runCatching {
                        val text = vm.exportBackup()
                        ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                        str(R.string.settings_backup_saved)
                    }.getOrElse { "✗ " + (it.message ?: str(R.string.settings_backup_save_failed)) }
                }
            }
            val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) scope.launch {
                    dataResult = runCatching {
                        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                        str(R.string.settings_backup_restored, vm.importBackup(text))
                    }.getOrElse { "✗ " + (it.message ?: str(R.string.settings_backup_restore_failed)) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PalmButton(str(R.string.settings_backup_save)) { saveLauncher.launch(str(R.string.settings_backup_filename, java.time.LocalDate.now())) }
                PalmButton(str(R.string.settings_backup_restore)) { openLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
            }
            dataResult?.let { Text(it, style = Palm.small, color = if (it.startsWith("✓")) Color(0xFF2E7D32) else Palm.nowLine, modifier = Modifier.padding(top = 6.dp)) }

            /* ---------- О приложении ---------- */
            Section(str(R.string.settings_about))
            Text("DateBook ${BuildConfig.VERSION_NAME}", style = Palm.body, color = Palm.ink)
            var update by remember { mutableStateOf<String?>(null) }
            var newer by remember { mutableStateOf(false) }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PalmButton(str(R.string.settings_check_update)) {
                    scope.launch {
                        update = str(R.string.settings_checking)
                        val latest = vm.latestBuild()
                        newer = latest != null && latest > BuildConfig.VERSION_CODE
                        update = when {
                            latest == null -> str(R.string.settings_update_failed)
                            newer -> str(R.string.settings_update_available, latest)
                            else -> str(R.string.settings_update_latest)
                        }
                    }
                }
                if (newer) PalmButton(str(R.string.settings_download), filled = true) {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LATEST_APK)))
                }
            }
            update?.let { Text(it, style = Palm.small, color = Palm.inkSoft, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

private fun durationLabel(m: Int) = when (m) {
    0 -> str(R.string.settings_all_day)
    60 -> str(R.string.rem_hour, 1)
    120 -> str(R.string.rem_hour, 2)
    else -> if (m % 60 == 0) str(R.string.rem_hour, m / 60) else str(R.string.rem_min, m)
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(18.dp))
    Text(title, style = Palm.button, color = Palm.navy)
    Spacer(Modifier.height(4.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Palm.navy.copy(alpha = 0.35f)))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Label(text: String) {
    Text(text, style = Palm.small, color = Palm.inkSoft)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Hint(text: String) {
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
private fun Toggle(text: String, on: Boolean, onChange: (Boolean) -> Unit) {
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

@Composable
private fun ServerFields(title: String, host: String, port: Int, onChange: (String, Int) -> Unit) {
    var h by remember { mutableStateOf(host) }
    var p by remember { mutableStateOf(port.toString()) }
    Row {
        OutlinedTextField(
            value = h, onValueChange = { h = it; onChange(it.trim(), p.toIntOrNull() ?: port) },
            label = { Text(str(R.string.settings_server, title)) }, singleLine = true,
            modifier = Modifier.weight(1f).keepAboveKeyboard(),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = p, onValueChange = { p = it.filter { c -> c.isDigit() }.take(5); onChange(h.trim(), p.toIntOrNull() ?: port) },
            label = { Text(str(R.string.settings_port)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(96.dp).keepAboveKeyboard(),
        )
    }
}

/** Окно (Activity), в котором показан экран, — нужно, чтобы пересоздать его при смене языка. */
private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
