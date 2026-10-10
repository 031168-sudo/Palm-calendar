package ru.palmdate.app.ui

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
import ru.palmdate.app.data.Addr
import ru.palmdate.app.data.ContactsRepository.EmailContact
import ru.palmdate.app.data.MailAttachment
import ru.palmdate.app.data.MailClient
import ru.palmdate.app.data.MailHeader
import ru.palmdate.app.data.MailMessage
import ru.palmdate.app.data.Outgoing
import ru.palmdate.app.model.MailKind
import ru.palmdate.app.model.PalmEvent
import ru.palmdate.app.ui.theme.Palm
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Почта из любого места экрана: выбрать письмо (для «Ответить») и открыть письмо события.
 */
class Mailer(
    val pick: (query: String, onPick: (MailHeader) -> Unit) -> Unit,
    val open: (PalmEvent) -> Unit,
)

val LocalMailer = staticCompositionLocalOf { Mailer({ _, _ -> }, {}) }

private class ViewerTarget(val event: PalmEvent, val messageId: String, val folder: String?, val draft: Outgoing?)
private class ComposerTarget(val event: PalmEvent, val initial: Outgoing, val fromDraft: Boolean, val fromViewer: Boolean)

/**
 * Держатель почтовых экранов: список и поиск писем, просмотр письма, новое письмо / ответ / пересылка.
 * Экраны открываются поверх всего на весь экран — как обычная почта.
 */
@Composable
fun MailHost(vm: DayViewModel, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf<Pair<String, (MailHeader) -> Unit>?>(null) }
    var viewer by remember { mutableStateOf<ViewerTarget?>(null) }
    var composer by remember { mutableStateOf<ComposerTarget?>(null) }

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
            list.isEmpty() -> Toast.makeText(ctx, "На телефоне нет почтовой программы", Toast.LENGTH_LONG).show()
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
                    cb?.setPrimaryClip(android.content.ClipData.newPlainText("Поиск письма", q))
                    Toast.makeText(ctx, "Для поиска скопировано: $q — вставьте в поиск почты", Toast.LENGTH_LONG).show()
                }
                apps.launch(pkg)
            }
            if (intent == null) {
                Toast.makeText(ctx, "Не получается открыть почту", Toast.LENGTH_SHORT).show()
            } else try {
                awaiting = e
                ctx.startActivity(intent)
            } catch (_: Exception) {
                awaiting = null
                Toast.makeText(ctx, "Не получается открыть почту", Toast.LENGTH_SHORT).show()
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
            pick = { q, cb -> picker = q to cb },
            open = { e ->
                val m = e.mail
                val builtin = ru.palmdate.app.data.SettingsStore.current.mailMode == ru.palmdate.app.data.MailMode.BUILTIN
                if (m == null) {
                    Toast.makeText(ctx, "У события нет письма", Toast.LENGTH_SHORT).show()
                } else if (!builtin || (m.kind == MailKind.REPLY && m.messageId == null)) {
                    openExternal(e)
                } else scope.launch {
                    val draft = vm.mailLink(e.eventId)?.draft?.let { Outgoing.fromJson(it) }
                    when (m.kind) {
                        MailKind.REPLY -> {
                            val id = m.messageId
                            if (id == null) Toast.makeText(ctx, "Письмо не прикреплено", Toast.LENGTH_SHORT).show()
                            else viewer = ViewerTarget(e, id, m.folder, draft)
                        }
                        MailKind.NEW -> composer = ComposerTarget(
                            e,
                            draft ?: Outgoing(to = m.peerAddr?.let { Addr(m.peerName, it).full } ?: ""),
                            fromDraft = draft != null,
                            fromViewer = false,
                        )
                    }
                }
            },
        )
    }

    CompositionLocalProvider(LocalMailer provides mailer) {
        Box(Modifier.fillMaxSize()) {
            content()
            viewer?.let { v ->
                MailViewer(
                    target = v,
                    onCompose = { o, fromDraft -> composer = ComposerTarget(v.event, o, fromDraft, fromViewer = true) },
                    onClose = { viewer = null },
                )
            }
            composer?.let { c ->
                MailComposer(
                    target = c, vm = vm,
                    onClose = { sentOrSaved ->
                        composer = null
                        // Отправили или отложили ответ — письмо больше не нужно держать открытым
                        if (sentOrSaved && c.fromViewer) viewer = null
                    },
                )
            }
            picker?.let { (q, cb) ->
                MailBrowser(q, vm, onPick = { picker = null; cb(it) }, onClose = { picker = null })
            }
            chooseApp?.let { action ->
                MailAppChooser(apps, onDismiss = { chooseApp = null }) { pkg -> chooseApp = null; action(pkg) }
            }
            askSent?.let { e ->
                AlertDialog(
                    onDismissRequest = { askSent = null },
                    containerColor = Palm.paper,
                    title = { Text("Письмо отправлено?", style = Palm.title, color = Palm.ink) },
                    text = { Text(e.title, style = Palm.body, color = Palm.ink) },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.setOutcome(e, ru.palmdate.app.model.Outcome.DONE, e.outcomeNote)
                            askSent = null
                        }) { Text("Да, отправлено") }
                    },
                    dismissButton = { TextButton(onClick = { askSent = null }) { Text("Нет") } },
                )
            }
        }
    }
}

