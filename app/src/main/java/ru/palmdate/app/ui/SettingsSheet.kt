package ru.palmdate.app.ui

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
                Text("Настройки", style = Palm.title, color = Palm.ink)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).background(Palm.navy))
        }

        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {

            /* ---------- События по умолчанию ---------- */
            Section("События по умолчанию")
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
                            durationLabel(dur) + " · " + (if (dur == 0) "напоминание как у событий на весь день"
                            else rem.joinToString(", ") { reminderLabel(it) }.ifEmpty { "без напоминания" }),
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
                    Text("Длительность", style = Palm.small, color = Palm.inkSoft)
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
            Label("Календарь для новых событий")
            var pickCal by remember { mutableStateOf(false) }
            val fixed = calendars.firstOrNull { it.id == s.calendarFixedId }
            SettingRow(
                "Записывать в",
                fixed?.displayName() ?: "последний использованный",
                sub = fixed?.accountName?.takeIf { it != fixed.displayName() },
                dot = fixed?.let { Color(it.color) },
                onClick = { pickCal = !pickCal },
            )
            if (pickCal) {
                PickRow("Последний использованный", s.calendarFixedId == null) {
                    set { it.copy(calendarFixedId = null) }; pickCal = false
                }
                calendars.forEach { c ->
                    PickRow(c.displayName() + " · " + c.accountName, s.calendarFixedId == c.id, dot = Color(c.color)) {
                        set { it.copy(calendarFixedId = c.id) }; pickCal = false
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Label("Шаг времени")
            Chips(listOf(15 to "15 минут", 30 to "30 минут"), s.timeStep) { v -> set { it.copy(timeStep = v) } }

            /* ---------- События на весь день ---------- */
            Section("События на весь день")
            Label("Напоминание в день события")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip("Без", selected = s.allDayReminderAt == null) { set { it.copy(allDayReminderAt = null) } }
                Spacer(Modifier.width(8.dp))
                Chip("Напоминать", selected = s.allDayReminderAt != null) {
                    if (s.allDayReminderAt == null) set { it.copy(allDayReminderAt = 540) }
                }
            }
            s.allDayReminderAt?.let { at ->
                Spacer(Modifier.height(8.dp))
                TimeStepper(at, step = 30) { v -> set { it.copy(allDayReminderAt = v) } }
                Hint("Подставляется само, когда событие на весь день. Хранится в событии и приходит на всех устройствах, как в Google Календаре.")
            }

            /* ---------- Вид ---------- */
            Section("Вид")
            Label("Открывать")
            Chips(StartView.entries.map { it to it.label }, s.startView) { v -> set { it.copy(startView = v) } }
            Spacer(Modifier.height(10.dp))
            Label("Неделя начинается")
            Chips(listOf(false to "С понедельника", true to "С воскресенья"), s.weekStartsSunday) { v -> set { it.copy(weekStartsSunday = v) } }
            Spacer(Modifier.height(10.dp))
            Label("Рабочие часы в виде «День»")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("с", style = Palm.body, color = Palm.inkSoft, modifier = Modifier.width(24.dp))
                HourStepper(s.dayFrom, 0..(s.dayTo - 1)) { v -> set { it.copy(dayFrom = v) } }
                Spacer(Modifier.width(16.dp))
                Text("до", style = Palm.body, color = Palm.inkSoft, modifier = Modifier.width(30.dp))
                HourStepper(s.dayTo, (s.dayFrom + 1)..23) { v -> set { it.copy(dayTo = v) } }
            }

            /* ---------- Календари и контакты ---------- */
            Section("Календари и контакты")
            SettingRow("Календари", "какие показывать", onClick = onCalendars)
            Toggle("Дни рождения из контактов", s.birthdaysShown) { v -> set { it.copy(birthdaysShown = v) } }
            val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                set { it.copy(birthdayNotify = ok) }
            }
            if (s.birthdaysShown) {
                Toggle("Напоминать о днях рождения", s.birthdayNotify) { v ->
                    if (v && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else set { it.copy(birthdayNotify = v) }
                }
                if (s.birthdayNotify) {
                    TimeStepper(s.birthdayNotifyAt, step = 30) { v -> set { it.copy(birthdayNotifyAt = v) } }
                    Hint("Уведомление приходит от DateBook в этот день в выбранное время.")
                }
            }

            /* ---------- Почта ---------- */
            Section("Почта")
            Chips(MailMode.entries.map { it to it.label }, s.mailMode) { v -> set { it.copy(mailMode = v) } }
            Spacer(Modifier.height(6.dp))
            if (s.mailMode == MailMode.APP) {
                val apps = remember { ru.palmdate.app.data.MailApps(ctx) }
                var appPkg by remember { mutableStateOf(apps.remembered()) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Label("Почтовая программа")
                        Text(appPkg?.let { apps.label(it) } ?: "спрашивать при открытии", style = Palm.body, color = Palm.ink)
                    }
                    if (appPkg != null) TextButton(onClick = { apps.remember(null); appPkg = null }) { Text("сбросить") }
                }
                Hint(
                    "«Написать» открывает новое письмо в почтовой программе (Gmail, Яндекс Почта…) с адресом. " +
                        "«Ответить» открывает почту, а слова для поиска письма копируются — их остаётся вставить в поиск. " +
                        "Пароли не нужны. Вернётесь в DateBook — он спросит, отправлено ли письмо.",
                )
            } else {
            Label("Почтовый ящик")
            Chips(MailPreset.entries.map { it to it.label }, s.mailPreset) { v -> set { it.copy(mailPreset = v) } }
            Spacer(Modifier.height(8.dp))
            var email by remember { mutableStateOf(s.mailEmail) }
            OutlinedTextField(
                value = email, onValueChange = { email = it; set { st -> st.copy(mailEmail = it.trim()) } },
                label = { Text("Адрес почты") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth().keepAboveKeyboard(),
            )
            Spacer(Modifier.height(6.dp))
            var password by remember { mutableStateOf(SettingsStore.mailPassword) }
            OutlinedTextField(
                value = password, onValueChange = { password = it; SettingsStore.mailPassword = it },
                label = { Text("Пароль приложения") }, singleLine = true,
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
                    MailPreset.GMAIL -> "Нужен пароль приложения: аккаунт Google → Безопасность → Двухэтапная аутентификация → Пароли приложений."
                    MailPreset.YANDEX -> "Нужен пароль приложения: Яндекс ID → Безопасность → Пароли приложений → Почта."
                    MailPreset.MAILRU -> "Нужен пароль для внешнего приложения: Mail.ru → Безопасность → Пароли для внешних приложений."
                    MailPreset.CUSTOM -> "Обычно IMAP — порт 993, SMTP — порт 465 (SSL)."
                } + " Пароль хранится зашифрованным только на этом телефоне.",
            )
            var mailResult by remember { mutableStateOf<String?>(null) }
            var checking by remember { mutableStateOf(false) }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PalmButton(if (checking) "Проверяю…" else "Проверить подключение", filled = true) {
                    if (!checking) scope.launch {
                        checking = true
                        mailResult = vm.checkMail(password)?.let { "✗ $it" } ?: "✓ Почта подключена"
                        checking = false
                    }
                }
            }
            mailResult?.let { Text(it, style = Palm.small, color = if (it.startsWith("✓")) Color(0xFF2E7D32) else Palm.nowLine, modifier = Modifier.padding(top = 6.dp)) }
            }

            /* ---------- Данные ---------- */
            Section("Данные")
            Hint("Копия всего, что DateBook хранит сам: типы и контакты событий, итоги, выбранные номера и адреса, настройки. Сами события и так в Google.")
            var dataResult by remember { mutableStateOf<String?>(null) }
            val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) scope.launch {
                    dataResult = runCatching {
                        val text = vm.exportBackup()
                        ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                        "✓ Копия сохранена"
                    }.getOrElse { "✗ " + (it.message ?: "Не удалось сохранить") }
                }
            }
            val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) scope.launch {
                    dataResult = runCatching {
                        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                        "✓ Восстановлено записей: " + vm.importBackup(text)
                    }.getOrElse { "✗ " + (it.message ?: "Не удалось восстановить") }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PalmButton("Сохранить копию") { saveLauncher.launch("DateBook-копия-${java.time.LocalDate.now()}.json") }
                PalmButton("Восстановить") { openLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
            }
            dataResult?.let { Text(it, style = Palm.small, color = if (it.startsWith("✓")) Color(0xFF2E7D32) else Palm.nowLine, modifier = Modifier.padding(top = 6.dp)) }

            /* ---------- О приложении ---------- */
            Section("О приложении")
            Text("DateBook ${BuildConfig.VERSION_NAME}", style = Palm.body, color = Palm.ink)
            var update by remember { mutableStateOf<String?>(null) }
            var newer by remember { mutableStateOf(false) }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PalmButton("Проверить обновление") {
                    scope.launch {
                        update = "Проверяю…"
                        val latest = vm.latestBuild()
                        newer = latest != null && latest > BuildConfig.VERSION_CODE
                        update = when {
                            latest == null -> "Не удалось проверить: нет интернета?"
                            newer -> "Есть новая версия 0.1.$latest"
                            else -> "У вас последняя версия"
                        }
                    }
                }
                if (newer) PalmButton("Скачать", filled = true) {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LATEST_APK)))
                }
            }
            update?.let { Text(it, style = Palm.small, color = Palm.inkSoft, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

private fun durationLabel(m: Int) = when (m) {
    0 -> "весь день"
    60 -> "1 ч"
    120 -> "2 ч"
    else -> if (m % 60 == 0) "${m / 60} ч" else "$m мин"
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
            label = { Text("Сервер $title") }, singleLine = true,
            modifier = Modifier.weight(1f).keepAboveKeyboard(),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = p, onValueChange = { p = it.filter { c -> c.isDigit() }.take(5); onChange(h.trim(), p.toIntOrNull() ?: port) },
            label = { Text("Порт") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(96.dp).keepAboveKeyboard(),
        )
    }
}
