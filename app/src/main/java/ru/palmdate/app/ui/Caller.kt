package ru.palmdate.app.ui

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.palmdate.app.data.CallOption
import ru.palmdate.app.data.CallOptions
import ru.palmdate.app.model.ContactRef
import ru.palmdate.app.ui.theme.Palm

/** "Позвонить человеку": показывает выбор — телефон или мессенджер. */
val LocalCaller = staticCompositionLocalOf<(ContactRef) -> Unit> { {} }

/**
 * Держатель выбора способа звонка. Оборачивает экран; любое место внутри
 * зовёт LocalCaller.current(контакт) — и появляется список "Телефон / WhatsApp / Telegram…".
 */
@Composable
fun CallerHost(content: @Composable () -> Unit) {
    var target by remember { mutableStateOf<ContactRef?>(null) }
    CompositionLocalProvider(LocalCaller provides { target = it }) { content() }
    target?.let { c -> CallChooser(c) { target = null } }
}

@Composable
private fun CallChooser(contact: ContactRef, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val calls = remember { CallOptions(ctx) }
    var options by remember { mutableStateOf<List<CallOption>?>(null) }
    var always by remember { mutableStateOf(false) }

    fun start(o: CallOption) {
        try {
            ctx.startActivity(o.intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, "Нет приложения для этого действия", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(ctx, "Приложение не разрешило звонок", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(contact.lookupKey) {
        val list = withContext(Dispatchers.IO) { calls.options(contact.lookupKey, contact.phone) }
        val remembered = calls.remembered(contact.lookupKey)
        val auto = list.firstOrNull { it.key == remembered } ?: list.singleOrNull()
        when {
            list.isEmpty() -> {
                Toast.makeText(ctx, "У контакта нет номера", Toast.LENGTH_SHORT).show(); onDone()
            }
            auto != null -> { start(auto); onDone() } // запомненный способ или выбирать не из чего
            else -> options = list
        }
    }

    val list = options ?: return
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = Palm.paper,
        title = { Text("Связаться: ${contact.name}", style = Palm.title, color = Palm.ink) },
        text = {
            Column {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    list.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                .alpha(if (o.available) 1f else 0.5f) // человека в мессенджере не видно — серым
                                .clickable {
                                if (always) calls.remember(contact.lookupKey, o.key)
                                start(o); onDone()
                            }.dottedRule().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                o.icon?.let { d ->
                                    Image(d.toBitmap(96, 96).asImageBitmap(), null, Modifier.size(30.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(o.app, style = Palm.body, color = Palm.ink)
                                Text(o.action, style = Palm.small, color = Palm.inkSoft, maxLines = 2)
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { always = !always }.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = always, onCheckedChange = { always = it },
                        colors = CheckboxDefaults.colors(checkedColor = Palm.navy),
                    )
                    Text("Всегда так для этого человека", style = Palm.body, color = Palm.ink)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text("Отмена") } },
    )
}
