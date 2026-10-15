package ru.palmdate.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController
import ru.palmdate.app.time.now
import ru.palmdate.app.time.plusDays
import ru.palmdate.app.data.AppSettings
import ru.palmdate.app.data.CurrentSettings
import ru.palmdate.app.model.L10n
import ru.palmdate.app.ui.DayScreen
import ru.palmdate.app.ui.IosPlatform
import ru.palmdate.app.ui.IosSettings
import ru.palmdate.app.ui.LocalPlatform
import ru.palmdate.app.ui.theme.PalmTheme

private var settings by mutableStateOf(AppSettings())
private var settingsLoaded = false

/** Главный экран iPhone-приложения: его показывает SwiftUI (iosApp). */
fun MainViewController(): UIViewController {
    L10n.provider = IosStrings
    if (!settingsLoaded) { settings = IosStore.loadSettings(); settingsLoaded = true }
    CurrentSettings.provider = { settings }
    CurrentSettings.updater = { block -> settings = block(settings); IosStore.saveSettings(settings) }
    val backend = EventKitBackend()
    val model = DayModel(CoroutineScope(SupervisorJob() + Dispatchers.Main), backend)
    model.start()
    if (platform.Foundation.NSProcessInfo.processInfo.environment["DATEBOOK_SEED"] != null) seedDemo(backend)
    return ComposeUIViewController {
        PalmTheme {
            CompositionLocalProvider(LocalPlatform provides IosPlatform) {
                DayScreen(model, IosSettings)
            }
        }
    }
}

/** Только для проверки на симуляторе: кладёт в календарь три тестовых события (запуск с DATEBOOK_SEED). */
private fun seedDemo(backend: EventKitBackend) {
    kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
        kotlinx.coroutines.delay(2500)
        val today = kotlinx.datetime.LocalDate.now()
        if (backend.eventsBetween(today, today.plusDays(1)).isNotEmpty()) return@launch
        val cal = backend.allCalendars().firstOrNull { it.writable }?.id ?: return@launch
        fun ev(type: ru.palmdate.app.model.EventType, title: String, h: Int, min: Int) = ru.palmdate.app.model.NewEvent(
            type = type, contact = null, title = title, start = kotlinx.datetime.LocalDateTime(today.year, today.monthNumber, today.dayOfMonth, h, min),
            minutes = min.let { 30 }, note = null, calendarId = cal, reminders = listOf(10),
        )
        backend.create(ev(ru.palmdate.app.model.EventType.CALL, "Иван Петров", 10, 0))
        backend.create(ev(ru.palmdate.app.model.EventType.MEETING, "Ольга Смирнова", 14, 0))
        backend.create(ev(ru.palmdate.app.model.EventType.TASK, "отправить отчёт", 16, 30))
    }
}
