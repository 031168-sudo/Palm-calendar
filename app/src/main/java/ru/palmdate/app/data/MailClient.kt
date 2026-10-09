package ru.palmdate.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.util.Date
import java.util.Properties
import javax.activation.DataHandler
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeUtility
import javax.mail.search.AndTerm
import javax.mail.search.BodyTerm
import javax.mail.search.FromStringTerm
import javax.mail.search.MessageIDTerm
import javax.mail.search.OrTerm
import javax.mail.search.RecipientStringTerm
import javax.mail.search.SearchTerm
import javax.mail.search.SubjectTerm
import javax.mail.util.ByteArrayDataSource

/** Понятная ошибка почты — её текст показываем как есть. */
class MailException(message: String) : Exception(message)

/** Адрес: имя (если есть) и почта. */
data class Addr(val name: String?, val email: String) {
    val display: String get() = name?.takeIf { it.isNotBlank() } ?: email
    /** Для поля «Кому»: "Иван Петров <ivan@mail.ru>". */
    val full: String get() = name?.takeIf { it.isNotBlank() }?.let { "$it <$email>" } ?: email
}

/** Строка списка писем. */
data class MailHeader(
    val folder: String,
    val uid: Long,
    val messageId: String?,
    val subject: String,
    val from: Addr?,
    val date: Long,
    val seen: Boolean,
    val hasAttachments: Boolean,
)

/** Вложение: path — номера частей письма, по ним его потом достаём. */
data class MailAttachment(val name: String, val mime: String, val size: Int, val path: List<Int>)

