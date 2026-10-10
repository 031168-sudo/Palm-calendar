package ru.palmdate.app.data

import ru.palmdate.app.R
import ru.palmdate.app.str
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import org.json.JSONObject
import ru.palmdate.app.R
import ru.palmdate.app.str
import ru.palmdate.app.model.EventType

/** С какого вида открывается приложение. */
enum class StartView(@StringRes val labelRes: Int) {
    LAST(R.string.start_last), DAY(R.string.start_day), AGENDA(R.string.start_agenda);

    val label: String get() = str(labelRes)
}

/** Через что звонить по кнопке "Позвонить". */
enum class CallVia(@StringRes val labelRes: Int) {
    PHONE(R.string.call_phone), WHATSAPP(R.string.call_whatsapp), TELEGRAM(R.string.call_telegram);

    val label: String get() = str(labelRes)
}

/** Почтовые ящики с готовыми серверами. */
enum class MailPreset(@StringRes val labelRes: Int, val imap: String, val smtp: String) {
    GMAIL(R.string.preset_gmail, "imap.gmail.com", "smtp.gmail.com"),
    YANDEX(R.string.preset_yandex, "imap.yandex.ru", "smtp.yandex.ru"),
    MAILRU(R.string.preset_mailru, "imap.mail.ru", "smtp.mail.ru"),
    CUSTOM(R.string.preset_custom, "", "");

    val label: String get() = str(labelRes)
}

/** Как работать с почтой: через почтовую программу телефона или встроенной почтой (нужен пароль приложения). */
enum class MailMode(@StringRes val labelRes: Int) {
    APP(R.string.mailmode_app), BUILTIN(R.string.mailmode_builtin);

    val label: String get() = str(labelRes)
}

