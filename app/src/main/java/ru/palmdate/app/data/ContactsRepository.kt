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

    /** День рождения из карточки контакта; год может быть неизвестен. */
    data class Birthday(val lookupKey: String, val name: String, val month: Int, val day: Int, val year: Int?)

    @Volatile private var birthdayCache: Pair<Long, List<Birthday>>? = null

    /** Все дни рождения из контактов. Кэш на минуту — экраны зовут это при каждой перерисовке. */
    fun birthdays(): List<Birthday> {
        birthdayCache?.let { (t, list) -> if (System.currentTimeMillis() - t < 60_000) return list }
        val list = ArrayList<Birthday>()
        val seen = HashSet<String>()
        runCatching {
            resolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.LOOKUP_KEY,
                    ContactsContract.Data.DISPLAY_NAME_PRIMARY,
                    ContactsContract.CommonDataKinds.Event.START_DATE,
                ),
                "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
                arrayOf(
                    ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString(),
                ),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val key = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val date = parseDate(c.getString(2)) ?: continue
                    if (!seen.add(key)) continue // у слитого контакта дата может быть в нескольких источниках
                    list += Birthday(key, name, date.first, date.second, date.third)
                }
            }
        }
        birthdayCache = System.currentTimeMillis() to list
        return list
    }

    /** Дата из контакта → (месяц, день, год?). Год 1604 и прочие "неизвестные" отбрасываем. */
    private fun parseDate(s: String?): Triple<Int, Int, Int?>? =
        parseRaw(s)
            ?.takeIf { (month, day, _) -> month in 1..12 && day in 1..31 }
            ?.let { (month, day, year) -> Triple(month, day, year?.takeIf { it in 1901..2200 }) }

    /** Форматы дат в контактах: 1980-05-17, --05-17, 19800517, 17.05.1980, 17.05. */
    private fun parseRaw(s: String?): Triple<Int, Int, Int?>? {
        val t = s?.trim() ?: return null
        Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""").find(t)?.let { m ->
            return Triple(m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[1].toInt())
        }
        Regex("""^--(\d{1,2})-?(\d{1,2})""").find(t)?.let { m ->
            return Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), null)
        }
        Regex("""^(\d{4})(\d{2})(\d{2})$""").find(t)?.let { m ->
            return Triple(m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[1].toInt())
        }
        Regex("""^(\d{1,2})\.(\d{1,2})\.?(\d{4})?""").find(t)?.let { m ->
            return Triple(m.groupValues[2].toInt(), m.groupValues[1].toInt(), m.groupValues[3].toIntOrNull())
        }
        return null
    }

    fun invalidate() {
        cache.clear()
        birthdayCache = null
    }
}
