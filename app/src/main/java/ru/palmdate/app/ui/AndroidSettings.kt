package ru.palmdate.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.palmdate.app.BirthdayReminder
import ru.palmdate.app.BuildConfig
import ru.palmdate.app.DayViewModel
import ru.palmdate.app.Lang
import ru.palmdate.app.LangMode
import ru.palmdate.app.R
import ru.palmdate.app.str
import ru.palmdate.app.ui.theme.Palm

private const val LATEST_APK = "https://github.com/031168-sudo/Palm-calendar/releases/latest/download/DateBook.apk"

/** Android-часть настроек: язык (с пересозданием окна), разрешение на уведомления, почтовая программа, резервная копия, обновления. */
class AndroidSettingsPlatform(private val ctx: Context, private val vm: DayViewModel) : SettingsPlatform {
    override val langMode: String get() = Lang.mode.name

    override fun setLangMode(mode: String) {
        Lang.setMode(LangMode.valueOf(mode))
        vm.settingsChanged()
        ctx.findActivity()?.recreate() // пересоздаём экран, чтобы все тексты стали на новом языке
    }

    override fun onSettingsChanged() {
        vm.settingsChanged()
        BirthdayReminder.schedule(ctx)
    }

    @Composable
    override fun BirthdayNotifySwitch(on: Boolean, onChange: (Boolean) -> Unit) {
        val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> onChange(ok) }
        SettingsToggle(str(R.string.settings_birthday_notify), on) { v ->
            if (v && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else onChange(v)
        }
    }

    @Composable
    override fun ColumnScope.ExtraSections() {
        val scope = rememberCoroutineScope()
            /* ---------- Почта ---------- */
            SettingsSection(str(R.string.settings_mail))
            val apps = remember { ru.palmdate.app.data.MailApps(ctx) }
            var appPkg by remember { mutableStateOf(apps.remembered()) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SettingsLabel(str(R.string.settings_mail_app))
                    Text(appPkg?.let { apps.label(it) } ?: str(R.string.settings_ask_on_open), style = Palm.body, color = Palm.ink)
                }
                if (appPkg != null) TextButton(onClick = { apps.remember(null); appPkg = null }) { Text(str(R.string.settings_reset)) }
            }
            SettingsHint(str(R.string.settings_mail_app_hint))

            /* ---------- Данные ---------- */
            SettingsSection(str(R.string.settings_data))
            SettingsHint(str(R.string.settings_data_hint))
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
            SettingsSection(str(R.string.settings_about))
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

/** Окно (Activity), в котором показан экран, — нужно, чтобы пересоздать его при смене языка. */
private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
