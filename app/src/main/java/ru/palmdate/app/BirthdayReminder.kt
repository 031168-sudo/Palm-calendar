package ru.palmdate.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ru.palmdate.app.data.ContactsRepository
import ru.palmdate.app.data.SettingsStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Напоминания о днях рождения из контактов: раз в день в выбранное время
 * DateBook сам показывает уведомление "Сегодня день рождения у …".
 */
class BirthdayReminder : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SettingsStore.init(context)
        val s = SettingsStore.current
        if (s.birthdayNotify && s.birthdaysShown) notifyToday(context)
        schedule(context) // следующий раз — завтра
    }

    private fun notifyToday(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return
        val today = LocalDate.now()
        val names = ContactsRepository(context).birthdays()
            .filter { it.month == today.monthValue && it.day == today.dayOfMonth }
            .map { b -> b.name + (b.year?.let { " (${today.year - it})" } ?: "") }
        if (names.isEmpty()) return

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Дни рождения", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(if (names.size == 1) "Сегодня день рождения" else "Сегодня дни рождения")
            .setContentText(names.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(names.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, n)
    }

    companion object {
        private const val CHANNEL = "birthdays"
        private const val NOTIFICATION_ID = 1001

        /** Запланировать (или снять) ежедневное напоминание по текущим настройкам. */
        fun schedule(context: Context) {
            SettingsStore.init(context)
            val s = SettingsStore.current
            val am = context.getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getBroadcast(
                context, 0, Intent(context, BirthdayReminder::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.cancel(pi)
            if (!s.birthdayNotify || !s.birthdaysShown) return
            var at = LocalDate.now().atStartOfDay().plusMinutes(s.birthdayNotifyAt.toLong())
            if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
            val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            // Неточный будильник: точность в пару минут, зато без особых разрешений
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }
}

/** После перезагрузки телефона заново ставим напоминание о днях рождения. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) BirthdayReminder.schedule(context)
    }
}
