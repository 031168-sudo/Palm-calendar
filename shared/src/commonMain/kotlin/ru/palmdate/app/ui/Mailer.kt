package ru.palmdate.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import ru.palmdate.app.model.PalmEvent

/** Почта из любого места экрана: открыть письмо события в почтовой программе. */
class Mailer(
    val open: (PalmEvent) -> Unit,
)

val LocalMailer = staticCompositionLocalOf { Mailer {} }
