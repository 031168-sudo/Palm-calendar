package ru.palmdate.app.data

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri

/** Почтовая программа телефона: Gmail, Яндекс Почта, Outlook… */
data class MailApp(val pkg: String, val label: String, val icon: Drawable?)

/**
 * Почта «через почтовую программу»: DateBook открывает Gmail (или другую выбранную программу)
 * на новом письме или на поиске — всё остальное делается в самой программе.
 */
class MailApps(private val context: Context) {
    private val pm = context.packageManager
    private val prefs = context.getSharedPreferences("mail_app", Context.MODE_PRIVATE)

    /** Установленные почтовые программы (те, что умеют писать письма). */
    fun installed(): List<MailApp> =
        pm.queryIntentActivities(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { pkg ->
                val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                MailApp(pkg, info?.let { pm.getApplicationLabel(it).toString() } ?: pkg,
                    runCatching { pm.getApplicationIcon(pkg) }.getOrNull())
            }
            .sortedBy { it.label.lowercase() }

    /** Запомненная программа («всегда открывать в ней»), если она ещё установлена. */
    fun remembered(): String? = prefs.getString(KEY, null)?.takeIf { pkg ->
        runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)
    }

    fun remember(pkg: String?) {
        prefs.edit().apply { if (pkg == null) remove(KEY) else putString(KEY, pkg) }.apply()
    }

    fun label(pkg: String): String =
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    /** Новое письмо в программе: адрес и тема уже вписаны. */
    fun compose(pkg: String, to: String?, subject: String?): Intent {
        val uri = buildString {
            append("mailto:")
            to?.let { append(Uri.encode(it, "@")) }
            subject?.takeIf { it.isNotBlank() }?.let { append("?subject=").append(Uri.encode(it)) }
        }
        return Intent(Intent.ACTION_SENDTO, Uri.parse(uri)).apply {
            to?.let { putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
            subject?.takeIf { it.isNotBlank() }?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            setPackage(pkg)
        }
    }

    /** Просто открыть программу (для поиска письма). */
    fun launch(pkg: String): Intent? = pm.getLaunchIntentForPackage(pkg)

    private companion object { const val KEY = "pkg" }
}
