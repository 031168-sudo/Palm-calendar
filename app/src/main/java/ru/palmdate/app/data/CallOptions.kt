package ru.palmdate.app.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds as K

/**
 * Чем можно связаться с человеком: обычный телефон плюс всё, что мессенджеры
 * (WhatsApp, Telegram, Viber…) сами добавили в карточку контакта — "Звонок WhatsApp",
 * "Видеозвонок", "Сообщение". Запуск такого действия — настоящий звонок через мессенджер.
 */
data class CallOption(
    val key: String,          // "PHONE" или тип данных мессенджера — по нему запоминаем выбор
    val app: String,          // "Телефон", "WhatsApp"
    val action: String,       // "Звонок +7 916 …", "Видеозвонок"
    val icon: Drawable?,
    val intent: Intent,
)

class CallOptions(private val context: Context) {
    private val resolver = context.contentResolver
    private val pm = context.packageManager
    private val prefs = context.getSharedPreferences("call_choice", Context.MODE_PRIVATE)

    /** Стандартные поля контакта — это не действия мессенджеров. */
    private val standard = setOf(
        K.Phone.CONTENT_ITEM_TYPE, K.Email.CONTENT_ITEM_TYPE, K.StructuredName.CONTENT_ITEM_TYPE,
        K.Photo.CONTENT_ITEM_TYPE, K.Organization.CONTENT_ITEM_TYPE, K.Nickname.CONTENT_ITEM_TYPE,
        K.Note.CONTENT_ITEM_TYPE, K.StructuredPostal.CONTENT_ITEM_TYPE, K.GroupMembership.CONTENT_ITEM_TYPE,
        K.Website.CONTENT_ITEM_TYPE, K.Im.CONTENT_ITEM_TYPE, K.Event.CONTENT_ITEM_TYPE,
        K.Relation.CONTENT_ITEM_TYPE, K.SipAddress.CONTENT_ITEM_TYPE, K.Identity.CONTENT_ITEM_TYPE,
    )

    fun options(lookupKey: String, phone: String?): List<CallOption> {
        val list = ArrayList<CallOption>()
        if (phone != null) {
            list += CallOption(
                "PHONE", "Телефон", "Звонок $phone", appIcon("com.android.dialer") ?: dialerIcon(),
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))),
            )
        }
        val seen = HashSet<String>()
        runCatching {
            resolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE,
                    ContactsContract.Data.DATA3, ContactsContract.Data.DATA2, ContactsContract.Data.RES_PACKAGE,
                ),
                "${ContactsContract.Data.LOOKUP_KEY} = ?", arrayOf(lookupKey), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val mime = c.getString(1) ?: continue
                    if (mime in standard) continue
                    val summary = (c.getString(2) ?: c.getString(3))?.trim().orEmpty()
                    if (!seen.add("$mime|$summary")) continue
                    val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, c.getLong(0))
                    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                    val ri = pm.resolveActivity(intent, 0) ?: continue // никто не умеет — не показываем
                    val pkg = c.getString(4) ?: ri.activityInfo.packageName
                    val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                    list += CallOption(mime, app, summary.ifEmpty { app }, appIcon(pkg), intent)
                }
            }
        }
        // Сначала телефон, потом мессенджеры по названию; внутри — звонки раньше сообщений
        return list.sortedWith(compareBy({ it.key != "PHONE" }, { it.app.lowercase() }, { !it.action.contains("звон", true) && !it.action.contains("call", true) }))
    }

    /** Запомненный способ для контакта ("PHONE" или тип данных мессенджера), или null. */
    fun remembered(lookupKey: String): String? = prefs.getString(lookupKey, null)

    fun remember(lookupKey: String, key: String?) {
        prefs.edit().apply { if (key == null) remove(lookupKey) else putString(lookupKey, key) }.apply()
    }

    /** Подпись запомненного способа: "WhatsApp" / "Телефон". */
    fun rememberedLabel(lookupKey: String, phone: String?): String? {
        val key = remembered(lookupKey) ?: return null
        return options(lookupKey, phone).firstOrNull { it.key == key }?.let { "${it.app}: ${it.action}" }
            ?: if (key == "PHONE") "Телефон" else null
    }

    private fun appIcon(pkg: String): Drawable? = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()

    private fun dialerIcon(): Drawable? = runCatching {
        pm.resolveActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:1")), 0)?.loadIcon(pm)
    }.getOrNull()
}
