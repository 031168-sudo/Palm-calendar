package ru.palmdate.app.ui

import ru.palmdate.app.model.L10n
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

actual fun longDate(ms: Long): String =
    if (ms <= 0) "" else Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.forLanguageTag(L10n.language)))
