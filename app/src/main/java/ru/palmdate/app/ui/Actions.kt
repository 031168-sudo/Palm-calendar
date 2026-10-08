package ru.palmdate.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import ru.palmdate.app.data.CalendarRepository
import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.PalmEvent

/** Что делает тап по иконке события — как на Palm: звонок набирает номер, встреча ведёт на карту. */
fun primaryActionLabel(e: PalmEvent): String? = when {
    e.fromContacts && e.contact?.phone != null -> "Поздравить"
    e.type == EventType.CALL && e.contact?.phone != null -> "Позвонить"
    e.type == EventType.MEETING && e.contact?.address != null -> "Маршрут"
    else -> null
}

fun Context.runPrimaryAction(e: PalmEvent) {
    val c = e.contact
    when {
        // День рождения из контактов — позвонить имениннику
        e.fromContacts -> c?.phone?.let { dial(it) }
        e.type == EventType.CALL && c?.phone != null -> dial(c.phone)
        e.type == EventType.MEETING && c?.address != null -> openMap(c.address)
        else -> openInCalendar(e)
    }
}

/** Открыть адрес в картах — с выбором приложения (Яндекс, Google, 2ГИС…). */
fun Context.openMap(address: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address)))
    launch(Intent.createChooser(intent, "Открыть в картах"))
}

/**
 * Позвонить: обычным телефоном, через WhatsApp или Telegram — как выбрано в настройках.
 * Мессенджер открывает чат с человеком, оттуда звонок одной кнопкой.
 * Если мессенджера нет — обычный набор.
 */
fun Context.dial(number: String) {
    val digits = normalizePhone(number)
    val via = ru.palmdate.app.data.SettingsStore.current.callVia
    val messenger = when (via) {
        ru.palmdate.app.data.CallVia.WHATSAPP -> Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))
            .setPackage(listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { isInstalled(it) })
            .takeIf { it.`package` != null }
        ru.palmdate.app.data.CallVia.TELEGRAM -> Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?phone=$digits"))
            .setPackage("org.telegram.messenger")
            .takeIf { isInstalled("org.telegram.messenger") }
        ru.palmdate.app.data.CallVia.PHONE -> null
    }
    launch(messenger ?: Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
}

/** +7 (916) 123-45-67 / 8 916 ... → 79161234567 (для мессенджеров). */
private fun normalizePhone(number: String): String {
    var d = number.filter { it.isDigit() }
    if (d.length == 11 && d.startsWith("8")) d = "7" + d.drop(1)
    if (d.length == 10) d = "7$d"
    return d
}

private fun Context.isInstalled(pkg: String): Boolean =
    runCatching { packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

fun Context.openInCalendar(e: PalmEvent) =
    launch(Intent(Intent.ACTION_VIEW, CalendarRepository.eventUri(e.eventId)))

private fun Context.launch(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "Нет приложения для этого действия", Toast.LENGTH_SHORT).show()
    }
}
