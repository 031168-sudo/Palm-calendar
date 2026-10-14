package ru.palmdate.app.ui

import ru.palmdate.app.R
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import ru.palmdate.app.str
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.palmdate.app.DayViewModel
import ru.palmdate.app.model.EmailContact
import ru.palmdate.app.model.MailKind
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.ui.theme.Palm
import java.time.Instant
import java.time.LocalDate

/** Почта из любого места экрана: открыть письмо события в почтовой программе. */
class Mailer(
    val open: (PalmEvent) -> Unit,
)

val LocalMailer = staticCompositionLocalOf { Mailer {} }

/**
 * Почта через почтовую программу телефона (Gmail, Яндекс Почта…): «Написать» открывает новое письмо,
 * «Ответить» запускает программу и кладёт слова для поиска в буфер; по возвращении спрашиваем, отправлено ли письмо.
 */
@Composable
fun MailHost(vm: DayViewModel, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    // Почта через почтовую программу телефона
    val apps = remember { ru.palmdate.app.data.MailApps(ctx) }
    var chooseApp by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var awaiting by remember { mutableStateOf<PalmEvent?>(null) }  // ушли в почту — по возвращении спросить
    var askSent by remember { mutableStateOf<PalmEvent?>(null) }

    /** Выполнить с почтовой программой: запомненной, единственной или выбранной сейчас. */
    fun withApp(action: (String) -> Unit) {
        val list = apps.installed()
        val r = apps.remembered()
        when {
            r != null -> action(r)
            list.isEmpty() -> Toast.makeText(ctx, str(R.string.mailui_no_mail_app), Toast.LENGTH_LONG).show()
            list.size == 1 -> action(list.first().pkg)
            else -> chooseApp = action
        }
    }

    /** «Написать» — новое письмо в программе; «Ответить» — открыть программу, слова для поиска в буфере. */
    fun openExternal(e: PalmEvent) {
        val m = e.mail ?: return
        withApp { pkg ->
            val intent = if (m.kind == MailKind.NEW) apps.compose(pkg, m.peerAddr, null)
            else {
                val q = listOfNotNull(m.peerAddr ?: m.peerName, m.subject?.takeIf { it.isNotBlank() }).joinToString(" ")
                if (q.isNotBlank()) {
                    val cb = ctx.getSystemService(android.content.ClipboardManager::class.java)
                    cb?.setPrimaryClip(android.content.ClipData.newPlainText(str(R.string.mailui_search_clip), q))
                    Toast.makeText(ctx, str(R.string.mailui_copied_for_search, q), Toast.LENGTH_LONG).show()
                }
                apps.launch(pkg)
            }
            if (intent == null) {
                Toast.makeText(ctx, str(R.string.mailui_cant_open), Toast.LENGTH_SHORT).show()
            } else try {
                awaiting = e
                ctx.startActivity(intent)
            } catch (_: Exception) {
                awaiting = null
                Toast.makeText(ctx, str(R.string.mailui_cant_open), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Вернулись из почты — спросить, отправлено ли письмо
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME && awaiting != null) {
                askSent = awaiting
                awaiting = null
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    val mailer = remember {
        Mailer(
            open = { e ->
                if (e.mail == null) Toast.makeText(ctx, str(R.string.mailui_no_mail_on_event), Toast.LENGTH_SHORT).show()
                else openExternal(e)
            },
        )
    }

    CompositionLocalProvider(LocalMailer provides mailer) {
        Box(Modifier.fillMaxSize()) {
            content()
            chooseApp?.let { action ->
                MailAppChooser(apps, onDismiss = { chooseApp = null }) { pkg -> chooseApp = null; action(pkg) }
            }
            askSent?.let { e ->
                AlertDialog(
                    onDismissRequest = { askSent = null },
                    containerColor = Palm.paper,
                    title = { Text(str(R.string.mailui_sent_q), style = Palm.title, color = Palm.ink) },
                    text = { Text(e.title, style = Palm.body, color = Palm.ink) },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.model.setOutcome(e, ru.palmdate.app.model.Outcome.DONE, e.outcomeNote)
                            askSent = null
                        }) { Text(str(R.string.mailui_yes_sent)) }
                    },
                    dismissButton = { TextButton(onClick = { askSent = null }) { Text(str(R.string.no)) } },
                )
            }
        }
    }
}

/** Выбор почтовой программы: Gmail, Яндекс Почта… С галочкой «всегда в ней». */
@Composable
private fun MailAppChooser(apps: ru.palmdate.app.data.MailApps, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val list = remember { apps.installed() }
    var always by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palm.paper,
        title = { Text(str(R.string.mailui_open_in_mail), style = Palm.title, color = Palm.ink) },
        text = {
            Column {
                list.forEach { a ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable { if (always) apps.remember(a.pkg); onPick(a.pkg) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        a.icon?.let { d ->
                            androidx.compose.foundation.Image(
                                d.toBitmap(96, 96).asImageBitmap(),
                                null, modifier = Modifier.size(32.dp),
                            )
                        } ?: Spacer(Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(a.label, style = Palm.body, color = Palm.ink)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { always = !always }.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(checked = always, onCheckedChange = { always = it })
                    Text(str(R.string.mailui_always_open), style = Palm.body, color = Palm.ink)
                }
                Text(str(R.string.mailui_change_in_settings), style = Palm.small, color = Palm.inkSoft)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str(R.string.cancel)) } },
    )
}
