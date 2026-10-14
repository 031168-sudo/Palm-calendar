package ru.palmdate.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import ru.palmdate.app.model.ContactRef

/** «Связаться» с человеком: экран зовёт LocalCaller.current(контакт), платформа показывает выбор способа. */
val LocalCaller = staticCompositionLocalOf<(ContactRef) -> Unit> { {} }
