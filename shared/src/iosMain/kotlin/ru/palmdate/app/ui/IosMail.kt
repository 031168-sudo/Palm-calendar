package ru.palmdate.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.URLQueryAllowedCharacterSet
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIPasteboard
import ru.palmdate.app.DayModel
import ru.palmdate.app.IosStore
import ru.palmdate.app.model.EventType
import ru.palmdate.app.model.MailKind
import ru.palmdate.app.model.Outcome
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.str
import ru.palmdate.app.ui.theme.Palm

internal class MailApp(val key: String, val label: String, val scheme: String)

// Почтовые программы, которые умеем запускать (схемы прописаны в Info.plist: LSApplicationQueriesSchemes)
private val MAIL_APPS = listOf(
    MailApp("mail", "Mail", "message://"),
    MailApp("gmail", "Gmail", "googlegmail://"),
    MailApp("outlook", "Outlook", "ms-outlook://"),
    MailApp("yandex", "Яндекс Почта", "yandexmail://"),
)

/** Запомненная почтовая программа (если она ещё установлена). */
internal object IosMailPrefs {
    fun remembered(): MailApp? = IosStore.getString("mail_app")?.let { k -> installedMailApps().firstOrNull { it.key == k } }
    fun set(app: MailApp?) { if (app == null) IosStore.putString("mail_app", "") else IosStore.putString("mail_app", app.key) }
}

private fun canOpen(url: String): Boolean = NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.canOpenURL(it) } ?: false
internal fun installedMailApps(): List<MailApp> = MAIL_APPS.filter { canOpen(it.scheme) }
/** Новое письмо в выбранной программе; где нет своей ссылки — почта по умолчанию (mailto). */
private fun composeUrl(app: MailApp, to: String): String = when (app.key) {
    "gmail" -> "googlegmail:///co?to=$to"
    "outlook" -> "ms-outlook://compose?to=$to"
    else -> "mailto:$to"
}

private fun openUrl(url: String): Boolean {
    val u = NSURL.URLWithString(url) ?: return false
    UIApplication.sharedApplication.openURL(u, emptyMap<Any?, Any?>(), null)
    return true
}

@Suppress("CAST_NEVER_SUCCEEDS")
private fun pct(s: String): String =
    (s as NSString).stringByAddingPercentEncodingWithAllowedCharacters(NSCharacterSet.URLQueryAllowedCharacterSet) ?: s

/**
 * Почта на iPhone через почтовую программу: «Написать» открывает новое письмо в программе по умолчанию,
 * «Ответить» запускает программу и кладёт слова для поиска в буфер; по возвращении спрашиваем, отправлено ли письмо.
 */
