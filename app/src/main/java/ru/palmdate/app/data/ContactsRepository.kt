package ru.palmdate.app.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import ru.palmdate.app.model.ContactRef
import ru.palmdate.app.model.PhoneNumber
import java.util.concurrent.ConcurrentHashMap

class ContactsRepository(private val context: Context) {
    private val resolver = context.contentResolver

    private data class Base(val name: String, val address: String?)
    private val cache = ConcurrentHashMap<String, Base>()

    /** Поиск по телефонной книге (пустой запрос — первые контакты по алфавиту). */
    fun search(query: String, limit: Int = 50): List<ContactRef> {
        val uri = if (query.isBlank()) ContactsContract.Contacts.CONTENT_URI
        else Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query.trim()))
        val result = ArrayList<ContactRef>()
        resolver.query(
            uri,
            arrayOf(ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            null, null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
        )?.use { c ->
            while (c.moveToNext() && result.size < limit) {
                val key = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                result += ContactRef(key, name)
            }
        }
        return result
    }

    /** Все номера контакта с подписями ("Мобильный", "Рабочий"…), основной первым, без дублей. */
    fun phones(lookupKey: String): List<PhoneNumber> {
        val list = ArrayList<PhoneNumber>()
        val seen = HashSet<String>()
        resolver.query(
            Phone.CONTENT_URI, arrayOf(Phone.NUMBER, Phone.TYPE, Phone.LABEL),
            "${Phone.LOOKUP_KEY} = ?", arrayOf(lookupKey),
            "${Phone.IS_SUPER_PRIMARY} DESC, ${Phone.IS_PRIMARY} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val number = c.getString(0) ?: continue
                if (!seen.add(number.filter { it.isDigit() || it == '+' })) continue
                val label = Phone.getTypeLabel(context.resources, c.getInt(1), c.getString(2)).toString()
                list += PhoneNumber(number, label)
            }
        }
        return list
    }

    /** Имя и адрес контакта; номер подставляет вызывающий (выбранный для события). С кэшем. */
    fun byLookupKey(key: String, phone: String?): ContactRef? {
        val base = cache[key] ?: loadBase(key)?.also { cache[key] = it } ?: return null
        return ContactRef(key, base.name, phone ?: phones(key).firstOrNull()?.number, base.address)
    }

    private fun loadBase(key: String): Base? {
        val name = resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            "${ContactsContract.Contacts.LOOKUP_KEY} = ?", arrayOf(key), null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null } ?: return null

        val address = resolver.query(
            StructuredPostal.CONTENT_URI, arrayOf(StructuredPostal.FORMATTED_ADDRESS),
            "${StructuredPostal.LOOKUP_KEY} = ?", arrayOf(key), null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

        return Base(name, address)
    }

    fun invalidate() = cache.clear()
}
