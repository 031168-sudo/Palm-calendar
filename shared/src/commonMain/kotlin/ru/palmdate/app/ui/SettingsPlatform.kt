package ru.palmdate.app.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable

/** То, что в настройках зависит от системы: язык, уведомления, почтовая программа, резервная копия, обновления. */
interface SettingsPlatform {
    /** "AUTO" / "RU" / "EN". */
    val langMode: String
    fun setLangMode(mode: String)

    /** Вызывается после любой правки настроек: перечитать календарь, переставить напоминания. */
    fun onSettingsChanged()

    /** Переключатель «Напоминать о днях рождения» (на Android просит разрешение на уведомления). */
    @Composable
    fun BirthdayNotifySwitch(on: Boolean, onChange: (Boolean) -> Unit)

    /** Разделы «Почта», «Данные», «О приложении» — у каждой платформы свои. */
    @Composable
    fun ColumnScope.ExtraSections()
}