/* ---------- Общая рамка почтового экрана ---------- */

@Composable
private fun MailScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    bottom: (@Composable () -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(
        Modifier
            .fillMaxSize()
            .background(Palm.paper)
            .pointerInput(Unit) { detectTapGestures { } } // нажатия не проходят на экран под письмом
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Palm.navy).padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад", tint = Color.White) }
            Text(
                title, style = Palm.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
        }
        // На планшете письмо не растягивается во всю ширину
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) { body() }
        }
        bottom?.let {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palm.rule))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 840.dp).fillMaxWidth()) { it() }
            }
        }
    }
}

private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", RU)
private val FULL_DATE = DateTimeFormatter.ofPattern("d.MM.yyyy", RU)
private val LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", RU)

/** Дата в списке писем: сегодня — время, в этом году — "9 окт", раньше — "9.10.2025". */
private fun listDate(ms: Long): String {
    if (ms <= 0) return ""
    val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    return when {
        t.toLocalDate() == today -> t.format(HM)
        t.year == today.year -> t.format(DAY_MONTH).trimEnd('.')
        else -> t.format(FULL_DATE)
    }
}

internal fun longDate(ms: Long): String =
    if (ms <= 0) "" else Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(LONG_DATE)

private fun sizeLabel(size: Long): String = when {
    size < 0 -> ""
    size < 1024 -> "$size Б"
    size < 1024 * 1024 -> "${size / 1024} КБ"
    else -> "%.1f МБ".format(size / 1024.0 / 1024.0)
}

/* ---------- Список и поиск писем ---------- */

/**
 * Входящие и поиск по всему ящику. Поиск — по мере набора: отправитель, получатель, тема, текст;
 * имя человека из контактов тоже находит его письма. Нажатие на письмо — выбрать его.
 */
