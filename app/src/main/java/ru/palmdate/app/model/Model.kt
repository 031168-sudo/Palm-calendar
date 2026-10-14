package ru.palmdate.app.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import ru.palmdate.app.ui.theme.Palm

// Типы, итоги, повторы, напоминания, события (PalmEvent, NewEvent) — в модуле shared (тот же пакет).
// Здесь остаётся только оформление: значок и цвет типа события.

/** Значок типа события. */
val EventType.icon: ImageVector
    get() = when (this) {
        EventType.CALL -> Icons.Outlined.Phone
        EventType.MEETING -> Icons.Outlined.Groups
        EventType.TASK -> Icons.Outlined.TaskAlt
        EventType.TRIP -> Icons.Outlined.Place
        EventType.MAIL -> Icons.Outlined.Email
        EventType.OTHER -> Icons.Outlined.Event
        EventType.BIRTHDAY -> Icons.Outlined.Cake
    }

/** Цвет типа с поправкой на тему (в тёмной — светлее). */
val EventType.color: Color get() = Palm.typeColor(Color(baseColorArgb))
