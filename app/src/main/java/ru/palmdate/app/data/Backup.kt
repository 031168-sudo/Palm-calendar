package ru.palmdate.app.data

import ru.palmdate.app.R
import ru.palmdate.app.str
import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import android.util.Base64
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Резервная копия всего, что DateBook хранит сам (а не в Google): привязки типов и контактов,
 * итоги, выбранные номера и адреса, настройки. Пароль почты в копию не попадает.
 *
 * Связи привязаны к номеру события в календаре, а на новом телефоне номера другие.
 * Поэтому каждая запись копии также несёт название события («ev») и его время («at»):
 * при восстановлении сначала ищем событие по старому номеру, потом по названию и времени.
 */
class Backup(private val context: Context, private val links: LinkDao) {

    private val resolver get() = context.contentResolver
    private val refCache = HashMap<Long, Ref?>()

    /** Название и время начала события в календаре (для сопоставления на новом телефоне). */
    private data class Ref(val title: String, val begin: Long)

    private fun refOf(eventId: Long): Ref? = refCache.getOrPut(eventId) {
        runCatching {
            resolver.query(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART), null, null, null,
            )?.use { c -> if (c.moveToFirst()) Ref(c.getString(0).orEmpty(), c.getLong(1)) else null }
        }.getOrNull()
    }

    /**
     * Номер события на этом телефоне для записи из копии. [instance] — итог одного раза серии
     * (ищем по разу, begin = начало раза), иначе — событие целиком (begin = его начало).
     * Если ничего не нашли, возвращаем старый номер, как и раньше.
     */
    private fun newIdFor(oldId: Long, title: String?, begin: Long?, instance: Boolean): Long {
        if (title == null || begin == null) return oldId // копия старого формата
        val here = refOf(oldId)
        if (here != null && here.title == title && (instance || here.begin == begin)) return oldId
        val found = runCatching {
            if (instance) {
                CalendarContract.Instances.query(
                    resolver, arrayOf(CalendarContract.Instances.EVENT_ID), begin, begin + 1,
                    "${CalendarContract.Instances.TITLE} = ?", arrayOf(title), null,
                )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
            } else {
                resolver.query(
                    CalendarContract.Events.CONTENT_URI, arrayOf(CalendarContract.Events._ID),
                    "${CalendarContract.Events.TITLE} = ? AND ${CalendarContract.Events.DTSTART} = ?",
                    arrayOf(title, begin.toString()), null,
                )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
            }
        }.getOrNull()
        return found ?: oldId
    }

    /** Добавляет в запись название и время события, если их удалось прочитать. */
    private fun JSONObject.withRef(eventId: Long, instanceStart: Long? = null): JSONObject {
        refOf(eventId)?.let { put("ev", it.title); put("at", instanceStart ?: it.begin) }
        return this
    }

    suspend fun export(): String {
        val j = JSONObject()
        j.put("format", "datebook-1")
        j.put("links", JSONArray().apply {
            links.allLinks().forEach {
                put(JSONObject().put("e", it.eventId).put("t", it.type).put("c", it.lookupKey).put("p", it.phone).withRef(it.eventId))
            }
        })
        j.put("outcomes", JSONArray().apply {
            links.allOutcomes().forEach {
                put(JSONObject().put("e", it.eventId).put("i", it.instanceStart).put("s", it.status).put("n", it.note)
                    .withRef(it.eventId, it.instanceStart))
            }
        })
        j.put("mail", JSONArray().apply {
            links.allMail().forEach {
                put(JSONObject().put("e", it.eventId).put("k", it.kind).put("id", it.messageId).put("f", it.folder)
                    .put("s", it.subject).put("pn", it.peerName).put("pa", it.peerAddr).put("d", it.date)
                    .put("dr", it.draft).put("di", it.draftId).withRef(it.eventId))
            }
        })
        // Документы выезда: сам файл вкладываем в копию (base64)
        j.put("trip_files", JSONArray().apply {
            links.allFiles().forEach { f ->
                val bytes = runCatching { File(f.path).readBytes() }.getOrNull() ?: return@forEach
                put(JSONObject().put("e", f.eventId).put("l", f.label).put("n", f.name).put("m", f.mime)
                    .put("d", Base64.encodeToString(bytes, Base64.NO_WRAP)).withRef(f.eventId))
            }
        })
        j.put("phones", JSONArray().apply {
            links.allPhones().forEach { put(JSONObject().put("c", it.lookupKey).put("p", it.number)) }
        })
        val addr = context.getSharedPreferences("contact_address", Context.MODE_PRIVATE)
        j.put("addresses", JSONObject().apply { addr.all.forEach { (k, v) -> put(k, v.toString()) } })
        j.put("ownAddresses", JSONObject().apply { OwnAddresses(context).all().forEach { (k, v) -> put(k, v) } })
        j.put("settings", SettingsStore.current.toJson())
        return j.toString(1)
    }

    /** Восстановить из копии. Возвращает, сколько записей восстановлено. */
    suspend fun import(text: String): Int {
        val j = JSONObject(text)
        require(j.optString("format").startsWith("datebook")) { str(R.string.err_not_backup) }
        var n = 0
        fun JSONObject.textOrNull(k: String) = optString(k).takeIf { it.isNotEmpty() && it != "null" }
        fun JSONObject.longOrNull(k: String) = if (has(k) && !isNull(k)) getLong(k) else null
        j.optJSONArray("links")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val id = newIdFor(o.getLong("e"), o.textOrNull("ev"), o.longOrNull("at"), instance = false)
                links.upsert(EventLink(id, o.getString("t"), o.textOrNull("c"), o.textOrNull("p")))
                n++
            }
        }
        j.optJSONArray("outcomes")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val id = newIdFor(o.getLong("e"), o.textOrNull("ev"), o.longOrNull("at"), instance = true)
                links.setOutcome(OutcomeRow(id, o.getLong("i"), o.getString("s"), o.textOrNull("n")))
                n++
            }
        }
        j.optJSONArray("mail")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val id = newIdFor(o.getLong("e"), o.textOrNull("ev"), o.longOrNull("at"), instance = false)
                links.upsertMail(MailLink(id, o.getString("k"), o.textOrNull("id"), o.textOrNull("f"), o.textOrNull("s"),
                    o.textOrNull("pn"), o.textOrNull("pa"), o.longOrNull("d"), o.textOrNull("dr"), o.textOrNull("di")))
                n++
            }
        }
        j.optJSONArray("trip_files")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val eventId = newIdFor(o.getLong("e"), o.textOrNull("ev"), o.longOrNull("at"), instance = false)
                val name = o.getString("n")
                val dir = File(context.filesDir, "trips/$eventId").apply { mkdirs() }
                val file = File(dir, UUID.randomUUID().toString() + "_" + TripFiles.safeName(name))
                file.writeBytes(Base64.decode(o.getString("d"), Base64.DEFAULT))
                links.insertFile(TripFile(
                    eventId = eventId, label = o.getString("l"), name = name, path = file.absolutePath,
                    mime = o.textOrNull("m"),
                ))
                n++
            }
        }
        j.optJSONArray("phones")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                links.rememberPhone(ContactPhone(o.getString("c"), o.getString("p")))
                n++
            }
        }
        j.optJSONObject("addresses")?.let { o ->
            val ed = context.getSharedPreferences("contact_address", Context.MODE_PRIVATE).edit()
            o.keys().forEach { k -> ed.putString(k, o.getString(k)); n++ }
            ed.apply()
        }
        j.optJSONObject("ownAddresses")?.let { o ->
            OwnAddresses(context).putAll(o.keys().asSequence().associateWith { o.getString(it) })
            n += o.length()
        }
        j.optJSONObject("settings")?.let { SettingsStore.replace(AppSettings.fromJson(it)) }
        return n
    }
}
