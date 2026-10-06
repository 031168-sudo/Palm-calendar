package ru.palmdate.app.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import ru.palmdate.app.model.ContactRef

class ContactsRepository(context: Context) {
    private val resolver = context.contentResolver
    private val cache = HashMap<String, ContactRef?>()

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

    /** Полные данные контакта: имя, основной телефон, адрес. С кэшем — экран дня зовёт это часто. */
    fun byLookupKey(key: String): ContactRef? = cache.getOrPut(key) {
        val name = resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            "${ContactsContract.Contacts.LOOKUP_KEY} = ?", arrayOf(key), null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null } ?: return@getOrPut null

        val phone = resolver.query(
            Phone.CONTENT_URI, arrayOf(Phone.NUMBER),
            "${Phone.LOOKUP_KEY} = ?", arrayOf(key),
            "${Phone.IS_SUPER_PRIMARY} DESC, ${Phone.IS_PRIMARY} DESC",
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

        val address = resolver.query(
            StructuredPostal.CONTENT_URI, arrayOf(StructuredPostal.FORMATTED_ADDRESS),
            "${StructuredPostal.LOOKUP_KEY} = ?", arrayOf(key), null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

        ContactRef(key, name, phone, address)
    }

    fun invalidate() = cache.clear()
}
