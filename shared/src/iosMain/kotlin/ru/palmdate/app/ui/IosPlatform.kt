package ru.palmdate.app.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import ru.palmdate.app.time.now
import kotlinx.datetime.LocalDate
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.URLQueryAllowedCharacterSet
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.UIKit.UIApplication
import ru.palmdate.app.IosLang
import ru.palmdate.app.IosStore
import ru.palmdate.app.model.ContactRef
import ru.palmdate.app.model.PalmEvent

/** Что экраны просят у iPhone. Пока работают карты и звонок; остальное — позже. */
object IosPlatform : PlatformActions {
    // Свои адреса и «всегда так для этого человека» хранятся в памяти телефона и переживают перезапуск
    override val ownAddresses = object : OwnAddressStore {
        override fun get(eventId: Long): String? = IosStore.getString("own_address_$eventId")?.takeIf { it.isNotBlank() }
        override fun set(eventId: Long, address: String?) {
            if (address.isNullOrBlank()) IosStore.remove("own_address_$eventId") else IosStore.putString("own_address_$eventId", address.trim())
        }
    }
    override val callPrefs = object : CallPrefsStore {
        override fun rememberedLabel(lookupKey: String): String? = IosCallPrefs.remembered(lookupKey)?.let { k -> IosCallPrefs.label(k) }
        override fun remember(lookupKey: String, key: String?) = IosCallPrefs.set(lookupKey, key)
    }

    private fun open(url: String) {
        NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any?>(), null) }
    }

    override fun openMap(address: String) = IosMaps.open(address)

    override fun openDocument(doc: TripDoc) {}

    override fun runPrimaryAction(e: PalmEvent, call: (ContactRef) -> Unit, mail: (PalmEvent) -> Unit) {
        val c = e.contact
        val place = e.place ?: c?.address
        when {
            e.type == ru.palmdate.app.model.EventType.MAIL && e.mail != null -> mail(e)
            e.fromContacts || (c != null && e.type == ru.palmdate.app.model.EventType.CALL) -> c?.let(call)
            place != null -> openMap(place)
        }
    }

    override fun openInCalendar(e: PalmEvent) {}

    @Composable
    override fun RepeatDialog(rrule: String?, anchor: LocalDate, allDay: Boolean, onDismiss: () -> Unit, onResult: (String?) -> Unit) {
        // Своё правило повтора на iPhone пока недоступно
    }
}

/** Настройки, зависящие от системы. На iPhone пока только язык. */
object IosSettings : SettingsPlatform {
    override val langMode: String get() = IosLang.mode
    override fun setLangMode(mode: String) = IosLang.setMode(mode)
    override fun onSettingsChanged() {}

    @Composable
    override fun BirthdayNotifySwitch(on: Boolean, onChange: (Boolean) -> Unit) {
        SettingsToggle(ru.palmdate.app.str("settings_birthday_notify"), on, onChange)
    }

    @Composable
    override fun ColumnScope.ExtraSections() {
        SettingsSection(ru.palmdate.app.str("settings_mail"))
        var app by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(IosMailPrefs.remembered()) }
        androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f)) {
                SettingsLabel(ru.palmdate.app.str("settings_mail_app"))
                androidx.compose.material3.Text(
                    app?.label ?: ru.palmdate.app.str("settings_ask_on_open"),
                    style = ru.palmdate.app.ui.theme.Palm.body, color = ru.palmdate.app.ui.theme.Palm.ink,
                )
            }
            if (app != null) androidx.compose.material3.TextButton(onClick = { IosMailPrefs.set(null); app = null }) {
                androidx.compose.material3.Text(ru.palmdate.app.str("settings_reset"))
            }
        }
        SettingsHint(ru.palmdate.app.str("settings_mail_app_hint"))

        SettingsSection(ru.palmdate.app.str("settings_maps"))
        var map by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(IosMapPrefs.remembered()) }
        androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f)) {
                SettingsLabel(ru.palmdate.app.str("settings_maps_app"))
                androidx.compose.material3.Text(
                    map?.label ?: ru.palmdate.app.str("settings_ask_on_open"),
                    style = ru.palmdate.app.ui.theme.Palm.body, color = ru.palmdate.app.ui.theme.Palm.ink,
                )
            }
            if (map != null) androidx.compose.material3.TextButton(onClick = { IosMapPrefs.set(null); map = null }) {
                androidx.compose.material3.Text(ru.palmdate.app.str("settings_reset"))
            }
        }
        SettingsHint(ru.palmdate.app.str("settings_maps_hint"))

        SettingsSection(ru.palmdate.app.str("settings_data"))
        SettingsHint(ru.palmdate.app.str("settings_data_hint_ios"))
        var result by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            PalmButton(ru.palmdate.app.str("settings_backup_save")) {
                result = runCatching {
                    val name = ru.palmdate.app.str("settings_backup_filename", kotlinx.datetime.LocalDate.now().toString())
                    if (IosBackup.share(name, IosBackup.export())) null else error(ru.palmdate.app.str("settings_backup_save_failed"))
                }.getOrElse { "✗ " + (it.message ?: ru.palmdate.app.str("settings_backup_save_failed")) }
            }
            PalmButton(ru.palmdate.app.str("settings_backup_restore")) {
                IosBackup.pick { text ->
                    result = runCatching {
                        if (text == null) error(ru.palmdate.app.str("settings_backup_restore_failed"))
                        ru.palmdate.app.str("settings_backup_restored", IosBackup.import(text)) + "\n" + ru.palmdate.app.str("settings_backup_restart")
                    }.getOrElse { "✗ " + (it.message ?: ru.palmdate.app.str("settings_backup_restore_failed")) }
                }
            }
        }
        result?.let {
            androidx.compose.material3.Text(
                it, style = ru.palmdate.app.ui.theme.Palm.small,
                color = if (it.startsWith("✗")) ru.palmdate.app.ui.theme.Palm.nowLine else androidx.compose.ui.graphics.Color(0xFF2E7D32),
                modifier = androidx.compose.ui.Modifier.padding(top = 6.dp),
            )
        }
    }
}
