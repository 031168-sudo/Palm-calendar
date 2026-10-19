package ru.palmdate.app.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSDateComponents
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import ru.palmdate.app.AppBackend
import ru.palmdate.app.data.CurrentSettings
import ru.palmdate.app.str
import ru.palmdate.app.time.now
import ru.palmdate.app.time.plusDays
import kotlinx.datetime.LocalDate

/**
 * Напоминания о днях рождения на iPhone: в выбранное время в день рождения приходит уведомление
 * «Сегодня день рождения: …». Заранее ставим уведомления на ближайшие 60 дней (у iPhone лимит — 64 ожидающих)
 * и пересчитываем при запуске, возвращении в приложение и смене настроек.
 */
internal object IosBirthdays {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var backend: AppBackend? = null
    private const val DAYS = 60
    private const val PREFIX = "bd_"

    /** Запомнить источник событий и следить за возвращением в приложение. */
    fun start(b: AppBackend) {
        backend = b
        NSNotificationCenter.defaultCenter.addObserverForName(
            UIApplicationWillEnterForegroundNotification, null, NSOperationQueue.mainQueue,
        ) { _ -> reschedule() }
        reschedule()
    }

    /** Спросить у системы разрешение на уведомления; ответ — на главном потоке. */
    fun askPermission(onResult: (Boolean) -> Unit) {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { ok, _ -> NSOperationQueue.mainQueue.addOperationWithBlock { onResult(ok) } }
    }

    /** Заново поставить уведомления по текущим настройкам (или снять, если выключено). */
    fun reschedule() {
        val b = backend ?: return
        scope.launch {
            val center = UNUserNotificationCenter.currentNotificationCenter()
            center.removeAllPendingNotificationRequests()
            val s = CurrentSettings.current
            if (!s.birthdayNotify || !s.birthdaysShown) return@launch
            val today = LocalDate.now()
            val byDay = b.eventsBetween(today, today.plusDays(DAYS)).filter { it.fromContacts }.groupBy { it.start.date }
            byDay.keys.sorted().take(60).forEach { d ->
                val names = byDay[d].orEmpty().map { it.title }.distinct()
                val content = UNMutableNotificationContent().apply {
                    setTitle(if (names.size == 1) str("notif_birthday_one") else str("notif_birthday_many"))
                    setBody(names.joinToString(", "))
                    setSound(UNNotificationSound.defaultSound)
                }
                val comps = NSDateComponents().apply {
                    year = d.year.toLong(); month = d.monthNumber.toLong(); day = d.dayOfMonth.toLong()
                    hour = (s.birthdayNotifyAt / 60).toLong(); minute = (s.birthdayNotifyAt % 60).toLong()
                }
                val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(comps, repeats = false)
                center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(PREFIX + d, content, trigger), withCompletionHandler = null)
            }
        }
    }
}
