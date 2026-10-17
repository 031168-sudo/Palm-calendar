package ru.palmdate.app.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.URLQueryAllowedCharacterSet
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.UIKit.UIApplication
import ru.palmdate.app.IosLang
import ru.palmdate.app.model.ContactRef
import ru.palmdate.app.model.PalmEvent

/** Что экраны просят у iPhone. Пока работают карты и звонок; остальное — позже. */
object IosPlatform : PlatformActions {
    private val ownMemory = HashMap<Long, String>()
    private val viaMemory = HashMap<String, String>()

    override val ownAddresses = object : OwnAddressStore {
        override fun get(eventId: Long): String? = ownMemory[eventId]
        override fun set(eventId: Long, address: String?) { if (address.isNullOrBlank()) ownMemory.remove(eventId) else ownMemory[eventId] = address }
    }
    override val callPrefs = object : CallPrefsStore {
        override fun rememberedLabel(lookupKey: String): String? = viaMemory[lookupKey]
        override fun remember(lookupKey: String, key: String?) { if (key == null) viaMemory.remove(lookupKey) else viaMemory[lookupKey] = key }
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
    override fun ColumnScope.ExtraSections() {}
}
