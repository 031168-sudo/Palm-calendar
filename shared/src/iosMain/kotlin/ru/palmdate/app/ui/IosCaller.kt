package ru.palmdate.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import ru.palmdate.app.IosStore
import ru.palmdate.app.model.ContactRef
import ru.palmdate.app.str
import ru.palmdate.app.ui.theme.Palm

/** Способ связаться с человеком: телефон или установленный мессенджер. */
internal class CallWay(val key: String, val label: String, val action: String, val url: String)

private class Messenger(val key: String, val label: String, val probe: String, val chat: (String) -> String)

// Схемы probe прописаны в Info.plist (LSApplicationQueriesSchemes), иначе iPhone не скажет, установлена ли программа
private val MESSENGERS = listOf(
    Messenger("whatsapp", "WhatsApp", "whatsapp://") { d -> "https://wa.me/$d" },
    Messenger("telegram", "Telegram", "tg://") { d -> "tg://resolve?phone=$d" },
    Messenger("viber", "Viber", "viber://") { d -> "viber://chat?number=%2B$d" },
)

private fun canOpen(url: String): Boolean = NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.canOpenURL(it) } ?: false

private fun digitsOf(number: String): String {
    var d = number.filter { it.isDigit() }
    if (d.length == 11 && d.startsWith("8")) d = "7" + d.drop(1)
    if (d.length == 10) d = "7$d"
    return d
}

/** «Всегда так для этого человека»: "phone" или ключ мессенджера. */
internal object IosCallPrefs {
    fun remembered(lookupKey: String): String? = IosStore.getString("call_via_$lookupKey")?.takeIf { it.isNotBlank() }
    fun set(lookupKey: String, key: String?) { if (key == null) IosStore.remove("call_via_$lookupKey") else IosStore.putString("call_via_$lookupKey", key) }
    fun label(key: String): String? = if (key == "phone") str("call_phone") else MESSENGERS.firstOrNull { it.key == key }?.label
}

internal fun callWays(phone: String?): List<CallWay> {
    if (phone.isNullOrBlank()) return emptyList()
    val list = ArrayList<CallWay>()
    val tel = phone.filter { it.isDigit() || it == '+' }
    list += CallWay("phone", str("call_phone"), str("call_action_phone", phone), "tel:$tel")
    val d = digitsOf(phone)
    for (m in MESSENGERS) if (canOpen(m.probe)) list += CallWay(m.key, m.label, str("call_open_chat"), m.chat(d))
    return list
}

private fun openUrl(url: String): Boolean {
    val u = NSURL.URLWithString(url) ?: return false
    UIApplication.sharedApplication.openURL(u, emptyMap<Any?, Any?>(), null)
    return true
}

/** «Связаться»: экран зовёт LocalCaller.current(контакт) — показываем список способов (или сразу запомненный). */
@Composable
fun IosCallerHost(content: @Composable () -> Unit) {
    var target by remember { mutableStateOf<ContactRef?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    CompositionLocalProvider(LocalCaller provides { target = it }) { content() }
    target?.let { c ->
        CallChooser(c, onNote = { note = it }, onDone = { target = null })
    }
    note?.let { text ->
        AlertDialog(
            onDismissRequest = { note = null },
            containerColor = Palm.paper,
            text = { Text(text, style = Palm.body, color = Palm.ink) },
            confirmButton = { TextButton(onClick = { note = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun CallChooser(contact: ContactRef, onNote: (String) -> Unit, onDone: () -> Unit) {
    var options by remember { mutableStateOf<List<CallWay>?>(null) }
    var always by remember { mutableStateOf(false) }

    fun start(w: CallWay) {
        if (!openUrl(w.url)) onNote(str("err_no_app"))
    }

    LaunchedEffect(contact.lookupKey) {
        val list = callWays(contact.phone)
        val remembered = IosCallPrefs.remembered(contact.lookupKey)
        val auto = list.firstOrNull { it.key == remembered } ?: list.singleOrNull()
        when {
            list.isEmpty() -> { onNote(str("err_no_number")); onDone() }
            auto != null -> { start(auto); onDone() }
            else -> options = list
        }
    }

    val list = options ?: return
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = Palm.paper,
        title = { Text(str("contact_via_title", contact.name), style = Palm.title, color = Palm.ink) },
        text = {
            Column {
                list.forEach { w ->
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .clickable { if (always) IosCallPrefs.set(contact.lookupKey, w.key); start(w); onDone() }
                            .dottedRule().padding(vertical = 8.dp),
                    ) {
                        Text(w.label, style = Palm.body, color = Palm.ink)
                        Text(w.action, style = Palm.small, color = Palm.inkSoft, maxLines = 2)
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { always = !always }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = always, onCheckedChange = { always = it })
                    Text(str("contact_via_always"), style = Palm.body, color = Palm.ink)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text(str("cancel")) } },
    )
}