@Composable
private fun MailBrowser(initialQuery: String, vm: DayViewModel, onPick: (MailHeader) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery) }
    var items by remember { mutableStateOf<List<MailHeader>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var canMore by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(query, attempt) {
        error = null
        if (query.isNotBlank()) delay(600) // ждём, пока допишут
        loading = true
        try {
            if (query.isBlank()) {
                items = MailClient.inbox(0)
                canMore = items.size >= 40
            } else {
                val people = vm.searchEmails(query).map { it.email }
                items = MailClient.search(query, people)
                canMore = false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Ошибка почты"
            items = emptyList()
        } finally {
            loading = false
        }
    }

    MailScreen("Выберите письмо", onBack = onClose) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Кто, тема, слова из письма") },
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Palm.inkSoft) },
            trailingIcon = if (query.isNotEmpty()) ({
                IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Очистить", tint = Palm.inkSoft) }
            }) else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Palm.navy)
        else Spacer(Modifier.height(2.dp))
        Text(
            when {
                query.isBlank() -> "Входящие"
                loading -> "Ищу по всему ящику…"
                else -> "Найдено: ${items.size}" + if (items.size >= 60) " (показаны последние)" else ""
            },
            style = Palm.small, color = Palm.inkSoft,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        error?.let { msg ->
            Column(Modifier.padding(16.dp)) {
                Text(msg, style = Palm.body, color = Palm.nowLine)
                Spacer(Modifier.height(8.dp))
                PalmButton("Повторить") { attempt++ }
            }
        }
        if (!loading && error == null && items.isEmpty() && query.isNotBlank()) {
            Text(
                "Ничего не нашлось. Попробуйте одно слово или часть адреса.",
                style = Palm.body, color = Palm.inkSoft, modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(items, key = { it.messageId ?: "${it.folder}/${it.uid}" }) { h -> MailRow(h) { onPick(h) } }
            if (canMore) item(key = "more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    PalmButton("Ещё") {
                        scope.launch {
                            loading = true
                            try {
                                val more = MailClient.inbox(items.size)
                                items = (items + more).distinctBy { it.messageId ?: "${it.folder}/${it.uid}" }
                                canMore = more.size >= 40
                            } catch (e: Exception) {
                                error = e.message
                            } finally {
                                loading = false
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MailRow(h: MailHeader, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                h.from?.display ?: "—",
                style = if (h.seen) Palm.body else Palm.body.copy(fontWeight = FontWeight.Bold),
                color = Palm.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(listDate(h.date), style = Palm.small, color = if (h.seen) Palm.inkSoft else Palm.navy)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (h.hasAttachments) {
                Icon(Icons.Outlined.AttachFile, "Вложения", tint = Palm.inkSoft, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(h.subject, style = Palm.small.copy(fontSize = Palm.small.fontSize * 1.1f), color = Palm.inkSoft,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Palm.rule))
}

/* ---------- Просмотр письма ---------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MailViewer(
    target: ViewerTarget,
    onCompose: (Outgoing, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<MailMessage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(target.messageId, attempt) {
        error = null
        try {
            msg = MailClient.open(target.messageId, target.folder)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Ошибка почты"
        }
    }

    // Сохранение вложения: пользователь выбирает, куда положить файл
    var saving by remember { mutableStateOf<MailAttachment?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val a = saving
        saving = null
        if (uri != null && a != null) scope.launch {
            try {
                MailClient.saveAttachment(target.messageId, msg?.header?.folder ?: target.folder, a.path) {
                    ctx.contentResolver.openOutputStream(uri) ?: throw IllegalStateException("Не удалось открыть файл")
                }
                Toast.makeText(ctx, "Сохранено: ${a.name}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, e.message ?: "Не удалось сохранить", Toast.LENGTH_LONG).show()
            }
        }
    }

    val m = msg
    MailScreen(
        title = m?.header?.subject ?: "Письмо",
        onBack = onClose,
        bottom = {
            if (m != null) FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PalmButton("Ответить", filled = true) { onCompose(MailClient.reply(m, all = false), false) }
                if (m.to.size + m.cc.size > 1) PalmButton("Ответить всем") { onCompose(MailClient.reply(m, all = true), false) }
                PalmButton("Переслать") { onCompose(MailClient.forward(m), false) }
            }
        },
    ) {
        target.draft?.let { d ->
            Row(
                Modifier.fillMaxWidth().background(Palm.navyLight).padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Есть черновик ответа", style = Palm.body, color = Palm.navy, modifier = Modifier.weight(1f))
                PalmButton("Продолжить", filled = true) { onCompose(d, true) }
            }
        }
        when {
            error != null -> Column(Modifier.padding(16.dp)) {
                Text(error!!, style = Palm.body, color = Palm.nowLine)
                Spacer(Modifier.height(8.dp))
                PalmButton("Повторить") { attempt++ }
            }
            m == null -> {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Palm.navy)
                Text("Открываю письмо…", style = Palm.small, color = Palm.inkSoft, modifier = Modifier.padding(16.dp))
            }
            else -> {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(m.header.subject, style = Palm.title, color = Palm.ink)
                    Spacer(Modifier.height(6.dp))
                    m.header.from?.let { AddrLine("От", listOf(it)) }
                    if (m.to.isNotEmpty()) AddrLine("Кому", m.to)
                    if (m.cc.isNotEmpty()) AddrLine("Копия", m.cc)
                    Text(longDate(m.header.date), style = Palm.small, color = Palm.inkSoft)
                    if (m.attachments.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            m.attachments.forEach { a ->
                                AttachmentChip(a.name, sizeLabel(a.size.toLong()), onRemove = null) {
                                    saving = a
                                    saveLauncher.launch(a.name)
                                }
                            }
                        }
                        Text("Нажмите на вложение, чтобы сохранить", style = Palm.small, color = Palm.inkSoft,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Palm.rule))
                val html = m.html
                if (html != null) {
                    key(m.header.messageId) { HtmlBody(html, Modifier.weight(1f).fillMaxWidth()) }
                } else {
                    SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
                        Text(
                            m.text ?: "(письмо без текста)", style = Palm.body.copy(fontWeight = FontWeight.Normal),
                            color = Palm.ink,
                            modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddrLine(label: String, list: List<Addr>) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(label, style = Palm.small, color = Palm.inkSoft, modifier = Modifier.width(52.dp))
        Text(list.joinToString(", ") { it.full }, style = Palm.small, color = Palm.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** Письмо в HTML — как в браузере, без скриптов; ссылки открываются в браузере. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlBody(html: String, modifier: Modifier) {
    val page = if (html.contains("name=\"viewport\"", ignoreCase = true)) html
    else "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">$html"
    AndroidView(
        modifier = modifier,
        factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                setBackgroundColor(android.graphics.Color.WHITE)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                        return true
                    }
                }
                loadDataWithBaseURL(null, page, "text/html", "UTF-8", null)
            }
        },
    )
}

@Composable
private fun AttachmentChip(name: String, size: String, onRemove: (() -> Unit)?, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.clip(shape).border(1.dp, Palm.rule, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 8.dp, end = if (onRemove != null) 2.dp else 10.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.AttachFile, null, tint = Palm.navy, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Column {
            Text(name, style = Palm.small.copy(fontWeight = FontWeight.Bold), color = Palm.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp))
            if (size.isNotEmpty()) Text(size, style = Palm.small, color = Palm.inkSoft)
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Close, "Убрать", tint = Palm.inkSoft, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/* ---------- Новое письмо, ответ, пересылка ---------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MailComposer(target: ComposerTarget, vm: DayViewModel, onClose: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val o = target.initial
    var to by remember { mutableStateOf(o.to) }
    var cc by remember { mutableStateOf(o.cc) }
    var showCc by remember { mutableStateOf(o.cc.isNotBlank()) }
    var subject by remember { mutableStateOf(o.subject) }
    var body by remember { mutableStateOf(o.body) }
    var files by remember { mutableStateOf(o.files) }
    var fwdParts by remember { mutableStateOf(o.forward?.parts.orEmpty()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var askClose by remember { mutableStateOf(false) }
    var pickFor by remember { mutableStateOf<String?>(null) } // "to" / "cc" — выбор человека из контактов

    fun current() = o.copy(to = to.trim(), cc = cc.trim(), subject = subject, body = body, files = files,
        forward = o.forward?.copy(parts = fwdParts))
    val changed = current() != o.copy(to = o.to.trim(), cc = o.cc.trim())

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { u ->
            runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        files = (files + uris.map { it.toString() }).distinct()
    }

    fun saveDraft() {
        busy = "Сохраняю…"
        scope.launch {
            try {
                vm.saveMailDraft(target.event.eventId, current())
                Toast.makeText(ctx, "Сохранено в черновики", Toast.LENGTH_SHORT).show()
                onClose(true)
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = null
            }
        }
    }

    val title = when (o.mode) {
        Outgoing.MODE_REPLY, Outgoing.MODE_REPLY_ALL -> "Ответ"
        Outgoing.MODE_FORWARD -> "Пересылка"
        else -> "Новое письмо"
    }
    val onBack: () -> Unit = { if (busy == null) { if (changed) askClose = true else onClose(false) } }

    if (askClose) {
        AlertDialog(
            onDismissRequest = { askClose = false },
            containerColor = Palm.paper,
            title = { Text("Закрыть письмо?", style = Palm.title, color = Palm.ink) },
            text = { Text("Сохранить его в черновики, чтобы дописать потом?", style = Palm.body, color = Palm.ink) },
            confirmButton = { TextButton(onClick = { askClose = false; saveDraft() }) { Text("В черновики") } },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        askClose = false
                        if (target.fromDraft) scope.launch { runCatching { vm.dropMailDraft(target.event.eventId) } }
                        onClose(false)
                    }) { Text(if (target.fromDraft) "Удалить" else "Не сохранять") }
                    TextButton(onClick = { askClose = false }) { Text("Писать дальше") }
                }
            },
        )
    }

    pickFor?.let { field ->
        EmailPickerDialog(vm, onDismiss = { pickFor = null }) { p ->
            val full = Addr(p.name, p.email).full
            if (field == "cc") cc = listOf(cc.trim().trimEnd(','), full).filter { it.isNotBlank() }.joinToString(", ")
            else to = listOf(to.trim().trimEnd(','), full).filter { it.isNotBlank() }.joinToString(", ")
            pickFor = null
        }
    }

    MailScreen(
        title = title,
        onBack = onBack,
        bottom = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                error?.let { Text(it, style = Palm.small, color = Palm.nowLine, modifier = Modifier.padding(bottom = 6.dp)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PalmButton("Отмена") { onBack() }
                    PalmButton("В черновики") { if (busy == null) saveDraft() }
                    Spacer(Modifier.weight(1f))
                    PalmButton(busy ?: "Отправить", filled = true) {
                        if (busy != null) return@PalmButton
                        error = null
                        busy = "Отправка…"
                        scope.launch {
                            try {
                                vm.sendMail(target.event, current())
                                Toast.makeText(ctx, "Письмо отправлено", Toast.LENGTH_SHORT).show()
                                onClose(true)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message ?: "Не удалось отправить"
                            } finally {
                                busy = null
                            }
                        }
                    }
                }
            }
        },
    ) {
        if (busy != null) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Palm.navy)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = to, onValueChange = { to = it }, label = { Text("Кому") },
                trailingIcon = { IconButton(onClick = { pickFor = "to" }) { Icon(Icons.Outlined.PersonAdd, "Из контактов", tint = Palm.navy) } },
                maxLines = 3, modifier = Modifier.fillMaxWidth(),
            )
            if (showCc) {
                OutlinedTextField(
                    value = cc, onValueChange = { cc = it }, label = { Text("Копия") },
                    trailingIcon = { IconButton(onClick = { pickFor = "cc" }) { Icon(Icons.Outlined.PersonAdd, "Из контактов", tint = Palm.navy) } },
                    maxLines = 3, modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TextButton(onClick = { showCc = true }) { Text("+ Копия") }
            }
            OutlinedTextField(
                value = subject, onValueChange = { subject = it }, label = { Text("Тема") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            // Вложения: свои файлы и пересылаемые из исходного письма
            if (files.isNotEmpty() || fwdParts.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    fwdParts.forEach { a ->
                        AttachmentChip(a.name, sizeLabel(a.size.toLong()), onRemove = { fwdParts = fwdParts - a })
                    }
                    files.forEach { u ->
                        val info = remember(u) { MailClient.fileInfo(ctx, Uri.parse(u)) }
                        AttachmentChip(info.first, sizeLabel(info.second), onRemove = { files = files - u })
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { pickFiles.launch(arrayOf("*/*")) }.padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AttachFile, null, tint = Palm.navy, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Прикрепить файл", style = Palm.button, color = Palm.navy)
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = body, onValueChange = { body = it }, label = { Text("Письмо") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
            )
        }
    }
}

/** Выбор человека с почтой из контактов. */
@Composable
private fun EmailPickerDialog(vm: DayViewModel, onDismiss: () -> Unit, onPick: (EmailContact) -> Unit) {
    var q by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<EmailContact>>(emptyList()) }
    LaunchedEffect(q) { delay(150); list = vm.searchEmails(q) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palm.paper,
        title = { Text("Кому", style = Palm.title, color = Palm.ink) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, placeholder = { Text("Имя или адрес") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                EmailList(list, Modifier.heightIn(max = 360.dp), onPick)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

/** Список "имя — адрес" (для «Кому» и для шага «Написать»). */
@Composable
internal fun EmailList(list: List<EmailContact>, modifier: Modifier, onPick: (EmailContact) -> Unit) {
    if (list.isEmpty()) {
        Text("Нет людей с почтой", style = Palm.small, color = Palm.inkSoft, modifier = Modifier.padding(vertical = 8.dp))
        return
    }
    LazyColumn(modifier.fillMaxWidth()) {
        items(list, key = { it.email }) { p ->
            Column(Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 7.dp)) {
                Text(p.name, style = Palm.body, color = Palm.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p.email, style = Palm.small, color = Palm.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palm.rule))
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
        title = { Text("Открыть в почте", style = Palm.title, color = Palm.ink) },
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
                    Text("Всегда открывать в ней", style = Palm.body, color = Palm.ink)
                }
                Text("Сменить можно в «Настройки → Почта»", style = Palm.small, color = Palm.inkSoft)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
