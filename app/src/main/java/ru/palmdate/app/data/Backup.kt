package ru.palmdate.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Резервная копия всего, что DateBook хранит сам (а не в Google): привязки типов и контактов,
 * итоги, выбранные номера и адреса, настройки. Пароль почты в копию не попадает.
 */
class Backup(private val context: Context, private val links: LinkDao) {

    suspend fun export(): String {
        val j = JSONObject()
        j.put("format", "datebook-1")
        j.put("links", JSONArray().apply {
            links.allLinks().forEach { put(JSONObject().put("e", it.eventId).put("t", it.type).put("c", it.lookupKey).put("p", it.phone)) }
        })
        j.put("outcomes", JSONArray().apply {
            links.allOutcomes().forEach { put(JSONObject().put("e", it.eventId).put("i", it.instanceStart).put("s", it.status).put("n", it.note)) }
        })
        j.put("mail", JSONArray().apply {
            links.allMail().forEach {
                put(JSONObject().put("e", it.eventId).put("k", it.kind).put("id", it.messageId).put("f", it.folder)
                    .put("s", it.subject).put("pn", it.peerName).put("pa", it.peerAddr).put("d", it.date)
                    .put("dr", it.draft).put("di", it.draftId))
            }
        })
        j.put("phones", JSONArray().apply {
            links.allPhones().forEach { put(JSONObject().put("c", it.lookupKey).put("p", it.number)) }
        })
        val addr = context.getSharedPreferences("contact_address", Context.MODE_PRIVATE)
        j.put("addresses", JSONObject().apply { addr.all.forEach { (k, v) -> put(k, v.toString()) } })
        j.put("settings", SettingsStore.current.toJson())
        return j.toString(1)
    }

    /** Восстановить из копии. Возвращает, сколько записей восстановлено. */
    suspend fun import(text: String): Int {
        val j = JSONObject(text)
        require(j.optString("format").startsWith("datebook")) { "Это не копия DateBook" }
        var n = 0
        j.optJSONArray("links")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                links.upsert(EventLink(o.getLong("e"), o.getString("t"), o.optString("c").takeIf { it.isNotEmpty() && it != "null" },
                    o.optString("p").takeIf { it.isNotEmpty() && it != "null" }))
                n++
            }
        }
        j.optJSONArray("outcomes")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                links.setOutcome(OutcomeRow(o.getLong("e"), o.getLong("i"), o.getString("s"),
                    o.optString("n").takeIf { it.isNotEmpty() && it != "null" }))
                n++
            }
        }
        j.optJSONArray("mail")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                fun str(k: String) = o.optString(k).takeIf { it.isNotEmpty() && it != "null" }
                links.upsertMail(MailLink(o.getLong("e"), o.getString("k"), str("id"), str("f"), str("s"), str("pn"), str("pa"),
                    if (o.has("d") && !o.isNull("d")) o.getLong("d") else null, str("dr"), str("di")))
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
        j.optJSONObject("settings")?.let { SettingsStore.replace(AppSettings.fromJson(it)) }
        return n
    }
}