/** Открытое письмо целиком. */
data class MailMessage(
    val header: MailHeader,
    val to: List<Addr>,
    val cc: List<Addr>,
    val replyTo: List<Addr>,
    val html: String?,
    val text: String?,
    val attachments: List<MailAttachment>,
    val references: String?,
) {
    /** Текст письма без разметки — для цитаты в ответе и пересылке. */
    val plain: String
        get() = text ?: html?.let {
            androidx.core.text.HtmlCompat.fromHtml(it, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
        } ?: ""
}

/** Пересылаемые вложения: из какого письма и какие части. */
data class ForwardFrom(val messageId: String, val folder: String?, val parts: List<MailAttachment>)

/** Письмо, которое пишем: новое, ответ или пересылка. Его же храним как черновик. */
data class Outgoing(
    val mode: String = MODE_NEW,       // NEW, REPLY, REPLY_ALL, FORWARD
    val to: String = "",
    val cc: String = "",
    val subject: String = "",
    val body: String = "",
    val files: List<String> = emptyList(), // свои вложения — content:// ссылки
    val forward: ForwardFrom? = null,
    val inReplyTo: String? = null,
    val references: String? = null,
) {
    fun toJson(): String = JSONObject().apply {
        put("mode", mode); put("to", to); put("cc", cc); put("subject", subject); put("body", body)
        put("files", JSONArray(files))
        inReplyTo?.let { put("inReplyTo", it) }
        references?.let { put("references", it) }
        forward?.let { f ->
            put("fwdId", f.messageId); f.folder?.let { put("fwdFolder", it) }
            put("fwdParts", JSONArray().apply {
                f.parts.forEach { a ->
                    put(JSONObject().put("n", a.name).put("m", a.mime).put("s", a.size).put("p", JSONArray(a.path)))
                }
            })
        }
    }.toString()

    companion object {
        const val MODE_NEW = "NEW"
        const val MODE_REPLY = "REPLY"
        const val MODE_REPLY_ALL = "REPLY_ALL"
        const val MODE_FORWARD = "FORWARD"

        fun fromJson(s: String): Outgoing? = runCatching {
            val j = JSONObject(s)
            fun opt(k: String) = j.optString(k).takeIf { j.has(k) && it.isNotEmpty() }
            val files = j.optJSONArray("files")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            val fwd = opt("fwdId")?.let { id ->
                val parts = j.optJSONArray("fwdParts")?.let { a ->
                    (0 until a.length()).map { i ->
                        val o = a.getJSONObject(i)
                        val p = o.getJSONArray("p")
                        MailAttachment(o.getString("n"), o.getString("m"), o.optInt("s"), (0 until p.length()).map { p.getInt(it) })
                    }
                }.orEmpty()
                ForwardFrom(id, opt("fwdFolder"), parts)
            }
            Outgoing(
                mode = j.optString("mode", MODE_NEW), to = j.optString("to"), cc = j.optString("cc"),
                subject = j.optString("subject"), body = j.optString("body"), files = files,
                forward = fwd, inReplyTo = opt("inReplyTo"), references = opt("references"),
            )
        }.getOrNull()
    }
}

/**
 * Почта по IMAP (чтение, поиск, черновики) и SMTP (отправка) — ящик из «Настройки → Почта».
 * Одно соединение на всё приложение, запросы по очереди.
 */
object MailClient {
    private val mutex = Mutex()
    private var store: IMAPStore? = null
    private var storeKey: String? = null
    private var roles: Map<String, String>? = null

    private val s get() = SettingsStore.current

    fun configured(): Boolean =
        s.mailEmail.isNotBlank() && s.imapHost.isNotBlank() && s.smtpHost.isNotBlank() && SettingsStore.mailPassword.isNotBlank()

    /** Мой адрес — чтобы не отвечать самому себе в «Ответить всем». */
    val myEmail: String get() = s.mailEmail.trim()

    private fun session(): Session = Session.getInstance(Properties().apply {
        put("mail.imaps.connectiontimeout", "20000")
        put("mail.imaps.timeout", "60000")
        put("mail.imaps.partialfetch", "false") // вложения качаем целиком — быстрее
        put("mail.smtps.connectiontimeout", "20000")
        put("mail.smtps.timeout", "120000")
        put("mail.smtps.auth", "true")
        put("mail.mime.charset", "UTF-8")
    })

    private fun connected(): IMAPStore {
        if (!configured()) throw MailException("Почта не настроена: «Настройки → Почта» — адрес и пароль приложения")
        val key = "${s.imapHost}|${s.mailImapPort}|${s.mailEmail}"
        store?.let { st ->
            if (storeKey == key && st.isConnected) return st
            runCatching { st.close() }
        }
        val st = session().getStore("imaps") as IMAPStore
        st.connect(s.imapHost, s.mailImapPort, s.mailEmail, SettingsStore.mailPassword)
        store = st; storeKey = key; roles = null
        return st
    }

    private fun reset() {
        runCatching { store?.close() }
        store = null; roles = null
    }

    /** Выполнить с ящиком: в фоне, по очереди; при обрыве связи — переподключиться и повторить. */
    private suspend fun <T> withStore(block: (IMAPStore) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                try {
                    block(connected())
                } catch (e: MessagingException) {
                    if (store?.isConnected == true && e !is javax.mail.FolderClosedException) throw e
                    reset()
                    block(connected())
                }
            } catch (e: MailException) {
                throw e
            } catch (e: Exception) {
                throw MailException(Mail.explain(e))
            }
        }
    }

    /** Служебные папки ящика: отправленные, черновики, «вся почта» (Gmail), архив. */
    private fun roles(st: IMAPStore): Map<String, String> {
        roles?.let { return it }
        val map = HashMap<String, String>()
        val all = runCatching { st.defaultFolder.list("*").toList() }.getOrDefault(emptyList())
        for (f in all) {
            val attrs = (f as? IMAPFolder)?.attributes ?: continue
            for (a in attrs) when (a.lowercase()) {
                "\\sent" -> map.putIfAbsent("sent", f.fullName)
                "\\drafts" -> map.putIfAbsent("drafts", f.fullName)
                "\\all" -> map.putIfAbsent("all", f.fullName)
                "\\archive" -> map.putIfAbsent("archive", f.fullName)
            }
        }
        fun byName(role: String, vararg names: String) {
            if (role in map) return
            all.firstOrNull { f -> names.any { f.name.equals(it, ignoreCase = true) } }?.let { map[role] = it.fullName }
        }
        byName("sent", "Sent", "Sent Items", "Sent Messages", "Отправленные")
        byName("drafts", "Drafts", "Черновики")
        byName("archive", "Archive", "Архив")
        return map.also { roles = it }
    }

    private fun fetch(f: IMAPFolder, msgs: Array<Message>) {
        if (msgs.isEmpty()) return
        val fp = FetchProfile().apply {
            add(FetchProfile.Item.ENVELOPE)
            add(FetchProfile.Item.FLAGS)
            add(FetchProfile.Item.CONTENT_INFO)
            add(UIDFolder.FetchProfileItem.UID)
        }
        f.fetch(msgs, fp)
    }

    private fun addr(a: javax.mail.Address?): Addr? =
        (a as? InternetAddress)?.let { Addr(it.personal, it.address ?: return null) }

    private fun header(f: IMAPFolder, m: Message): MailHeader {
        val mm = m as MimeMessage
        return MailHeader(
            folder = f.fullName,
            uid = runCatching { f.getUID(m) }.getOrDefault(0L),
            messageId = runCatching { mm.messageID }.getOrNull(),
            subject = runCatching { mm.subject }.getOrNull()?.takeIf { it.isNotBlank() } ?: "(без темы)",
            from = runCatching { addr(mm.from?.firstOrNull()) }.getOrNull(),
            date = runCatching { (mm.receivedDate ?: mm.sentDate)?.time }.getOrNull() ?: 0L,
            seen = runCatching { m.isSet(Flags.Flag.SEEN) }.getOrDefault(true),
            hasAttachments = runCatching { m.isMimeType("multipart/mixed") }.getOrDefault(false),
        )
    }

    /** Входящие, новые сверху. offset — сколько уже показано (для «Ещё»). */
    suspend fun inbox(offset: Int, count: Int = 40): List<MailHeader> = withStore { st ->
        val f = st.getFolder("INBOX") as IMAPFolder
        f.open(Folder.READ_ONLY)
        try {
            val end = f.messageCount - offset
            if (end < 1) emptyList()
            else {
                val msgs = f.getMessages(maxOf(1, end - count + 1), end)
                fetch(f, msgs)
                msgs.reversed().map { header(f, it) }
            }
        } finally {
            runCatching { f.close(false) }
        }
    }

    /**
     * Поиск по всему ящику: каждое слово ищется в отправителе, получателе, теме и тексте.
     * people — адреса людей из контактов, чьё имя совпало с запросом: их письма тоже находятся.
     */
    suspend fun search(query: String, people: List<String>, limit: Int = 60): List<MailHeader> = withStore { st ->
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return@withStore emptyList()
        fun anyField(w: String): SearchTerm = OrTerm(arrayOf(
            FromStringTerm(w), RecipientStringTerm(Message.RecipientType.TO, w),
            RecipientStringTerm(Message.RecipientType.CC, w), SubjectTerm(w), BodyTerm(w),
        ))
        var term: SearchTerm = if (words.size == 1) anyField(words[0]) else AndTerm(words.map { anyField(it) }.toTypedArray())
        if (people.isNotEmpty()) {
            val byPeople = people.take(5).flatMap { listOf(FromStringTerm(it), RecipientStringTerm(Message.RecipientType.TO, it)) }
            term = OrTerm(arrayOf(term) + byPeople)
        }
        val r = roles(st)
        val folders = r["all"]?.let { listOf(it) } ?: listOfNotNull("INBOX", r["sent"], r["archive"]).distinct()
        val result = ArrayList<MailHeader>()
        for (name in folders) {
            val f = st.getFolder(name) as IMAPFolder
            if (!runCatching { f.exists() }.getOrDefault(false)) continue
            f.open(Folder.READ_ONLY)
            try {
                val found = f.search(term)
                val last = found.takeLast(limit).toTypedArray()
                fetch(f, last)
                last.forEach { result += header(f, it) }
            } finally {
                runCatching { f.close(false) }
            }
        }
        result.distinctBy { it.messageId ?: "${it.folder}/${it.uid}" }.sortedByDescending { it.date }.take(limit)
    }

    /** Найти письмо по Message-ID: сначала в его папке, потом по всему ящику. Папку закрывает вызывающий. */
    private fun find(st: IMAPStore, messageId: String, folderHint: String?, write: Boolean): Pair<IMAPFolder, MimeMessage>? {
        val r = roles(st)
        val names = listOfNotNull(folderHint, r["all"], "INBOX", r["sent"], r["archive"], r["drafts"]).distinct()
        for (name in names) {
            val f = st.getFolder(name) as IMAPFolder
            if (!runCatching { f.exists() }.getOrDefault(false)) continue
            try { f.open(if (write) Folder.READ_WRITE else Folder.READ_ONLY) } catch (_: MessagingException) { f.open(Folder.READ_ONLY) }
            val found = runCatching { f.search(MessageIDTerm(messageId)) }.getOrDefault(emptyArray())
            if (found.isNotEmpty()) return f to (found.last() as MimeMessage)
            runCatching { f.close(false) }
        }
        return null
    }

    private class Parts {
        var html: String? = null
        var text: String? = null
        val attachments = ArrayList<MailAttachment>()
    }

    private fun fileName(p: Part): String? = runCatching { p.fileName }.getOrNull()?.let {
        if (it.contains("=?")) runCatching { MimeUtility.decodeText(it) }.getOrDefault(it) else it
    }

    private fun textOf(p: Part): String = when (val c = p.content) {
        is String -> c
        is java.io.InputStream -> c.readBytes().toString(Charsets.UTF_8)
        else -> c?.toString() ?: ""
    }

    private fun walk(p: Part, path: List<Int>, acc: Parts) {
        val name = fileName(p)
        val attachment = Part.ATTACHMENT.equals(runCatching { p.disposition }.getOrNull(), ignoreCase = true) ||
            (name != null && !p.isMimeType("text/plain") && !p.isMimeType("text/html")) ||
            p.isMimeType("message/rfc822")
        when {
            p.isMimeType("multipart/*") -> {
                val mp = p.content as Multipart
                for (i in 0 until mp.count) walk(mp.getBodyPart(i), path + i, acc)
            }
            attachment -> acc.attachments += MailAttachment(
                name ?: if (p.isMimeType("message/rfc822")) "письмо.eml" else "вложение",
                runCatching { p.contentType.substringBefore(";").trim().lowercase() }.getOrDefault("application/octet-stream"),
                runCatching { p.size }.getOrDefault(-1),
                path,
            )
            p.isMimeType("text/html") -> if (acc.html == null) acc.html = textOf(p)
            p.isMimeType("text/plain") -> if (acc.text == null) acc.text = textOf(p)
        }
    }

    private fun addrs(a: Array<javax.mail.Address>?): List<Addr> = a?.mapNotNull { addr(it) }.orEmpty()

    /** Открыть письмо: текст, адреса, вложения. Отмечается прочитанным, как в обычной почте. */
    suspend fun open(messageId: String, folder: String?): MailMessage = withStore { st ->
        val (f, m) = find(st, messageId, folder, write = true)
            ?: throw MailException("Письмо не найдено в ящике — возможно, его удалили")
        try {
            val acc = Parts()
            walk(m, emptyList(), acc)
            if (f.mode == Folder.READ_WRITE) runCatching { m.setFlag(Flags.Flag.SEEN, true) }
            MailMessage(
                header = header(f, m),
                to = addrs(runCatching { m.getRecipients(Message.RecipientType.TO) }.getOrNull()),
                cc = addrs(runCatching { m.getRecipients(Message.RecipientType.CC) }.getOrNull()),
                replyTo = addrs(runCatching { m.replyTo }.getOrNull()),
                html = acc.html,
                text = acc.text,
                attachments = acc.attachments,
                references = runCatching { m.getHeader("References")?.firstOrNull() }.getOrNull(),
            )
        } finally {
            runCatching { f.close(false) }
        }
    }

    private fun partAt(m: MimeMessage, path: List<Int>): Part {
        var p: Part = m
        for (i in path) p = (p.content as Multipart).getBodyPart(i)
        return p
    }

    /** Сохранить вложение письма в файл (out — поток, выбранный пользователем). */
    suspend fun saveAttachment(messageId: String, folder: String?, path: List<Int>, out: () -> OutputStream) = withStore { st ->
        val (f, m) = find(st, messageId, folder, write = false) ?: throw MailException("Письмо не найдено в ящике")
        try {
            val p = partAt(m, path)
            out().use { o -> p.inputStream.use { it.copyTo(o) } }
        } finally {
            runCatching { f.close(false) }
        }
    }

    /** Достать пересылаемые вложения в память. */
    private fun forwardParts(st: IMAPStore, fwd: ForwardFrom): List<MimeBodyPart> {
        if (fwd.parts.isEmpty()) return emptyList()
        val (f, m) = find(st, fwd.messageId, fwd.folder, write = false)
            ?: throw MailException("Пересылаемое письмо не найдено в ящике")
        try {
            return fwd.parts.map { a ->
                val p = partAt(m, a.path)
                val bytes = p.inputStream.use { it.readBytes() }
                attachmentPart(bytes, a.mime, a.name)
            }
        } finally {
            runCatching { f.close(false) }
        }
    }

    private fun attachmentPart(bytes: ByteArray, mime: String, name: String) = MimeBodyPart().apply {
        dataHandler = DataHandler(ByteArrayDataSource(bytes, mime))
        fileName = MimeUtility.encodeText(name, "UTF-8", "B")
        disposition = Part.ATTACHMENT
    }

    /** "Иван <ivan@x.ru>, petr@y.ru" → адреса; имена кодируются правильно (кириллица). */
    fun parseAddresses(s: String): List<InternetAddress> {
        if (s.isBlank()) return emptyList()
        val parsed = try {
            InternetAddress.parse(s.replace(';', ','), false)
        } catch (e: Exception) {
            throw MailException("Не получается разобрать адрес: ${s.take(60)}")
        }
        return parsed.map { a ->
            val email = a.address?.trim() ?: ""
            if (!email.contains("@") || email.contains(" ")) throw MailException("Неверный адрес: $email")
            InternetAddress(email, a.personal, "UTF-8")
        }
    }

    private fun build(ctx: Context, st: IMAPStore?, o: Outgoing): MimeMessage {
        val to = parseAddresses(o.to)
        val cc = parseAddresses(o.cc)
        val msg = MimeMessage(session())
        msg.setFrom(InternetAddress(myEmail))
        if (to.isNotEmpty()) msg.setRecipients(Message.RecipientType.TO, to.toTypedArray())
        if (cc.isNotEmpty()) msg.setRecipients(Message.RecipientType.CC, cc.toTypedArray())
        msg.setSubject(o.subject, "UTF-8")
        msg.sentDate = Date()
        o.inReplyTo?.let { msg.setHeader("In-Reply-To", it) }
        o.references?.let { msg.setHeader("References", it) }

        val files = o.files.map { u -> fileAttachment(ctx, Uri.parse(u)) }
        val forwarded = if (st != null && o.forward != null) forwardParts(st, o.forward) else emptyList()
        if (files.isEmpty() && forwarded.isEmpty()) {
            msg.setText(o.body, "UTF-8")
        } else {
            val mp = MimeMultipart("mixed")
            mp.addBodyPart(MimeBodyPart().apply { setText(o.body, "UTF-8") })
            (files + forwarded).forEach { mp.addBodyPart(it) }
            msg.setContent(mp)
        }
        msg.saveChanges()
        return msg
    }

    /** Имя и размер файла с телефона — для списка вложений. */
    fun fileInfo(ctx: Context, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "файл"
        var size = -1L
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        return name to size
    }

    private fun fileAttachment(ctx: Context, uri: Uri): MimeBodyPart {
        val (name, _) = fileInfo(ctx, uri)
        val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
        val bytes = try {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) { null } ?: throw MailException("Не удалось прочитать файл «$name»")
        return attachmentPart(bytes, mime, name)
    }

    /**
     * Отправить письмо. Копия попадает в «Отправленные»: Gmail кладёт её сам,
     * у остальных проверяем и, если сервер не положил, кладём сами.
     * Возвращает Message-ID отправленного письма.
     */
    suspend fun send(ctx: Context, o: Outgoing): String {
        if (parseAddresses(o.to).isEmpty()) throw MailException("Укажите, кому отправить")
        val msg = withStore { st -> build(ctx, st, o) }
        withContext(Dispatchers.IO) {
            try {
                val t = session().getTransport("smtps")
                try {
                    t.connect(s.smtpHost, s.mailSmtpPort, s.mailEmail, SettingsStore.mailPassword)
                    t.sendMessage(msg, msg.allRecipients)
                } finally {
                    runCatching { t.close() }
                }
            } catch (e: Exception) {
                throw MailException("Не отправлено: " + Mail.explain(e))
            }
        }
        val id = msg.messageID
        if (s.mailPreset != MailPreset.GMAIL) {
            delay(1500)
            runCatching {
                withStore { st ->
                    val sent = roles(st)["sent"] ?: return@withStore
                    val f = st.getFolder(sent) as IMAPFolder
                    f.open(Folder.READ_WRITE)
                    try {
                        if (f.search(MessageIDTerm(id)).isEmpty()) {
                            msg.setFlag(Flags.Flag.SEEN, true)
                            f.appendMessages(arrayOf(msg))
                        }
                    } finally {
                        runCatching { f.close(false) }
                    }
                }
            }
        }
        return id
    }

    /**
     * Положить черновик в «Черновики» ящика (старый — oldId — убрать).
     * Возвращает Message-ID нового черновика или null, если папки черновиков нет.
     */
    suspend fun saveDraft(ctx: Context, o: Outgoing, oldId: String?): String? = withStore { st ->
        val drafts = roles(st)["drafts"] ?: return@withStore null
        val msg = build(ctx, st, o.copy(to = o.to.takeIf { runCatching { parseAddresses(it) }.isSuccess } ?: "",
            cc = o.cc.takeIf { runCatching { parseAddresses(it) }.isSuccess } ?: ""))
        msg.setFlags(Flags(Flags.Flag.DRAFT).apply { add(Flags.Flag.SEEN) }, true)
        val f = st.getFolder(drafts) as IMAPFolder
        f.open(Folder.READ_WRITE)
        try {
            f.appendMessages(arrayOf(msg))
            oldId?.let { removeFrom(f, it) }
        } finally {
            runCatching { f.close(true) }
        }
        msg.messageID
    }

    /** Убрать черновик из «Черновиков» (после отправки или отмены). */
    suspend fun deleteDraft(draftId: String) {
        runCatching {
            withStore { st ->
                val drafts = roles(st)["drafts"] ?: return@withStore
                val f = st.getFolder(drafts) as IMAPFolder
                f.open(Folder.READ_WRITE)
                try { removeFrom(f, draftId) } finally { runCatching { f.close(true) } }
            }
        }
    }

    private fun removeFrom(f: IMAPFolder, messageId: String) {
        f.search(MessageIDTerm(messageId)).forEach { it.setFlag(Flags.Flag.DELETED, true) }
    }

    /* ---------- Ответ и пересылка: тема, цитата, адреса ---------- */

    private val dateFmt = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", java.util.Locale("ru"))

    private fun fmtDate(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).format(dateFmt)

    private fun withPrefix(prefix: String, subject: String): String {
        val clean = subject.takeIf { it != "(без темы)" } ?: ""
        return if (clean.startsWith("$prefix:", ignoreCase = true)) clean else "$prefix: $clean".trimEnd()
    }

    /** Ответ (или «ответить всем»): кому, копия, тема, цитата, ссылки на переписку. */
    fun reply(m: MailMessage, all: Boolean): Outgoing {
        val me = myEmail.lowercase()
        val to = (m.replyTo.ifEmpty { listOfNotNull(m.header.from) })
        val toSet = to.map { it.email.lowercase() }.toSet()
        val cc = if (!all) emptyList() else (m.to + m.cc).filter {
            val e = it.email.lowercase(); e != me && e !in toSet
        }.distinctBy { it.email.lowercase() }
        val who = m.header.from?.full ?: ""
        val quote = m.plain.trimEnd().lineSequence().joinToString("\n") { "> $it" }
        val refs = listOfNotNull(m.references, m.header.messageId).joinToString(" ").takeIf { it.isNotBlank() }
        return Outgoing(
            mode = if (all) Outgoing.MODE_REPLY_ALL else Outgoing.MODE_REPLY,
            to = to.joinToString(", ") { it.full },
            cc = cc.joinToString(", ") { it.full },
            subject = withPrefix("Re", m.header.subject),
            body = "\n\n${fmtDate(m.header.date)}, $who пишет:\n$quote\n",
            inReplyTo = m.header.messageId,
            references = refs,
        )
    }

    /** Пересылка: тема, заголовок исходного письма и его текст, все вложения. */
    fun forward(m: MailMessage): Outgoing {
        val head = buildString {
            append("\n\n---------- Пересланное сообщение ----------\n")
            m.header.from?.let { append("От: ${it.full}\n") }
            append("Дата: ${fmtDate(m.header.date)}\n")
            append("Тема: ${m.header.subject}\n")
            if (m.to.isNotEmpty()) append("Кому: ${m.to.joinToString(", ") { it.full }}\n")
            append("\n")
        }
        return Outgoing(
            mode = Outgoing.MODE_FORWARD,
            subject = withPrefix("Fwd", m.header.subject),
            body = head + m.plain.trimEnd() + "\n",
            forward = m.header.messageId?.let { ForwardFrom(it, m.header.folder, m.attachments) },
        )
    }
}
