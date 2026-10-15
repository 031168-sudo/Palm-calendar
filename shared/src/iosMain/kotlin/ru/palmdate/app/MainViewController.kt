package ru.palmdate.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import platform.UIKit.UIViewController
import ru.palmdate.app.data.AppSettings
import ru.palmdate.app.data.CurrentSettings
import ru.palmdate.app.model.L10n
import ru.palmdate.app.ui.DayScreen
import ru.palmdate.app.ui.IosPlatform
import ru.palmdate.app.ui.IosSettings
import ru.palmdate.app.ui.LocalPlatform
import ru.palmdate.app.ui.theme.PalmTheme

private var settings by mutableStateOf(AppSettings())

/** Главный экран iPhone-приложения: его показывает SwiftUI (iosApp). */
fun MainViewController(): UIViewController {
    L10n.provider = IosStrings
    CurrentSettings.provider = { settings }
    CurrentSettings.updater = { block -> settings = block(settings) }
    val model = DayModel(CoroutineScope(SupervisorJob() + Dispatchers.Main), DemoBackend())
    model.start()
    return ComposeUIViewController {
        PalmTheme {
            CompositionLocalProvider(LocalPlatform provides IosPlatform) {
                DayScreen(model, IosSettings)
            }
        }
    }
}
