package ru.palmdate.app.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
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

    @Suppress("CAST_NEVER_SUCCEEDS")
    override fun openMap(address: String) {
        val q = (address as NSString).stringByAddingPercentEncodingWithAllowedCharacters(NSCharacterSet.URLQueryAllowedCharacterSet) ?: address
        open("http://maps.apple.com/?q=$q")
    }

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
    }
}