@Composable
fun IosMailHost(model: DayModel, content: @Composable () -> Unit) {
    var chooseApp by remember { mutableStateOf<((MailApp) -> Unit)?>(null) }
    var awaiting by remember { mutableStateOf<PalmEvent?>(null) }
    var left by remember { mutableStateOf(false) }
    var askSent by remember { mutableStateOf<PalmEvent?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var replyPrompt by remember { mutableStateOf<Triple<PalmEvent, MailApp, String>?>(null) }

    fun withApp(action: (MailApp) -> Unit) {
        val list = installedMailApps()
        val r = IosMailPrefs.remembered()
        when {
            r != null -> action(r)
            list.isEmpty() -> note = str("mailui_no_mail_app")
            list.size == 1 -> action(list.first())
            else -> chooseApp = action
        }
    }

    fun openExternal(e: PalmEvent) {
        val m = e.mail ?: return
        if (m.kind == MailKind.NEW) {
            val to = m.peerAddr?.let { pct(it) }.orEmpty()
            withApp { app ->
                awaiting = e; left = false
                if (!openUrl(composeUrl(app, to))) { awaiting = null; note = str("mailui_cant_open") }
            }
        } else {
            val q = listOfNotNull(m.peerAddr ?: m.peerName, m.subject?.takeIf { it.isNotBlank() }).joinToString(" ")
            withApp { app ->
                // Сначала показываем, что скопируем, и только по кнопке копируем и запускаем почту
                if (q.isNotBlank()) replyPrompt = Triple(e, app, q)
                else { awaiting = e; left = false; if (!openUrl(app.scheme)) { awaiting = null; note = str("mailui_cant_open") } }
            }
        }
    }

    // Ушли из приложения в почту и вернулись — спросить, отправлено ли письмо
    DisposableEffect(Unit) {
        val nc = NSNotificationCenter.defaultCenter
        val resign = nc.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
            if (awaiting != null) left = true
        }
        val active = nc.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
            if (awaiting != null && left) { askSent = awaiting; awaiting = null; left = false }
        }
        onDispose { nc.removeObserver(resign); nc.removeObserver(active) }
    }

    val mailer = remember {
        Mailer(open = { e -> if (e.mail == null) note = str("mailui_no_mail_on_event") else openExternal(e) })
    }

    CompositionLocalProvider(LocalMailer provides mailer) {
        Box(Modifier.fillMaxSize()) {
            content()
            chooseApp?.let { action -> MailAppChooser(onDismiss = { chooseApp = null }) { app -> chooseApp = null; action(app) } }
            replyPrompt?.let { (e, app, q) ->
                AlertDialog(
                    onDismissRequest = { replyPrompt = null },
                    containerColor = Palm.paper,
                    text = { Text(str("mailui_will_copy", q), style = Palm.body, color = Palm.ink) },
                    confirmButton = {
                        TextButton(onClick = {
                            replyPrompt = null
                            UIPasteboard.generalPasteboard.string = q
                            awaiting = e; left = false
                            if (!openUrl(app.scheme)) { awaiting = null; note = str("mailui_cant_open") }
                        }) { Text(str("mailui_open_in_mail")) }
                    },
                    dismissButton = { TextButton(onClick = { replyPrompt = null }) { Text(str("cancel")) } },
                )
            }
            note?.let { text ->
                AlertDialog(
                    onDismissRequest = { note = null },
                    containerColor = Palm.paper,
                    text = { Text(text, style = Palm.body, color = Palm.ink) },
                    confirmButton = { TextButton(onClick = { note = null }) { Text("OK") } },
                )
            }
            askSent?.let { e ->
                AlertDialog(
                    onDismissRequest = { askSent = null },
                    containerColor = Palm.paper,
                    title = { Text(str("mailui_sent_q"), style = Palm.title, color = Palm.ink) },
                    text = { Text(e.title, style = Palm.body, color = Palm.ink) },
                    confirmButton = {
                        TextButton(onClick = { model.setOutcome(e, Outcome.DONE, e.outcomeNote); askSent = null }) { Text(str("mailui_yes_sent")) }
                    },
                    dismissButton = { TextButton(onClick = { askSent = null }) { Text(str("no")) } },
                )
            }
        }
    }
}

@Composable
private fun MailAppChooser(onDismiss: () -> Unit, onPick: (MailApp) -> Unit) {
    val list = remember { installedMailApps() }
    var always by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palm.paper,
        title = { Text(str("mailui_open_in_mail"), style = Palm.title, color = Palm.ink) },
        text = {
            Column {
                list.forEach { a ->
                    Text(
                        a.label, style = Palm.body, color = Palm.ink,
                        modifier = Modifier.fillMaxWidth().clickable { if (always) IosMailPrefs.set(a); onPick(a) }.padding(vertical = 12.dp),
                    )
                }
                Row(Modifier.fillMaxWidth().clickable { always = !always }.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = always, onCheckedChange = { always = it })
                    Text(str("mailui_always_open"), style = Palm.body, color = Palm.ink)
                }
                Text(str("mailui_change_in_settings"), style = Palm.small, color = Palm.inkSoft)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("cancel")) } },
    )
}