/** Все настройки DateBook. Пароль почты хранится отдельно и зашифрованно. */
data class AppSettings(
    // События по умолчанию
    val durations: Map<EventType, Int> = EventType.entries.associateWith { it.defaultMinutes },
    val reminders: Map<EventType, List<Int>> = EventType.entries.associateWith { it.defaultReminders },
    val calendarFixedId: Long? = null,          // null — последний использованный
    val timeStep: Int = 15,
    // Весь день: напоминание в этот день в N минут от полуночи (540 = 9:00); null — без напоминания
    val allDayReminderAt: Int? = 540,
    // Вид
    val startView: StartView = StartView.LAST,
    val weekStartsSunday: Boolean = false,
    val dayFrom: Int = 8,
    val dayTo: Int = 18,
    // Контакты
    val birthdaysShown: Boolean = true,
    val birthdayNotify: Boolean = false,
    val birthdayNotifyAt: Int = 540,
    val callVia: CallVia = CallVia.PHONE,
    // Почта
    val mailMode: MailMode = MailMode.APP,
    val mailPreset: MailPreset = MailPreset.GMAIL,
    val mailEmail: String = "",
    val mailImapHost: String = "",
    val mailImapPort: Int = 993,
    val mailSmtpHost: String = "",
    val mailSmtpPort: Int = 465,
) {
    fun duration(t: EventType) = durations[t] ?: t.defaultMinutes
    fun remindersFor(t: EventType) = reminders[t] ?: t.defaultReminders

    /** Напоминание для события на весь день: минус минуты = "в этот день в ..." (так его хранит Android/Google). */
    val allDayReminders: List<Int> get() = allDayReminderAt?.let { listOf(-it) } ?: emptyList()

    val imapHost get() = if (mailPreset == MailPreset.CUSTOM) mailImapHost else mailPreset.imap
    val smtpHost get() = if (mailPreset == MailPreset.CUSTOM) mailSmtpHost else mailPreset.smtp

    fun toJson(): JSONObject = JSONObject().apply {
        put("durations", JSONObject().apply { durations.forEach { (k, v) -> put(k.name, v) } })
        put("reminders", JSONObject().apply { reminders.forEach { (k, v) -> put(k.name, v.joinToString(",")) } })
        put("calendarFixedId", calendarFixedId ?: -1)
        put("timeStep", timeStep)
        put("allDayReminderAt", allDayReminderAt ?: -1)
        put("startView", startView.name)
        put("weekStartsSunday", weekStartsSunday)
        put("dayFrom", dayFrom)
        put("dayTo", dayTo)
        put("birthdaysShown", birthdaysShown)
        put("birthdayNotify", birthdayNotify)
        put("birthdayNotifyAt", birthdayNotifyAt)
        put("callVia", callVia.name)
        put("mailMode", mailMode.name)
        put("mailPreset", mailPreset.name)
        put("mailEmail", mailEmail)
        put("mailImapHost", mailImapHost)
        put("mailImapPort", mailImapPort)
        put("mailSmtpHost", mailSmtpHost)
        put("mailSmtpPort", mailSmtpPort)
    }

    companion object {
        fun fromJson(j: JSONObject): AppSettings {
            val d = AppSettings()
            fun durs() = j.optJSONObject("durations")?.let { o ->
                EventType.entries.associateWith { o.optInt(it.name, d.duration(it)) }
            } ?: d.durations
            fun rems() = j.optJSONObject("reminders")?.let { o ->
                EventType.entries.associateWith { t ->
                    if (!o.has(t.name)) d.remindersFor(t)
                    else o.getString(t.name).split(",").mapNotNull { it.trim().toIntOrNull() }
                }
            } ?: d.reminders
            return AppSettings(
                durations = durs(),
                reminders = rems(),
                calendarFixedId = j.optLong("calendarFixedId", -1).takeIf { it >= 0 },
                timeStep = j.optInt("timeStep", 15),
                allDayReminderAt = j.optInt("allDayReminderAt", 540).takeIf { it >= 0 },
                startView = runCatching { StartView.valueOf(j.optString("startView")) }.getOrDefault(d.startView),
                weekStartsSunday = j.optBoolean("weekStartsSunday", false),
                dayFrom = j.optInt("dayFrom", 8),
                dayTo = j.optInt("dayTo", 18),
                birthdaysShown = j.optBoolean("birthdaysShown", true),
                birthdayNotify = j.optBoolean("birthdayNotify", false),
                birthdayNotifyAt = j.optInt("birthdayNotifyAt", 540),
                callVia = runCatching { CallVia.valueOf(j.optString("callVia")) }.getOrDefault(d.callVia),
                mailMode = runCatching { MailMode.valueOf(j.optString("mailMode")) }.getOrDefault(d.mailMode),
                mailPreset = runCatching { MailPreset.valueOf(j.optString("mailPreset")) }.getOrDefault(d.mailPreset),
                mailEmail = j.optString("mailEmail", ""),
                mailImapHost = j.optString("mailImapHost", ""),
                mailImapPort = j.optInt("mailImapPort", 993),
                mailSmtpHost = j.optString("mailSmtpHost", ""),
                mailSmtpPort = j.optInt("mailSmtpPort", 465),
            )
        }
    }
}

/**
 * Хранилище настроек. Текущие настройки — живое состояние: экраны, которые их читают,
 * перерисовываются сами, когда что-то поменяли.
 */
object SettingsStore {
    private lateinit var prefs: SharedPreferences
    private var secret: SharedPreferences? = null

    var current by mutableStateOf(AppSettings())
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        current = prefs.getString("json", null)
            ?.let { runCatching { AppSettings.fromJson(JSONObject(it)) }.getOrNull() } ?: AppSettings()
        // Пароль почты — в зашифрованном хранилище (ключ в защищённом хранилище Android)
        secret = runCatching {
            val key = androidx.security.crypto.MasterKey.Builder(context.applicationContext)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build()
            androidx.security.crypto.EncryptedSharedPreferences.create(
                context.applicationContext, "secret", key,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrNull()
    }

    fun update(block: (AppSettings) -> AppSettings) {
        current = block(current)
        prefs.edit().putString("json", current.toJson().toString()).apply()
    }

    fun replace(s: AppSettings) = update { s }

    var mailPassword: String
        get() = (secret?.getString("mail_password", "") ?: "").filterNot { it.isWhitespace() }
        // В паролях приложений пробелов не бывает — Google показывает пароль группами через пробел
        set(v) { secret?.edit()?.putString("mail_password", v.filterNot { it.isWhitespace() })?.apply() }
}
