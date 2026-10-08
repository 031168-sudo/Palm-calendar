package ru.palmdate.app.data

import java.util.Properties
import javax.mail.Session

/**
 * Проверка подключения к почте: вход по IMAP (чтение) и по SMTP (отправка).
 * Само чтение и отправка писем появятся позже — здесь только настройка ящика.
 */
object Mail {
    /** Возвращает null, если всё хорошо, иначе — понятное описание ошибки. */
    fun check(s: AppSettings, password: String): String? {
        if (s.mailEmail.isBlank() || password.isBlank()) return "Укажите адрес и пароль приложения"
        if (s.imapHost.isBlank() || s.smtpHost.isBlank()) return "Укажите серверы IMAP и SMTP"
        val props = Properties().apply {
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "15000")
            put("mail.smtps.connectiontimeout", "15000")
            put("mail.smtps.timeout", "15000")
            put("mail.smtps.auth", "true")
        }
        val session = Session.getInstance(props)
        try {
            val store = session.getStore("imaps")
            try { store.connect(s.imapHost, s.mailImapPort, s.mailEmail, password) } finally { runCatching { store.close() } }
        } catch (e: Exception) {
            return "Чтение (IMAP): " + explain(e)
        }
        try {
            val t = session.getTransport("smtps")
            try { t.connect(s.smtpHost, s.mailSmtpPort, s.mailEmail, password) } finally { runCatching { t.close() } }
        } catch (e: Exception) {
            return "Отправка (SMTP): " + explain(e)
        }
        return null
    }

    private fun explain(e: Exception): String {
        val m = (e.message ?: e.javaClass.simpleName)
        return when {
            m.contains("AUTHENTICATIONFAILED", true) || m.contains("Invalid credentials", true) ||
                m.contains("535") || m.contains("authentication failed", true) ->
                "неверный адрес или пароль. Нужен именно пароль приложения, а не обычный пароль от почты"
            m.contains("UnknownHost", true) || e is java.net.UnknownHostException -> "сервер не найден, проверьте адрес и интернет"
            m.contains("timed out", true) -> "сервер не отвечает"
            else -> m.take(200)
        }
    }
}
