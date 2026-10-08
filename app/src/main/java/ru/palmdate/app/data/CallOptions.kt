package ru.palmdate.app.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.ContactsContract

/**
 * Чем можно связаться с человеком: обычный телефон + установленные мессенджеры,
 * открытые сразу на чате с ним. Есть ли человек в мессенджере — видно по отметкам,
 * которые мессенджер сам добавляет в карточку контакта.
 */
data class CallOption(
    val key: String,          // "PHONE" или пакет мессенджера — по нему запоминаем выбор
    val app: String,          // "Телефон", "WhatsApp"
    val action: String,       // "Звонок +7…", "Открыть чат", "Нет в контактах Viber"
    val icon: Drawable?,
    val intent: Intent,
    val available: Boolean = true, // false — человека в мессенджере не видно (серым)
)

/** Мессенджер из списка известных: как открыть чат по номеру и как узнать его отметки в контактах. */
private data class Messenger(
    val pkg: String,
    val name: String,
    val mimeKey: String,                    // кусок типа данных его отметок в контактах
    val chatByPhone: ((String) -> Intent)?, // null — по номеру открыть нельзя (MAX)
)

private val MESSENGERS = listOf(
    Messenger("com.whatsapp", "WhatsApp", "whatsapp") { d -> Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$d")) },
    Messenger("com.whatsapp.w4b", "WhatsApp Business", "whatsapp") { d -> Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$d")) },
    Messenger("org.telegram.messenger", "Telegram", "telegram") { d -> Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?phone=$d")) },
    Messenger("com.viber.voip", "Viber", "viber") { d -> Intent(Intent.ACTION_VIEW, Uri.parse("viber://chat?number=%2B$d")) },
    Messenger("ru.oneme.app", "MAX", "oneme", null),
)

class CallOptions(private val context: Context) {
    private val resolver = context.contentResolver
    private val pm = context.packageManager
    private val prefs = context.getSharedPreferences("call_choice", Context.MODE_PRIVATE)

    fun options(lookupKey: String, phone: String?): List<CallOption> {
        val list = ArrayList<CallOption>()
        if (phone != null) {
            list += CallOption(
                "PHONE", "Телефон", "Звонок $phone", dialerIcon(),
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))),
            )
        }

        // Отметки мессенджеров в карточке контакта: тип данных → id строки
        val marks = ArrayList<Pair<String, Long>>()
        runCatching {
            resolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE),
                "${ContactsContract.Data.LOOKUP_KEY} = ?", arrayOf(lookupKey), null,
            )?.use { c -> while (c.moveToNext()) marks += (c.getString(1) ?: "") to c.getLong(0) }
        }

        val digits = phone?.let { normalizePhone(it) }
        for (m in MESSENGERS) {
            if (!isInstalled(m.pkg)) continue
            val own = marks.filter { it.first.contains(m.mimeKey, ignoreCase = true) }
            val present = own.isNotEmpty()
            val intent: Intent = when {
                m.chatByPhone != null && digits != null -> m.chatByPhone.invoke(digits).setPackage(m.pkg)
                present -> {
                    // Нет ссылки по номеру (MAX) — открываем через его отметку; лучше "написать", чем "позвонить"
                    val (mime, id) = own.firstOrNull { !it.first.contains("call", true) && !it.first.contains("voip", true) } ?: own.first()
                    Intent(Intent.ACTION_VIEW).setDataAndType(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, id), mime)
                        .setPackage(m.pkg)
                }
                else -> pm.getLaunchIntentForPackage(m.pkg) ?: continue
            }
            val action = when {
                present -> "Открыть чат"
                m.chatByPhone == null -> "Человек не отмечен в MAX — откроется приложение, найдите его там"
                else -> "Нет в контактах ${m.name} — мессенджер проверит номер сам"
            }
            list += CallOption(m.pkg, m.name, action, appIcon(m.pkg), intent, available = present)
        }
        return list
    }

    /** Запомненный способ для контакта ("PHONE" или пакет мессенджера), или null. */
    fun remembered(lookupKey: String): String? = prefs.getString(lookupKey, null)

    fun remember(lookupKey: String, key: String?) {
        prefs.edit().apply { if (key == null) remove(lookupKey) else putString(lookupKey, key) }.apply()
    }

    /** Подпись запомненного способа: "WhatsApp" / "Телефон". */
    fun rememberedLabel(lookupKey: String): String? {
        val key = remembered(lookupKey) ?: return null
        if (key == "PHONE") return "Телефон"
        return MESSENGERS.firstOrNull { it.pkg == key }?.name
    }

    private fun isInstalled(pkg: String): Boolean =
        runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    private fun appIcon(pkg: String): Drawable? = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()

    private fun dialerIcon(): Drawable? = runCatching {
        pm.resolveActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1")), 0)?.loadIcon(pm)
    }.getOrNull()

    /** +7 (916) 123-45-67 / 8 916 ... → 79161234567. */
    private fun normalizePhone(number: String): String {
        var d = number.filter { it.isDigit() }
        if (d.length == 11 && d.startsWith("8")) d = "7" + d.drop(1)
        if (d.length == 10) d = "7$d"
        return d
    }
}
