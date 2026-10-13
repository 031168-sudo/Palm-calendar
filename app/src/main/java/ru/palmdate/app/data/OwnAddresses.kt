package ru.palmdate.app.data

import android.content.Context

/**
 * Свой адрес события (встречи), введённый вручную. Хранится отдельно от поля «Место» календаря,
 * чтобы он не пропадал, когда для встречи выбран адрес контакта: «Место» — это адрес, который сейчас действует.
 */
class OwnAddresses(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun get(eventId: Long): String? = prefs.getString(eventId.toString(), null)?.takeIf { it.isNotBlank() }

    fun set(eventId: Long, address: String?) {
        prefs.edit().apply {
            if (address.isNullOrBlank()) remove(eventId.toString()) else putString(eventId.toString(), address.trim())
        }.apply()
    }

    fun all(): Map<String, String> = prefs.all.mapValues { it.value.toString() }

    fun putAll(map: Map<String, String>) {
        prefs.edit().apply { map.forEach { (k, v) -> putString(k, v) } }.apply()
    }

    companion object { const val NAME = "own_address" }
}
